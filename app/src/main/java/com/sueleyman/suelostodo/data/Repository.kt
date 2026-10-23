package com.sueleyman.suelostodo.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.sueleyman.suelostodo.reminder.ReminderScheduler
import com.sueleyman.suelostodo.sync.SyncScheduler
import com.sueleyman.suelostodo.widget.TaskWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

class Repository(
    private val context: Context,
    private val db: AppDatabase,
    private val appScope: CoroutineScope,
) {
    private val dao = db.dao()

    val attachmentDir: File get() = File(context.filesDir, "attachments").apply { mkdirs() }
    fun attachmentFile(id: String) = File(attachmentDir, id)

    // ---------- observe ----------
    fun observeListsWithCount() = dao.observeListsWithCount()
    fun observeLists() = dao.observeLists()
    fun observeGroups() = dao.observeGroups()
    fun observeList(id: String) = dao.observeList(id)
    fun observeTasksOfList(listId: String) = dao.observeTasksOfList(listId)
    fun observeAllTasks() = dao.observeAllTasks()
    fun observeOpenTasks() = dao.observeOpenTasks()
    fun observeTask(id: String) = dao.observeTask(id)
    fun observeChildren(id: String) = dao.observeChildren(id)
    fun observeAttachments(taskId: String): Flow<List<Attachment>> = dao.observeAttachments(taskId)
    fun observeTaskIdsWithAttachments() = dao.observeTaskIdsWithAttachments()
    suspend fun getLists() = dao.getLists()
    suspend fun getTask(id: String) = dao.getTask(id)

    suspend fun ensureInbox() {
        dao.insertListIfAbsent(TaskList(id = INBOX_ID, name = "Aufgaben", color = 0, sortOrder = 0, updatedAt = 0))
    }

    // ---------- lists ----------
    suspend fun createList(name: String, color: Int): String {
        val l = TaskList(name = name.trim().ifBlank { "Unbenannte Liste" }, color = color)
        dao.upsertList(l)
        changed()
        return l.id
    }

    suspend fun updateList(id: String, name: String? = null, color: Int? = null) {
        val l = dao.getList(id) ?: return
        dao.upsertList(
            l.copy(
                name = name?.trim()?.ifBlank { l.name } ?: l.name,
                color = color ?: l.color,
                updatedAt = now()
            )
        )
        changed()
    }

    suspend fun deleteList(id: String) {
        if (id == INBOX_ID) return
        val l = dao.getList(id) ?: return
        dao.upsertList(l.copy(deleted = true, updatedAt = now()))
        dao.getTasksOfList(id).forEach { deleteTaskInternal(it) }
        changed()
    }

    // ---------- groups ----------
    /** Creates a group. [parentId] must be a top-level group (two levels max). */
    suspend fun createGroup(name: String, parentId: String? = null): String? {
        val parent = parentId?.let { dao.getGroup(it) }
        if (parent != null && parent.parentId != null) return null
        val g = ListGroup(name = name.trim().ifBlank { "Gruppe" }, parentId = parent?.id)
        dao.upsertGroup(g)
        changed()
        return g.id
    }

    suspend fun renameGroup(id: String, name: String) {
        val g = dao.getGroup(id) ?: return
        dao.upsertGroup(g.copy(name = name.trim().ifBlank { g.name }, updatedAt = now()))
        changed()
    }

    /** Ungroups: lists and subgroups move one level up, the group itself is removed. */
    suspend fun dissolveGroup(id: String) {
        val g = dao.getGroup(id) ?: return
        dao.getListsInGroup(id).forEach { dao.upsertList(it.copy(groupId = g.parentId, updatedAt = now())) }
        dao.getGroups().filter { it.parentId == id }.forEach {
            dao.upsertGroup(it.copy(parentId = g.parentId, updatedAt = now()))
        }
        dao.upsertGroup(g.copy(deleted = true, updatedAt = now()))
        changed()
    }

    suspend fun setGroupColor(id: String, color: Long?) {
        val g = dao.getGroup(id) ?: return
        dao.upsertGroup(g.copy(color = color, updatedAt = now()))
        changed()
    }

    suspend fun setAllGroupsExpanded(expanded: Boolean) = dao.setAllGroupsExpanded(expanded)

    suspend fun setGroupExpanded(id: String, expanded: Boolean) {
        dao.setGroupExpanded(id, expanded)
    }

    suspend fun moveListToGroup(listId: String, groupId: String?) {
        if (listId == INBOX_ID) return
        val l = dao.getList(listId) ?: return
        dao.upsertList(l.copy(groupId = groupId, updatedAt = now()))
        changed()
    }

    suspend fun createListInGroup(name: String, color: Int, groupId: String?): String {
        val l = TaskList(name = name.trim().ifBlank { "Unbenannte Liste" }, color = color, groupId = groupId)
        dao.upsertList(l)
        changed()
        return l.id
    }

    /** Persists a manual arrangement (order and parents of groups and lists). Only changed rows are written. */
    suspend fun applyArrangement(a: com.sueleyman.suelostodo.ui.Arrangement) {
        val gMap = dao.allGroupsRaw().associateBy { it.id }
        for (p in a.groups) gMap[p.id]?.let {
            if (it.parentId != p.parentId || it.sortOrder != p.sortOrder)
                dao.upsertGroup(it.copy(parentId = p.parentId, sortOrder = p.sortOrder, updatedAt = now()))
        }
        val lMap = dao.allListsRaw().associateBy { it.id }
        for (p in a.lists) lMap[p.id]?.let {
            if (it.groupId != p.parentId || it.sortOrder != p.sortOrder)
                dao.upsertList(it.copy(groupId = p.parentId, sortOrder = p.sortOrder, updatedAt = now()))
        }
        changed()
    }

    // ---------- tasks ----------
    /**
     * Adds a task. For smart lists the [listId] must be a real list; flags such as
     * important / myDay / dueDate can be pre-set.
     */
    suspend fun addTask(
        listId: String,
        title: String,
        parentId: String? = null,
        important: Boolean = false,
        myDay: Boolean = false,
        dueToday: Boolean = false,
    ): String? {
        if (title.isBlank()) return null
        val parent = parentId?.let { dao.getTask(it) }
        val depth = if (parent == null) 0 else parent.depth + 1
        if (depth > MAX_DEPTH) return null
        val t = Task(
            listId = parent?.listId ?: listId,
            parentId = parent?.id,
            depth = depth,
            title = title.trim(),
            important = important,
            myDay = if (myDay) LocalDate.now().toEpochDay() else null,
            dueDate = if (dueToday) LocalDate.now().toEpochDay() else null,
        )
        dao.upsertTask(t)
        if (parent != null && !parent.expanded) dao.setExpanded(parent.id, true)
        changed()
        return t.id
    }

    suspend fun updateTask(task: Task) {
        val updated = task.copy(updatedAt = now())
        dao.upsertTask(updated)
        ReminderScheduler.schedule(context, updated)
        changed()
    }

    suspend fun toggleDone(id: String) {
        val t = dao.getTask(id) ?: return
        setDone(t, !t.done)
        changed()
    }

    private suspend fun setDone(t: Task, done: Boolean) {
        val upd = t.copy(done = done, completedAt = if (done) now() else null, updatedAt = now())
        dao.upsertTask(upd)
        if (done) ReminderScheduler.cancel(context, t.id) else ReminderScheduler.schedule(context, upd)
        // completing a parent completes all of its subtasks
        if (done) dao.getChildren(t.id).filter { !it.done }.forEach { setDone(it, true) }
    }

    suspend fun toggleImportant(id: String) {
        val t = dao.getTask(id) ?: return
        dao.upsertTask(t.copy(important = !t.important, updatedAt = now()))
        changed()
    }

    suspend fun setExpanded(id: String, expanded: Boolean) {
        dao.setExpanded(id, expanded)
        changed(sync = false)
    }

    suspend fun deleteTask(id: String) {
        val t = dao.getTask(id) ?: return
        deleteTaskInternal(t)
        changed()
    }

    private suspend fun deleteTaskInternal(t: Task) {
        dao.upsertTask(t.copy(deleted = true, updatedAt = now()))
        ReminderScheduler.cancel(context, t.id)
        dao.getAttachments(t.id).forEach { deleteAttachmentInternal(it) }
        dao.getChildren(t.id).forEach { deleteTaskInternal(it) }
    }

    /** Moves a root task (with its subtree) to another list. */
    suspend fun moveTask(id: String, targetListId: String) {
        val t = dao.getTask(id) ?: return
        moveRecursive(t.copy(parentId = null, depth = 0), targetListId)
        changed()
    }

    private suspend fun moveRecursive(t: Task, listId: String) {
        dao.upsertTask(t.copy(listId = listId, updatedAt = now()))
        dao.getChildren(t.id).forEach { moveRecursive(it, listId) }
    }

    // ---------- attachments ----------
    suspend fun addAttachment(taskId: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        var name = "Datei"
        var size = -1L
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        val mime = cr.getType(uri) ?: "application/octet-stream"
        val a = Attachment(taskId = taskId, fileName = name, mimeType = mime, size = size)
        val target = attachmentFile(a.id)
        val ok = runCatching {
            cr.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
        }.isSuccess
        if (!ok) return@withContext false
        dao.upsertAttachment(a.copy(size = target.length()))
        touchTask(taskId)
        changed()
        true
    }

    suspend fun deleteAttachment(id: String) {
        val a = dao.getAttachment(id) ?: return
        deleteAttachmentInternal(a)
        touchTask(a.taskId)
        changed()
    }

    private suspend fun deleteAttachmentInternal(a: Attachment) {
        dao.upsertAttachment(a.copy(deleted = true, updatedAt = now()))
        withContext(Dispatchers.IO) { attachmentFile(a.id).delete() }
    }

    private suspend fun touchTask(id: String) {
        dao.getTask(id)?.let { dao.upsertTask(it.copy(updatedAt = now())) }
    }

    // ---------- sync support ----------
    suspend fun snapshot(): SyncSnapshot =
        SyncSnapshot(
            lists = dao.allListsRaw(), groups = dao.allGroupsRaw(),
            tasks = dao.allTasksRaw(), attachments = dao.allAttachmentsRaw()
        )

    /** Last-write-wins merge per record. Returns the merged snapshot. */
    suspend fun mergeRemote(remote: SyncSnapshot): SyncSnapshot {
        val localLists = dao.allListsRaw().associateBy { it.id }
        val localTasks = dao.allTasksRaw().associateBy { it.id }
        val localAtt = dao.allAttachmentsRaw().associateBy { it.id }
        val localGroups = dao.allGroupsRaw().associateBy { it.id }
        val newerGroups = remote.groups.filter { r -> localGroups[r.id].let { it == null || r.updatedAt > it.updatedAt } }
            .map { r -> r.copy(expanded = localGroups[r.id]?.expanded ?: true) }
        if (newerGroups.isNotEmpty()) dao.upsertGroups(newerGroups)

        val newerLists = remote.lists.filter { r -> localLists[r.id].let { it == null || r.updatedAt > it.updatedAt } }
        val newerTasks = remote.tasks.filter { r -> localTasks[r.id].let { it == null || r.updatedAt > it.updatedAt } }
            .map { r -> r.copy(expanded = localTasks[r.id]?.expanded ?: true) }
        val newerAtt = remote.attachments.filter { r -> localAtt[r.id].let { it == null || r.updatedAt > it.updatedAt } }

        if (newerLists.isNotEmpty()) dao.upsertLists(newerLists)
        if (newerTasks.isNotEmpty()) dao.upsertTasks(newerTasks)
        if (newerAtt.isNotEmpty()) dao.upsertAttachments(newerAtt)
        newerAtt.filter { it.deleted }.forEach { withContext(Dispatchers.IO) { attachmentFile(it.id).delete() } }

        if (newerLists.isNotEmpty() || newerTasks.isNotEmpty() || newerAtt.isNotEmpty() || newerGroups.isNotEmpty()) {
            ReminderScheduler.rescheduleAll(context)
            changed(sync = false)
        }
        return snapshot()
    }

    // ---------- import / export ----------
    suspend fun exportTree(listIds: Set<String>? = null): List<com.sueleyman.suelostodo.io.ExportList> {
        val lists = dao.getLists().filter { listIds == null || it.id in listIds }
        val tasks = dao.allTasksRaw().filter { !it.deleted }
        val atts = dao.allAttachmentsRaw().filter { !it.deleted }.groupBy { it.taskId }
        val byParent = tasks.groupBy { it.parentId }
        fun node(t: Task): com.sueleyman.suelostodo.io.ExportNode = com.sueleyman.suelostodo.io.ExportNode(
            t, byParent[t.id].orEmpty().sortedBy { it.sortOrder }.map { node(it) }, atts[t.id].orEmpty()
        )
        return lists.map { l ->
            com.sueleyman.suelostodo.io.ExportList(
                l, tasks.filter { it.listId == l.id && it.parentId == null }.sortedBy { it.sortOrder }.map { node(it) }
            )
        }
    }

    /**
     * Imports lists from an external source. Items with an [ImportItem.id] are upserted (re-import updates
     * instead of duplicating). Levels deeper than MAX_DEPTH are attached to the deepest allowed level.
     */
    suspend fun importLists(
        lists: List<com.sueleyman.suelostodo.io.ImportList>,
        targetListId: String? = null,
    ): com.sueleyman.suelostodo.io.ImportResult {
        var nLists = 0; var nTasks = 0; var nAtt = 0; var flattened = 0
        val now0 = now()
        var seq = 0L
        for (il in lists) {
            val listId: String = when {
                targetListId != null -> targetListId
                il.id != null -> {
                    val existing = dao.getList(il.id)
                    dao.upsertList(
                        (existing ?: TaskList(id = il.id, name = il.name, color = il.color ?: 0))
                            .copy(name = il.name, deleted = false, updatedAt = now())
                    )
                    il.id
                }
                else -> TaskList(name = il.name.ifBlank { "Import" }, color = il.color ?: ((nLists + 1) % ListColors.all.size))
                    .also { dao.upsertList(it) }.id
            }
            nLists++
            suspend fun insert(item: com.sueleyman.suelostodo.io.ImportItem, parent: Task?) {
                var p = parent
                if (p != null && p.depth >= MAX_DEPTH) { p = p.parentId?.let { dao.getTask(it) }; flattened++ }
                val id = item.id ?: newId()
                val existing = dao.getTask(id)
                val t = Task(
                    id = id, listId = listId, parentId = p?.id, depth = (p?.depth ?: -1) + 1,
                    title = item.title, note = item.note, done = item.done, important = item.important,
                    dueDate = item.dueDate, reminderAt = item.reminderAt,
                    completedAt = item.completedAt ?: if (item.done) now0 else null,
                    createdAt = item.createdAt ?: now0,
                    sortOrder = existing?.sortOrder ?: (now0 + seq++),
                    updatedAt = now(),
                    expanded = existing?.expanded ?: true,
                    myDay = existing?.myDay,
                )
                dao.upsertTask(t)
                nTasks++
                for (a in item.attachments) {
                    val att = Attachment(taskId = id, fileName = a.name, mimeType = a.mime, size = a.bytes.size.toLong())
                    withContext(Dispatchers.IO) { attachmentFile(att.id).writeBytes(a.bytes) }
                    dao.upsertAttachment(att)
                    nAtt++
                }
                item.children.forEach { insert(it, t) }
            }
            il.items.forEach { insert(it, null) }
        }
        ReminderScheduler.rescheduleAll(context)
        changed()
        return com.sueleyman.suelostodo.io.ImportResult(nLists, nTasks, nAtt, flattened)
    }

    /** Restores a Sülos ToDo backup by merging it (newest change wins). */
    suspend fun importBackup(snapshot: SyncSnapshot, files: Map<String, ByteArray>): com.sueleyman.suelostodo.io.ImportResult {
        withContext(Dispatchers.IO) {
            files.forEach { (id, bytes) -> if (!attachmentFile(id).exists()) attachmentFile(id).writeBytes(bytes) }
        }
        mergeRemote(snapshot)
        changed()
        return com.sueleyman.suelostodo.io.ImportResult(
            snapshot.lists.count { !it.deleted }, snapshot.tasks.count { !it.deleted }, files.size
        )
    }

    // ---------- change propagation ----------
    private fun changed(sync: Boolean = true) {
        appScope.launch {
            runCatching { TaskWidget.refresh(context) }
            if (sync) SyncScheduler.scheduleSoon(context)
        }
    }

    private fun now() = System.currentTimeMillis()
}
