package com.sueleyman.suelostodo.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.sueleyman.suelostodo.SuelosToDoApp
import com.sueleyman.suelostodo.data.SyncSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

object GoogleAuth {
    /** SHA-1 of the certificate this APK is signed with (needed for the Google Cloud OAuth client). */
    fun signingSha1(c: android.content.Context): String = runCatching {
        val pm = c.packageManager
        val sigs = if (android.os.Build.VERSION.SDK_INT >= 28)
            pm.getPackageInfo(c.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES).signingInfo!!.apkContentsSigners
        else @Suppress("DEPRECATION") pm.getPackageInfo(c.packageName, android.content.pm.PackageManager.GET_SIGNATURES).signatures!!
        val md = java.security.MessageDigest.getInstance("SHA-1").digest(sigs[0].toByteArray())
        md.joinToString(":") { "%02X".format(it) }
    }.getOrDefault("unbekannt")

    /** Human readable reason for a failed Google sign-in. */
    fun explain(e: Throwable, wasOk: Boolean): String {
        val code = (e as? com.google.android.gms.common.api.ApiException)?.statusCode
        return when (code) {
            10 -> "Google meldet DEVELOPER_ERROR (Code 10): In Google Cloud fehlt ein Android-OAuth-Client für diese App – " +
                "Paketname und SHA-1 (siehe unten) müssen exakt passen, und die Google Drive API muss aktiviert sein."
            16, 12501 -> "Anmeldung abgebrochen (Code $code). Falls du nichts abgebrochen hast: meist fehlt die Einrichtung in Google Cloud " +
                "(OAuth-Client mit Paketname + SHA-1, Testnutzer im OAuth-Zustimmungsbildschirm)."
            7 -> "Keine Internetverbindung (Code 7)."
            8, 13 -> "Interner Google-Fehler (Code $code) – bitte später erneut versuchen."
            null -> if (wasOk) "Verbindung fehlgeschlagen: ${e.message}"
                else "Anmeldung abgebrochen – meist fehlt die Einrichtung in Google Cloud (OAuth-Client mit Paketname + SHA-1)."
            else -> "Google-Anmeldung fehlgeschlagen (Code $code): ${e.message}"
        }
    }

    const val SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"

    fun request(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE_APPDATA))).build()

    /** Returns an access token if the user already granted access, otherwise null. */
    suspend fun silentToken(context: Context): String? {
        val result = Identity.getAuthorizationClient(context).authorize(request()).await()
        return if (result.hasResolution()) null else result.accessToken
    }
}

object SyncManager {
    private const val DATA_FILE = "suelostodo-data.json"
    private const val ATT_PREFIX = "att_"
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** @return null on success, otherwise an error message */
    suspend fun sync(context: Context): String? = mutex.withLock {
        if (!SyncPrefs.isConnected(context)) return null
        SyncState.set(SyncStatus.Running)
        val error = runCatching { doSync(context) }.exceptionOrNull()?.let { e ->
            when (e) {
                is DriveAuthException -> "Google-Anmeldung abgelaufen – bitte in den Einstellungen neu verbinden."
                else -> e.message ?: e.javaClass.simpleName
            }
        }
        if (error == null) {
            val now = System.currentTimeMillis()
            SyncPrefs.setLastSync(context, now)
            SyncPrefs.setLastError(context, null)
            SyncState.set(SyncStatus.Done(now))
        } else {
            SyncPrefs.setLastError(context, error)
            SyncState.set(SyncStatus.Failed(error))
        }
        error
    }

    private suspend fun doSync(context: Context) {
        val repo = (context.applicationContext as SuelosToDoApp).repo
        val token = GoogleAuth.silentToken(context)
            ?: throw DriveAuthException("Keine Berechtigung")
        val drive = DriveClient(token)
        if (SyncPrefs.account(context) == null) SyncPrefs.setAccount(context, drive.userEmail())

        val files = drive.listFiles()
        val dataFile = files.firstOrNull { it.name == DATA_FILE }
        val remote = dataFile?.let { json.decodeFromString(SyncSnapshot.serializer(), drive.downloadText(it.id)) }
        val merged = if (remote != null) repo.mergeRemote(remote) else repo.snapshot()

        // attachments: upload missing, download missing, delete tombstoned
        val byName = files.associateBy { it.name }
        for (a in merged.attachments) {
            val remoteFile = byName[ATT_PREFIX + a.id]
            val local = repo.attachmentFile(a.id)
            when {
                a.deleted -> remoteFile?.let { drive.delete(it.id) }
                remoteFile == null && local.exists() ->
                    drive.create(ATT_PREFIX + a.id, DriveClient.fileBody(local, a.mimeType))
                remoteFile != null && !local.exists() ->
                    drive.downloadToFile(remoteFile.id, local)
            }
        }

        val text = json.encodeToString(SyncSnapshot.serializer(), merged.copy(exportedAt = System.currentTimeMillis()))
        if (dataFile == null) drive.create(DATA_FILE, DriveClient.textBody(text))
        else drive.updateContent(dataFile.id, DriveClient.textBody(text))
    }
}

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val err = SyncManager.sync(applicationContext)
        return when {
            err == null -> Result.success()
            err.contains("Anmeldung") -> Result.failure()
            runAttemptCount < 3 -> Result.retry()
            else -> Result.failure()
        }
    }
}

object SyncScheduler {
    private const val PERIODIC = "sync-periodic"
    private const val SOON = "sync-soon"
    private const val NOW = "sync-now"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private fun enabled(c: Context) = SyncPrefs.isConnected(c) && SyncPrefs.autoSync(c)

    fun configurePeriodic(c: Context) {
        val wm = WorkManager.getInstance(c)
        if (enabled(c)) {
            wm.enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                    .setConstraints(network)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                    .build()
            )
        } else {
            wm.cancelUniqueWork(PERIODIC)
            wm.cancelUniqueWork(SOON)
        }
    }

    /** Debounced sync after local changes. */
    fun scheduleSoon(c: Context) {
        if (!enabled(c)) return
        WorkManager.getInstance(c).enqueueUniqueWork(
            SOON, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setInitialDelay(30, TimeUnit.SECONDS)
                .setConstraints(network)
                .build()
        )
    }

    fun syncNow(c: Context) {
        if (!SyncPrefs.isConnected(c)) return
        WorkManager.getInstance(c).enqueueUniqueWork(
            NOW, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(network)
                .build()
        )
    }
}
