package com.sueleyman.suelostodo.widget

import android.content.Context
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.sueleyman.suelostodo.SuelosToDoApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Inflates the real RemoteViews (root + every row type) for all layouts and the picker. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetRenderTest {
    @Test fun all_layouts_inflate() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val repo = (ctx.applicationContext as SuelosToDoApp).repo
        repo.ensureInbox()
        val g = repo.createGroup("Achievements")!!
        val l = repo.createListInGroup("Bucket", 2, g)
        val t = repo.addTask(l, "Fallschirm")!!
        repo.addTask(l, "Unteraufgabe", t)
        val t2 = repo.addTask(l, "Nordlichter")!!
        // new tasks appear on top
        suspend fun order() = repo.getTasksOfList(l).filter { it.parentId == null }.sortedBy { it.sortOrder }.map { it.title }
        org.junit.Assert.assertEquals(listOf("Nordlichter", "Fallschirm"), order())
        // manual order: move "Nordlichter" down
        repo.moveAmongSiblings(t2, 1)
        org.junit.Assert.assertEquals(listOf("Fallschirm", "Nordlichter"), order())
        WidgetPrefs.setMoving(42, t)
        repo.addTask("inbox", "Einkauf")
        val data = repo.widgetData()
        val parent = FrameLayout(ctx)
        val sels = WidgetLayout.entries.map { WidgetSel("group:$g", it.key, false) } +
            WidgetSel("group:$g", "light", true) + WidgetSel("smart:due", "levels", false)
        for (sel in sels) {
            WidgetPrefs.update(ctx, 42) { sel }
            TaskWidget.buildRoot(ctx, 42, data, sel).apply(ctx, parent)
            val f = WidgetFactory(ctx, 42)
            f.onDataSetChanged()
            for (i in 0 until f.count) f.getViewAt(i).apply(ctx, parent)
            assertTrue(sel.picker || sel.source.startsWith("smart") || f.count > 0)
            println("WIDGET OK ${sel.layout} picker=${sel.picker} items=${f.count}")
        }
    }
}
