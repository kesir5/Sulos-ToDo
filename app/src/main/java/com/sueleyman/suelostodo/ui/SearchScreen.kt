package com.sueleyman.suelostodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sueleyman.suelostodo.SuelosToDoApp
import com.sueleyman.suelostodo.data.Task
import com.sueleyman.suelostodo.data.TaskList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchHit(val task: Task, val path: String, val inNote: Boolean)

data class SearchState(
    val query: String = "",
    val withDone: Boolean = false,
    val lists: List<TaskList> = emptyList(),
    val hits: List<SearchHit> = emptyList(),
    /** colour index of every list (for hit subtitles) */
    val colors: Map<String, Int> = emptyMap(),
)

/** Pure search – unit-testable. Matches title or note (case-insensitive), lists by name. */
fun searchTasks(query: String, withDone: Boolean, tasks: List<Task>, lists: List<TaskList>): Pair<List<TaskList>, List<SearchHit>> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList<TaskList>() to emptyList()
    val listName = lists.associate { it.id to it.name }
    val byId = tasks.associateBy { it.id }
    val hits = tasks.asSequence()
        .filter { !it.deleted && it.listId in listName && (withDone || !it.done) }
        .mapNotNull { t ->
            val inTitle = t.title.contains(q, ignoreCase = true)
            val inNote = !inTitle && t.note.contains(q, ignoreCase = true)
            if (!inTitle && !inNote) null else {
                val parents = generateSequence(t.parentId?.let { byId[it] }) { p -> p.parentId?.let { byId[it] } }
                    .take(3).map { it.title }.toList().reversed()
                SearchHit(t, (listOf(listName[t.listId] ?: "") + parents).joinToString(" › "), inNote)
            }
        }
        // done last, title hits before note hits, then by title
        .sortedWith(compareBy({ it.task.done }, { it.inNote }, { it.task.title.lowercase() }))
        .take(200).toList()
    return lists.filter { it.name.contains(q, ignoreCase = true) } to hits
}

class SearchViewModel : ViewModel() {
    private val repo get() = SuelosToDoApp.instance.repo
    val query = MutableStateFlow("")
    val withDone = MutableStateFlow(false)
    val state = combine(query, withDone, repo.observeAllTasks(), repo.observeLists()) { q, d, tasks, lists ->
        val (ls, hits) = searchTasks(q, d, tasks, lists)
        SearchState(q, d, ls, hits, lists.associate { it.id to it.color })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState())

    fun toggleDone(id: String) = viewModelScope.launch { repo.toggleDone(id) }
}

private fun highlight(text: String, q: String, color: Color): AnnotatedString = buildAnnotatedString {
    val query = q.trim()
    if (query.isEmpty()) { append(text); return@buildAnnotatedString }
    var i = 0
    while (i < text.length) {
        val hit = text.indexOf(query, i, ignoreCase = true)
        if (hit < 0) { append(text.substring(i)); break }
        append(text.substring(i, hit))
        pushStyle(SpanStyle(background = color.copy(alpha = 0.25f), fontWeight = FontWeight.SemiBold))
        append(text.substring(hit, hit + query.length))
        pop()
        i = hit + query.length
    }
}

/** Snippet of the note around the match. */
private fun noteSnippet(note: String, q: String): String {
    val i = note.indexOf(q.trim(), ignoreCase = true).coerceAtLeast(0)
    val start = (i - 30).coerceAtLeast(0)
    val end = (i + q.length + 50).coerceAtMost(note.length)
    return (if (start > 0) "…" else "") + note.substring(start, end).replace('\n', ' ') + if (end < note.length) "…" else ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit, onOpenTask: (String) -> Unit, onOpenList: (String) -> Unit) {
    val vm: SearchViewModel = viewModel { SearchViewModel() }
    val st by vm.state.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val withDone by vm.withDone.collectAsStateWithLifecycle()
    val fr = remember { FocusRequester() }
    val hl = MaterialTheme.colorScheme.primary
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box0 {
                            if (q.isEmpty()) Text("Aufgaben, Notizen, Listen suchen", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge)
                            BasicTextField(
                                value = q, onValueChange = { vm.query.value = it }, singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(hl),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                modifier = Modifier.fillMaxWidth().focusRequester(fr)
                            )
                        }
                    }
                },
                actions = {
                    if (q.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Filled.Close, "Leeren") }
                }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).imePadding()) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = withDone, onClick = { vm.withDone.value = !withDone }, label = { Text("Erledigte einbeziehen") })
                    Spacer(Modifier.weight(1f))
                    if (q.isNotBlank()) Text("${st.hits.size} Treffer", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (q.isBlank()) item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Suchbegriff eingeben", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (st.lists.isNotEmpty()) {
                item { SectionLabel("Listen") }
                items(st.lists, key = { "l" + it.id }) { l ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenList(l.id) }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.List, null, tint = listColor(l.color))
                        Spacer(Modifier.width(16.dp))
                        Text(highlight(l.name, q, hl))
                    }
                }
            }
            if (st.hits.isNotEmpty()) item { SectionLabel("Aufgaben") }
            items(st.hits, key = { "t" + it.task.id }) { h ->
                val t = h.task
                val color = st.listsColor(t.listId)
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenTask(t.id) }.padding(start = 6.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CheckCircle(t.done, color, 18.dp) { vm.toggleDone(t.id) }
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(
                            highlight(t.title, q, hl), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textDecoration = if (t.done) TextDecoration.LineThrough else null,
                            color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                        if (h.inNote) Text(
                            highlight(noteSnippet(t.note, q), q, hl), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            h.path + (t.dueDate?.let { "  ·  " + formatDue(it) } ?: ""),
                            style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (t.important) Icon(Icons.Filled.Star, "Wichtig", tint = color, modifier = Modifier.size(18.dp))
                }
            }
            if (q.isNotBlank() && st.hits.isEmpty() && st.lists.isEmpty()) item {
                Text("Nichts gefunden.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
            }
        }
    }
}

private fun SearchState.listsColor(listId: String): Color = listColor(colors[listId] ?: 0)

@Composable
private fun SectionLabel(t: String) {
    Text(
        t.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun Box0(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) { content() }
}
