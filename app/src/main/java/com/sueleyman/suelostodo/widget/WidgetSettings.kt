package com.sueleyman.suelostodo.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.sueleyman.suelostodo.SuelosToDoApp
import com.sueleyman.suelostodo.ui.orderedGroups
import kotlinx.coroutines.launch

/** IDs of all placed Sülos-ToDo widgets. */
fun placedWidgetIds(c: Context): IntArray =
    AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c, TaskWidgetReceiver::class.java))

/** Applies a selection and redraws the widget. */
suspend fun applyWidgetSelection(c: Context, appWidgetId: Int, change: (WidgetSel) -> WidgetSel) {
    WidgetPrefs.update(c, appWidgetId, change)
    runCatching {
        val gid = GlanceAppWidgetManager(c).getGlanceIdBy(appWidgetId)
        TaskWidget().update(c, gid)
    }
}

/** Settings section: choose content and layout for every placed widget (works independent of widget taps). */
@Composable
fun WidgetSettingsSection() {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val ids = remember(refresh) { placedWidgetIds(c).toList() }
    var editing by remember { mutableStateOf<Int?>(null) }
    val repo = (c.applicationContext as SuelosToDoApp).repo
    val lists by repo.observeLists().let { f -> remember { f } }.collectAsState(initial = emptyList())
    val groups by repo.observeGroups().let { f -> remember { f } }.collectAsState(initial = emptyList())

    fun label(src: String): String {
        val id = src.substringAfter(':')
        return when (src.substringBefore(':')) {
            "smart" -> mapOf("myday" to "Mein Tag", "important" to "Wichtig", "due" to "Fällig", "planned" to "Geplant")[id] ?: id
            "group" -> "Gruppe " + (groups.firstOrNull { it.id == id }?.name ?: "?")
            else -> lists.firstOrNull { it.id == id }?.name ?: "Aufgaben"
        }
    }

    if (ids.isEmpty()) {
        Text("Noch kein Widget auf dem Startbildschirm platziert.", style = MaterialTheme.typography.bodySmall)
    }
    ids.forEachIndexed { i, id ->
        val sel = WidgetPrefs.get(c, id)
        OutlinedButton(onClick = { editing = id }, modifier = Modifier.fillMaxWidth()) {
            Text("Widget ${i + 1}: ${label(sel.source)} · ${WidgetLayout.of(sel.layout).title.substringAfter("· ")}")
        }
    }
    TextButton(onClick = { refresh++ }) { Text("Liste aktualisieren") }

    editing?.let { id ->
        var src by remember(id) { mutableStateOf(WidgetPrefs.get(c, id).source) }
        var lay by remember(id) { mutableStateOf(WidgetPrefs.get(c, id).layout) }
        val options = buildList {
            add("smart:myday" to "Mein Tag"); add("smart:important" to "Wichtig")
            add("smart:due" to "Fällig"); add("smart:planned" to "Geplant")
            orderedGroups(groups).forEach { (g, d) -> add("group:${g.id}" to ("    ".repeat(d) + "📁 " + g.name)) }
            lists.forEach { add("list:${it.id}" to it.name) }
        }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Widget einrichten") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    Text("Layout", fontWeight = FontWeight.Bold)
                    WidgetLayout.entries.forEach { l ->
                        Row(Modifier.fillMaxWidth().clickable { lay = l.key }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = lay == l.key, onClick = { lay = l.key }); Text(l.title)
                        }
                    }
                    Text("Inhalt", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    options.forEach { (k, name) ->
                        Row(Modifier.fillMaxWidth().clickable { src = k }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = src == k, onClick = { src = k }); Text(name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val s = src; val l = lay
                    editing = null
                    scope.launch { applyWidgetSelection(c, id) { it.copy(source = s, layout = l, picker = false) }; refresh++ }
                }) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Abbrechen") } }
        )
    }
}
