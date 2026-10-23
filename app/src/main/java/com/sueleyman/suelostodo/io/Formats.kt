package com.sueleyman.suelostodo.io

import com.sueleyman.suelostodo.data.SyncSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class FileFormat(val label: String, val ext: String, val mime: String) {
    BACKUP("Sülos-ToDo-Backup (ZIP mit Anhängen)", "zip", "application/zip"),
    TODOIST("Todoist CSV", "csv", "text/csv"),
    ICAL("iCalendar / VTODO", "ics", "text/calendar"),
    GOOGLE("Google Tasks (Takeout JSON)", "json", "application/json"),
}

// ======================================================================== detection

sealed interface Parsed {
    data class Backup(val snapshot: SyncSnapshot, val files: Map<String, ByteArray>) : Parsed
    data class Lists(val format: FileFormat, val lists: List<ImportList>) : Parsed
}

object FormatDetector {
    private val json = Json { ignoreUnknownKeys = true }

    /** Detects the format of [bytes] and parses it. [fileName] is used as fallback list name. */
    fun parse(bytes: ByteArray, fileName: String): Parsed {
        val base = fileName.substringBeforeLast('.').ifBlank { "Import" }
        if (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) return parseZip(bytes, base)
        val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        val trimmed = text.trimStart()
        return when {
            trimmed.startsWith("BEGIN:VCALENDAR", ignoreCase = true) ->
                Parsed.Lists(FileFormat.ICAL, ICal.parse(text, base))
            trimmed.startsWith("{") || trimmed.startsWith("[") -> {
                val el = json.parseToJsonElement(trimmed)
                if (el is JsonObject && "lists" in el && "tasks" in el)
                    Parsed.Backup(json.decodeFromJsonElement(SyncSnapshot.serializer(), el), emptyMap())
                else Parsed.Lists(FileFormat.GOOGLE, GoogleTakeout.parse(el, base))
            }
            trimmed.uppercase().startsWith("TYPE,") || trimmed.uppercase().startsWith("\"TYPE\"") ->
                Parsed.Lists(FileFormat.TODOIST, listOf(TodoistCsv.parse(text, base)))
            else -> throw IllegalArgumentException("Unbekanntes Dateiformat")
        }
    }

    private fun parseZip(bytes: ByteArray, base: String): Parsed {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                if (!e.isDirectory) entries[e.name] = zin.readBytes()
                e = zin.nextEntry
            }
        }
        (entries["suelostodo.json"] ?: entries["tasknest.json"])?.let { data ->
            val snap = json.decodeFromString(SyncSnapshot.serializer(), data.toString(Charsets.UTF_8))
            val files = entries.filterKeys { it.startsWith("attachments/") }.mapKeys { it.key.removePrefix("attachments/") }
            return Parsed.Backup(snap, files)
        }
        // Zip of other exports (e.g. Google Takeout folder) -> parse every supported file
        val lists = mutableListOf<ImportList>()
        var fmt: FileFormat? = null
        entries.forEach { (name, data) ->
            val lower = name.lowercase()
            if (lower.endsWith(".json") || lower.endsWith(".ics") || lower.endsWith(".csv")) {
                runCatching { parse(data, name.substringAfterLast('/')) }.getOrNull()?.let {
                    if (it is Parsed.Lists) { lists += it.lists; fmt = fmt ?: it.format }
                }
            }
        }
        if (lists.isEmpty()) throw IllegalArgumentException("ZIP enthält keine unterstützten Dateien")
        return Parsed.Lists(fmt ?: FileFormat.GOOGLE, lists)
    }
}

// ======================================================================== Sülos ToDo backup

object Backup {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun write(out: OutputStream, snapshot: SyncSnapshot, attachmentFile: (String) -> File) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("suelostodo.json"))
            zip.write(json.encodeToString(SyncSnapshot.serializer(), snapshot).toByteArray())
            zip.closeEntry()
            snapshot.attachments.filter { !it.deleted }.forEach { a ->
                val f = attachmentFile(a.id)
                if (f.exists()) {
                    zip.putNextEntry(ZipEntry("attachments/${a.id}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }
}

// ======================================================================== Todoist CSV

object TodoistCsv {
    private val header = listOf(
        "TYPE", "CONTENT", "DESCRIPTION", "PRIORITY", "INDENT", "AUTHOR", "RESPONSIBLE",
        "DATE", "DATE_LANG", "TIMEZONE", "DURATION", "DURATION_UNIT", "DEADLINE", "DEADLINE_LANG"
    )

    /** Todoist CSV contains one project. Completed tasks are not part of the format and are skipped. */
    fun write(list: ExportList, includeDone: Boolean = false): String {
        val sb = StringBuilder()
        sb.append(header.joinToString(",")).append("\r\n")
        list.roots.forEach { root ->
            root.walk { node, depth ->
                val t = node.task
                if (t.done && !includeDone) return@walk
                val cols = MutableList(header.size) { "" }
                cols[0] = "task"
                cols[1] = t.title
                cols[2] = t.note
                cols[3] = if (t.important) "1" else "4"
                cols[4] = (depth + 1).coerceAtMost(4).toString()
                cols[7] = t.dueDate?.let { LocalDate.ofEpochDay(it).toString() } ?: ""
                cols[8] = if (t.dueDate != null) "en" else ""
                sb.append(cols.joinToString(",") { csvEscape(it) }).append("\r\n")
            }
        }
        return sb.toString()
    }

    fun parse(text: String, listName: String): ImportList {
        val rows = parseCsv(text)
        if (rows.isEmpty()) return ImportList(listName, emptyList())
        val head = rows.first().map { it.trim().uppercase() }
        fun idx(n: String) = head.indexOf(n)
        val iType = idx("TYPE"); val iContent = idx("CONTENT"); val iDesc = idx("DESCRIPTION")
        val iPrio = idx("PRIORITY"); val iIndent = idx("INDENT"); val iDate = idx("DATE")
        val roots = mutableListOf<ImportItem>()
        val stack = ArrayList<ImportItem>() // stack[i] = last item at indent i+1
        for (r in rows.drop(1)) {
            fun col(i: Int) = if (i >= 0 && i < r.size) r[i] else ""
            if (col(iType).trim() != "task") continue
            val title = col(iContent).replace(Regex("""\s@\S+"""), "").trim()
            if (title.isEmpty()) continue
            val indent = col(iIndent).trim().toIntOrNull()?.coerceIn(1, 4) ?: 1
            val prio = col(iPrio).trim().toIntOrNull()
            val item = ImportItem(
                title = title,
                note = col(iDesc),
                important = prio == 1,
                dueDate = parseLooseDate(col(iDate)),
            )
            while (stack.size >= indent) stack.removeAt(stack.size - 1)
            val parent = stack.lastOrNull()
            if (parent == null) roots += item else parent.children += item
            stack += item
        }
        return ImportList(listName, roots)
    }

    private fun csvEscape(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQ = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQ) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else inQ = false
                } else cell.append(c)
            } else when (c) {
                '"' -> inQ = true
                ',' -> { row += cell.toString(); cell.clear() }
                '\r' -> {}
                '\n' -> { row += cell.toString(); cell.clear(); rows += row; row = mutableListOf() }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    /** Accepts ISO dates and dd.MM.yyyy; natural language (e.g. "every day") is ignored. */
    private fun parseLooseDate(s: String): Long? {
        val v = s.trim()
        if (v.isEmpty()) return null
        Regex("""(\d{4})-(\d{2})-(\d{2})""").find(v)?.let { m ->
            return runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()).toEpochDay() }.getOrNull()
        }
        Regex("""(\d{1,2})\.(\d{1,2})\.(\d{4})""").find(v)?.let { m ->
            return runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()).toEpochDay() }.getOrNull()
        }
        return when (v.lowercase()) {
            "today", "heute" -> LocalDate.now().toEpochDay()
            "tomorrow", "morgen" -> LocalDate.now().plusDays(1).toEpochDay()
            else -> null
        }
    }
}

// ======================================================================== iCalendar VTODO

object ICal {
    private val utcFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val dateFmt = DateTimeFormatter.BASIC_ISO_DATE

    fun write(lists: List<ExportList>): String {
        val sb = StringBuilder()
        fun line(s: String) = sb.append(fold(s)).append("\r\n")
        line("BEGIN:VCALENDAR")
        line("VERSION:2.0")
        line("PRODID:-//SuelosToDo//Android//DE")
        line("CALSCALE:GREGORIAN")
        val now = utcFmt.format(Instant.now())
        for (l in lists) {
            l.roots.forEach { root ->
                root.walk { node, _ ->
                    val t = node.task
                    line("BEGIN:VTODO")
                    line("UID:${t.id}@suelostodo")
                    line("DTSTAMP:$now")
                    line("CREATED:${utcFmt.format(Instant.ofEpochMilli(t.createdAt))}")
                    line("LAST-MODIFIED:${utcFmt.format(Instant.ofEpochMilli(t.updatedAt))}")
                    line("SUMMARY:${esc(t.title)}")
                    if (t.note.isNotBlank()) line("DESCRIPTION:${esc(t.note)}")
                    line("CATEGORIES:${esc(l.list.name)}")
                    line("X-SUELOSTODO-LIST:${esc(l.list.name)}")
                    line("STATUS:${if (t.done) "COMPLETED" else "NEEDS-ACTION"}")
                    if (t.done) {
                        line("PERCENT-COMPLETE:100")
                        t.completedAt?.let { line("COMPLETED:${utcFmt.format(Instant.ofEpochMilli(it))}") }
                    }
                    if (t.important) line("PRIORITY:1")
                    t.dueDate?.let { line("DUE;VALUE=DATE:${dateFmt.format(LocalDate.ofEpochDay(it))}") }
                    t.parentId?.let { line("RELATED-TO;RELTYPE=PARENT:$it@suelostodo") }
                    line("X-APPLE-SORT-ORDER:${t.sortOrder / 1000}")
                    t.reminderAt?.let {
                        line("BEGIN:VALARM")
                        line("ACTION:DISPLAY")
                        line("DESCRIPTION:${esc(t.title)}")
                        line("TRIGGER;VALUE=DATE-TIME:${utcFmt.format(Instant.ofEpochMilli(it))}")
                        line("END:VALARM")
                    }
                    line("END:VTODO")
                }
            }
        }
        line("END:VCALENDAR")
        return sb.toString()
    }

    private class Todo(
        val uid: String, val item: ImportItem, val parent: String?, val list: String?, val sort: Long,
    )

    fun parse(text: String, fallbackList: String): List<ImportList> {
        val lines = unfold(text)
        val todos = mutableListOf<Todo>()
        var props: MutableList<Pair<String, String>>? = null
        var inAlarm = false
        var alarmTrigger: String? = null
        var calName: String? = null
        for (raw in lines) {
            val (nameWithParams, value) = splitProp(raw) ?: continue
            val name = nameWithParams.substringBefore(';').uppercase()
            when {
                name == "X-WR-CALNAME" && props == null -> calName = unesc(value)
                name == "BEGIN" && value.equals("VTODO", true) -> { props = mutableListOf(); alarmTrigger = null }
                name == "BEGIN" && value.equals("VALARM", true) -> inAlarm = true
                name == "END" && value.equals("VALARM", true) -> inAlarm = false
                name == "END" && value.equals("VTODO", true) -> {
                    props?.let { p -> todos += toTodo(p, alarmTrigger) }
                    props = null
                }
                props != null && inAlarm && name == "TRIGGER" -> {
                    if (nameWithParams.contains("VALUE=DATE-TIME", true) || value.endsWith("Z")) alarmTrigger = value
                }
                props != null && !inAlarm -> props!!.add(nameWithParams to value)
            }
        }
        // Build hierarchy via RELATED-TO
        val byUid = todos.associateBy { it.uid }
        val roots = mutableListOf<Todo>()
        todos.sortedBy { it.sort }.forEach { t ->
            val p = t.parent?.let { byUid[it] }
            if (p != null && p !== t) p.item.children += t.item else roots += t
        }
        val defaultName = calName ?: fallbackList
        return roots.groupBy { it.list ?: defaultName }.map { (name, items) -> ImportList(name, items.map { it.item }) }
    }

    private fun toTodo(p: List<Pair<String, String>>, alarm: String?): Todo {
        fun get(n: String) = p.firstOrNull { it.first.substringBefore(';').equals(n, true) }
        val uid = get("UID")?.second ?: java.util.UUID.randomUUID().toString()
        val status = get("STATUS")?.second?.uppercase()
        val prio = get("PRIORITY")?.second?.trim()?.toIntOrNull() ?: 0
        val due = get("DUE")?.second?.let { parseDate(it) }
        val completed = get("COMPLETED")?.second?.let { parseDateTime(it) }
        val created = get("CREATED")?.second?.let { parseDateTime(it) }
        val parent = p.firstOrNull { (n, _) ->
            n.substringBefore(';').equals("RELATED-TO", true) &&
                (!n.contains("RELTYPE", true) || n.contains("RELTYPE=PARENT", true))
        }?.second?.trim()
        val list = ((get("X-SUELOSTODO-LIST") ?: get("X-TASKNEST-LIST"))?.second?.let { unesc(it) }
            ?: get("CATEGORIES")?.second?.split(Regex("""(?<!\\),"""))?.first()?.let { unesc(it) })
            ?.trim()?.ifBlank { null }
        val sort = get("X-APPLE-SORT-ORDER")?.second?.trim()?.toLongOrNull() ?: created ?: 0L
        val item = ImportItem(
            title = unesc(get("SUMMARY")?.second ?: "(ohne Titel)"),
            note = unesc(get("DESCRIPTION")?.second ?: ""),
            done = status == "COMPLETED",
            important = prio in 1..4,
            dueDate = due,
            completedAt = completed,
            createdAt = created,
            reminderAt = alarm?.let { parseDateTime(it) },
        )
        return Todo(uid, item, parent, list, sort)
    }

    private fun parseDate(v: String): Long? = runCatching {
        LocalDate.parse(v.trim().take(8), dateFmt).toEpochDay()
    }.getOrNull()

    private fun parseDateTime(v: String): Long? = runCatching {
        val s = v.trim()
        when {
            s.endsWith("Z") -> Instant.from(utcFmt.parse(s)).toEpochMilli()
            s.length >= 15 -> LocalDateTime.parse(s.take(15), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            else -> LocalDate.parse(s.take(8), dateFmt).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }.getOrNull()

    private fun unfold(text: String): List<String> {
        val out = mutableListOf<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { l ->
            if ((l.startsWith(" ") || l.startsWith("\t")) && out.isNotEmpty()) out[out.size - 1] = out.last() + l.substring(1)
            else if (l.isNotEmpty()) out += l
        }
        return out
    }

    /** Splits "NAME;PARAM=x:VALUE" respecting quoted params. */
    private fun splitProp(l: String): Pair<String, String>? {
        var inQ = false
        for (i in l.indices) {
            val c = l[i]
            if (c == '"') inQ = !inQ
            if (c == ':' && !inQ) return l.substring(0, i) to l.substring(i + 1)
        }
        return null
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")
    private fun unesc(s: String) = s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private fun fold(s: String): String {
        if (s.toByteArray(Charsets.UTF_8).size <= 75) return s
        val sb = StringBuilder()
        var count = 0
        var limit = 75
        for (ch in s) {
            val len = ch.toString().toByteArray(Charsets.UTF_8).size
            if (count + len > limit) { sb.append("\r\n "); count = 0; limit = 74 }
            sb.append(ch)
            count += len
        }
        return sb.toString()
    }
}

// ======================================================================== Google Takeout

object GoogleTakeout {
    fun parse(el: JsonElement, fallbackName: String): List<ImportList> {
        val listObjs: List<JsonObject> = when {
            el is JsonArray -> el.filterIsInstance<JsonObject>()
            el is JsonObject && el.str("kind") == "tasks#taskLists" -> (el["items"] as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList()
            el is JsonObject && el["items"] is JsonArray && (el["items"] as JsonArray).firstOrNull()
                ?.let { it is JsonObject && it.str("kind") == "tasks#taskList" } == true ->
                (el["items"] as JsonArray).filterIsInstance<JsonObject>()
            el is JsonObject -> listOf(el)
            else -> emptyList()
        }
        return listObjs.map { lo ->
            val name = lo.str("title") ?: fallbackName
            val tasks = (lo["items"] as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList()
            data class G(val id: String, val parent: String?, val pos: String, val item: ImportItem)
            val gs = tasks.filter { it.str("deleted") != "true" }.mapNotNull { t ->
                val title = t.str("title")?.trim().orEmpty()
                if (title.isEmpty()) return@mapNotNull null
                G(
                    id = t.str("id") ?: java.util.UUID.randomUUID().toString(),
                    parent = t.str("parent"),
                    pos = t.str("position") ?: "",
                    item = ImportItem(
                        title = title,
                        note = t.str("notes") ?: "",
                        done = t.str("status") == "completed",
                        dueDate = t.str("due")?.let { d -> runCatching { OffsetDateTime.parse(d).toLocalDate().toEpochDay() }.getOrNull() },
                        completedAt = t.str("completed")?.let { d -> runCatching { OffsetDateTime.parse(d).toInstant().toEpochMilli() }.getOrNull() },
                        createdAt = t.str("created")?.let { d -> runCatching { OffsetDateTime.parse(d).toInstant().toEpochMilli() }.getOrNull() },
                    )
                )
            }.sortedBy { it.pos }
            val byId = gs.associateBy { it.id }
            val roots = mutableListOf<ImportItem>()
            gs.forEach { g -> val p = g.parent?.let { byId[it] }; if (p != null) p.item.children += g.item else roots += g.item }
            ImportList(name, roots)
        }
    }

    private fun JsonObject.str(k: String) = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
}

