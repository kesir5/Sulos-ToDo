package com.sueleyman.suelostodo.io

import com.sueleyman.suelostodo.data.Task
import com.sueleyman.suelostodo.data.TaskList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FormatsTest {

    private fun sample(): ExportList {
        val list = TaskList(id = "l1", name = "Umzug, Wohnung")
        val root = Task(id = "a", listId = "l1", title = "Wohnung übergeben", note = "Mit Vermieter; Termin\nfrüh", important = true,
            dueDate = LocalDate.of(2026, 11, 2).toEpochDay(), sortOrder = 1)
        val c1 = Task(id = "b", listId = "l1", parentId = "a", depth = 1, title = "Zählerstände notieren", sortOrder = 2)
        val c2 = Task(id = "c", listId = "l1", parentId = "b", depth = 2,
            title = "Strom – ein sehr langer Titel mit Ümläuten äöü, damit die Zeilenfaltung bei 75 Oktetten greift", done = true, sortOrder = 3)
        val root2 = Task(id = "d", listId = "l1", title = "Ummelden \"Amt\"", sortOrder = 4)
        return ExportList(list, listOf(
            ExportNode(root, listOf(ExportNode(c1, listOf(ExportNode(c2, emptyList(), emptyList())), emptyList())), emptyList()),
            ExportNode(root2, emptyList(), emptyList()),
        ))
    }

    @Test fun ical_roundtrip_keeps_hierarchy() {
        val ics = ICal.write(listOf(sample()))
        ics.lines().forEach { assertTrue("line too long: $it", it.toByteArray().size <= 75) }
        val parsed = FormatDetector.parse(ics.toByteArray(), "x.ics") as Parsed.Lists
        assertEquals(FileFormat.ICAL, parsed.format)
        assertEquals(1, parsed.lists.size)
        val l = parsed.lists[0]
        assertEquals("Umzug, Wohnung", l.name)
        assertEquals(2, l.items.size)
        val a = l.items[0]
        assertEquals("Wohnung übergeben", a.title)
        assertEquals("Mit Vermieter; Termin\nfrüh", a.note)
        assertTrue(a.important)
        assertEquals(LocalDate.of(2026, 11, 2).toEpochDay(), a.dueDate)
        val c2 = a.children[0].children[0]
        assertTrue(c2.title.startsWith("Strom"))
        assertTrue(c2.title.endsWith("greift"))
        assertTrue(c2.done)
    }

    @Test fun todoist_roundtrip() {
        val csv = TodoistCsv.write(sample(), includeDone = true)
        val parsed = FormatDetector.parse(csv.toByteArray(), "Umzug.csv") as Parsed.Lists
        assertEquals(FileFormat.TODOIST, parsed.format)
        val l = parsed.lists[0]
        assertEquals("Umzug", l.name)
        assertEquals(2, l.items.size)
        assertEquals("Ummelden \"Amt\"", l.items[1].title)
        assertEquals("Mit Vermieter; Termin\nfrüh", l.items[0].note)
        assertTrue(l.items[0].important)
        assertEquals(1, l.items[0].children[0].children.size)
    }

    @Test fun todoist_real_template() {
        val csv = "TYPE,CONTENT,DESCRIPTION,PRIORITY,INDENT,AUTHOR,RESPONSIBLE,DATE,DATE_LANG,TIMEZONE\n" +
            "section,Planung,,,,,,,,\n" +
            "task,Kartons @einkauf,,1,1,Me,,2026-10-30,en,Europe/Berlin\n" +
            "task,Baumarkt,,4,2,Me,,,,\n" +
            "note,irgendwas,,,,,,,,\n" +
            "task,Abmelden,,4,1,Me,,every day,en,\n"
        val l = (FormatDetector.parse(csv.toByteArray(), "P.csv") as Parsed.Lists).lists[0]
        assertEquals(listOf("Kartons", "Abmelden"), l.items.map { it.title })
        assertEquals("Baumarkt", l.items[0].children[0].title)
        assertTrue(l.items[0].important)
        assertEquals(null, l.items[1].dueDate)
    }

    @Test fun google_takeout() {
        val json = """
        {"kind":"tasks#taskLists","items":[
          {"kind":"tasks#taskList","id":"L1","title":"Meine Aufgaben","items":[
            {"kind":"tasks#task","id":"t2","title":"Kind","parent":"t1","position":"00000000000000000001","status":"needsAction"},
            {"kind":"tasks#task","id":"t1","title":"Eltern","notes":"N","position":"00000000000000000000","status":"completed",
             "due":"2026-11-05T00:00:00.000Z","completed":"2026-10-01T10:00:00.000Z"},
            {"kind":"tasks#task","id":"t3","title":"","position":"2"}
          ]},
          {"kind":"tasks#taskList","id":"L2","title":"Zweite","items":[]}
        ]}""".trimIndent()
        val p = FormatDetector.parse(json.toByteArray(), "Tasks.json") as Parsed.Lists
        assertEquals(FileFormat.GOOGLE, p.format)
        assertEquals(2, p.lists.size)
        val root = p.lists[0].items.single()
        assertEquals("Eltern", root.title)
        assertTrue(root.done)
        assertEquals(LocalDate.of(2026, 11, 5).toEpochDay(), root.dueDate)
        assertEquals("Kind", root.children.single().title)
    }

    @Test fun foreign_ics_with_parent_and_alarm() {
        val ics = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nX-WR-CALNAME:Privat\r\n" +
            "BEGIN:VTODO\r\nUID:p\r\nSUMMARY:Parent\r\nPRIORITY:9\r\nEND:VTODO\r\n" +
            "BEGIN:VTODO\r\nUID:z\r\nSUMMARY:Other\r\nCATEGORIES:Haus\\, Garten,Zweit\r\nEND:VTODO\r\n" +
            "BEGIN:VTODO\r\nUID:k\r\nSUMMARY:Child with\r\n  folded line\r\nRELATED-TO:p\r\nDUE:20261201T120000Z\r\n" +
            "BEGIN:VALARM\r\nTRIGGER;VALUE=DATE-TIME:20261130T080000Z\r\nACTION:DISPLAY\r\nEND:VALARM\r\nEND:VTODO\r\n" +
            "END:VCALENDAR\r\n"
        val all = (FormatDetector.parse(ics.toByteArray(), "a.ics") as Parsed.Lists).lists
        assertEquals(setOf("Privat", "Haus, Garten"), all.map { it.name }.toSet())
        val l = all.first { it.name == "Privat" }
        val c = l.items.single().children.single()
        assertEquals("Child with folded line", c.title)
        assertEquals(LocalDate.of(2026, 12, 1).toEpochDay(), c.dueDate)
        assertTrue(c.reminderAt != null)
        assertTrue(!l.items.single().important)
    }
}
