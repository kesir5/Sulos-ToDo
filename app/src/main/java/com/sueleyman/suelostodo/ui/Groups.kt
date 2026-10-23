package com.sueleyman.suelostodo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.sueleyman.suelostodo.data.ListGroup

/** Groups in display order: each top-level group followed by its subgroups. */
fun orderedGroups(groups: List<ListGroup>): List<Pair<ListGroup, Int>> {
    val ids = groups.mapTo(HashSet()) { it.id }
    val tops = groups.filter { it.parentId == null || it.parentId !in ids }
    return tops.flatMap { t -> listOf(t to 0) + groups.filter { it.parentId == t.id }.map { it to 1 } }
}

@Composable
private fun PickRow(selected: Boolean, label: String, depth: Int, folder: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = (depth * 22).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        if (folder) {
            Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label)
    }
}

/** Choose the target group for a list (any group, both levels). */
@Composable
fun GroupPickDialog(
    title: String,
    groups: List<ListGroup>,
    current: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
    onNewGroup: (() -> Unit)? = null,
) {
    var sel by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                PickRow(sel == null, "Keine Gruppe", 0, false) { sel = null }
                orderedGroups(groups).forEach { (g, d) -> PickRow(sel == g.id, g.name, d, true) { sel = g.id } }
                if (groups.isEmpty()) Text(
                    "Du hast noch keine Gruppen. Lege zuerst eine an – über „Gruppe“ unten auf der Startseite oder hier.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp)
                )
                if (onNewGroup != null) Row(
                    Modifier.fillMaxWidth().clickable(onClick = onNewGroup).padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.CreateNewFolder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Neue Gruppe anlegen …", color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(sel) }) { Text("Verschieben") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

/** Create a group, optionally below a top-level group. */
@Composable
fun NewGroupDialog(
    groups: List<ListGroup>,
    presetParent: String?,
    onDismiss: () -> Unit,
    onCreate: (String, String?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var parent by remember { mutableStateOf(presetParent) }
    val fr = remember { FocusRequester() }
    val tops = groups.filter { it.parentId == null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (presetParent != null) "Neue Untergruppe" else "Neue Gruppe") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth().focusRequester(fr)
                )
                if (tops.isNotEmpty()) {
                    Spacer(Modifier.size(12.dp))
                    Text("Einordnen in", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        item { PickRow(parent == null, "Oberste Ebene", 0, false) { parent = null } }
                        items(tops, key = { it.id }) { g -> PickRow(parent == g.id, g.name, 0, true) { parent = g.id } }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name, parent) }) { Text("Erstellen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
}
