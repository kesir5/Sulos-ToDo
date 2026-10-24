package com.sueleyman.suelostodo.widget

import com.sueleyman.suelostodo.data.INBOX_ID
import com.sueleyman.suelostodo.data.ListColors
import com.sueleyman.suelostodo.data.ListGroup
import com.sueleyman.suelostodo.data.Task
import com.sueleyman.suelostodo.data.TaskList
import com.sueleyman.suelostodo.ui.TreeRow
import com.sueleyman.suelostodo.ui.flattenTree
import java.time.LocalDate

enum class WidgetLayout(val key: String, val title: String, val description: String) {
    COLOR("color", "1 · Farbfläche", "Ganze Fläche in der Gruppenfarbe, weiße Schrift"),
    LIGHT("light", "2 · Hell mit Farbkopf", "Weiße Karte, farbiger Kopfbalken, Fälligkeit rechts"),
    LINES("lines", "3 · Trennlinien", "Weiß, feine Linie zwischen allen Einträgen"),
    ZEBRA("zebra", "4 · Zebra", "Zeilen abwechselnd weiß / hellgrau, farbiger Kopf"),
    TILES("tiles", "5 · Kachelzeilen", "Jeder Eintrag als eigene Kachel auf getöntem Grund"),
    COLOR_LINES("colorlines", "6 · Farbfläche mit Linien", "Farbfläche mit kräftigen hellen Trennlinien"),
    LEVELS("levels", "7 · Ebenen-Schattierung", "Farbbalken in Listenfarbe, Linien zwischen den Einträgen");

    /** layouts with white text on the group colour */
    val dark get() = this == COLOR || this == COLOR_LINES

    companion object {
        /** "cards" and "compact" were removed – such widgets fall back to "Trennlinien". */
        fun of(key: String?) = entries.firstOrNull { it.key == key }
            ?: if (key == "cards" || key == "compact") LINES else COLOR
    }
}

/** Everything a widget needs, loaded from the database. */
data class WidgetData(
    val lists: List<TaskList> = emptyList(),
    val groups: List<ListGroup> = emptyList(),
    val open: List<Task> = emptyList(),
    val loaded: Boolean = false,
    /** completed tasks – only shown when the widget's eye toggle is on */
    val done: List<Task> = emptyList(),
)

/** One block of rows; [title] is shown as small section head (group view). */
data class WidgetSection(val title: String?, val color: Long, val rows: List<TreeRow>, val listId: String? = null)

data class WidgetView(
    val title: String,
    val color: Long,
    val sections: List<WidgetSection>,
    val addToList: String,
) {
    val count get() = sections.sumOf { s -> s.rows.count { it.task.depth == 0 && !it.task.done } }
}

/** Resolves the selected source into title, colour and rows. Pure function – unit-testable. */
/**
 * Open tasks that are really visible: a subtask is hidden when its parent (or grand-parent)
 * is already done – completed tasks are not in [open], so the chain breaks there.
 */
fun visibleOpenTasks(open: List<Task>): List<Task> {
    val byId = open.associateBy { it.id }
    fun visible(t: Task, guard: Int): Boolean {
        val p = t.parentId ?: return true
        val parent = byId[p] ?: return false   // parent done/missing -> hide
        return !parent.done && guard < 5 && visible(parent, guard + 1)
    }
    return open.filter { visible(it, 0) }
}

/**
 * Rows of one list. Open tasks keep their tree; with [showDone] completed subtasks of open
 * parents appear in place (struck through) and completed top-level tasks follow at the end.
 */
private fun listRows(tasks: List<Task>, showDone: Boolean): List<TreeRow> {
    if (!showDone) return flattenTree(tasks)
    val tree = flattenTree(tasks.filter { !(it.done && it.parentId == null) }.sortedBy { it.sortOrder })
    val doneRoots = tasks.filter { it.done && it.parentId == null }
        .sortedByDescending { it.completedAt ?: it.updatedAt }
        .map { TreeRow(it, false, 0, 0) }
    return tree + doneRoots
}

fun buildWidgetView(source: String, data: WidgetData, showDone: Boolean = false): WidgetView {
    val d = data.copy(open = visibleOpenTasks(if (showDone) data.open + data.done else data.open))
    val type = source.substringBefore(':')
    val id = source.substringAfter(':', "")
    val listById = d.lists.associateBy { it.id }
    val today = LocalDate.now().toEpochDay()
    fun listColor(l: TaskList?) = ListColors.argb(l?.color ?: 0)
    fun sectionFor(l: TaskList, title: String?) =
        WidgetSection(title, listColor(l), listRows(d.open.filter { it.listId == l.id }, showDone), l.id)

    return when (type) {
        "group" -> {
            val g = d.groups.firstOrNull { it.id == id }
            if (g == null) buildWidgetView("list:$INBOX_ID", data, showDone) else {
                val subs = d.groups.filter { it.parentId == g.id }
                val direct = d.lists.filter { it.groupId == g.id }
                val color = g.color ?: direct.firstOrNull()?.let { listColor(it) } ?: ListColors.argb(0)
                val sections = direct.map { sectionFor(it, it.name) } +
                    subs.flatMap { s -> d.lists.filter { it.groupId == s.id }.map { sectionFor(it, "${s.name} › ${it.name}") } }
                WidgetView(g.name, color, sections.filter { it.rows.isNotEmpty() }, direct.firstOrNull()?.id ?: INBOX_ID)
            }
        }
        "smart" -> {
            val (title, color, filter) = when (id) {
                "myday" -> Triple("Mein Tag", 0xFF3B6EA5, { t: Task -> t.myDay == today })
                "important" -> Triple("Wichtig", 0xFFC4314B, { t: Task -> t.important })
                "planned" -> Triple("Geplant", 0xFF038387, { t: Task -> t.dueDate != null })
                else -> Triple("Fällig", 0xFFCA5010, { t: Task -> t.dueDate != null && t.dueDate <= today })
            }
            val tasks = (d.open.filter { !it.done && it.listId in listById && filter(it) }
                .sortedWith(compareBy({ it.dueDate ?: Long.MAX_VALUE }, { it.sortOrder })) +
                d.open.filter { it.done && it.listId in listById && filter(it) }
                    .sortedByDescending { it.completedAt ?: it.updatedAt })
                .map { TreeRow(it.copy(depth = 0), false, 0, 0) }
            WidgetView(title, color, listOf(WidgetSection(null, color, tasks)), INBOX_ID)
        }
        else -> {
            val l = listById[id] ?: listById[INBOX_ID] ?: d.lists.firstOrNull()
            if (l == null) WidgetView("Sülos ToDo", ListColors.argb(0), emptyList(), INBOX_ID)
            else WidgetView(l.name, listColor(l), listOf(sectionFor(l, null)), l.id)
        }
    }
}

