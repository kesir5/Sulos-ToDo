package com.sueleyman.suelostodo.ui

import com.sueleyman.suelostodo.data.INBOX_ID
import com.sueleyman.suelostodo.data.ListGroup
import com.sueleyman.suelostodo.data.ListWithCount

/** One row of the arrange screen. */
sealed interface ArrangeItem {
    val key: String

    data class Top(val group: ListGroup) : ArrangeItem { override val key = "g" + group.id }
    data class Sub(val group: ListGroup) : ArrangeItem { override val key = "g" + group.id }
    data class Lst(val list: ListWithCount) : ArrangeItem { override val key = "l" + list.id }
    /** Fixed separator: lists below it have no group. */
    data object NoGroup : ArrangeItem { override val key = "nogroup" }
}

/** New position of a group or list after arranging. */
data class Placement(val id: String, val parentId: String?, val sortOrder: Long)

data class Arrangement(val groups: List<Placement>, val lists: List<Placement>)

/**
 * Flat order: top group, its direct lists, its subgroups (each followed by its lists),
 * next top group …, then the "Ohne Gruppe" separator and all ungrouped lists.
 */
fun buildArrangeItems(lists: List<ListWithCount>, groups: List<ListGroup>): List<ArrangeItem> {
    val gIds = groups.mapTo(HashSet()) { it.id }
    val tops = groups.filter { it.parentId == null || it.parentId !in gIds }
    val subsOf = groups.filter { it.parentId != null && it.parentId in gIds }.groupBy { it.parentId }
    val userLists = lists.filter { it.id != INBOX_ID }
    val listsOf = userLists.filter { it.groupId != null && it.groupId in gIds }.groupBy { it.groupId }
    val out = mutableListOf<ArrangeItem>()
    for (t in tops) {
        out += ArrangeItem.Top(t)
        listsOf[t.id].orEmpty().forEach { out += ArrangeItem.Lst(it) }
        for (s in subsOf[t.id].orEmpty()) {
            out += ArrangeItem.Sub(s)
            listsOf[s.id].orEmpty().forEach { out += ArrangeItem.Lst(it) }
        }
    }
    out += ArrangeItem.NoGroup
    userLists.filter { it.groupId == null || it.groupId !in gIds }.forEach { out += ArrangeItem.Lst(it) }
    return out
}

/** Visual indentation per row, derived from the current (possibly mid-drag) order. */
fun arrangeDepths(items: List<ArrangeItem>): List<Int> {
    var top = false
    var sub = false
    return items.map {
        when (it) {
            is ArrangeItem.Top -> { top = true; sub = false; 0 }
            is ArrangeItem.Sub -> if (top) { sub = true; 1 } else { top = true; sub = false; 0 }
            is ArrangeItem.Lst -> if (sub) 2 else if (top) 1 else 0
            ArrangeItem.NoGroup -> { top = false; sub = false; 0 }
        }
    }
}

private fun isBoundary(item: ArrangeItem, forTop: Boolean) =
    item is ArrangeItem.Top || item is ArrangeItem.NoGroup || (!forTop && item is ArrangeItem.Sub)

/**
 * When a group header was dragged, its content (lists, and subgroups for a top group) moves along.
 * The block is placed at the next group boundary, so it never "steals" lists of another group.
 * [before] is the order at drag start, [after] the order at drag end (only the header moved).
 */
fun settleGroupMove(before: List<ArrangeItem>, after: List<ArrangeItem>, draggedKey: String): List<ArrangeItem> {
    val startIdx = before.indexOfFirst { it.key == draggedKey }
    val header = before.getOrNull(startIdx) ?: return after
    if (header !is ArrangeItem.Top && header !is ArrangeItem.Sub) return after
    val forTop = header is ArrangeItem.Top
    var end = startIdx + 1
    while (end < before.size && !isBoundary(before[end], forTop)) end++
    val block = before.subList(startIdx, end)
    val blockKeys = block.mapTo(HashSet()) { it.key }

    val posInAfter = after.indexOfFirst { it.key == draggedKey }
    // items before the header's new position that don't belong to the block
    val head = after.subList(0, posInAfter).filter { it.key !in blockKeys }
    val tail = after.subList(posInAfter + 1, after.size).filter { it.key !in blockKeys }
    // move forward to the next boundary so the block doesn't split another group
    var cut = 0
    while (cut < tail.size && !isBoundary(tail[cut], forTop)) cut++
    return head + tail.subList(0, cut) + block + tail.subList(cut, tail.size)
}

/** Derives parents and sort orders from the flat order. */
fun computeArrangement(items: List<ArrangeItem>): Arrangement {
    val groups = mutableListOf<Placement>()
    val lists = mutableListOf<Placement>()
    var top: String? = null
    var sub: String? = null
    items.forEachIndexed { i, it ->
        val order = (i + 1) * 1000L
        when (it) {
            is ArrangeItem.Top -> { top = it.group.id; sub = null; groups += Placement(it.group.id, null, order) }
            is ArrangeItem.Sub -> if (top == null) {
                // a subgroup dragged above all groups becomes a top-level group
                top = it.group.id; sub = null; groups += Placement(it.group.id, null, order)
            } else {
                sub = it.group.id; groups += Placement(it.group.id, top, order)
            }
            is ArrangeItem.Lst -> lists += Placement(it.list.id, sub ?: top, order)
            ArrangeItem.NoGroup -> { top = null; sub = null }
        }
    }
    return Arrangement(groups, lists)
}
