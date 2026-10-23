package com.sueleyman.suelostodo.widget

import com.sueleyman.suelostodo.data.ListGroup
import com.sueleyman.suelostodo.data.Task
import com.sueleyman.suelostodo.data.TaskList
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetViewTest {
    private val d = WidgetData(
        lists = listOf(
            TaskList(id = "inbox", name = "Aufgaben"),
            TaskList(id = "b", name = "Bucket", groupId = "ach"),
            TaskList(id = "m", name = "Marathon", groupId = "sport"),
        ),
        groups = listOf(ListGroup(id = "ach", name = "Achievements", color = 0xFFD85A30), ListGroup(id = "sport", name = "Sport", parentId = "ach")),
        open = listOf(
            Task(id = "1", listId = "b", title = "Fallschirm", important = true),
            Task(id = "2", listId = "m", title = "Training"),
            Task(id = "3", listId = "inbox", title = "Einkauf"),
        ),
        loaded = true,
    )

    @Test fun group_shows_all_lists_incl_subgroups() {
        val v = buildWidgetView("group:ach", d)
        assertEquals("Achievements", v.title)
        assertEquals(0xFFD85A30, v.color)
        assertEquals(listOf("Bucket", "Sport › Marathon"), v.sections.map { it.title })
        assertEquals(2, v.count)
    }

    @Test fun smart_and_fallback() {
        assertEquals(listOf("Fallschirm"), buildWidgetView("smart:important", d).sections.single().rows.map { it.task.title })
        assertEquals("Aufgaben", buildWidgetView("group:gone", d).title)
        assertEquals("Aufgaben", buildWidgetView("list:inbox", d).title)
    }
}

class WidgetHideDoneParentTest {
    @Test fun subtasks_of_done_task_are_hidden() {
        val open = listOf(
            Task(id = "c1", listId = "l", parentId = "doneParent", depth = 1, title = "Kind von erledigt"),
            Task(id = "g1", listId = "l", parentId = "c1", depth = 2, title = "Enkel"),
            Task(id = "p", listId = "l", title = "Offen"),
            Task(id = "c2", listId = "l", parentId = "p", depth = 1, title = "Kind von offen"),
        )
        assertEquals(listOf("Offen", "Kind von offen"), visibleOpenTasks(open).map { it.title })
    }
}
