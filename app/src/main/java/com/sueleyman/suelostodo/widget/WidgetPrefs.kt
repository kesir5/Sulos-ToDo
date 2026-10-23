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
)

/**
 * Widget settings kept in our own SharedPreferences + an in-memory StateFlow.
 * A running widget session observes the flow, so a change is visible immediately –
 * independent of Glance's own state handling (which did not refresh reliably).
 */
object WidgetPrefs {
    private val state = MutableStateFlow<Map<Int, WidgetSel>>(emptyMap())
    private fun sp(c: Context) = c.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun get(c: Context, id: Int): WidgetSel = state.value[id] ?: load(c, id)

    private fun load(c: Context, id: Int): WidgetSel {
        val p = sp(c)
        val sel = WidgetSel(
            source = p.getString("src_$id", null) ?: "list:$INBOX_ID",
            layout = p.getString("lay_$id", null) ?: WidgetLayout.COLOR.key,
        )
        state.value = state.value + (id to sel)
        return sel
    }

    fun flow(c: Context, id: Int): Flow<WidgetSel> {
        get(c, id)
        return state.map { it[id] ?: WidgetSel() }.distinctUntilChanged()
    }

    fun update(c: Context, id: Int, change: (WidgetSel) -> WidgetSel) {
        val new = change(get(c, id))
        sp(c).edit().putString("src_$id", new.source).putString("lay_$id", new.layout).apply()
        state.value = state.value + (id to new)
    }
}
