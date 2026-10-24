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
private const val ACTION_EYE = "com.sueleyman.suelostodo.widget.EYE"
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

    /** Picker menu closes itself after 15 s without a choice (restarted by every layout tap). */
    const val PICKER_TIMEOUT_MS = 15_000L
    private val pickerTokens = java.util.concurrent.ConcurrentHashMap<Int, Long>()

    private val moveTokens = java.util.concurrent.ConcurrentHashMap<Int, Long>()

    /** Move mode ends by itself after 15 s without tapping ▲/▼. */
    fun scheduleMoveTimeout(c: Context, id: Int) {
        val app = c.applicationContext as SuelosToDoApp
        val token = System.nanoTime()
        moveTokens[id] = token
        app.appScope.launch {
            kotlinx.coroutines.delay(PICKER_TIMEOUT_MS)
            if (moveTokens[id] == token && WidgetPrefs.movingTask(id) != null) {
                WidgetPrefs.setMoving(id, null)
                runCatching { update(app, id) }
            }
        }
    }

    fun schedulePickerTimeout(c: Context, id: Int) {
        val app = c.applicationContext as SuelosToDoApp
        val token = System.nanoTime()
        pickerTokens[id] = token
        app.appScope.launch {
            kotlinx.coroutines.delay(PICKER_TIMEOUT_MS)
            if (pickerTokens[id] == token && WidgetPrefs.get(app, id).picker) {
                WidgetPrefs.update(app, id) { it.copy(picker = false) }
                runCatching { update(app, id) }
            }
        }
    }

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
        val v = buildWidgetView(sel.source, data, sel.showDone)
        val layout = WidgetLayout.of(sel.layout)
        val rv = RemoteViews(c.packageName, R.layout.widget_root)
        val accent = v.color

        // ---------- colours per layout
        val bg: Int; val titleColor: Int; val subColor: Int; val plusBg: Int; val plusFg: Int; var headBg = false
        when {
            sel.picker -> { bg = WHITE; titleColor = INK; subColor = INK2; plusBg = 0xFFF1F0EC.toInt(); plusFg = INK }
            layout.dark -> { bg = accent.toInt(); titleColor = WHITE; subColor = DIM; plusBg = 0x33FFFFFF; plusFg = WHITE }
            layout == WidgetLayout.LIGHT || layout == WidgetLayout.ZEBRA -> { bg = WHITE; titleColor = WHITE; subColor = DIM; plusBg = 0x33FFFFFF; plusFg = WHITE; headBg = true }
            layout == WidgetLayout.LINES || layout == WidgetLayout.LEVELS ->
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
        val fs = WidgetPrefs.fontScale(c)
        rv.setTextViewTextSize(R.id.w_title, android.util.TypedValue.COMPLEX_UNIT_SP, 17f * fs)
        rv.setTextViewTextSize(R.id.w_count, android.util.TypedValue.COMPLEX_UNIT_SP, 12f * fs)
        rv.setTextViewTextSize(R.id.w_empty, android.util.TypedValue.COMPLEX_UNIT_SP, 13f * fs)
        listOf(R.id.w_c1, R.id.w_c2, R.id.w_c3).forEach { rv.setTextViewTextSize(it, android.util.TypedValue.COMPLEX_UNIT_SP, 11f * fs) }
        rv.setOnClickPendingIntent(
            R.id.w_app,
            PendingIntent.getActivity(
                c, id * 10 + 4,
                Intent(c, MainActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        rv.setTextViewText(R.id.w_count, if (sel.picker) "" else " ${v.count}")
        rv.setTextColor(R.id.w_count, subColor)
        rv.setViewVisibility(R.id.w_drop, if (sel.picker) View.GONE else View.VISIBLE)
        rv.setInt(R.id.w_drop, "setColorFilter", titleColor)
        rv.setOnClickPendingIntent(R.id.w_title_box, broadcast(c, id, ACTION_PICKER, 1))
        rv.setInt(R.id.w_add_bg, "setColorFilter", plusBg)
        rv.setInt(R.id.w_add_icon, "setColorFilter", plusFg)
        // eye: show / hide completed tasks (hidden while the picker is open)
        rv.setViewVisibility(R.id.w_eye, if (sel.picker) View.GONE else View.VISIBLE)
        rv.setInt(R.id.w_eye_bg, "setColorFilter", plusBg)
        rv.setInt(R.id.w_eye_icon, "setColorFilter", plusFg)
        rv.setImageViewResource(R.id.w_eye_icon, if (sel.showDone) R.drawable.ic_eye else R.drawable.ic_eye_off)
        rv.setContentDescription(R.id.w_eye_icon, if (sel.showDone) "Erledigte ausblenden" else "Erledigte einblenden")
        rv.setOnClickPendingIntent(R.id.w_eye, broadcast(c, id, ACTION_EYE, 5))
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
        val showCounters = false // "Kompakt mit Zählern" removed
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
    data class Section(val title: String, val color: Long, val listId: String? = null) : WItem { override val id = ("s$title").hashCode().toLong() }
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
    val v = buildWidgetView(sel.source, d, sel.showDone)
    return when (WidgetLayout.of(sel.layout)) {
        else -> v.sections.flatMap { s ->
            (if (s.title != null) listOf(WItem.Section(s.title, s.color, s.listId)) else emptyList()) + s.rows.map { WItem.Row(it, s.color, false) }
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
    private var listColors: Map<String, Long> = emptyMap()

    override fun onCreate() {}
    override fun onDestroy() {}
    override fun onDataSetChanged() {
        sel = WidgetPrefs.get(c, widgetId)
        val data = runCatching { runBlocking { (c.applicationContext as SuelosToDoApp).repo.widgetData() } }.getOrElse { WidgetData() }
        items = widgetItems(sel, data)
        listColors = data.lists.associate { it.id to ListColors.argb(it.color) }
    }

    override fun getCount() = items.size
    override fun getViewTypeCount() = 5
    override fun hasStableIds() = true
    override fun getItemId(position: Int) = items.getOrNull(position)?.id ?: position.toLong()
    override fun getLoadingView(): RemoteViews? = null

    override fun getViewAt(position: Int): RemoteViews {
        val layout = WidgetLayout.of(sel.layout)
        val dark = !sel.picker && layout.dark
        val fs = WidgetPrefs.fontScale(c)
        // index among task rows (for zebra shading)
        val rowIndex = items.take(position).count { it is WItem.Row }
        return when (val it = items.getOrNull(position)) {
            is WItem.Section -> RemoteViews(c.packageName, R.layout.widget_item_section).apply {
                setTextViewText(R.id.s_title, it.title)
                setTextColor(R.id.s_title, if (dark) DIM else deep(it.color))
                setTextViewTextSize(R.id.s_title, android.util.TypedValue.COMPLEX_UNIT_SP, 13.65f * fs /* category heading: +30 % */)
                // tap on a list name opens this list in the app
                it.listId?.let { lid ->
                    setTextViewText(R.id.s_title, it.title + "  ›")
                    setOnClickFillInIntent(R.id.s_title, Intent().putExtra(EXTRA_OP, "openlist").putExtra(EXTRA_SOURCE, lid))
                }
            }
            is WItem.Head -> RemoteViews(c.packageName, R.layout.widget_item_section).apply {
                setTextViewText(R.id.s_title, it.label)
                setTextViewTextSize(R.id.s_title, android.util.TypedValue.COMPLEX_UNIT_SP, 10.5f * fs)
                setTextColor(R.id.s_title, INK2)
            }
            is WItem.Option -> RemoteViews(c.packageName, R.layout.widget_item_option).apply {
                setTextViewText(R.id.o_label, it.label)
                setTextViewTextSize(R.id.o_label, android.util.TypedValue.COMPLEX_UNIT_SP, 14f * fs)
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
                if (t.done)
                    setTextViewText(R.id.i_title, android.text.SpannableString(title).apply {
                        setSpan(android.text.style.StrikethroughSpan(), 0, length, 0)
                    })
                else if (layout == WidgetLayout.LEVELS && !sub)
                    setTextViewText(R.id.i_title, android.text.SpannableString(title).apply {
                        setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, length, 0)
                    })
                else setTextViewText(R.id.i_title, title)
                setTextColor(R.id.i_title, if (t.done) (if (dark) DIM else INK2) else if (dark) (if (sub) DIM else WHITE) else (if (sub) INK2 else INK))
                setImageViewResource(R.id.i_check, if (t.done) R.drawable.ic_check_circle else R.drawable.ic_circle)
                setInt(R.id.i_title, "setMaxLines", if (it.single) 1 else 2)
                setTextViewTextSize(R.id.i_title, android.util.TypedValue.COMPLEX_UNIT_SP, (if (it.single) 13f else 14f) * fs)
                setTextViewTextSize(R.id.i_due, android.util.TypedValue.COMPLEX_UNIT_SP, 11f * fs)
                setInt(R.id.i_check, "setColorFilter", if (dark) WHITE else accent)
                setInt(R.id.i_star, "setColorFilter", if (dark) WHITE else accent)
                setViewVisibility(R.id.i_star, if (t.important) View.VISIBLE else View.GONE)
                val showDue = layout in setOf(WidgetLayout.LIGHT, WidgetLayout.LINES, WidgetLayout.ZEBRA,
                    WidgetLayout.TILES, WidgetLayout.COLOR_LINES, WidgetLayout.LEVELS)
                val due = if (showDue && !t.done) dueLabel(t) else null
                setViewVisibility(R.id.i_due, if (due != null) View.VISIBLE else View.GONE)
                due?.let { (txt, hot) ->
                    setTextViewText(R.id.i_due, txt)
                    setTextColor(R.id.i_due, if (dark) (if (hot) WHITE else DIM) else if (hot) RED else INK2)
                }
                val indent = t.depth * (if (layout == WidgetLayout.TILES) 36 else 44)
                setViewPadding(R.id.i_row, 12 + indent, 0, 12, 0)
                // ---- separation per layout
                when (layout) {
                    WidgetLayout.COLOR -> {
                        setViewVisibility(R.id.i_div2, View.VISIBLE)
                        setInt(R.id.i_div2, "setBackgroundColor", 0x4DFFFFFF)
                    }
                    WidgetLayout.LINES -> {
                        setViewVisibility(R.id.i_div, View.VISIBLE)
                        setInt(R.id.i_div, "setBackgroundColor", 0xFFE7E5E0.toInt())
                    }
                    WidgetLayout.COLOR_LINES -> {
                        setViewVisibility(R.id.i_div_thick, View.VISIBLE)
                        setInt(R.id.i_div_thick, "setBackgroundColor", 0x66FFFFFF)
                    }
                    WidgetLayout.ZEBRA -> setInt(R.id.i_row, "setBackgroundColor", if (rowIndex % 2 == 1) 0xFFF4F2EF.toInt() else WHITE)
                    WidgetLayout.LEVELS -> {
                        // side bars always in the colour of the task's own list
                        val listCol = (listColors[t.listId] ?: it.color).toInt()
                        val tint = when (t.depth) { 0 -> 0x24; 1 -> 0x10; else -> 0x00 }
                        val bgc = blend(WHITE, listCol, tint)
                        setInt(R.id.i_wrap, "setBackgroundColor", bgc)
                        setViewVisibility(R.id.i_bar, View.VISIBLE)
                        setInt(R.id.i_bar, "setBackgroundColor", (when (t.depth) { 0 -> 0xFF; 1 -> 0x99; else -> 0x66 } shl 24) or (listCol and 0xFFFFFF))
                        setInt(R.id.i_check, "setColorFilter", listCol)
                        setInt(R.id.i_star, "setColorFilter", listCol)
                        // 2 dp line between the entries
                        setViewVisibility(R.id.i_div2, View.VISIBLE)
                        setInt(R.id.i_div2, "setBackgroundColor", blend(WHITE, listCol, 0x55))
                        setViewPadding(R.id.i_row, 0, 0, 12, 0)
                        setViewPadding(R.id.i_wrap, indent / 2, if (t.depth == 0 && rowIndex > 0) 6 else 0, 0, 0)
                    }
                    else -> {}
                }
                // ---- move mode via the handle at the right edge (widgets cannot do real drag & drop)
                val canMove = !t.done && (sel.source.startsWith("list:") || sel.source.startsWith("group:"))
                val moving = canMove && WidgetPrefs.movingTask(widgetId) == t.id
                val handleTint = if (dark) WHITE else INK2
                setViewVisibility(R.id.i_handle, if (canMove) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.i_up, if (moving) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.i_down, if (moving) View.VISIBLE else View.GONE)
                if (canMove) {
                    setImageViewResource(R.id.i_handle, if (moving) R.drawable.ic_done_w else R.drawable.ic_drag)
                    setInt(R.id.i_handle, "setColorFilter", if (moving) accent else handleTint)
                    setInt(R.id.i_handle, "setImageAlpha", if (moving) 255 else 140)
                    setOnClickFillInIntent(R.id.i_handle, Intent().putExtra(EXTRA_OP, if (moving) "move_end" else "move_start").putExtra(EXTRA_TASK, t.id))
                }
                if (moving) {
                    setInt(R.id.i_up, "setColorFilter", if (dark) WHITE else accent)
                    setInt(R.id.i_down, "setColorFilter", if (dark) WHITE else accent)
                    setOnClickFillInIntent(R.id.i_up, Intent().putExtra(EXTRA_OP, "move_up").putExtra(EXTRA_TASK, t.id))
                    setOnClickFillInIntent(R.id.i_down, Intent().putExtra(EXTRA_OP, "move_down").putExtra(EXTRA_TASK, t.id))
                    setInt(R.id.i_row, "setBackgroundColor", if (dark) 0x33FFFFFF else blend(WHITE, accent, 0x30))
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
                if (WidgetPrefs.get(context, id).picker) TaskWidget.schedulePickerTimeout(context, id)
                redraw(context, id)
            }
            ACTION_EYE -> {
                WidgetPrefs.update(context, id) { it.copy(showDone = !it.showDone) }
                redraw(context, id)
            }
            ACTION_LAYOUT -> {
                val l = intent.getStringExtra(EXTRA_LAYOUT) ?: return
                WidgetPrefs.update(context, id) { it.copy(layout = l) }
                TaskWidget.schedulePickerTimeout(context, id)
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
                TaskWidget.schedulePickerTimeout(app, id)
            }
            "move_start" -> intent.getStringExtra(EXTRA_TASK)?.let { t ->
                WidgetPrefs.setMoving(id, t)
                TaskWidget.scheduleMoveTimeout(app, id)
                app.appScope.launch { runCatching { TaskWidget.update(app, id) } }
            }
            "move_end" -> {
                WidgetPrefs.setMoving(id, null)
                app.appScope.launch { runCatching { TaskWidget.update(app, id) } }
            }
            "move_up", "move_down" -> intent.getStringExtra(EXTRA_TASK)?.let { t ->
                TaskWidget.scheduleMoveTimeout(app, id)
                app.appScope.launch {
                    runCatching {
                        app.repo.moveAmongSiblings(t, if (intent.getStringExtra(EXTRA_OP) == "move_up") -1 else 1)
                        TaskWidget.update(app, id)
                    }
                }
            }
            "openlist" -> intent.getStringExtra(EXTRA_SOURCE)?.let { lid ->
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_OPEN_LIST, lid)
                        .setData(Uri.parse("suelostodo://list/$lid"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
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
