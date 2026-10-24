package com.sueleyman.suelostodo.util

import com.sueleyman.suelostodo.data.ListGroup
import com.sueleyman.suelostodo.data.Task
import com.sueleyman.suelostodo.data.TaskList
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintTest {
    private val lists = listOf(
        TaskList(id = "inbox", name = "Aufgaben"),
        TaskList(id = "b", name = "Bucket", groupId = "ach"),
        TaskList(id = "w", name = "Projekte", groupId = "work"),
    )
    private val groups = listOf(ListGroup(id = "ach", name = "Achievements"), ListGroup(id = "work", name = "Arbeit"))
    private val tasks = listOf(
        Task(id = "1", listId = "b", title = "Fallschirm <Sprung>"),
        Task(id = "2", listId = "b", title = "Erledigt", done = true),
        Task(id = "3", listId = "b", parentId = "1", depth = 1, title = "Termin"),
        Task(id = "4", listId = "w", title = "Bericht"),
        Task(id = "5", listId = "inbox", title = "Einkauf"),
    )

    @Test fun only_selected_groups_and_no_done() {
        val html = TaskPrinter.buildHtml(lists, groups, tasks, PrintSelection(setOf("ach"), includeUngrouped = false, includeDone = false))
        assertTrue(html.contains("Achievements"))
        assertTrue(html.contains("Fallschirm &lt;Sprung&gt;"))
        assertTrue(html.contains("Termin"))
        assertFalse(html.contains("Erledigt</span>"))
        assertFalse(html.contains("Bericht"))
        assertFalse(html.contains("Einkauf"))
    }

    @Test fun with_done_and_ungrouped() {
        val html = TaskPrinter.buildHtml(lists, groups, tasks, PrintSelection(emptySet(), includeUngrouped = true, includeDone = true))
        assertTrue(html.contains("Einkauf"))
        assertFalse(html.contains("Fallschirm"))
    }
}
