package com.sueleyman.suelostodo.ui

import com.sueleyman.suelostodo.data.Task

data class TreeRow(
    val task: Task,
    val hasChildren: Boolean,
    val childCount: Int,
    val doneChildCount: Int,
    val isRoot: Boolean = true,
)

/**
 * Flattens a set of tasks into display rows (depth-first). Children of collapsed
 * tasks are hidden. Tasks whose parent is not part of [tasks] are treated as roots.
 */
fun flattenTree(
    tasks: List<Task>,
    respectExpanded: Boolean = true,
    rootFilter: (Task) -> Boolean = { true },
): List<TreeRow> {
    val ids = tasks.mapTo(HashSet()) { it.id }
    val byParent = tasks.groupBy { it.parentId }
    val roots = tasks.filter { (it.parentId == null || it.parentId !in ids) && rootFilter(it) }
    val out = ArrayList<TreeRow>(tasks.size)
    fun visit(t: Task, guard: Int) {
        val kids = byParent[t.id].orEmpty()
        out += TreeRow(t, kids.isNotEmpty(), kids.size, kids.count { it.done }, guard == 0)
        if (guard < 5 && (!respectExpanded || t.expanded)) kids.forEach { visit(it, guard + 1) }
    }
    roots.forEach { visit(it, 0) }
    return out
}

/** Splits flattened rows into groups, each starting with a root row. */
fun groupRows(rows: List<TreeRow>): List<List<TreeRow>> {
    val out = ArrayList<MutableList<TreeRow>>()
    rows.forEach { r -> if (r.isRoot || out.isEmpty()) out += mutableListOf(r) else out.last() += r }
    return out
}
