package com.sueleyman.suelostodo.io

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import com.sueleyman.suelostodo.BuildConfig
import com.sueleyman.suelostodo.MainActivity
import com.sueleyman.suelostodo.SuelosToDoApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

private val http = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS)
    .build()
private val json = Json { ignoreUnknownKeys = true }

// =================================================================== OAuth 2.0 (Auth-Code + PKCE)

data class MsAccountState(val signedIn: Boolean, val account: String?, val error: String? = null)

object MsAuth {
    const val REDIRECT_URI = "suelostodo://auth"
    private const val SCOPES = "offline_access User.Read Tasks.ReadWrite"
    private const val BASE = "https://login.microsoftonline.com/common/oauth2/v2.0"

    private val _state = MutableStateFlow(MsAccountState(false, null))
    val state: StateFlow<MsAccountState> = _state

    private fun sp(c: Context) = c.getSharedPreferences("ms", Context.MODE_PRIVATE)

    fun refreshState(c: Context) {
        _state.value = MsAccountState(sp(c).getString("refresh", null) != null, sp(c).getString("account", null))
    }

    fun clientId(c: Context): String = sp(c).getString("clientId", null)?.trim()?.ifBlank { null } ?: BuildConfig.MS_CLIENT_ID
    fun setClientId(c: Context, id: String) = sp(c).edit().putString("clientId", id.trim()).apply()

    private fun b64url(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun startLogin(c: Context) {
        val cid = clientId(c)
        require(cid.isNotBlank()) { "Keine Microsoft Client-ID hinterlegt" }
        val verifier = b64url(ByteArray(48).also { SecureRandom().nextBytes(it) })
        val challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val st = b64url(ByteArray(16).also { SecureRandom().nextBytes(it) })
        sp(c).edit().putString("verifier", verifier).putString("state", st).apply()
        val uri = Uri.parse("$BASE/authorize").buildUpon()
            .appendQueryParameter("client_id", cid)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_mode", "query")
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("state", st)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("prompt", "select_account")
            .build()
        runCatching { CustomTabsIntent.Builder().build().launchUrl(c, uri) }
            .onFailure { c.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Called by [MsAuthRedirectActivity]. */
    suspend fun handleRedirect(c: Context, uri: Uri) {
        val err = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error")
        if (err != null) { _state.value = _state.value.copy(error = "Microsoft-Anmeldung: $err"); return }
        val code = uri.getQueryParameter("code") ?: return
        if (uri.getQueryParameter("state") != sp(c).getString("state", null)) {
            _state.value = _state.value.copy(error = "Ungültige Anmeldeantwort (state)"); return
        }
        runCatching {
            tokenRequest(
                c, FormBody.Builder()
                    .add("client_id", clientId(c))
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", REDIRECT_URI)
                    .add("code_verifier", sp(c).getString("verifier", "")!!)
                    .add("scope", SCOPES)
                    .build()
            )
            val me = GraphClient(accessToken(c)).get("https://graph.microsoft.com/v1.0/me?\$select=userPrincipalName,mail,displayName")
            val acc = me.s("mail") ?: me.s("userPrincipalName") ?: me.s("displayName")
            sp(c).edit().putString("account", acc).apply()
        }.onFailure { _state.value = MsAccountState(false, null, "Microsoft-Anmeldung fehlgeschlagen: ${it.message}"); return }
        refreshState(c)
    }

    private suspend fun tokenRequest(c: Context, body: FormBody) = withContext(Dispatchers.IO) {
        val resp = withNetRetry { http.newCall(Request.Builder().url("$BASE/token").post(body).build()).execute() }
        val text = resp.use { it.body?.string().orEmpty() }
        val o = json.parseToJsonElement(text).jsonObject
        if (!resp.isSuccessful) throw IOException(o.s("error_description")?.lineSequence()?.firstOrNull() ?: "HTTP ${resp.code}")
        val e = sp(c).edit()
            .putString("access", o.s("access_token"))
            .putLong("expires", System.currentTimeMillis() + ((o.s("expires_in")?.toLongOrNull() ?: 3600L) - 120) * 1000)
        o.s("refresh_token")?.let { e.putString("refresh", it) }
        e.apply()
    }

    suspend fun accessToken(c: Context): String {
        val p = sp(c)
        val access = p.getString("access", null)
        if (access != null && System.currentTimeMillis() < p.getLong("expires", 0)) return access
        val refresh = p.getString("refresh", null) ?: throw IOException("Nicht bei Microsoft angemeldet")
        tokenRequest(
            c, FormBody.Builder()
                .add("client_id", clientId(c))
                .add("grant_type", "refresh_token")
                .add("refresh_token", refresh)
                .add("scope", SCOPES)
                .build()
        )
        return p.getString("access", null)!!
    }

    fun signOut(c: Context) {
        sp(c).edit().remove("access").remove("refresh").remove("expires").remove("account").apply()
        refreshState(c)
    }
}

/** Receives suelostodo://auth?code=… after the Microsoft login. */
class MsAuthRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        val app = applicationContext as SuelosToDoApp
        if (uri != null) app.appScope.launch { MsAuth.handleRedirect(app, uri) }
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TRANSFER, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}

// =================================================================== network retry

/** Called while waiting after a network error, e.g. to show "Netzwerk weg – neuer Versuch in 8 s". */
typealias RetryListener = (String) -> Unit

private val RETRY_WAITS = longArrayOf(2, 4, 8, 15, 30, 30, 60)

/**
 * Retries [block] on transient network errors (no DNS, no connection, timeouts, connection resets).
 * Mobile networks often drop out for a few seconds – e.g. "Unable to resolve host graph.microsoft.com".
 */
suspend fun <T> withNetRetry(onRetry: RetryListener? = null, block: suspend () -> T): T {
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: IOException) {
            if (e is GraphException || attempt >= RETRY_WAITS.size) throw e
            val wait = RETRY_WAITS[attempt++]
            onRetry?.invoke("Keine Verbindung zu Microsoft – neuer Versuch in $wait s (${attempt}/${RETRY_WAITS.size}) …")
            delay(wait * 1000)
        }
    }
}

// =================================================================== Graph client

class GraphClient(private val token: String, private val onRetry: RetryListener? = null) {
    private fun req(url: String) = Request.Builder().url(url).header("Authorization", "Bearer $token")

    private suspend fun exec(r: Request): okhttp3.Response = withContext(Dispatchers.IO) {
        var attempt = 0
        while (true) {
            val resp = withNetRetry(onRetry) { http.newCall(r).execute() }
            if ((resp.code == 429 || resp.code == 503) && attempt < 4) {
                val wait = resp.header("Retry-After")?.toLongOrNull() ?: (2L shl attempt)
                resp.close(); attempt++
                delay(wait * 1000)
                continue
            }
            if (!resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                resp.close()
                val msg = runCatching { json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.s("message") }.getOrNull()
                throw GraphException(resp.code, msg ?: "HTTP ${resp.code}")
            }
            return@withContext resp
        }
        @Suppress("UNREACHABLE_CODE")
        throw IllegalStateException()
    }

    suspend fun get(url: String): JsonObject = withNetRetry(onRetry) {
        exec(req(url).get().build()).use { json.parseToJsonElement(it.body!!.string()).jsonObject }
    }

    suspend fun getAll(url: String): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        var next: String? = url
        while (next != null) {
            val o = get(next)
            (o["value"] as? JsonArray)?.forEach { out += it.jsonObject }
            next = o.s("@odata.nextLink")
        }
        return out
    }

    suspend fun bytes(url: String): ByteArray = withNetRetry(onRetry) { exec(req(url).get().build()).use { it.body!!.bytes() } }

    suspend fun post(url: String, body: JsonObject): JsonObject =
        exec(req(url).post(body.toString().toRequestBody("application/json".toMediaType())).build())
            .use { r -> r.body!!.string().let { if (it.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(it).jsonObject } }
}

class GraphException(val code: Int, msg: String) : IOException(msg)

private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.o(k: String) = this[k] as? JsonObject

// =================================================================== To Do import / export

data class MsList(val id: String, val name: String, val wellKnown: String?)

object MsTodo {
    private const val G = "https://graph.microsoft.com/v1.0/me/todo"
    private const val MAX_UPLOAD = 3 * 1024 * 1024

    suspend fun lists(c: Context): List<MsList> =
        GraphClient(MsAuth.accessToken(c)).getAll("$G/lists").map {
            MsList(it.s("id")!!, it.s("displayName") ?: "Liste", it.s("wellknownListName")?.takeIf { w -> w != "none" })
        }

    suspend fun import(
        c: Context,
        lists: List<MsList>,
        withAttachments: Boolean,
        progress: (String) -> Unit,
    ): ImportResult {
        val repo = (c.applicationContext as SuelosToDoApp).repo
        var total = ImportResult(0, 0, 0)
        for ((li, l) in lists.withIndex()) {
            val prefix = "Liste ${li + 1}/${lists.size} \"${l.name}\""
            progress("$prefix: lade Aufgaben …")
            // fresh token per list: long imports can outlive the 1-hour access token
            val g = GraphClient(MsAuth.accessToken(c), progress)
            // $expand=checklistItems loads the steps together with the tasks (1 request per 100 tasks
            // instead of 1 per task). Falls back to separate requests if the server refuses.
            val tasks = try {
                g.getAll("$G/lists/${l.id}/tasks?\$top=100&\$expand=checklistItems")
            } catch (e: GraphException) {
                g.getAll("$G/lists/${l.id}/tasks?\$top=100")
            }
            val items = ArrayList<ImportItem>(tasks.size)
            for ((i, t) in tasks.withIndex()) {
                if (i % 5 == 0) progress("$prefix: ${i + 1} / ${tasks.size}")
                val tid = t.s("id")!!
                val steps = (t["checklistItems"] as? JsonArray)?.map { it.jsonObject }
                    ?: runCatching { g.getAll("$G/lists/${l.id}/tasks/$tid/checklistItems") }
                        .getOrElse { e -> if (e is GraphException) emptyList() else throw e }
                val atts = if (withAttachments && (t["hasAttachments"] as? JsonPrimitive)?.booleanOrNull == true) {
                    try {
                        g.getAll("$G/lists/${l.id}/tasks/$tid/attachments").mapNotNull { a ->
                            val aid = a.s("id") ?: return@mapNotNull null
                            ImportAttachment(
                                a.s("name") ?: "Anhang",
                                a.s("contentType") ?: "application/octet-stream",
                                g.bytes("$G/lists/${l.id}/tasks/$tid/attachments/$aid/\$value")
                            )
                        }
                    } catch (e: GraphException) { emptyList() }
                } else emptyList()
                items += ImportItem(
                    id = "ms-$tid",
                    title = t.s("title")?.ifBlank { null } ?: "(ohne Titel)",
                    note = t.o("body")?.let { b ->
                        val content = b.s("content").orEmpty()
                        if (b.s("contentType") == "html") Html.fromHtml(content, Html.FROM_HTML_MODE_COMPACT).toString().trim() else content.trim()
                    } ?: "",
                    done = t.s("status") == "completed",
                    important = t.s("importance") == "high",
                    dueDate = t.o("dueDateTime")?.let { parseDtz(it)?.let { ms -> Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() } },
                    reminderAt = if ((t["isReminderOn"] as? JsonPrimitive)?.booleanOrNull == true) t.o("reminderDateTime")?.let { parseDtz(it) } else null,
                    completedAt = t.o("completedDateTime")?.let { parseDtz(it) },
                    createdAt = t.s("createdDateTime")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
                    children = steps.map { st ->
                        ImportItem(
                            id = "ms-" + st.s("id"),
                            title = st.s("displayName") ?: "",
                            done = (st["isChecked"] as? JsonPrimitive)?.booleanOrNull == true,
                        )
                    }.filter { it.title.isNotBlank() }.toMutableList(),
                    attachments = atts,
                )
            }
            // save every list right away – if the connection breaks later, finished lists are kept
            progress("$prefix: speichere …")
            val r = repo.importLists(listOf(ImportList(id = "ms-${l.id}", name = l.name, items = items)))
            total = ImportResult(total.lists + r.lists, total.tasks + r.tasks, total.attachments + r.attachments, total.flattened + r.flattened)
        }
        return total
    }

    /**
     * Exports Sülos ToDo lists. Level 1 = To-Do task, level 2 = step, level 3 = step prefixed with "↳".
     * Tasks whose title already exists in the target list are skipped, so a repeated export doesn't duplicate.
     */
    suspend fun export(c: Context, lists: List<ExportList>, progress: (String) -> Unit): String {
        val g = GraphClient(MsAuth.accessToken(c), progress)
        val existing = lists(c)
        var created = 0; var skipped = 0; var steps = 0; var files = 0; var tooBig = 0
        for (l in lists) {
            progress("Exportiere \"${l.list.name}\" …")
            val targetId = existing.firstOrNull { it.name.equals(l.list.name, true) }?.id
                ?: g.post("$G/lists", buildJsonObject { put("displayName", l.list.name) }).s("id")!!
            val titles = g.getAll("$G/lists/$targetId/tasks?\$select=title&\$top=100").mapNotNull { it.s("title") }.toHashSet()
            for (root in l.roots) {
                val t = root.task
                if (t.title in titles) { skipped++; continue }
                val body = buildJsonObject {
                    put("title", t.title)
                    put("status", if (t.done) "completed" else "notStarted")
                    put("importance", if (t.important) "high" else "normal")
                    if (t.note.isNotBlank()) putJsonObject("body") { put("content", t.note); put("contentType", "text") }
                    t.dueDate?.let { d ->
                        putJsonObject("dueDateTime") { put("dateTime", LocalDate.ofEpochDay(d).atStartOfDay().format(ISO)); put("timeZone", "UTC") }
                    }
                    t.reminderAt?.let { r ->
                        put("isReminderOn", true)
                        putJsonObject("reminderDateTime") {
                            put("dateTime", LocalDateTime.ofInstant(Instant.ofEpochMilli(r), ZoneOffset.UTC).format(ISO)); put("timeZone", "UTC")
                        }
                    }
                }
                val tid = g.post("$G/lists/$targetId/tasks", body).s("id")!!
                created++
                progress("\"${l.list.name}\": $created exportiert")
                for (child in root.children) {
                    g.post("$G/lists/$targetId/tasks/$tid/checklistItems", step(child.task.title, child.task.done)); steps++
                    for (gc in child.children) {
                        g.post("$G/lists/$targetId/tasks/$tid/checklistItems", step("↳ " + gc.task.title, gc.task.done)); steps++
                    }
                }
                val repo = (c.applicationContext as SuelosToDoApp).repo
                for (a in root.attachments) {
                    val f = repo.attachmentFile(a.id)
                    if (!f.exists()) continue
                    if (f.length() > MAX_UPLOAD) { tooBig++; continue }
                    val b64 = withContext(Dispatchers.IO) { Base64.encodeToString(f.readBytes(), Base64.NO_WRAP) }
                    g.post("$G/lists/$targetId/tasks/$tid/attachments", buildJsonObject {
                        put("@odata.type", "#microsoft.graph.taskFileAttachment")
                        put("name", a.fileName)
                        put("contentType", a.mimeType)
                        put("contentBytes", b64)
                    })
                    files++
                }
            }
        }
        return buildString {
            append("$created Aufgabe(n), $steps Schritt(e)")
            if (files > 0) append(", $files Anhang/Anhänge")
            append(" nach Microsoft To Do exportiert.")
            if (skipped > 0) append(" $skipped bereits vorhanden – übersprungen.")
            if (tooBig > 0) append(" $tooBig Anhang/Anhänge über 3 MB nicht übertragen.")
        }
    }

    private fun step(title: String, done: Boolean) = buildJsonObject { put("displayName", title); put("isChecked", done) }

    private val ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    /** Parses Graph dateTimeTimeZone {dateTime, timeZone} into epoch millis. */
    private fun parseDtz(o: JsonObject): Long? = runCatching {
        val dt = LocalDateTime.parse(o.s("dateTime")!!.take(19))
        val zone = o.s("timeZone")?.let { tz -> runCatching { ZoneId.of(if (tz == "UTC") "Z" else tz) }.getOrNull() } ?: ZoneOffset.UTC
        dt.atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()
}
