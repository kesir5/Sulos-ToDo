package com.sueleyman.suelostodo.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.sueleyman.suelostodo.SuelosToDoApp
import com.sueleyman.suelostodo.data.INBOX_ID
import com.sueleyman.suelostodo.ui.SuelosToDoTheme
import com.sueleyman.suelostodo.ui.listColor
import com.sueleyman.suelostodo.ui.orderedGroups
import kotlinx.coroutines.launch

/**
 * Selection screen for a home-screen widget: smart list, group or single list.
 * Opened when the widget is placed and when the widget title is tapped.
 */
class WidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // when placing: cancelling must not leave a broken widget -> default result OK with inbox
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        val repo = (application as SuelosToDoApp).repo
        val lists = repo.observeLists()
        val groups = repo.observeGroups()
        setContent {
            SuelosToDoTheme {
                val scope = rememberCoroutineScope()
                val l by lists.collectAsState(initial = emptyList())
                val g by groups.collectAsState(initial = emptyList())
                var current by remember { mutableStateOf<String?>(null) }
                var layout by remember { mutableStateOf(WidgetLayout.COLOR) }
                LaunchedEffect(Unit) {
                    runCatching {
                        val gid = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
                        val prefs = getAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, gid)
                        current = prefs[KEY_SOURCE] ?: "list:$INBOX_ID"
                        layout = WidgetLayout.of(prefs[KEY_LAYOUT])
                    }
                }
                fun choose(source: String) = scope.launch {
                    runCatching {
                        val gid = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
                        updateAppWidgetState(this@WidgetConfigActivity, gid) { it[KEY_SOURCE] = source }
                        TaskWidget().update(this@WidgetConfigActivity, gid)
                    }
                    finish()
                }
                fun chooseLayout(l: WidgetLayout) = scope.launch {
                    layout = l
                    runCatching {
                        val gid = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
                        updateAppWidgetState(this@WidgetConfigActivity, gid) { it[KEY_LAYOUT] = l.key }
                        TaskWidget().update(this@WidgetConfigActivity, gid)
                    }
                }
                Picker(
                    layout = layout,
                    onLayout = { chooseLayout(it) },
                    current = current,
                    smart = listOf(
                        Triple("smart:myday", "Mein Tag", Icons.Outlined.WbSunny to Color(0xFF3B6EA5)),
                        Triple("smart:important", "Wichtig", Icons.Outlined.StarOutline to Color(0xFFC4314B)),
                        Triple("smart:due", "Fällig", Icons.Outlined.Event to Color(0xFFCA5010)),
                        Triple("smart:planned", "Geplant", Icons.Outlined.CalendarMonth to Color(0xFF038387)),
                    ),
                    groups = orderedGroups(g).map { (grp, depth) ->
                        Triple("group:${grp.id}", grp.name, depth to (grp.color?.let { Color(it) } ?: Color(0xFF5D5A58)))
                    },
                    lists = l.map { Triple("list:${it.id}", it.name, listColor(it.color)) },
                    onPick = { choose(it) },
                    onClose = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Picker(
    layout: WidgetLayout,
    onLayout: (WidgetLayout) -> Unit,
    current: String?,
    smart: List<Triple<String, String, Pair<ImageVector, Color>>>,
    groups: List<Triple<String, String, Pair<Int, Color>>>,
    lists: List<Triple<String, String, Color>>,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Widget: Inhalt wählen") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Schließen") } }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item { Head("Layout") }
            items(WidgetLayout.entries, key = { "layout-" + it.key }) { l ->
                Row(
                    Modifier.fillMaxWidth().clickable { onLayout(l) }.padding(start = 8.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = layout == l, onClick = { onLayout(l) })
                    androidx.compose.foundation.layout.Column(Modifier.padding(vertical = 6.dp)) {
                        Text(l.title, style = MaterialTheme.typography.bodyLarge)
                        Text(l.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { Head("Inhalt – Tippen übernimmt und schließt") }
            item { Head("Smart-Listen") }
            items(smart, key = { it.first }) { (key, name, iv) ->
                PickRow(current == key, name, iv.first, iv.second, 0) { onPick(key) }
            }
            if (groups.isNotEmpty()) {
                item { Head("Gruppen – zeigt alle Listen der Gruppe") }
                items(groups, key = { it.first }) { (key, name, dc) ->
                    PickRow(current == key, name, Icons.Outlined.Folder, dc.second, dc.first) { onPick(key) }
                }
            }
            item { Head("Einzelne Liste") }
            items(lists, key = { it.first }) { (key, name, c) ->
                PickRow(current == key, name, Icons.AutoMirrored.Outlined.List, c, 0) { onPick(key) }
            }
        }
    }
}

@Composable
private fun Head(t: String) {
    Text(
        t.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 16.dp, vertical = 7.dp)
    )
}

@Composable
private fun PickRow(selected: Boolean, name: String, icon: ImageVector, color: Color, depth: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = (8 + depth * 24).dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Box(Modifier.size(28.dp).background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 12.dp))
    }
}
