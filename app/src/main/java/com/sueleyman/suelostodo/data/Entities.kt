package com.sueleyman.suelostodo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

/** Maximum nesting: level 0 = task, level 1 = subtask, level 2 = sub-subtask. */
const val MAX_DEPTH = 2

const val INBOX_ID = "inbox"

fun newId(): String = UUID.randomUUID().toString()

@Serializable
@Entity(tableName = "lists")
data class TaskList(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val color: Int = 0,
    val sortOrder: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    /** Group (folder) this list belongs to, or null for top level. */
    val groupId: String? = null,
)

/** Maximum group nesting: a top-level group may contain subgroups, subgroups contain only lists. */
const val MAX_GROUP_DEPTH = 2

@Serializable
@Entity(tableName = "list_groups", indices = [Index("parentId")])
data class ListGroup(
    @PrimaryKey val id: String = newId(),
    val name: String,
    /** Parent group (only top-level groups can be parents). */
    val parentId: String? = null,
    /** User-chosen colour as ARGB (0xFFRRGGBB), null = automatic (colour of the first list). */
    val color: Long? = null,
    val sortOrder: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    /** Local UI state only – not synced */
    @ColumnInfo(defaultValue = "1") val expanded: Boolean = true,
)

@Serializable
@Entity(
    tableName = "tasks",
    indices = [Index("listId"), Index("parentId")]
)
data class Task(
    @PrimaryKey val id: String = newId(),
    val listId: String,
    val parentId: String? = null,
    val depth: Int = 0,
    val title: String,
    val note: String = "",
    val done: Boolean = false,
    val important: Boolean = false,
    /** Due date as epoch day (LocalDate.toEpochDay) */
    val dueDate: Long? = null,
    /** Reminder as epoch millis */
    val reminderAt: Long? = null,
    /** Epoch day on which the task was added to "Mein Tag" */
    val myDay: Long? = null,
    val sortOrder: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    /** Local UI state only – not synced, preserved during merge */
    @ColumnInfo(defaultValue = "1") val expanded: Boolean = true,
)

@Serializable
@Entity(tableName = "attachments", indices = [Index("taskId")])
data class Attachment(
    @PrimaryKey val id: String = newId(),
    val taskId: String,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

/** Snapshot exchanged with Google Drive */
@Serializable
data class SyncSnapshot(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val lists: List<TaskList> = emptyList(),
    val groups: List<ListGroup> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
)

/** Theme colors for lists (MS-To-Do-like palette). */
object ListColors {
    val all = longArrayOf(
        0xFF2564CF, // Blau
        0xFF7B5EA7, // Lila
        0xFFC4314B, // Rot
        0xFF3E8A5E, // Grün
        0xFFCA5010, // Orange
        0xFFBF4C8A, // Pink
        0xFF038387, // Türkis
        0xFF5D5A58, // Grau
    )
    val names = listOf("Blau", "Lila", "Rot", "Grün", "Orange", "Pink", "Türkis", "Grau")
    fun argb(index: Int): Long = all[index.mod(all.size)]
}
