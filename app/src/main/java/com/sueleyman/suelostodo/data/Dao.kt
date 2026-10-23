package com.sueleyman.suelostodo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class ListWithCount(
    val id: String,
    val name: String,
    val color: Int,
    val openCount: Int,
    val groupId: String?,
)

@Dao
interface TaskDao {
    // ---------- lists ----------
    @Query(
        """SELECT l.id, l.name, l.color, l.groupId,
           (SELECT COUNT(*) FROM tasks t WHERE t.listId = l.id AND t.deleted = 0 AND t.done = 0 AND t.parentId IS NULL) AS openCount
           FROM lists l WHERE l.deleted = 0 ORDER BY CASE WHEN l.id = 'inbox' THEN 0 ELSE 1 END, l.sortOrder"""
    )
    fun observeListsWithCount(): Flow<List<ListWithCount>>

    @Query("SELECT * FROM lists WHERE deleted = 0 ORDER BY CASE WHEN id = 'inbox' THEN 0 ELSE 1 END, sortOrder")
    fun observeLists(): Flow<List<TaskList>>

    @Query("SELECT * FROM lists WHERE deleted = 0 ORDER BY CASE WHEN id = 'inbox' THEN 0 ELSE 1 END, sortOrder")
    suspend fun getLists(): List<TaskList>

    @Query("SELECT * FROM lists WHERE id = :id")
    fun observeList(id: String): Flow<TaskList?>

    @Query("SELECT * FROM lists WHERE id = :id")
    suspend fun getList(id: String): TaskList?

    @Upsert
    suspend fun upsertList(list: TaskList)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertListIfAbsent(list: TaskList)

    // ---------- groups ----------
    @Query("SELECT * FROM list_groups WHERE deleted = 0 ORDER BY sortOrder")
    fun observeGroups(): Flow<List<ListGroup>>

    @Query("SELECT * FROM list_groups WHERE deleted = 0 ORDER BY sortOrder")
    suspend fun getGroups(): List<ListGroup>

    @Query("SELECT * FROM list_groups WHERE id = :id")
    suspend fun getGroup(id: String): ListGroup?

    @Upsert
    suspend fun upsertGroup(g: ListGroup)

    @Upsert
    suspend fun upsertGroups(g: List<ListGroup>)

    @Query("SELECT * FROM list_groups")
    suspend fun allGroupsRaw(): List<ListGroup>

    @Query("UPDATE list_groups SET expanded = :expanded WHERE id = :id")
    suspend fun setGroupExpanded(id: String, expanded: Boolean)

    @Query("SELECT * FROM lists WHERE groupId = :groupId AND deleted = 0")
    suspend fun getListsInGroup(groupId: String): List<TaskList>

    // ---------- tasks ----------
    @Query("SELECT * FROM tasks WHERE listId = :listId AND deleted = 0 ORDER BY sortOrder")
    fun observeTasksOfList(listId: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE deleted = 0 ORDER BY sortOrder")
    fun observeAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE deleted = 0 AND done = 0 ORDER BY sortOrder")
    fun observeOpenTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeTask(id: String): Flow<Task?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTask(id: String): Task?

    @Query("SELECT * FROM tasks WHERE parentId = :parentId AND deleted = 0 ORDER BY sortOrder")
    fun observeChildren(parentId: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE parentId = :parentId AND deleted = 0")
    suspend fun getChildren(parentId: String): List<Task>

    @Query("SELECT * FROM tasks WHERE listId = :listId AND deleted = 0")
    suspend fun getTasksOfList(listId: String): List<Task>

    @Query("SELECT * FROM tasks WHERE deleted = 0 AND done = 0 AND reminderAt IS NOT NULL")
    suspend fun getPendingReminders(): List<Task>

    @Upsert
    suspend fun upsertTask(task: Task)

    @Upsert
    suspend fun upsertTasks(tasks: List<Task>)

    @Query("UPDATE tasks SET expanded = :expanded WHERE id = :id")
    suspend fun setExpanded(id: String, expanded: Boolean)

    // ---------- attachments ----------
    @Query("SELECT * FROM attachments WHERE taskId = :taskId AND deleted = 0 ORDER BY createdAt")
    fun observeAttachments(taskId: String): Flow<List<Attachment>>

    @Query("SELECT taskId FROM attachments WHERE deleted = 0")
    fun observeTaskIdsWithAttachments(): Flow<List<String>>

    @Query("SELECT * FROM attachments WHERE taskId = :taskId AND deleted = 0")
    suspend fun getAttachments(taskId: String): List<Attachment>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun getAttachment(id: String): Attachment?

    @Upsert
    suspend fun upsertAttachment(a: Attachment)

    // ---------- sync ----------
    @Query("SELECT * FROM lists")
    suspend fun allListsRaw(): List<TaskList>

    @Query("SELECT * FROM tasks")
    suspend fun allTasksRaw(): List<Task>

    @Query("SELECT * FROM attachments")
    suspend fun allAttachmentsRaw(): List<Attachment>

    @Upsert
    suspend fun upsertLists(lists: List<TaskList>)

    @Upsert
    suspend fun upsertAttachments(a: List<Attachment>)
}
