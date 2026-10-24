package com.sueleyman.suelostodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sueleyman.suelostodo.data.ListColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Round check circle in MS-To-Do style. */
@Composable
fun CheckCircle(checked: Boolean, color: Color, size: Dp = 22.dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size + 14.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .then(if (checked) Modifier.background(color) else Modifier.border(2.dp, color, CircleShape)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(size * 0.7f))
        }
    }
}

@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    LazyRow {
        items(ListColors.all.indices.toList()) { i ->
            Box(
                Modifier
                    .padding(4.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(listColor(i))
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                if (i == selected) Icon(Icons.Filled.Check, ListColors.names[i], tint = Color.White)
            }
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String = "",
    confirm: String = "Speichern",
    withColor: Int? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Int?) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var color by remember { mutableIntStateOf(withColor ?: 0) }
    var pickColor by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    if (pickColor) {
        GroupColorDialog(
            name = text.ifBlank { "Liste" }, initial = ListColors.argb(color),
            allowAuto = false,
            onDismiss = { pickColor = false },
            onPick = { c -> pickColor = false; if (c != null) color = c.toInt() }
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.foundation.layout.Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(fr)
                )
                if (withColor != null) {
                    Spacer(Modifier.size(12.dp))
                    // same full colour screen as "Farbe wählen" for groups
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { pickColor = true }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(listColor(color)))
                        Spacer(Modifier.width(12.dp))
                        Text("Farbe wählen …", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        Text(
                            "#%06X".format(ListColors.argb(color) and 0xFFFFFF),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text, if (withColor != null) color else null) }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "Löschen", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
fun MetaChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String?, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
        if (!text.isNullOrEmpty()) {
            Spacer(Modifier.width(3.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = tint)
        }
        Spacer(Modifier.width(8.dp))
    }
}

private val dayFmt = DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN)
private val dayYearFmt = DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", Locale.GERMAN)

fun formatDue(epochDay: Long): String {
    val d = LocalDate.ofEpochDay(epochDay)
    val today = LocalDate.now()
    return when (d) {
        today -> "Heute"
        today.plusDays(1) -> "Morgen"
        today.minusDays(1) -> "Gestern"
        else -> if (d.year == today.year) d.format(dayFmt) else d.format(dayYearFmt)
    }
}

fun formatSize(bytes: Long): String = when {
    bytes < 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(Locale.GERMAN, "%.1f MB", bytes / 1024.0 / 1024.0)
}

/** Radio-button list of designs, opened from the ⋮ menus of the list and detail screens. */
@Composable
fun <T> StyleDialog(
    title: String, options: List<T>, selected: T,
    label: (T) -> String, description: (T) -> String,
    onDismiss: () -> Unit, onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.foundation.layout.Column(
                Modifier.androidx_verticalScroll()
            ) {
                options.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onSelect(o) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(selected = o == selected, onClick = { onSelect(o) })
                        androidx.compose.foundation.layout.Column {
                            Text(label(o), style = MaterialTheme.typography.bodyLarge)
                            Text(description(o), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
}

@Composable
private fun Modifier.androidx_verticalScroll(): Modifier =
    this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))
