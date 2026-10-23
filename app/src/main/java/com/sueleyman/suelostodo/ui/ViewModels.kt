package com.sueleyman.suelostodo.ui

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sueleyman.suelostodo.SuelosToDoApp
import com.sueleyman.suelostodo.data.INBOX_ID
import com.sueleyman.suelostodo.data.ListWithCount
import com.sueleyman.suelostodo.data.ListGroup
import com.sueleyman.suelostodo.data.Task
import com.sueleyman.suelostodo.data.TaskList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

private val repo get() = SuelosToDoApp.instance.repo

private fun <T> Flow<T>.state(vm: ViewModel, initial: T): StateFlow<T> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

enum class SmartList(val id: String, val title: String, val color: Color) {
    MY_DAY("smart:myday", "Mein Tag", SmartColors.myDay),
    IMPORTANT("smart:important", "Wichtig", SmartColors.important),
    PLANNED("smart:planned", "Geplant", SmartColors.planned),
    /** Open tasks due today or overdue. */
    DUE("smart:due", "Fällig", SmartColors.due);

    fun matches(t: Task): Boolean = when (this) {
        MY_DAY -> t.myDay == LocalDate.now().toEpochDay()
        IMPORTANT -> t.important
        PLANNED -> t.dueDate != null
        DUE -> !t.done && t.dueDate != null && t.dueDate <= LocalDate.now().toEpochDay()
    }

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id }
    }
}

// ------------------------------------------------------------------ Home
sealed interface HomeRow {
    val depth: Int
    data class GroupRow(val group: ListGroup, override val depth: Int, val openCount: Int) : HomeRow
    data class ListRow(val list: ListWithCount, override val depth: Int) : HomeRow
}

data class GroupNode(
    val group: ListGroup,
    val depth: Int,
    val subs: List<GroupNode>,
    val lists: List<ListWithCount>,
    val open: Int,
    val done: Int,
    val total: Int,
    /** Theme color index – taken from the first list inside the group. */
    val color: Int,
) {
    val progress: Float get() = if (total == 0) 0f else done.toFloat() / total
}

data class HomeTree(val groups: List<GroupNode> = emptyList(), val loose: List<ListWithCount> = emptyList())

data class HomeState(
    val lists: List<ListWithCount> = emptyList(),
    val groups: List<ListGroup> = emptyList(),
    val rows: List<HomeRow> = emptyList(),
    val tree: HomeTree = HomeTree(),
    val smartCounts: Map<SmartList, Int> = emptyMap(),
    val myDayDone: Int = 0,
    val myDayTotal: Int = 0,
    val todayCount: Int = 0,
    val overdueCount: Int = 0,
    /** Up to 3 most urgent open tasks (by due date) for the "Fokus" layout. */
    val nextTasks: List<Task> = emptyList(),
    val datedOpenCount: Int = 0,
)

/** Same structure as [buildHomeRows] but nested, with progress per group (root tasks). */
fun buildHomeTree(lists: List<ListWithCount>, groups: List<ListGroup>, tasks: List<Task>): HomeTree {
    val gIds = groups.mapTo(HashSet()) { it.id }
    val roots = tasks.filter { it.parentId == null }
    val totalBy = roots.groupingBy { it.listId }.eachCount()
    val doneBy = roots.filter { it.done }.groupingBy { it.listId }.eachCount()
    val userLists = lists.filter { it.id != INBOX_ID }
    val listsOf = userLists.filter { it.groupId != null && it.groupId in gIds }.groupBy { it.groupId }
    val subsOf = groups.filter { it.parentId != null && it.parentId in gIds }.groupBy { it.parentId }
    fun node(g: ListGroup, depth: Int): GroupNode {
        val subs = if (depth == 0) subsOf[g.id].orEmpty().map { node(it, 1) } else emptyList()
        val ls = listsOf[g.id].orEmpty()
        val color = ls.firstOrNull()?.color ?: subs.firstOrNull()?.color ?: (g.id.hashCode() and 7)
        return GroupNode(
            g, depth, subs, ls,
            open = ls.sumOf { it.openCount } + subs.sumOf { it.open },
            done = ls.sumOf { doneBy[it.id] ?: 0 } + subs.sumOf { it.done },
            total = ls.sumOf { totalBy[it.id] ?: 0 } + subs.sumOf { it.total },
            color = color,
        )
    }
    val tops = groups.filter { it.parentId == null || it.parentId !in gIds }.map { node(it, 0) }
    return HomeTree(tops, userLists.filter { it.groupId == null || it.groupId !in gIds })
}

/** Builds the folder tree: groups (max. 2 levels) with their lists, then ungrouped lists. */
fun buildHomeRows(lists: List<ListWithCount>, groups: List<ListGroup>): List<HomeRow> {
    val gIds = groups.mapTo(HashSet()) { it.id }
    val tops = groups.filter { it.parentId == null || it.parentId !in gIds }
    val subsOf = groups.filter { it.parentId != null && it.parentId in gIds }.groupBy { it.parentId }
    val userLists = lists.filter { it.id != INBOX_ID }
    val listsOf = userLists.filter { it.groupId != null && it.groupId in gIds }.groupBy { it.groupId }
    fun count(g: ListGroup): Int =
        listsOf[g.id].orEmpty().sumOf { it.openCount } + subsOf[g.id].orEmpty().sumOf { count(it) }

    val out = mutableListOf<HomeRow>()
    for (top in tops) {
        out += HomeRow.GroupRow(top, 0, count(top))
        if (!top.expanded) continue
        // direct lists first, then subgroups (same order as the arrange screen)
        listsOf[top.id].orEmpty().forEach { out += HomeRow.ListRow(it, 1) }
        for (sub in subsOf[top.id].orEmpty()) {
            out += HomeRow.GroupRow(sub, 1, count(sub))
            if (sub.expanded) listsOf[sub.id].orEmpty().forEach { out += HomeRow.ListRow(it, 2) }
        }
    }
    userLists.filter { it.groupId == null || it.groupId !in gIds }.forEach { out += HomeRow.ListRow(it, 0) }
    return out
}

class HomeViewModel : ViewModel() {
    val state: StateFlow<HomeState> = combine(
        repo.observeListsWithCount(), repo.observeGroups(), repo.observeAllTasks()
    ) { lists, groups, all ->
        val listIds = lists.mapTo(HashSet()) { it.id }
        val live = all.filter { it.listId in listIds }
        val open = live.filter { !it.done }
        val today = LocalDate.now().toEpochDay()
        val myDay = live.filter { it.myDay == today }
        HomeState(
            lists = lists, groups = groups, rows = buildHomeRows(lists, groups),
            tree = buildHomeTree(lists, groups, live),
            smartCounts = SmartList.entries.associateWith { s -> open.count { s.matches(it) } },
            myDayDone = myDay.count { it.done }, myDayTotal = myDay.size,
            todayCount = open.count { it.myDay == today || it.dueDate == today },
            overdueCount = open.count { it.dueDate != null && it.dueDate < today },
            nextTasks = open.filter { it.dueDate != null }
                .sortedWith(compareBy<Task>({ it.dueDate }, { it.reminderAt ?: Long.MAX_VALUE }, { it.sortOrder }))
                .take(3),
            datedOpenCount = open.count { it.dueDate != null },
        )
    }.state(this, HomeState())

    fun createList(name: String, color: Int, groupId: String?, onCreated: (String) -> Unit) = viewModelScope.launch {
        onCreated(repo.createListInGroup(name, color, groupId))
    }
    fun createGroup(name: String, parentId: String?) = viewModelScope.launch { repo.createGroup(name, parentId) }
    fun renameGroup(id: String, name: String) = viewModelScope.launch { repo.renameGroup(id, name) }
    fun dissolveGroup(id: String) = viewModelScope.launch { repo.dissolveGroup(id) }
    fun toggleDone(taskId: String) = viewModelScope.launch { repo.toggleDone(taskId) }
    /** Collapses all groups if every group is open, otherwise opens all. */
    fun toggleAllGroups() = viewModelScope.launch {
        val g = state.value.groups
        repo.setAllGroupsExpanded(!(g.isNotEmpty() && g.all { it.expanded }))
    }
    fun setGroupColor(id: String, color: Long?) = viewModelScope.launch { repo.setGroupColor(id, color) }
    fun toggleGroupId(id: String) = viewModelScope.launch {
        state.value.groups.firstOrNull { it.id == id }?.let { repo.setGroupExpanded(it.id, !it.expanded) }
    }
    fun toggleGroup(g: ListGroup) = viewModelScope.launch { repo.setGroupExpanded(g.id, !g.expanded) }
    fun moveList(listId: String, groupId: String?) = viewModelScope.launch { repo.moveListToGroup(listId, groupId) }
    fun createGroupAndMove(name: String, parentId: String?, listId: String) = viewModelScope.launch {
        repo.createGroup(name, parentId)?.let { repo.moveListToGroup(listId, it) }
    }
}

// ------------------------------------------------------------------ List
data class ListHeader(val title: String, val color: Color, val colorIndex: Int, val smart: SmartList?, val exists: Boolean)

data class ListUiState(
    val header: ListHeader? = null,
    val openRows: List<TreeRow> = emptyList(),
    val doneRows: List<TreeRow> = emptyList(),
    val doneCount: Int = 0,
    val withAttachments: Set<String> = emptySet(),
    val listNames: Map<String, String> = emptyMap(),
    val allLists: List<TaskList> = emptyList(),
    val openCount: Int = 0,
    val total: Int = 0,
    val doneTotal: Int = 0,
) {
    val openGroups: List<List<TreeRow>> get() = groupRows(openRows)
    val doneGroups: List<List<TreeRow>> get() = groupRows(doneRows)
    val progress: Float get() = if (total == 0) 0f else doneTotal.toFloat() / total
}

@OptIn(ExperimentalCoroutinesApi::class)
class ListViewModel(val listId: String) : ViewModel() {
    val smart = SmartList.byId(listId)

    private val header: Flow<ListHeader> =
        if (smart != null) flowOf(ListHeader(smart.title, smart.color, -1, smart, true))
        else repo.observeList(listId).map { l ->
            ListHeader(l?.name ?: "", listColor(l?.color ?: 0), l?.color ?: 0, null, l != null && !l.deleted)
        }

    private val tasks: Flow<List<Task>> =
        if (smart != null) repo.observeAllTasks() else repo.observeTasksOfList(listId)

    val state: StateFlow<ListUiState> = combine(
        header, tasks, repo.observeTaskIdsWithAttachments(), repo.observeLists()
    ) { h, all, att, lists ->
        val names = lists.associate { it.id to it.name }
        if (smart != null) {
            val matching = all.filter { smart.matches(it) && it.listId in names }
            val sorted = if (smart == SmartList.PLANNED || smart == SmartList.DUE) matching.sortedBy { it.dueDate } else matching
            ListUiState(
                header = h,
                openRows = sorted.filter { !it.done }.map { TreeRow(it, false, 0, 0) },
                doneRows = sorted.filter { it.done }.map { TreeRow(it, false, 0, 0) },
                doneCount = sorted.count { it.done },
                withAttachments = att.toSet(), listNames = names, allLists = lists,
                openCount = sorted.count { !it.done }, total = sorted.size, doneTotal = sorted.count { it.done },
            )
        } else {
            val ids = all.mapTo(HashSet()) { it.id }
            val isRoot = { t: Task -> t.parentId == null || t.parentId !in ids }
            ListUiState(
                header = h,
                openRows = flattenTree(all) { isRoot(it) && !it.done },
                doneRows = flattenTree(all) { isRoot(it) && it.done },
                doneCount = all.count { isRoot(it) && it.done },
                withAttachments = att.toSet(), listNames = names, allLists = lists,
                openCount = all.count { isRoot(it) && !it.done }, total = all.size, doneTotal = all.count { it.done },
            )
        }
    }.state(this, ListUiState())

    fun addTask(title: String) = viewModelScope.launch {
        repo.addTask(
            listId = if (smart != null) INBOX_ID else listId,
            title = title,
            important = smart == SmartList.IMPORTANT,
            myDay = smart == SmartList.MY_DAY,
            dueToday = smart == SmartList.PLANNED || smart == SmartList.DUE,
        )
    }

    fun addSubtask(parentId: String, title: String) = viewModelScope.launch { repo.addTask(listId, title, parentId) }
    fun toggleDone(id: String) = viewModelScope.launch { repo.toggleDone(id) }
    fun toggleImportant(id: String) = viewModelScope.launch { repo.toggleImportant(id) }
    fun setExpanded(id: String, e: Boolean) = viewModelScope.launch { repo.setExpanded(id, e) }
    fun delete(id: String) = viewModelScope.launch { repo.deleteTask(id) }
    fun move(id: String, target: String) = viewModelScope.launch { repo.moveTask(id, target) }
    fun renameList(name: String, color: Int?) = viewModelScope.launch { repo.updateList(listId, name, color) }
    fun deleteList() = viewModelScope.launch { repo.deleteList(listId) }
    val groups = repo.observeGroups()
    val groupId = repo.observeList(listId).map { it?.groupId }
    fun moveToGroup(groupId: String?) = viewModelScope.launch { repo.moveListToGroup(listId, groupId) }
    fun createGroupAndMove(name: String, parentId: String?) = viewModelScope.launch {
        repo.createGroup(name, parentId)?.let { repo.moveListToGroup(listId, it) }
    }
}

// ------------------------------------------------------------------ Detail
data class DetailState(
    val task: Task? = null,
    val list: TaskList? = null,
    val parent: Task? = null,
    val children: List<Task> = emptyList(),
    val attachments: List<com.sueleyman.suelostodo.data.Attachment> = emptyList(),
    val lists: List<TaskList> = emptyList(),
    /** All descendants as tree rows (direct children are roots), respecting expanded state. */
    val subRows: List<TreeRow> = emptyList(),
) {
    val doneChildren: Int get() = children.count { it.done }
}

private fun descendantsOf(id: String, all: List<Task>): List<Task> {
    val byParent = all.groupBy { it.parentId }
    val out = mutableListOf<Task>()
    fun walk(p: String, guard: Int) {
        if (guard > 5) return
        byParent[p].orEmpty().forEach { out += it; walk(it.id, guard + 1) }
    }
    walk(id, 0)
    return out
}

@OptIn(ExperimentalCoroutinesApi::class)
class TaskDetailViewModel(val taskId: String) : ViewModel() {
    private val task = repo.observeTask(taskId)

    private val listTasks: Flow<List<Task>> =
        task.flatMapLatest { t -> if (t == null) flowOf(emptyList()) else repo.observeTasksOfList(t.listId) }

    val state: StateFlow<DetailState> = combine(
        task,
        task.flatMapLatest { t -> t?.parentId?.let { repo.observeTask(it) } ?: flowOf(null) },
        listTasks,
        repo.observeAttachments(taskId),
        repo.observeLists(),
    ) { t, parent, all, att, lists ->
        val desc = descendantsOf(taskId, all)
        DetailState(
            t, lists.firstOrNull { it.id == t?.listId }, parent,
            desc.filter { it.parentId == taskId }, att, lists, flattenTree(desc)
        )
    }.state(this, DetailState())

    fun toggleExpanded(t: Task) = viewModelScope.launch { repo.setExpanded(t.id, !t.expanded) }
    fun addSubtaskTo(parentId: String, title: String) = viewModelScope.launch { repo.addTask("", title, parentId) }

    private fun edit(block: (Task) -> Task) = viewModelScope.launch {
        repo.getTask(taskId)?.let { repo.updateTask(block(it)) }
    }

    fun setTitle(s: String) { if (s.isNotBlank()) edit { it.copy(title = s.trim()) } }
    fun setNote(s: String) = edit { it.copy(note = s) }
    fun setDue(epochDay: Long?) = edit { it.copy(dueDate = epochDay) }
    fun setReminder(millis: Long?) = edit { it.copy(reminderAt = millis) }
    fun toggleMyDay() = edit { it.copy(myDay = if (it.myDay == LocalDate.now().toEpochDay()) null else LocalDate.now().toEpochDay()) }
    fun toggleDone(id: String = taskId) = viewModelScope.launch { repo.toggleDone(id) }
    fun toggleImportant() = viewModelScope.launch { repo.toggleImportant(taskId) }
    fun addSubtask(title: String) = viewModelScope.launch { repo.addTask("", title, taskId) }
    fun deleteTask(id: String = taskId) = viewModelScope.launch { repo.deleteTask(id) }
    fun addAttachment(uri: Uri, onResult: (Boolean) -> Unit) = viewModelScope.launch { onResult(repo.addAttachment(taskId, uri)) }
    fun deleteAttachment(id: String) = viewModelScope.launch { repo.deleteAttachment(id) }
    fun move(target: String) = viewModelScope.launch { repo.moveTask(taskId, target) }
    fun attachmentFile(id: String) = repo.attachmentFile(id)
}
