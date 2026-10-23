package com.sueleyman.suelostodo.ui

import com.sueleyman.suelostodo.data.ListGroup
import com.sueleyman.suelostodo.data.ListWithCount
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRowsTest {
    private fun l(id: String, open: Int, g: String?) = ListWithCount(id, id, 0, open, g)

    @Test fun builds_two_level_tree_with_counts() {
        val groups = listOf(
            ListGroup(id = "ach", name = "Achievements", sortOrder = 1),
            ListGroup(id = "sport", name = "Sport", parentId = "ach", sortOrder = 2),
            ListGroup(id = "y26", name = "2026", parentId = "ach", sortOrder = 3, expanded = false),
            ListGroup(id = "work", name = "Arbeit", sortOrder = 4, expanded = false),
        )
        val lists = listOf(
            l("inbox", 9, null), l("marathon", 4, "sport"), l("cert", 2, "y26"),
            l("bucket", 7, "ach"), l("ista", 5, "work"), l("loose", 1, null), l("orphan", 1, "gone"),
        )
        val rows = buildHomeRows(lists, groups)
        val flat = rows.map {
            when (it) {
                is HomeRow.GroupRow -> "G${it.depth}:${it.group.id}:${it.openCount}"
                is HomeRow.ListRow -> "L${it.depth}:${it.list.id}"
            }
        }
        assertEquals(
            listOf(
                "G0:ach:13", "G1:sport:4", "L2:marathon", "G1:y26:2", "L1:bucket",
                "G0:work:5", "L0:loose", "L0:orphan"
            ), flat
        )
    }

    @Test fun tree_sums_progress_and_color() {
        val groups = listOf(
            ListGroup(id = "ach", name = "Achievements"),
            ListGroup(id = "sport", name = "Sport", parentId = "ach"),
        )
        val lists = listOf(ListWithCount("m", "Marathon", 3, 1, "sport"), ListWithCount("b", "Bucket", 5, 1, "ach"))
        val tasks = listOf(
            com.sueleyman.suelostodo.data.Task(listId = "m", title = "a", done = true),
            com.sueleyman.suelostodo.data.Task(listId = "m", title = "b"),
            com.sueleyman.suelostodo.data.Task(listId = "b", title = "c", done = true),
            com.sueleyman.suelostodo.data.Task(listId = "b", title = "d"),
        )
        val tree = buildHomeTree(lists, groups, tasks)
        val ach = tree.groups.single()
        assertEquals(2, ach.done); assertEquals(4, ach.total); assertEquals(2, ach.open)
        assertEquals(5, ach.color)
        assertEquals(1, ach.subs.single().done)
    }
}
