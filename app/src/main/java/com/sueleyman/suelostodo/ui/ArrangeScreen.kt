package com.sueleyman.suelostodo.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sueleyman.suelostodo.SuelosToDoApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

class ArrangeViewModel : ViewModel() {
    private val repo get() = SuelosToDoApp.instance.repo
    val items = combine(repo.observeListsWithCount(), repo.observeGroups()) { l, g -> buildArrangeItems(l, g) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun apply(order: List<ArrangeItem>) = viewModelScope.launch { repo.applyArrangement(computeArrangement(order)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArrangeScreen(onBack: () -> Unit) {
    val vm: ArrangeViewModel = viewModel { ArrangeViewModel() }
    val source by vm.items.collectAsStateWithLifecycle()
    var items by remember { mutableStateOf<List<ArrangeItem>>(emptyList()) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var before by remember { mutableStateOf<List<ArrangeItem>>(emptyList()) }
    LaunchedEffect(source) { if (dragging == null) source?.let { items = it } }

    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val f = items.indexOfFirst { it.key == from.key }
        val t = items.indexOfFirst { it.key == to.key }
        if (f >= 0 && t >= 0) items = items.toMutableList().apply { add(t, removeAt(f)) }
    }

    fun finish() {
        val key = dragging ?: return
        val settled = settleGroupMove(before, items, key)
        items = settled
        dragging = null
        vm.apply(settled)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Listen anordnen") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = { TextButton(onClick = onBack) { Text("Fertig") } }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Text(
                "Am Griff ⠿ ziehen (oder Zeile lange drücken). Eine Liste gehört zur Gruppe, unter der sie steht. " +
                    "Gruppen nehmen ihre Listen mit. Unter „Ohne Gruppe“ stehen Listen ohne Ordner.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            HorizontalDivider()
            val depths = arrangeDepths(items)
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(items, key = { it.key }) { item ->
                    val depth = depths.getOrElse(items.indexOf(item)) { 0 }
                    ReorderableItem(reorder, key = item.key) { isDragging ->
                        val surface = MaterialTheme.colorScheme.surface
                        val variant = MaterialTheme.colorScheme.surfaceVariant
                        val bg = when {
                            isDragging -> MaterialTheme.colorScheme.primaryContainer
                            item is ArrangeItem.Top || item is ArrangeItem.NoGroup -> variant.copy(alpha = 0.85f).compositeOver(surface)
                            item is ArrangeItem.Sub -> variant.copy(alpha = 0.4f).compositeOver(surface)
                            else -> surface
                        }
                        Surface(color = bg, shadowElevation = if (isDragging) 6.dp else 0.dp) {
                            val draggable = item !is ArrangeItem.NoGroup
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (draggable) Modifier.longPressDraggableHandle(
                                            onDragStarted = { before = items; dragging = item.key },
                                            onDragStopped = { finish() }
                                        ) else Modifier
                                    )
                                    .padding(start = (16 + depth * 24).dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when (item) {
                                    is ArrangeItem.Top, is ArrangeItem.Sub -> {
                                        val g = if (item is ArrangeItem.Top) item.group else (item as ArrangeItem.Sub).group
                                        Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Text(g.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(vertical = 14.dp))
                                    }
                                    is ArrangeItem.Lst -> {
                                        Icon(Icons.AutoMirrored.Outlined.List, null, tint = listColor(item.list.color), modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Text(item.list.name, modifier = Modifier.weight(1f).padding(vertical = 14.dp))
                                    }
                                    ArrangeItem.NoGroup -> {
                                        Icon(Icons.Outlined.Inbox, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Text("Ohne Gruppe", fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f).padding(vertical = 14.dp))
                                    }
                                }
                                if (draggable) IconButton(
                                    onClick = {},
                                    modifier = Modifier.draggableHandle(
                                        onDragStarted = { before = items; dragging = item.key },
                                        onDragStopped = { finish() }
                                    )
                                ) { Icon(Icons.Filled.DragHandle, "Verschieben", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        }
    }
}
