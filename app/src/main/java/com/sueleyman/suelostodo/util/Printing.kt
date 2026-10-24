package com.sueleyman.suelostodo.util

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.sueleyman.suelostodo.data.ListColors
import com.sueleyman.suelostodo.data.ListGroup
import com.sueleyman.suelostodo.data.Task
import com.sueleyman.suelostodo.data.TaskList
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the user picked in the print dialog. */
data class PrintSelection(
    val groupIds: Set<String>,
    val includeUngrouped: Boolean,
    val includeDone: Boolean,
)

object TaskPrinter {
    private var webView: WebView? = null   // must stay alive until the print job is spooled

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    private fun hex(c: Long) = "#%06X".format(c and 0xFFFFFF)

    /** Builds a printable HTML document. Pure function – unit-testable. */
    fun buildHtml(lists: List<TaskList>, groups: List<ListGroup>, tasks: List<Task>, sel: PrintSelection): String {
        val gIds = groups.mapTo(HashSet()) { it.id }
        val tops = groups.filter { it.parentId == null || it.parentId !in gIds }
        val today = LocalDate.now().toEpochDay()
        val live = tasks.filter { !it.deleted && (sel.includeDone || !it.done) }
        val byParent = live.groupBy { it.parentId }

        fun taskHtml(t: Task, depth: Int): String {
            val box = if (t.done) "☑" else "☐"
            val due = t.dueDate?.let { d ->
                val label = LocalDate.ofEpochDay(d).format(DateTimeFormatter.ofPattern("d.M.", Locale.GERMAN))
                val cls = if (d < today && !t.done) "due over" else "due"
                " <span class=\"$cls\">fällig $label</span>"
            } ?: ""
            val star = if (t.important) " <span class=\"star\">★</span>" else ""
            val note = if (t.note.isNotBlank()) "<div class=\"note\">${esc(t.note).replace("\n", "<br>")}</div>" else ""
            val kids = byParent[t.id].orEmpty().sortedBy { it.sortOrder }.joinToString("") { taskHtml(it, depth + 1) }
            return "<div class=\"task d$depth${if (t.done) " done" else ""}\"><span class=\"box\">$box</span> " +
                "<span class=\"title\">${esc(t.title)}</span>$star$due$note</div>$kids"
        }

        fun listHtml(l: TaskList): String {
            val roots = live.filter { it.listId == l.id && it.parentId == null }.sortedBy { it.sortOrder }
            val color = hex(ListColors.argb(l.color))
            val body = if (roots.isEmpty()) "<div class=\"empty\">keine Aufgaben</div>" else roots.joinToString("") { taskHtml(it, 0) }
            return "<div class=\"list\"><h3 style=\"border-color:$color;color:$color\">${esc(l.name)}</h3>$body</div>"
        }

        val sb = StringBuilder()
        for (top in tops) {
            val subs = groups.filter { it.parentId == top.id }
            val topSelected = top.id in sel.groupIds
            val chosenSubs = subs.filter { topSelected || it.id in sel.groupIds }
            val direct = if (topSelected) lists.filter { it.groupId == top.id } else emptyList()
            if (direct.isEmpty() && chosenSubs.isEmpty()) continue
            sb.append("<h2>${esc(top.name)}</h2>")
            direct.forEach { sb.append(listHtml(it)) }
            chosenSubs.forEach { s ->
                val ls = lists.filter { it.groupId == s.id }
                if (ls.isNotEmpty()) {
                    sb.append("<h2 class=\"sub\">${esc(top.name)} › ${esc(s.name)}</h2>")
                    ls.forEach { sb.append(listHtml(it)) }
                }
            }
        }
        if (sel.includeUngrouped) {
            val loose = lists.filter { it.groupId == null || it.groupId !in gIds }
            if (loose.isNotEmpty()) {
                sb.append("<h2>Ohne Gruppe</h2>")
                loose.forEach { sb.append(listHtml(it)) }
            }
        }
        val stamp = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN))
        return """<!DOCTYPE html><html><head><meta charset="utf-8"><style>
body{font-family:sans-serif;font-size:11pt;color:#1f1f1f;margin:0}
h1{font-size:18pt;margin:0 0 2pt} .stamp{color:#777;font-size:9pt;margin-bottom:10pt}
h2{font-size:14pt;margin:14pt 0 4pt;border-bottom:1.5pt solid #999;padding-bottom:2pt} h2.sub{font-size:12pt}
h3{font-size:11.5pt;margin:8pt 0 3pt;border-left:4pt solid;padding-left:6pt}
.list{page-break-inside:auto}
.task{padding:2pt 0;page-break-inside:avoid} .d1{margin-left:16pt} .d2{margin-left:32pt}
.box{font-size:12pt} .done .title{text-decoration:line-through;color:#888}
.due{color:#555;font-size:9pt} .over{color:#C4314B;font-weight:bold} .star{color:#D85A30}
.note{color:#666;font-size:9pt;margin:1pt 0 2pt 18pt;white-space:normal}
.empty{color:#999;font-size:9pt;margin-left:4pt}
</style></head><body><h1>Sülos ToDo</h1><div class="stamp">Stand: $stamp${if (sel.includeDone) " · inkl. erledigter Aufgaben" else ""}</div>
$sb</body></html>"""
    }

    /** One list (title + all its tasks). */
    fun buildListHtml(list: TaskList, tasks: List<Task>, includeDone: Boolean): String =
        buildHtml(listOf(list.copy(groupId = null)), emptyList(), tasks, PrintSelection(emptySet(), true, includeDone))
            .replace("<h2>Ohne Gruppe</h2>", "")

    /** A single task with all its details, subtasks (incl. completed ones) and attachment names. */
    fun buildTaskHtml(task: Task, listName: String, all: List<Task>, attachmentNames: List<String>): String {
        val today = LocalDate.now().toEpochDay()
        val byParent = all.filter { !it.deleted }.groupBy { it.parentId }
        fun sub(t: Task, depth: Int): String =
            "<div class=\"task d$depth${if (t.done) " done" else ""}\"><span class=\"box\">${if (t.done) "☑" else "☐"}</span> " +
                "<span class=\"title\">${esc(t.title)}</span></div>" +
                byParent[t.id].orEmpty().sortedBy { it.sortOrder }.joinToString("") { sub(it, depth + 1) }
        val props = buildList {
            add("Status" to if (task.done) "erledigt" else "offen")
            task.dueDate?.let { d -> add("Fällig" to LocalDate.ofEpochDay(d).format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)) +
                if (d < today && !task.done) " (überfällig)" else "") }
            task.reminderAt?.let { add("Erinnerung" to java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d. MMMM yyyy, HH:mm", Locale.GERMAN))) }
            if (task.myDay == today) add("Mein Tag" to "ja")
            if (task.important) add("Wichtig" to "★")
        }.joinToString("") { (k, v) -> "<tr><td class=\"k\">$k</td><td>${esc(v)}</td></tr>" }
        val subs = byParent[task.id].orEmpty().sortedBy { it.sortOrder }
        return """<!DOCTYPE html><html><head><meta charset="utf-8"><style>
body{font-family:sans-serif;font-size:11pt;color:#1f1f1f} .crumb{color:#777;font-size:9.5pt}
h1{font-size:18pt;margin:2pt 0 8pt} table{border-collapse:collapse;margin-bottom:8pt} td{padding:2pt 10pt 2pt 0} .k{color:#777}
h2{font-size:12pt;margin:12pt 0 4pt;border-bottom:1.5pt solid #999;padding-bottom:2pt}
.task{padding:2pt 0} .d1{margin-left:16pt} .d2{margin-left:32pt} .done .title{text-decoration:line-through;color:#888}
.note{white-space:pre-wrap}
</style></head><body><div class="crumb">${esc(listName)}</div><h1>${if (task.done) "☑" else "☐"} ${esc(task.title)}</h1>
<table>$props</table>
${if (subs.isNotEmpty()) "<h2>Unteraufgaben</h2>" + subs.joinToString("") { sub(it, 0) } else ""}
${if (task.note.isNotBlank()) "<h2>Notiz</h2><div class=\"note\">${esc(task.note)}</div>" else ""}
${if (attachmentNames.isNotEmpty()) "<h2>Anhänge</h2>" + attachmentNames.joinToString("") { "<div>📎 ${esc(it)}</div>" } else ""}
</body></html>"""
    }

    /** Opens the Android print dialog (printer or "Als PDF speichern"). [context] must be an Activity. */
    fun print(context: Context, html: String, jobName: String = "Sülos ToDo") {
        val wv = WebView(context)
        webView = wv
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val pm = context.getSystemService(PrintManager::class.java)
                pm.print(jobName, view.createPrintDocumentAdapter(jobName), PrintAttributes.Builder().build())
            }
        }
        wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
}
