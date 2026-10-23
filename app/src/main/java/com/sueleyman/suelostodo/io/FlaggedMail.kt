package com.sueleyman.suelostodo.io

import android.content.Context
import android.text.Html
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sueleyman.suelostodo.SuelosToDoApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/**
 * Outlook flagged e-mails → local list "Markierte E-Mails".
 *
 * Uses the built-in Microsoft To Do list "Markierte E-Mails" (wellknownListName = flaggedEmails),
 * which Outlook fills automatically with every flagged mail. Therefore no extra Azure permission
 * (Mail.Read) is needed – Tasks.ReadWrite is enough.
 *
 * Two-way: a mail completed here is marked "completed" in To Do (= flag done in Outlook);
 * a mail that is unflagged / completed in Outlook gets checked off here.
 */
object FlaggedMail {
    private const val G = "https://graph.microsoft.com/v1.0/me/todo"
    private const val WORK = "flagged-mail-sync"
    private val mutex = Mutex()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private fun sp(c: Context) = c.getSharedPreferences("ms", Context.MODE_PRIVATE)
    fun isEnabled(c: Context) = sp(c).getBoolean("flagged_auto", false)
    fun lastRun(c: Context) = sp(c).getLong("flagged_last", 0L)
    fun lastMessage(c: Context) = sp(c).getString("flagged_msg", null)

    fun setEnabled(c: Context, on: Boolean) {
        sp(c).edit().putBoolean("flagged_auto", on).apply()
        schedule(c)
    }

    /** Hourly background job while enabled. */
    fun schedule(c: Context) {
        val wm = WorkManager.getInstance(c)
        if (isEnabled(c) && MsAuth.state.value.signedIn) {
            wm.enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<FlaggedMailWorker>(1, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            )
        } else wm.cancelUniqueWork(WORK)
    }

    /** Called when the app is opened: sync if enabled and the last run is older than 10 minutes. */
    suspend fun syncIfDue(c: Context) {
        if (!isEnabled(c) || !MsAuth.state.value.signedIn) return
        if (System.currentTimeMillis() - lastRun(c) < 10 * 60_000) return
        runCatching { sync(c) }
    }

    suspend fun sync(c: Context): String = mutex.withLock {
        _status.value = "Markierte E-Mails werden abgerufen …"
        try {
            val msg = doSync(c)
            sp(c).edit().putLong("flagged_last", System.currentTimeMillis()).putString("flagged_msg", msg).apply()
            _status.value = msg
            msg
        } catch (e: Exception) {
            val msg = "Markierte E-Mails: ${e.message ?: e.javaClass.simpleName}"
            sp(c).edit().putString("flagged_msg", msg).apply()
            _status.value = msg
            throw e
        }
    }

    private suspend fun doSync(c: Context): String {
        val repo = (c.applicationContext as SuelosToDoApp).repo
        val g = GraphClient(MsAuth.accessToken(c))
        val list = g.getAll("$G/lists").firstOrNull { it.s("wellknownListName") == "flaggedEmails" }
            ?: throw IOException(
                "In Microsoft To Do gibt es die Liste „Markierte E-Mails“ nicht. Aktiviere sie in To Do unter " +
                    "Einstellungen → Verbundene Apps → „Markierte E-Mail“."
            )
        val lid = list.s("id")!!
        val localListId = "ms-$lid"
        var remote = g.getAll("$G/lists/$lid/tasks?\$top=100")

        // 1) push: completed here -> completed in To Do / Outlook
        var pushed = 0
        for (t in remote) {
            val tid = t.s("id") ?: continue
            val local = repo.getTask("ms-$tid") ?: continue
            if (local.done && t.s("status") != "completed") {
                g.patch("$G/lists/$lid/tasks/$tid", buildJsonObject { put("status", "completed") })
                pushed++
            }
        }
        if (pushed > 0) remote = g.getAll("$G/lists/$lid/tasks?\$top=100")

        // 2) pull: every flagged mail as task (with link to the mail)
        val items = remote.map { t ->
            val tid = t.s("id")!!
            val link = runCatching {
                g.getAll("$G/lists/$lid/tasks/$tid/linkedResources").firstNotNullOfOrNull { it.s("webUrl") }
            }.getOrNull()
            val body = (t["body"] as? JsonObject)?.let { b ->
                val content = b.s("content").orEmpty()
                if (b.s("contentType") == "html") Html.fromHtml(content, Html.FROM_HTML_MODE_COMPACT).toString().trim() else content.trim()
            }.orEmpty()
            ImportItem(
                id = "ms-$tid",
                title = t.s("title")?.ifBlank { null } ?: "(E-Mail ohne Betreff)",
                note = listOfNotNull(body.take(1500).ifBlank { null }, link?.let { "📧 In Outlook öffnen: $it" }).joinToString("\n\n"),
                done = t.s("status") == "completed",
                important = t.s("importance") == "high",
                dueDate = (t["dueDateTime"] as? JsonObject)?.s("dateTime")?.let {
                    runCatching { LocalDateTime.parse(it.take(19)).toLocalDate().toEpochDay() }.getOrNull()
                },
                createdAt = t.s("createdDateTime")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
            )
        }
        repo.importLists(listOf(ImportList(id = localListId, name = "Markierte E-Mails", items = items, color = 2)))

        // 3) mails no longer flagged in Outlook -> check off here
        val remoteIds = items.mapNotNull { it.id }.toSet()
        var removed = 0
        repo.getTasksOfList(localListId).filter { it.parentId == null && !it.done && it.id !in remoteIds }.forEach {
            repo.toggleDone(it.id); removed++
        }
        val open = items.count { !it.done }
        return buildString {
            append("$open markierte E-Mail(s) offen")
            if (pushed > 0) append(", $pushed als erledigt an Outlook gemeldet")
            if (removed > 0) append(", $removed nicht mehr markiert → abgehakt")
            append(".")
        }
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
}

class FlaggedMailWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result =
        if (runCatching { FlaggedMail.sync(applicationContext) }.isSuccess) Result.success() else Result.retry()
}
