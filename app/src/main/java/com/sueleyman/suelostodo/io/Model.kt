package com.sueleyman.suelostodo.io

import com.sueleyman.suelostodo.data.Attachment
import com.sueleyman.suelostodo.data.Task
import com.sueleyman.suelostodo.data.TaskList

/** Neutral intermediate model for every importer. */
data class ImportAttachment(val name: String, val mime: String, val bytes: ByteArray)

data class ImportItem(
    val title: String,
    val id: String? = null,
    val note: String = "",
    val done: Boolean = false,
    val important: Boolean = false,
    /** epoch day */
    val dueDate: Long? = null,
    val reminderAt: Long? = null,
    val completedAt: Long? = null,
    val createdAt: Long? = null,
    val children: MutableList<ImportItem> = mutableListOf(),
    val attachments: List<ImportAttachment> = emptyList(),
)

data class ImportList(
    val name: String,
    val items: List<ImportItem>,
    val id: String? = null,
    val color: Int? = null,
)

data class ImportResult(val lists: Int, val tasks: Int, val attachments: Int, val flattened: Int = 0) {
    fun describe() = buildString {
        append("$lists Liste(n), $tasks Aufgabe(n)")
        if (attachments > 0) append(", $attachments Anhang/Anhänge")
        append(" importiert")
        if (flattened > 0) append(" – $flattened zu tief verschachtelte Einträge wurden auf Ebene 3 eingeordnet")
    }
}

/** Export tree */
data class ExportNode(val task: Task, val children: List<ExportNode>, val attachments: List<Attachment>)
data class ExportList(val list: TaskList, val roots: List<ExportNode>)

fun ExportNode.walk(depth: Int = 0, block: (ExportNode, Int) -> Unit) {
    block(this, depth)
    children.forEach { it.walk(depth + 1, block) }
}
