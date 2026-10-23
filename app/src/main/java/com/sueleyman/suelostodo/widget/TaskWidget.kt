package com.sueleyman.suelostodo.widget

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.sueleyman.suelostodo.MainActivity
import com.sueleyman.suelostodo.R
import com.sueleyman.suelostodo.SuelosToDoApp
import com.sueleyman.suelostodo.data.ListColors
import com.sueleyman.suelostodo.data.Task
import com.sueleyman.suelostodo.ui.TreeRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.LocalDate

/*
 * Home-screen widget built with classic RemoteViews (no Glance / WorkManager):
 * every change is pushed directly via AppWidgetManager, so the widget never hangs on a spinner
 * and keeps reacting even when the system holds back background jobs.
 */

private const val ACTION_PICKER = "com.sueleyman.suelostodo.widget.PICKER"
private const val ACTION_LAYOUT = "com.sueleyman.suelostodo.widget.LAYOUT"
private const val EXTRA_LAYOUT = "layout"
internal const val EXTRA_OP = "op"
internal const val EXTRA_TASK = "task"
internal const val EXTRA_SOURCE = "source"

private const val INK = 0xFF1F1F1F.toInt()
private const val INK2 = 0xFF6B6B6B.toInt()
private const val RED = 0xFFC4314B.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
private const val DIM = 0xCCFFFFFF.toInt()

private fun deep(c: Long): Int {
    val r = ((c shr 16) and 0xFF) * 7 / 10
    val g = ((c shr 8) and 0xFF) * 7 / 10
    val b = (c and 0xFF) * 7 / 10
    return (0xFF000000L or (r shl 16) or (g shl 8) or b).toInt()
}

/** Mixes [over] with [alpha] (0..255) onto opaque [base]. */
private fun blend(base: Int, over: Int, alpha: Int): Int {
    fun ch(v: Int, s: Int) = (v shr s) and 0xFF
    val r = (ch(base, 16) * (255 - alpha) + ch(over, 16) * alpha) / 255
    val g = (ch(base, 8) * (255 - alpha) + ch(over, 8) * alpha) / 255
    val b = (ch(base, 0) * (255 - alpha) + ch(over, 0) * alpha) / 255
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

/** Entry point used by the app to redraw widgets. */
object TaskWidget {
    fun ids(c: Context): IntArray =
        AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c, TaskWidgetReceiver::class.java))

    suspend fun refresh(c: Context) = updateAll(c)

    suspend fun updateAll(c: Context) {
        val ids = ids(c)
        if (ids.isEmpty()) return
        val data = withContext(Dispatchers.IO) { (c.applicationContext as SuelosToDoApp).repo.widgetData() }
        ids.forEach { push(c, it, data) }
    }

    suspend fun update(c: Context, id: Int) {
        val data = withContext(Dispatchers.IO) { (c.applicationContext as SuelosToDoApp).repo.widgetData() }
        push(c, id, data)
    }

    private fun push(c: Context, id: Int, data: WidgetData) {
        val mgr = AppWidgetManager.getInstance(c)
        mgr.updateAppWidget(id, buildRoot(c, id, data, WidgetPrefs.get(c, id)))
        @Suppress("DEPRECATION")
        mgr.notifyAppWidgetViewDataChanged(id, R.id.w_list)
    }

    /** Builds the outer widget (background, header, counters, chips, list adapter). */
    fun buildRoot(c: Context, id: Int, data: WidgetData, sel: WidgetSel): RemoteViews {
        val v = buildWidgetView(sel.source, data)
        val layout = WidgetLayout.of(sel.layout)
        val rv = RemoteViews(c.packageName, R.layout.widget_root)
        val accent = v.color

        // ---------- colours per layout
        val bg: Int; val titleColor: Int; val subColor: Int; val plusBg: Int; val plusFg: Int; var headBg = false
        when {
            sel.picker -> { bg = WHITE; titleColor = INK; subColor = INK2; plusBg = 0xFFF1F0EC.toInt(); plusFg = INK }
            layout.dark -> { bg = accent.toInt(); titleColor = WHITE; subColor = DIM; plusBg = 0x33FFFFFF; plusFg = WHITE }
            layout == WidgetLayout.LIGHT || layout == WidgetLayout.ZEBRA -> { bg = WHITE; titleColor = WHITE; subColor = DIM; plusBg = 0x33FFFFFF; plusFg = WHITE; headBg = true }
            layout == WidgetLayout.COMPACT || layout == WidgetLayout.LINES || layout == WidgetLayout.LEVELS ->
                { bg = WHITE; titleColor = INK; subColor = INK2; plusBg = (0x2E000000L or (accent and 0xFFFFFF)).toInt(); plusFg = deep(accent) }
            layout == WidgetLayout.TILES -> { bg = 0xFFF1ECE8.toInt(); titleColor = deep(accent); subColor = INK2; plusBg = accent.toInt(); plusFg = WHITE }
            else -> { bg = 0xFFFBF8F6.toInt(); titleColor = deep(accent); subColor = INK2; plusBg = accent.toInt(); plusFg = WHITE }
        }
        rv.setInt(R.id.w_bg, "setColorFilter", bg)
        val headLine = !sel.picker && layout == WidgetLayout.LINES
        rv.setViewVisibility(R.id.w_head_line, if (headLine) View.VISIBLE else View.GONE)
        if (headLine) rv.setInt(R.id.w_head_line, "setBackgroundColor", accent.toInt())
        rv.setViewVisibility(R.id.w_head_bg, if (headBg) View.VISIBLE else View.GONE)
        if (headBg) rv.setInt(R.id.w_head_bg, "setColorFilter", accent.toInt())

        // ---------- header
        rv.setTextViewText(R.id.w_title, if (sel.picker) "Anzeigen …" else v.title)
        rv.setTextColor(R.id.w_title, titleColor)
        rv.setTextViewText(R.id.w_count, if (sel.picker) "" else " ${v.count}")
        rv.setTextColor(R.id.w_count, subColor)
        rv.setViewVisibility(R.id.w_drop, if (sel.picker) View.GONE else View.VISIBLE)
        rv.setInt(R.id.w_drop, "setColorFilter", titleColor)
        rv.setOnClickPendingIntent(R.id.w_title_box, broadcast(c, id, ACTION_PICKER, 1))
        rv.setInt(R.id.w_add_bg, "setColorFilter", plusBg)
        rv.setInt(R.id.w_add_icon, "setColorFilter", plusFg)
        if (sel.picker) {
            rv.setImageViewResource(R.id.w_add_icon, R.drawable.ic_close_w)
            rv.setOnClickPendingIntent(R.id.w_add, broadcast(c, id, ACTION_PICKER, 2))
        } else {
            rv.setImageViewResource(R.id.w_add_icon, R.drawable.ic_add)
            rv.setOnClickPendingIntent(
                R.id.w_add,
                PendingIntent.getActivity(
                    c, id * 10 + 3,
                    Intent(c, MainActivity::class.java)
                        .setData(Uri.parse("suelostodo://add/${v.addToList}"))
                        .putExtra(MainActivity.EXTRA_ADD_TO_LIST, v.addToList)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        // ---------- compact counters
        val showCounters = !sel.picker && layout == WidgetLayout.COMPACT
        rv.setViewVisibility(R.id.w_counters, if (showCounters) View.VISIBLE else View.GONE)
        if (showCounters) {
            val all = v.sections.flatMap { it.rows }.filter { it.task.depth == 0 }
            val today = LocalDate.now().toEpochDay()
            val due = all.count { (it.task.dueDate ?: Long.MAX_VALUE) <= today }
            rv.setTextViewText(R.id.w_c1, "${all.size} offen")
            rv.setTextViewText(R.id.w_c2, "$due fällig")
            rv.setTextViewText(R.id.w_c3, "${all.count { it.task.important }} wichtig")
            listOf(R.id.w_c1, R.id.w_c2, R.id.w_c3).forEach { rv.setTextColor(it, INK); rv.setInt(it, "setBackgroundColor", 0) }
            if (due > 0) rv.setTextColor(R.id.w_c2, RED)
        }

        // layout choice is part of the picker list (9 layouts)
        rv.setViewVisibility(R.id.w_chips, View.GONE)

        // ---------- list
        val svc = Intent(c, WidgetListService::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .setData(Uri.parse("suelostodo://widgetlist/$id"))
        @Suppress("DEPRECATION")
        rv.setRemoteAdapter(R.id.w_list, svc)
        rv.setEmptyView(R.id.w_list, R.id.w_empty)
        rv.setTextColor(R.id.w_empty, if (!sel.picker && layout.dark) DIM else INK2)
        rv.setPendingIntentTemplate(
            R.id.w_list,
            PendingIntent.getActivity(
                c, id * 10 + 7,
                Intent(c, WidgetActionActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .setData(Uri.parse("suelostodo://widgetaction/$id")),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        )
        return rv
    }

    private fun broadcast(c: Context, id: Int, action: String, code: Int, layout: String? = null): PendingIntent =
        PendingIntent.getBroadcast(
            c, id * 100 + code,
            Intent(c, TaskWidgetReceiver::class.java).setAction(action)
                .setData(Uri.parse("suelostodo://widgetbc/$id/$code"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .apply { layout?.let { putExtra(EXTRA_LAYOUT, it) } },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

// ===================================================================== list items

sealed interface WItem {
    val id: Long
    data class Section(val title: String, val color: Long) : WItem { override val id = ("s$title").hashCode().toLong() }
    data class Row(val row: TreeRow, val color: Long, val single: Boolean) : WItem { override val id = row.task.id.hashCode().toLong() }
    data class Head(val label: String) : WItem { override val id = ("h$label").hashCode().toLong() }
    data class Option(val key: String, val label: String, val color: Long, val indent: Int, val selected: Boolean) : WItem {
        override val id = key.hashCode().toLong()
    }
}

/** Items shown in the widget list for the given selection. Pure function – unit-testable. */
fun widgetItems(sel: WidgetSel, d: WidgetData): List<WItem> {
    if (sel.picker) return buildList {
        add(WItem.Head("Layout"))
        WidgetLayout.entries.forEach { l ->
            add(WItem.Option("layout:${l.key}", (if (l.key == sel.layout || (sel.layout !in WidgetLayout.entries.map { it.key } && l == WidgetLayout.COLOR)) "✓ " else "") + l.title,
                0xFF185FA5, 0, l == WidgetLayout.of(sel.layout)))
        }
        add(WItem.Head("Smart-Listen"))
        listOf(
            Triple("smart:myday", "Mein Tag", 0xFF3B6EA5), Triple("smart:important", "Wichtig", 0xFFC4314B),
            Triple("smart:due", "Fällig", 0xFFCA5010), Triple("smart:planned", "Geplant", 0xFF038387),
        ).forEach { (k, l, c) -> add(WItem.Option(k, l, c, 0, sel.source == k)) }
        val ids = d.groups.mapTo(HashSet()) { it.id }
        val tops = d.groups.filter { it.parentId == null || it.parentId !in ids }
        if (tops.isNotEmpty()) add(WItem.Head("Gruppen"))
        tops.forEach { t ->
            val first = d.lists.firstOrNull { it.groupId == t.id }?.let { ListColors.argb(it.color) } ?: 0xFF5D5A58
            add(WItem.Option("group:${t.id}", "📁 ${t.name}", t.color ?: first, 0, sel.source == "group:${t.id}"))
            d.groups.filter { it.parentId == t.id }.forEach { s ->
                add(WItem.Option("group:${s.id}", "📁 ${s.name}", s.color ?: t.color ?: first, 1, sel.source == "group:${s.id}"))
            }
        }
        add(WItem.Head("Listen"))
        d.lists.forEach { add(WItem.Option("list:${it.id}", it.name, ListColors.argb(it.color), 0, sel.source == "list:${it.id}")) }
    }
    val v = buildWidgetView(sel.source, d)
    return when (WidgetLayout.of(sel.layout)) {
        WidgetLayout.COMPACT -> v.sections.flatMap { s -> s.rows.filter { it.task.depth == 0 }.map { it to s.color } }
            .sortedWith(compareBy({ it.first.task.dueDate ?: Long.MAX_VALUE }, { if (it.first.task.important) 0 else 1 }))
            .take(6).map { (r, c) -> WItem.Row(r.copy(hasChildren = false), c, true) }
        WidgetLayout.CARDS -> v.sections.flatMap { s ->
            listOf(WItem.Section("${s.title ?: v.title}  ·  ${s.rows.count { it.task.depth == 0 }}", s.color)) +
                s.rows.map { WItem.Row(it, s.color, false) }
        }
        else -> v.sections.flatMap { s ->
            (if (s.title != null) listOf(WItem.Section(s.title, s.color)) else emptyList()) + s.rows.map { WItem.Row(it, s.color, false) }
        }
    }
}

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

class WidgetListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        WidgetFactory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
}

class WidgetFactory(private val c: Context, private val widgetId: Int) : RemoteViewsService.RemoteViewsFactory {
    private var items: List<WItem> = emptyList()
    private var sel = WidgetSel()

    override fun onCreate() {}
    override fun onDestroy() {}
    override fun onDataSetChanged() {
        sel = WidgetPrefs.get(c, widgetId)
        val data = runCatching { runBlocking { (c.applicationContext as SuelosToDoApp).repo.widgetData() } }.getOrElse { WidgetData() }
        items = widgetItems(sel, data)
    }

    override fun getCount() = items.size
    override fun getViewTypeCount() = 5
    override fun hasStableIds() = true
    override fun getItemId(position: Int) = items.getOrNull(position)?.id ?: position.toLong()
    override fun getLoadingView(): RemoteViews? = null

    override fun getViewAt(position: Int): RemoteViews {
        val layout = WidgetLayout.of(sel.layout)
        val dark = !sel.picker && layout.dark
        // index among task rows (for zebra shading)
        val rowIndex = items.take(position).count { it is WItem.Row }
        return when (val it = items.getOrNull(position)) {
            is WItem.Section -> RemoteViews(c.packageName, R.layout.widget_item_section).apply {
                setTextViewText(R.id.s_title, it.title)
                setTextColor(R.id.s_title, if (dark) DIM else deep(it.color))
                if (layout == WidgetLayout.CARDS) {
                    setInt(R.id.s_title, "setBackgroundColor", WHITE)
                    setViewPadding(R.id.s_title, 24, 26, 24, 6)
                }
            }
            is WItem.Head -> RemoteViews(c.packageName, R.layout.widget_item_section).apply {
                setTextViewText(R.id.s_title, it.label)
                setTextColor(R.id.s_title, INK2)
            }
            is WItem.Option -> RemoteViews(c.packageName, R.layout.widget_item_option).apply {
                setTextViewText(R.id.o_label, it.label)
                setInt(R.id.o_dot, "setColorFilter", it.color.toInt())
                setInt(R.id.o_row, "setBackgroundColor", if (it.selected) 0xFFE6F1FB.toInt() else 0)
                setViewPadding(R.id.o_row, (24 + it.indent * 40), 0, 16, 0)
                if (it.key.startsWith("layout:"))
                    setOnClickFillInIntent(R.id.o_row, Intent().putExtra(EXTRA_OP, "layout").putExtra(EXTRA_SOURCE, it.key.removePrefix("layout:")))
                else
                    setOnClickFillInIntent(R.id.o_row, Intent().putExtra(EXTRA_OP, "select").putExtra(EXTRA_SOURCE, it.key))
            }
            is WItem.Row -> RemoteViews(
                c.packageName, if (layout == WidgetLayout.TILES) R.layout.widget_item_task_tile else R.layout.widget_item_task
            ).apply {
                val t = it.row.task
                val accent = it.color.toInt()
                val sub = t.depth > 0
                val title = t.title + if (it.row.hasChildren && !t.expanded) "  (+${it.row.childCount})" else ""
                if (layout == WidgetLayout.LEVELS && !sub)
                    setTextViewText(R.id.i_title, android.text.SpannableString(title).apply {
                        setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, length, 0)
                    })
                else setTextViewText(R.id.i_title, title)
                setTextColor(R.id.i_title, if (dark) (if (sub) DIM else WHITE) else (if (sub) INK2 else INK))
                setInt(R.id.i_title, "setMaxLines", if (it.single) 1 else 2)
                setInt(R.id.i_check, "setColorFilter", if (dark) WHITE else accent)
                setInt(R.id.i_star, "setColorFilter", if (dark) WHITE else accent)
                setViewVisibility(R.id.i_star, if (t.important) View.VISIBLE else View.GONE)
                val showDue = layout in setOf(WidgetLayout.LIGHT, WidgetLayout.CARDS, WidgetLayout.LINES, WidgetLayout.ZEBRA,
                    WidgetLayout.TILES, WidgetLayout.COLOR_LINES, WidgetLayout.LEVELS)
                val due = if (showDue) dueLabel(t) else null
                setViewVisibility(R.id.i_due, if (due != null) View.VISIBLE else View.GONE)
                due?.let { (txt, hot) ->
                    setTextViewText(R.id.i_due, txt)
                    setTextColor(R.id.i_due, if (dark) (if (hot) WHITE else DIM) else if (hot) RED else INK2)
                }
                val indent = t.depth * (if (layout == WidgetLayout.TILES) 36 else 44)
                setViewPadding(R.id.i_row, 12 + indent, 0, 12, 0)
                // ---- separation per layout
                when (layout) {
                    WidgetLayout.CARDS -> setInt(R.id.i_row, "setBackgroundColor", WHITE)
                    WidgetLayout.LINES -> {
                        setViewVisibility(R.id.i_div, View.VISIBLE)
                        setInt(R.id.i_div, "setBackgroundColor", 0xFFE7E5E0.toInt())
                    }
                    WidgetLayout.COLOR_LINES -> {
                        setViewVisibility(R.id.i_div, View.VISIBLE)
                        setInt(R.id.i_div, "setBackgroundColor", 0x38FFFFFF)
                    }
                    WidgetLayout.ZEBRA -> setInt(R.id.i_row, "setBackgroundColor", if (rowIndex % 2 == 1) 0xFFF4F2EF.toInt() else WHITE)
                    WidgetLayout.LEVELS -> {
                        val tint = when (t.depth) { 0 -> 0x24; 1 -> 0x10; else -> 0x00 }
                        val bgc = blend(WHITE, accent, tint)
                        setInt(R.id.i_wrap, "setBackgroundColor", bgc)
                        setViewVisibility(R.id.i_bar, View.VISIBLE)
                        setInt(R.id.i_bar, "setBackgroundColor", (when (t.depth) { 0 -> 0xFF; 1 -> 0x99; else -> 0x55 } shl 24) or (accent and 0xFFFFFF))
                        setViewPadding(R.id.i_row, 0, 0, 12, 0)
                        setViewPadding(R.id.i_wrap, indent / 2, if (t.depth == 0 && rowIndex > 0) 6 else 0, 0, 0)
                    }
                    else -> {}
                }
                setOnClickFillInIntent(R.id.i_check, Intent().putExtra(EXTRA_OP, "toggle").putExtra(EXTRA_TASK, t.id))
                setOnClickFillInIntent(R.id.i_title, Intent().putExtra(EXTRA_OP, "open").putExtra(EXTRA_TASK, t.id))
            }
            null -> RemoteViews(c.packageName, R.layout.widget_item_section)
        }
    }
}

// ===================================================================== receiver + action activity

class TaskWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        (context.applicationContext as SuelosToDoApp).appScope.launch {
            try { ids.forEach { runCatching { TaskWidget.update(context, it) } } } finally { pending.finish() }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        onUpdate(context, mgr, intArrayOf(id))
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetPrefs.remove(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        when (intent.action) {
            ACTION_PICKER -> {
                WidgetPrefs.update(context, id) { it.copy(picker = !it.picker) }
                redraw(context, id)
            }
            ACTION_LAYOUT -> {
                val l = intent.getStringExtra(EXTRA_LAYOUT) ?: return
                WidgetPrefs.update(context, id) { it.copy(layout = l) }
                redraw(context, id)
            }
            else -> super.onReceive(context, intent)
        }
    }

    private fun redraw(context: Context, id: Int) {
        val pending = goAsync()
        (context.applicationContext as SuelosToDoApp).appScope.launch {
            try { runCatching { TaskWidget.update(context, id) } } finally { pending.finish() }
        }
    }
}

/** Invisible activity handling taps inside the widget list (abhaken, öffnen, Auswahl). */
class WidgetActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SuelosToDoApp
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        when (intent.getStringExtra(EXTRA_OP)) {
            "toggle" -> intent.getStringExtra(EXTRA_TASK)?.let { t ->
                app.appScope.launch { runCatching { app.repo.toggleDone(t); TaskWidget.updateAll(app) } }
            }
            "layout" -> intent.getStringExtra(EXTRA_SOURCE)?.let { l ->
                WidgetPrefs.update(app, id) { it.copy(layout = l) }
                app.appScope.launch { runCatching { TaskWidget.update(app, id) } }
            }
            "select" -> intent.getStringExtra(EXTRA_SOURCE)?.let { src ->
                WidgetPrefs.update(app, id) { it.copy(source = src, picker = false) }
                app.appScope.launch { runCatching { TaskWidget.update(app, id) } }
            }
            "open" -> intent.getStringExtra(EXTRA_TASK)?.let { t ->
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_TASK_ID, t)
                        .setData(Uri.parse("suelostodo://task/$t"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
