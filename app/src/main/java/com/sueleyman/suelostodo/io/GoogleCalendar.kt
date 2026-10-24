package com.sueleyman.suelostodo.io

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/** One appointment from Google Calendar. */
data class CalEvent(
    val id: String,
    val calendar: String,
    val color: Long,
    val title: String,
    /** start day (epoch day, local) */
    val day: Long,
    /** start / end in epoch millis; null for all-day events */
    val start: Long?,
    val end: Long?,
    val allDay: Boolean,
    val location: String,
    val description: String,
) {
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
    private fun hm(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(time)

    /** "Ganztägig" or "10:00–11:30" */
    val timeLabel: String get() = if (allDay || start == null) "Ganztägig" else hm(start) + (end?.let { "–" + hm(it) } ?: "")

    /** Note for the imported task. */
    fun toNote(): String = buildString {
        val d = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", Locale.GERMAN))
        append("📅 Termin: $d · $timeLabel")
        if (location.isNotBlank()) append("\n📍 ").append(location.trim())
        append("\n🗓 Kalender: ").append(calendar)
        if (description.isNotBlank()) append("\n\n").append(stripHtml(description).trim())
    }

    companion object {
        fun stripHtml(s: String) = s.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").replace("&amp;", "&")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
    }
}

object GoogleCalendar {
    const val SCOPE = "https://www.googleapis.com/auth/calendar.readonly"

    fun request(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    /** Access token if the user already granted calendar access, otherwise null (UI must show consent). */
    suspend fun silentToken(c: Context): String? {
        val r = Identity.getAuthorizationClient(c).authorize(request()).await()
        return if (r.hasResolution()) null else r.accessToken
    }

    // ---------------------------------------------------------------- imported ids
    private fun sp(c: Context) = c.getSharedPreferences("calendar", Context.MODE_PRIVATE)
    fun importedIds(c: Context): Set<String> = sp(c).getStringSet("imported", emptySet()) ?: emptySet()
    fun markImported(c: Context, ids: Collection<String>) {
        sp(c).edit().putStringSet("imported", importedIds(c) + ids).apply()
    }

    // ---------------------------------------------------------------- REST
    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    }
    private val json = Json { ignoreUnknownKeys = true }

    class CalendarException(msg: String) : Exception(msg)

    private fun get(token: String, url: okhttp3.HttpUrl): JsonObject {
        val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw CalendarException(
                when (r.code) {
                    401 -> "Google-Anmeldung abgelaufen – bitte neu verbinden."
                    403 -> if ("accessNotConfigured" in body || "SERVICE_DISABLED" in body)
                        "Die Google Calendar API ist im Google-Cloud-Projekt nicht aktiviert (APIs & Dienste → Bibliothek → „Google Calendar API“ aktivieren)."
                    else "Kein Zugriff auf den Kalender (403)."
                    else -> "Kalender-Fehler ${r.code}"
                }
            )
            return json.parseToJsonElement(body).jsonObject
        }
    }

    /** All appointments of all visible calendars between today and today + [months]. */
    suspend fun upcoming(token: String, months: Long = 2): List<CalEvent> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val from = LocalDate.now().atStartOfDay(zone).toOffsetDateTime()
        val to = LocalDate.now().plusMonths(months).plusDays(1).atStartOfDay(zone).toOffsetDateTime()
        val cals = get(token, "https://www.googleapis.com/calendar/v3/users/me/calendarList".toHttpUrl())["items"]
            ?.jsonArray.orEmpty().map { it.jsonObject }
            .filter { it.str("selected") != "false" && it.str("hidden") != "true" }
        cals.flatMap { cal ->
            val calId = cal.str("id") ?: return@flatMap emptyList()
            val name = cal.str("summaryOverride") ?: cal.str("summary") ?: calId
            val color = parseColor(cal.str("backgroundColor"))
            val out = ArrayList<CalEvent>()
            var page: String? = null
            do {
                val url = "https://www.googleapis.com/calendar/v3/calendars".toHttpUrl().newBuilder()
                    .addPathSegment(calId).addPathSegment("events")
                    .addQueryParameter("timeMin", from.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    .addQueryParameter("timeMax", to.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    .addQueryParameter("singleEvents", "true")
                    .addQueryParameter("orderBy", "startTime")
                    .addQueryParameter("maxResults", "250")
                    .apply { page?.let { addQueryParameter("pageToken", it) } }
                    .build()
                // a single unreadable calendar (e.g. a subscribed holiday feed) must not break the others
                val res = try { get(token, url) } catch (e: CalendarException) { if (cals.size > 1) null else throw e }
                if (res == null) break
                out += parseEvents(res, name, color)
                page = res.str("nextPageToken")
            } while (page != null && out.size < 1000)
            out
        }.sortedWith(compareBy({ it.day }, { it.start ?: Long.MIN_VALUE }, { it.title }))
    }

    private fun JsonObject.str(k: String) = this[k]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }

    private fun parseColor(hex: String?): Long =
        hex?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000L or it } ?: 0xFF4285F4L

    /** Parses one page of the events endpoint (public for unit tests). */
    fun parseEvents(res: JsonObject, calendar: String, color: Long): List<CalEvent> {
        val zone = ZoneId.systemDefault()
        return res["items"]?.jsonArray.orEmpty().mapNotNull { el ->
            val e = el.jsonObject
            if (e.str("status") == "cancelled") return@mapNotNull null
            val start = e["start"]?.jsonObject ?: return@mapNotNull null
            val end = e["end"]?.jsonObject
            val date = start.str("date")
            val (day, s, en) = if (date != null) Triple(LocalDate.parse(date).toEpochDay(), null, null) else {
                val st = OffsetDateTime.parse(start.str("dateTime") ?: return@mapNotNull null)
                Triple(
                    st.atZoneSameInstant(zone).toLocalDate().toEpochDay(),
                    st.toInstant().toEpochMilli(),
                    end?.str("dateTime")?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() },
                )
            }
            CalEvent(
                id = e.str("id") ?: return@mapNotNull null,
                calendar = calendar, color = color,
                title = e.str("summary")?.takeIf { it.isNotBlank() } ?: "(ohne Titel)",
                day = day, start = s, end = en, allDay = date != null,
                location = e.str("location").orEmpty(),
                description = e.str("description").orEmpty(),
            )
        }
    }
}
