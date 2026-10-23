package com.sueleyman.suelostodo.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class DriveFile(val id: String, val name: String)

class DriveAuthException(msg: String) : IOException(msg)

/**
 * Minimal Google Drive REST v3 client working in the hidden appDataFolder.
 * Uses an OAuth access token obtained via Google Identity AuthorizationClient.
 */
class DriveClient(private val token: String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private fun req(url: String) = Request.Builder().url(url).header("Authorization", "Bearer $token")

    private fun exec(r: Request): okhttp3.Response {
        val resp = http.newCall(r).execute()
        if (resp.code == 401 || resp.code == 403) {
            val body = resp.body?.string().orEmpty()
            resp.close()
            throw DriveAuthException("Drive-Zugriff verweigert (${resp.code}): ${body.take(200)}")
        }
        if (!resp.isSuccessful) {
            val body = resp.body?.string().orEmpty()
            resp.close()
            throw IOException("Drive-Fehler ${resp.code}: ${body.take(200)}")
        }
        return resp
    }

    suspend fun listFiles(): List<DriveFile> = withContext(Dispatchers.IO) {
        val out = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("spaces", "appDataFolder")
                .addQueryParameter("fields", "nextPageToken,files(id,name)")
                .addQueryParameter("pageSize", "1000")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build().toString()
            val body = exec(req(url).get().build()).use { it.body!!.string() }
            val obj = json.parseToJsonElement(body).jsonObject
            obj["files"]?.jsonArray?.forEach {
                val f = it.jsonObject
                out += DriveFile(f.str("id"), f.str("name"))
            }
            pageToken = obj["nextPageToken"]?.jsonPrimitive?.content
        } while (pageToken != null)
        out
    }

    suspend fun userEmail(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val body = exec(req("https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)").get().build())
                .use { it.body!!.string() }
            json.parseToJsonElement(body).jsonObject["user"]?.jsonObject?.str("emailAddress")
        }.getOrNull()
    }

    suspend fun downloadText(id: String): String = withContext(Dispatchers.IO) {
        exec(req("https://www.googleapis.com/drive/v3/files/$id?alt=media").get().build()).use { it.body!!.string() }
    }

    suspend fun downloadToFile(id: String, target: File) = withContext(Dispatchers.IO) {
        val tmp = File(target.parentFile, target.name + ".part")
        exec(req("https://www.googleapis.com/drive/v3/files/$id?alt=media").get().build()).use { resp ->
            resp.body!!.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        }
        tmp.renameTo(target)
    }

    suspend fun create(name: String, content: RequestBody): String = withContext(Dispatchers.IO) {
        val meta = """{"name":"$name","parents":["appDataFolder"]}"""
            .toRequestBody("application/json; charset=UTF-8".toMediaType())
        val multipart = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(meta)
            .addPart(content)
            .build()
        val body = exec(
            req("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
                .post(multipart).build()
        ).use { it.body!!.string() }
        json.parseToJsonElement(body).jsonObject.str("id")
    }

    suspend fun updateContent(id: String, content: RequestBody) = withContext(Dispatchers.IO) {
        exec(
            req("https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=media&fields=id")
                .patch(content).build()
        ).close()
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val resp = http.newCall(req("https://www.googleapis.com/drive/v3/files/$id").delete().build()).execute()
        resp.close() // 404 is fine (already gone)
    }

    companion object {
        fun textBody(s: String) = s.toRequestBody("application/json; charset=UTF-8".toMediaType())
        fun fileBody(f: File, mime: String) =
            f.asRequestBody(mime.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType())
    }
}

private fun JsonObject.str(key: String) = this[key]!!.jsonPrimitive.content
