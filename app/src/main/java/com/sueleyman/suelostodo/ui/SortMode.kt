package com.sueleyman.suelostodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sueleyman.suelostodo.data.Task
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Sort mode of a list: drag entries by the handle (or long press) to reorder them.
 * Sorting happens per level – tap › to sort the subtasks of an entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortMode(vm: ListViewModel, title: String, accent: Color, onDone: () -> Unit) {
    val all by vm.listTasks.collectAsStateWithLifecycle(initialValue = emptyList())
    val path = remember { mutableStateListOf<Task>() }          // drill-down: parents
    val parentId = path.lastOrNull()?.id
    val source = all.filter { it.parentId == parentId && !it.done && !it.deleted }.sortedBy { it.sortOrder }
    val childCount = all.filter { !it.done && !it.deleted }.groupingBy { it.parentId }.eachCount()
    var items by remember { mutableStateOf(source) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(source.map { it.id to it.title }, parentId) { if (!dragging) items = source }

    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val f = items.indexOfFirst { it.id == from.key }
        val t = items.indexOfFirst { it.id == to.key }
        if (f >= 0 && t >= 0) items = items.toMutableList().apply { add(t, removeAt(f)) }
    }
    fun finish() { dragging = false; vm.reorder(items.map { it.id }) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sortieren")
                        Text(
                            (listOf(title) + path.map { it.title }).joinToString(" › "),
                            style = MaterialTheme.typography.labelMedium, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (path.isNotEmpty()) path.removeAt(path.lastIndex) else onDone() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                },
                actions = { TextButton(onClick = onDone) { Text("Fertig") } }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Text(
                "Am Griff ≡ rechts ziehen (oder Zeile lange drücken). Mit › sortierst du die Unteraufgaben eines Eintrags.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (items.isEmpty()) Text("Keine offenen Einträge auf dieser Ebene.", modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { t ->
                    ReorderableItem(reorder, key = t.id) { isDragging ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shadowElevation = if (isDragging) 6.dp else 0.5.dp,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Row(
                                Modifier.longPressDraggableHandle(onDragStarted = { dragging = true }, onDragStopped = { finish() })
                                    .padding(start = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.foundation.layout.Box(
                                    Modifier.size(10.dp).background(accent, RoundedCornerShape(5.dp))
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(t.title, modifier = Modifier.weight(1f).padding(vertical = 14.dp), maxLines = 2,
                                    overflow = TextOverflow.Ellipsis)
                                val kids = childCount[t.id] ?: 0
                                if (kids > 0) Row(
                                    Modifier.clickable { path.add(t) }.padding(horizontal = 6.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("$kids", style = MaterialTheme.typography.labelMedium, color = accent)
                                    Icon(Icons.Filled.ChevronRight, "Unteraufgaben sortieren", tint = accent)
                                }
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.draggableHandle(onDragStarted = { dragging = true }, onDragStopped = { finish() })
                                ) { Icon(Icons.Filled.DragHandle, "Verschieben", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        }
    }
}
