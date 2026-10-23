package com.sueleyman.suelostodo.ui

import com.sueleyman.suelostodo.data.ListGroup
import com.sueleyman.suelostodo.data.ListWithCount
import org.junit.Assert.assertEquals
import org.junit.Test

class ArrangeTest {
    private val ach = ListGroup(id = "ach", name = "Achievements", sortOrder = 1)
    private val sport = ListGroup(id = "sport", name = "Sport", parentId = "ach", sortOrder = 2)
    private val work = ListGroup(id = "work", name = "Arbeit", sortOrder = 3)
    private fun l(id: String, g: String?) = ListWithCount(id, id, 0, 0, g)
    private val lists = listOf(l("bucket", "ach"), l("marathon", "sport"), l("ista", "work"), l("einkauf", null))
    private fun keys(items: List<ArrangeItem>) = items.map { it.key }
    private fun move(items: List<ArrangeItem>, key: String, to: Int) =
        items.toMutableList().apply { val i = indexOfFirst { it.key == key }; add(to, removeAt(i)) }

    @Test fun flat_order() {
        assertEquals(
            listOf("gach", "lbucket", "gsport", "lmarathon", "gwork", "lista", "nogroup", "leinkauf"),
            keys(buildArrangeItems(lists, listOf(ach, sport, work)))
        )
    }

    @Test fun list_dragged_into_other_group_and_out() {
        val items = buildArrangeItems(lists, listOf(ach, sport, work))
        // einkauf (last) -> directly below "Arbeit"
        val a = move(items, "leinkauf", 5)
        val arr = computeArrangement(a)
        assertEquals("work", arr.lists.first { it.id == "einkauf" }.parentId)
        // bucket -> below "Ohne Gruppe"
        val b = move(items, "lbucket", items.size - 1)
        assertEquals(null, computeArrangement(b).lists.first { it.id == "bucket" }.parentId)
        // marathon -> under top group Arbeit
        val c = move(items, "lmarathon", 5)
        assertEquals("work", computeArrangement(c).lists.first { it.id == "marathon" }.parentId)
    }

    @Test fun group_moves_with_its_content() {
        val items = buildArrangeItems(lists, listOf(ach, sport, work))
        // drag "Arbeit" header to the very top -> whole block moves, Achievements keeps its lists
        val after = move(items, "gwork", 0)
        val settled = settleGroupMove(items, after, "gwork")
        assertEquals(
            listOf("gwork", "lista", "gach", "lbucket", "gsport", "lmarathon", "nogroup", "leinkauf"),
            keys(settled)
        )
        val arr = computeArrangement(settled)
        assertEquals("ach", arr.lists.first { it.id == "bucket" }.parentId)
        assertEquals("ach", arr.groups.first { it.id == "sport" }.parentId)
    }

    @Test fun subgroup_dropped_inside_other_group_snaps_to_boundary() {
        val items = buildArrangeItems(lists, listOf(ach, sport, work))
        // drag "Sport" header right below "Arbeit" (between Arbeit and ista)
        val after = move(items, "gsport", 4)
        val settled = settleGroupMove(items, after, "gsport")
        assertEquals(
            listOf("gach", "lbucket", "gwork", "lista", "gsport", "lmarathon", "nogroup", "leinkauf"),
            keys(settled)
        )
        assertEquals("work", computeArrangement(settled).groups.first { it.id == "sport" }.parentId)
    }
}
