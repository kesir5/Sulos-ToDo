package com.sueleyman.suelostodo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

// ===================================================================== colour picker

/** Presets: the list colours plus a few extra tones. */
private val PRESETS = longArrayOf(
    0xFF2564CF, 0xFF0C447C, 0xFF038387, 0xFF3E8A5E, 0xFF639922, 0xFFBA7517,
    0xFFCA5010, 0xFFC4314B, 0xFFBF4C8A, 0xFF7B5EA7, 0xFF534AB7, 0xFF5D5A58,
)

private fun hsv(color: Long): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(color.toInt(), it) }
private fun fromHsv(h: Float, s: Float, v: Float): Long =
    android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)).toLong() and 0xFFFFFFFFL
private fun hex(c: Long) = "#%06X".format(c and 0xFFFFFF)

/** Free colour choice for a group: presets, hue/saturation/brightness sliders or hex code. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupColorDialog(name: String, initial: Long?, onDismiss: () -> Unit, onPick: (Long?) -> Unit) {
    val start = initial ?: PRESETS[0]
    var h by remember { mutableFloatStateOf(hsv(start)[0]) }
    var sat by remember { mutableFloatStateOf(hsv(start)[1]) }
    var v by remember { mutableFloatStateOf(hsv(start)[2]) }
    val current = fromHsv(h, sat, v)
    var hexText by remember { mutableStateOf(hex(start)) }
    fun set(c: Long) { val x = hsv(c); h = x[0]; sat = x[1]; v = x[2]; hexText = hex(c) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Farbe für „$name“") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // preview
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(current)))
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color(current).copy(alpha = 0.16f))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) { Text(name, color = Color(current), fontWeight = FontWeight.SemiBold) }
                }
                Spacer(Modifier.size(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS.forEach { c ->
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(Color(c)).clickable { set(c) },
                            contentAlignment = Alignment.Center
                        ) { if (c == current) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    }
                }
                Spacer(Modifier.size(12.dp))
                // hue track
                Box(
                    Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(
                        Brush.horizontalGradient((0..6).map { Color(fromHsv(it * 60f % 360f + if (it == 6) 359f else 0f, 1f, 1f)) })
                    )
                )
                Slider(value = h, onValueChange = { h = it; hexText = hex(fromHsv(h, sat, v)) }, valueRange = 0f..359f)
                Text("Sättigung", style = MaterialTheme.typography.labelSmall)
                Slider(value = sat, onValueChange = { sat = it; hexText = hex(fromHsv(h, sat, v)) })
                Text("Helligkeit", style = MaterialTheme.typography.labelSmall)
                Slider(value = v, onValueChange = { v = it; hexText = hex(fromHsv(h, sat, v)) }, valueRange = 0.2f..1f)
                OutlinedTextField(
                    value = hexText, singleLine = true, label = { Text("Hex-Code") },
                    onValueChange = { t ->
                        hexText = t
                        val clean = t.trim().removePrefix("#")
                        if (clean.length == 6) clean.toLongOrNull(16)?.let { set(0xFF000000L or it); hexText = t }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(current) }) { Text("Übernehmen") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onPick(null) }) { Text("Automatisch") }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        }
    )
}
