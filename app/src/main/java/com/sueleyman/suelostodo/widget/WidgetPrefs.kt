package com.sueleyman.suelostodo.widget

import android.content.Context
import com.sueleyman.suelostodo.data.INBOX_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Selection of one widget instance. */
data class WidgetSel(
    val source: String = "list:$INBOX_ID",
    val layout: String = WidgetLayout.COLOR.key,
    val picker: Boolean = false,
    /** eye icon in the header: also show completed tasks */
    val showDone: Boolean = false,
)

/**
 * Widget settings kept in our own SharedPreferences + an in-memory StateFlow.
 * A running widget session observes the flow, so a change is visible immediately –
 * independent of Glance's own state handling (which did not refresh reliably).
 */
object WidgetPrefs {
    private val state = MutableStateFlow<Map<Int, WidgetSel>>(emptyMap())
    private fun sp(c: Context) = c.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    /** Font sizes for all widgets. */
    val FONT_STEPS = listOf(0.85f to "Klein", 1.0f to "Normal", 1.15f to "Groß", 1.3f to "Sehr groß")

    fun fontScale(c: Context): Float = sp(c).getFloat("font_scale", 1.0f)
    fun setFontScale(c: Context, v: Float) = sp(c).edit().putFloat("font_scale", v).apply()

    /** Task currently in move mode per widget (in memory only). */
    private val moving = java.util.concurrent.ConcurrentHashMap<Int, String>()
    fun movingTask(id: Int): String? = moving[id]
    fun setMoving(id: Int, taskId: String?) { if (taskId == null) moving.remove(id) else moving[id] = taskId }

    fun get(c: Context, id: Int): WidgetSel = state.value[id] ?: load(c, id)

    private fun load(c: Context, id: Int): WidgetSel {
        val p = sp(c)
        val sel = WidgetSel(
            source = p.getString("src_$id", null) ?: "list:$INBOX_ID",
            layout = p.getString("lay_$id", null) ?: WidgetLayout.COLOR.key,
            picker = p.getBoolean("pick_$id", false),
            showDone = p.getBoolean("done_$id", false),
        )
        state.value = state.value + (id to sel)
        return sel
    }

    fun flow(c: Context, id: Int): Flow<WidgetSel> {
        get(c, id)
        return state.map { it[id] ?: WidgetSel() }.distinctUntilChanged()
    }

    fun remove(c: Context, id: Int) {
        sp(c).edit().remove("src_$id").remove("lay_$id").remove("pick_$id").remove("done_$id").apply()
        state.value = state.value - id
    }

    fun update(c: Context, id: Int, change: (WidgetSel) -> WidgetSel) {
        val new = change(get(c, id))
        sp(c).edit().putString("src_$id", new.source).putString("lay_$id", new.layout).putBoolean("pick_$id", new.picker).putBoolean("done_$id", new.showDone).apply()
        state.value = state.value + (id to new)
    }
}
