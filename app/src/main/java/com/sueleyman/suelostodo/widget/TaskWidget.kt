package com.sueleyman.suelostodo.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.ColorFilter
import androidx.glance.layout.Box
import com.sueleyman.suelostodo.ui.TreeRow
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sueleyman.suelostodo.MainActivity
import com.sueleyman.suelostodo.R
import com.sueleyman.suelostodo.SuelosToDoApp
import com.sueleyman.suelostodo.data.INBOX_ID
import com.sueleyman.suelostodo.data.ListColors
import com.sueleyman.suelostodo.data.ListGroup
import com.sueleyman.suelostodo.data.Task
import com.sueleyman.suelostodo.data.TaskList
import com.sueleyman.suelostodo.ui.flattenTree
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/** Selected source of a widget: "list:<id>", "group:<id>" or "smart:<myday|important|due|planned>". */
internal val KEY_SOURCE = stringPreferencesKey("source")
private val KEY_LEGACY_LIST = stringPreferencesKey("listId")
/** Layout of a widget: "color" | "light" | "compact" | "cards" */
internal val KEY_LAYOUT = stringPreferencesKey("layout")

enum class WidgetLayout(val key: String, val title: String, val description: String) {
    COLOR("color", "1 · Farbfläche", "Ganze Fläche in der Gruppenfarbe, weiße Schrift"),
    LIGHT("light", "2 · Hell mit Farbkopf", "Weiße Karte, farbiger Kopfbalken, Fälligkeit rechts"),
    COMPACT("compact", "3 · Kompakt mit Zählern", "Zähler oben, dringendste Aufgaben einzeilig – für kleine Widgets"),
    CARDS("cards", "4 · Karten je Liste", "Jede Liste als eigene Karte – für große Widgets");

    companion object { fun of(key: String?) = entries.firstOrNull { it.key == key } ?: COLOR }
}
internal val KEY_TASK = ActionParameters.Key<String>("taskId")

/** Everything a widget needs, loaded from the database. */
data class WidgetData(
    val lists: List<TaskList> = emptyList(),
    val groups: List<ListGroup> = emptyList(),
    val open: List<Task> = emptyList(),
    val loaded: Boolean = false,
)

/** One block of rows; [title] is shown as small section head (group view). */
data class WidgetSection(val title: String?, val color: Long, val rows: List<com.sueleyman.suelostodo.ui.TreeRow>)

data class WidgetView(
    val title: String,
    val color: Long,
    val sections: List<WidgetSection>,
    val addToList: String,
) {
    val count get() = sections.sumOf { s -> s.rows.count { it.task.depth == 0 } }
}

/** Resolves the selected source into title, colour and rows. Pure function – unit-testable. */
fun buildWidgetView(source: String, d: WidgetData): WidgetView {
    val type = source.substringBefore(':')
    val id = source.substringAfter(':', "")
    val listById = d.lists.associateBy { it.id }
    val today = LocalDate.now().toEpochDay()
    fun listColor(l: TaskList?) = ListColors.argb(l?.color ?: 0)
    fun sectionFor(l: TaskList, title: String?) =
        WidgetSection(title, listColor(l), flattenTree(d.open.filter { it.listId == l.id }))

    return when (type) {
        "group" -> {
            val g = d.groups.firstOrNull { it.id == id }
            if (g == null) buildWidgetView("list:$INBOX_ID", d) else {
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
            val tasks = d.open.filter { it.listId in listById && filter(it) }
                .sortedWith(compareBy({ it.dueDate ?: Long.MAX_VALUE }, { it.sortOrder }))
                .map { com.sueleyman.suelostodo.ui.TreeRow(it.copy(depth = 0), false, 0, 0) }
            WidgetView(title, color, listOf(WidgetSection(null, color, tasks)), INBOX_ID)
        }
        else -> {
            val l = listById[id] ?: listById[INBOX_ID] ?: d.lists.firstOrNull()
            if (l == null) WidgetView("Sülos ToDo", ListColors.argb(0), emptyList(), INBOX_ID)
            else WidgetView(l.name, listColor(l), listOf(sectionFor(l, null)), l.id)
        }
    }
}

class TaskWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = (context.applicationContext as SuelosToDoApp).repo
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        // IMPORTANT: create the flow once. Creating it inside the composition made collectAsState
        // restart on every recomposition, so the widget never finished loading (endless spinner).
        val data: Flow<WidgetData> = combine(repo.observeLists(), repo.observeGroups(), repo.observeOpenTasks()) { l, g, o ->
            WidgetData(l, g, o, loaded = true)
        }
        provideContent {
            val prefs = currentState<Preferences>()
            val d by data.collectAsState(initial = WidgetData())
            val source = prefs[KEY_SOURCE] ?: prefs[KEY_LEGACY_LIST]?.let { "list:$it" } ?: "list:$INBOX_ID"
            val view = remember(source, d) { buildWidgetView(source, d) }
            when (WidgetLayout.of(prefs[KEY_LAYOUT])) {
                WidgetLayout.COLOR -> WidgetContent(context, appWidgetId, view, d.loaded)
                WidgetLayout.LIGHT -> LightContent(context, appWidgetId, view, d.loaded)
                WidgetLayout.COMPACT -> CompactContent(context, appWidgetId, view, d.loaded)
                WidgetLayout.CARDS -> CardsContent(context, appWidgetId, view, d.loaded)
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) = TaskWidget().updateAll(context)
    }
}

@Composable
private fun WidgetContent(context: Context, appWidgetId: Int, v: WidgetView, loaded: Boolean) {
    val bg = Color(v.color)
    val white = ColorProvider(Color.White)
    val dim = ColorProvider(Color(0xCCFFFFFF))
    val pick = actionStartActivity(
        Intent(context, WidgetConfigActivity::class.java)
            .setData(Uri.parse("suelostodo://widget/$appWidgetId"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    )

    Column(
        modifier = GlanceModifier.fillMaxSize().background(bg).cornerRadius(20.dp).padding(12.dp)
    ) {
        // header: tap the title to choose group / list
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier.defaultWeight().clickable(pick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = v.title,
                    style = TextStyle(color = white, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Image(ImageProvider(R.drawable.ic_dropdown), "Gruppe oder Liste wählen", GlanceModifier.size(22.dp))
                Spacer(GlanceModifier.width(4.dp))
                Text("${v.count}", style = TextStyle(color = dim, fontSize = 13.sp))
            }
            Image(
                ImageProvider(R.drawable.ic_add), "Aufgabe hinzufügen",
                GlanceModifier.size(30.dp).clickable(
                    actionStartActivity(
                        Intent(context, MainActivity::class.java)
                            .setData(Uri.parse("suelostodo://add/${v.addToList}"))
                            .putExtra(MainActivity.EXTRA_ADD_TO_LIST, v.addToList)
                    )
                )
            )
        }
        val rows = v.sections.flatMap { s ->
            (if (s.title != null) listOf<Any>(s) else emptyList()) + s.rows
        }
        if (rows.isEmpty()) {
            Text(
                if (loaded) "Keine offenen Aufgaben" else "Lade …",
                style = TextStyle(color = dim, fontSize = 14.sp)
            )
        } else {
            LazyColumn {
                items(rows, itemId = { r ->
                    when (r) {
                        is WidgetSection -> ("s" + r.title).hashCode().toLong()
                        else -> (r as com.sueleyman.suelostodo.ui.TreeRow).task.id.hashCode().toLong()
                    }
                }) { r ->
                    if (r is WidgetSection) {
                        Text(
                            r.title ?: "",
                            style = TextStyle(color = dim, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            modifier = GlanceModifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    } else {
                        val row = r as com.sueleyman.suelostodo.ui.TreeRow
                        val t = row.task
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().padding(start = (t.depth * 18).dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                ImageProvider(R.drawable.ic_circle), "Erledigen",
                                GlanceModifier.size(if (t.depth == 0) 22.dp else 18.dp)
                                    .clickable(actionRunCallback<ToggleDoneAction>(actionParametersOf(KEY_TASK to t.id)))
                            )
                            Spacer(GlanceModifier.width(8.dp))
                            Text(
                                text = t.title + if (row.hasChildren && !t.expanded) "  (+${row.childCount})" else "",
                                style = TextStyle(color = if (t.depth == 0) white else dim, fontSize = if (t.depth == 0) 15.sp else 14.sp),
                                maxLines = 2,
                                modifier = GlanceModifier.defaultWeight().clickable(
                                    actionStartActivity(
                                        Intent(context, MainActivity::class.java)
                                            .setData(Uri.parse("suelostodo://task/${t.id}"))
                                            .putExtra(MainActivity.EXTRA_TASK_ID, t.id)
                                    )
                                )
                            )
                            if (t.important) Image(ImageProvider(R.drawable.ic_star), "Wichtig", GlanceModifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================== shared helpers

private fun pickAction(context: Context, appWidgetId: Int) = actionStartActivity(
    Intent(context, WidgetConfigActivity::class.java)
        .setData(Uri.parse("suelostodo://widget/$appWidgetId"))
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
)

private fun addAction(context: Context, listId: String) = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .setData(Uri.parse("suelostodo://add/$listId"))
        .putExtra(MainActivity.EXTRA_ADD_TO_LIST, listId)
)

private fun openTaskAction(context: Context, id: String) = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .setData(Uri.parse("suelostodo://task/$id"))
        .putExtra(MainActivity.EXTRA_TASK_ID, id)
)

private fun doneAction(id: String) = actionRunCallback<ToggleDoneAction>(actionParametersOf(KEY_TASK to id))

private val INK = Color(0xFF1F1F1F)
private val INK2 = Color(0xFF6B6B6B)
private val RED = Color(0xFFC4314B)

/** Darker variant of a colour for text on light backgrounds. */
private fun Color.deep() = Color(red = red * 0.7f, green = green * 0.7f, blue = blue * 0.7f)

private fun dueLabel(t: Task): Pair<String, Boolean>? {
    val d = t.dueDate ?: return null
    val today = LocalDate.now().toEpochDay()
    return when {
        d < today -> "überfällig" to true
        d == today -> "heute" to true
        d == today + 1 -> "morgen" to false
        else -> LocalDate.ofEpochDay(d).let { "${it.dayOfMonth}.${it.monthValue}." } to false
    }
}

@Composable
private fun TaskLine(context: Context, row: TreeRow, accent: Color, textColor: Color, subColor: Color, showDue: Boolean, singleLine: Boolean = false) {
    val t = row.task
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(start = (t.depth * 18).dp, top = if (singleLine) 3.dp else 5.dp, bottom = if (singleLine) 3.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            ImageProvider(R.drawable.ic_circle), "Erledigen",
            GlanceModifier.size(if (t.depth == 0 && !singleLine) 20.dp else 16.dp).clickable(doneAction(t.id)),
            colorFilter = ColorFilter.tint(ColorProvider(accent))
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = t.title + if (row.hasChildren && !t.expanded) "  (+${row.childCount})" else "",
            style = TextStyle(color = ColorProvider(if (t.depth == 0) textColor else subColor), fontSize = if (singleLine) 13.sp else 14.sp),
            maxLines = if (singleLine) 1 else 2,
            modifier = GlanceModifier.defaultWeight().clickable(openTaskAction(context, t.id))
        )
        if (showDue) dueLabel(t)?.let { (txt, hot) ->
            Text(txt, style = TextStyle(color = ColorProvider(if (hot) RED else subColor), fontSize = 11.sp))
        }
        if (t.important) Image(
            ImageProvider(R.drawable.ic_star), "Wichtig", GlanceModifier.size(14.dp),
            colorFilter = ColorFilter.tint(ColorProvider(accent))
        )
    }
}

@Composable
private fun Header(context: Context, appWidgetId: Int, v: WidgetView, fg: Color, sub: Color, plusBg: Color, plusFg: Color, size: Int = 17) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(GlanceModifier.defaultWeight().clickable(pickAction(context, appWidgetId)), verticalAlignment = Alignment.CenterVertically) {
            Text(v.title, style = TextStyle(color = ColorProvider(fg), fontSize = size.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Image(ImageProvider(R.drawable.ic_dropdown), "Auswahl", GlanceModifier.size(20.dp), colorFilter = ColorFilter.tint(ColorProvider(fg)))
            Text("${v.count}", style = TextStyle(color = ColorProvider(sub), fontSize = 12.sp))
        }
        Box(
            GlanceModifier.size(28.dp).cornerRadius(14.dp).background(plusBg).clickable(addAction(context, v.addToList)),
            contentAlignment = Alignment.Center
        ) {
            Image(ImageProvider(R.drawable.ic_add), "Aufgabe hinzufügen", GlanceModifier.size(20.dp), colorFilter = ColorFilter.tint(ColorProvider(plusFg)))
        }
    }
}

private fun flatItems(v: WidgetView): List<Any> =
    v.sections.flatMap { s -> (if (s.title != null) listOf<Any>(s) else emptyList()) + s.rows }

private fun itemId(r: Any): Long = when (r) {
    is WidgetSection -> ("s" + r.title).hashCode().toLong()
    else -> (r as TreeRow).task.id.hashCode().toLong()
}

// =========================================================================== 2 · light

@Composable
private fun LightContent(context: Context, appWidgetId: Int, v: WidgetView, loaded: Boolean) {
    val accent = Color(v.color)
    Column(GlanceModifier.fillMaxSize().background(Color.White).cornerRadius(20.dp)) {
        Box(GlanceModifier.fillMaxWidth().background(accent).padding(horizontal = 12.dp, vertical = 9.dp)) {
            Header(context, appWidgetId, v, Color.White, Color(0xDDFFFFFF), Color(0x33FFFFFF), Color.White, 16)
        }
        val items = flatItems(v)
        if (items.isEmpty()) Text(if (loaded) "Keine offenen Aufgaben" else "Lade …",
            style = TextStyle(color = ColorProvider(INK2), fontSize = 13.sp), modifier = GlanceModifier.padding(12.dp))
        else LazyColumn(GlanceModifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            items(items, itemId = { itemId(it) }) { r ->
                if (r is WidgetSection) Text(
                    (r.title ?: "").uppercase(), maxLines = 1,
                    style = TextStyle(color = ColorProvider(accent.deep()), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.padding(top = 7.dp, bottom = 1.dp)
                ) else TaskLine(context, r as TreeRow, accent, INK, INK2, showDue = true)
            }
        }
    }
}

// =========================================================================== 3 · compact

@Composable
private fun CompactContent(context: Context, appWidgetId: Int, v: WidgetView, loaded: Boolean) {
    val accent = Color(v.color)
    val all = v.sections.flatMap { it.rows }.filter { it.task.depth == 0 }
    val today = LocalDate.now().toEpochDay()
    val due = all.count { (it.task.dueDate ?: Long.MAX_VALUE) <= today }
    val important = all.count { it.task.important }
    val top = all.sortedWith(compareBy({ it.task.dueDate ?: Long.MAX_VALUE }, { if (it.task.important) 0 else 1 }, { it.task.sortOrder }))
    val shown = top.take(5)
    Column(GlanceModifier.fillMaxSize().background(Color.White).cornerRadius(18.dp).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Header(context, appWidgetId, v, INK, INK2, accent.copy(alpha = 0.18f), accent.deep(), 14)
        Row(GlanceModifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
            Counter("${all.size}", "offen", Color(0xFFF1F0EC), INK, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(5.dp))
            Counter("$due", "fällig", if (due > 0) Color(0xFFFCE6EA) else Color(0xFFF1F0EC), if (due > 0) RED else INK, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(5.dp))
            Counter("$important", "wichtig", Color(0xFFF1F0EC), INK, GlanceModifier.defaultWeight())
        }
        if (shown.isEmpty()) Text(if (loaded) "Keine offenen Aufgaben" else "Lade …", style = TextStyle(color = ColorProvider(INK2), fontSize = 12.sp))
        shown.forEach { TaskLine(context, it.copy(hasChildren = false), accent, INK, INK2, showDue = false, singleLine = true) }
        if (top.size > shown.size) Text("+${top.size - shown.size} weitere", style = TextStyle(color = ColorProvider(INK2), fontSize = 11.sp))
    }
}

@Composable
private fun Counter(n: String, label: String, bg: Color, fg: Color, modifier: GlanceModifier) {
    Row(modifier.background(bg).cornerRadius(7.dp).padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically) {
        Text(n, style = TextStyle(color = ColorProvider(fg), fontSize = 12.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.width(3.dp))
        Text(label, style = TextStyle(color = ColorProvider(fg), fontSize = 11.sp))
    }
}

// =========================================================================== 4 · cards

@Composable
private fun CardsContent(context: Context, appWidgetId: Int, v: WidgetView, loaded: Boolean) {
    val accent = Color(v.color)
    Column(GlanceModifier.fillMaxSize().background(Color(0xFFFBF8F6)).cornerRadius(20.dp).padding(horizontal = 10.dp, vertical = 10.dp)) {
        Box(GlanceModifier.padding(start = 4.dp, bottom = 6.dp)) {
            Header(context, appWidgetId, v, accent.deep(), INK2, accent, Color.White, 16)
        }
        if (v.sections.isEmpty()) Text(if (loaded) "Keine offenen Aufgaben" else "Lade …",
            style = TextStyle(color = ColorProvider(INK2), fontSize = 13.sp), modifier = GlanceModifier.padding(4.dp))
        else LazyColumn {
            items(v.sections, itemId = { ("c" + it.title + it.rows.firstOrNull()?.task?.id).hashCode().toLong() }) { s ->
                val c = Color(s.color)
                Column(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Column(GlanceModifier.fillMaxWidth().background(Color.White).cornerRadius(12.dp).padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(c)) {}
                            Spacer(GlanceModifier.width(6.dp))
                            Text((s.title ?: v.title).uppercase(), maxLines = 1,
                                style = TextStyle(color = ColorProvider(c.deep()), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                modifier = GlanceModifier.defaultWeight())
                            Text("${s.rows.count { it.task.depth == 0 }}", style = TextStyle(color = ColorProvider(c.deep()), fontSize = 11.sp))
                        }
                        s.rows.take(8).forEach { TaskLine(context, it, c, INK, INK2, showDue = true) }
                        if (s.rows.size > 8) Text("+${s.rows.size - 8} weitere", style = TextStyle(color = ColorProvider(INK2), fontSize = 11.sp))
                    }
                }
            }
        }
    }
}

class ToggleDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[KEY_TASK] ?: return
        (context.applicationContext as SuelosToDoApp).repo.toggleDone(id)
        TaskWidget().update(context, glanceId)
    }
}

class TaskWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TaskWidget()
}
