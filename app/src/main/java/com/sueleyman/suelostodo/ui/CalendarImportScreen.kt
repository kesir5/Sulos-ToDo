package com.sueleyman.suelostodo.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.identity.Identity
import com.sueleyman.suelostodo.SuelosToDoApp
import com.sueleyman.suelostodo.data.INBOX_ID
import com.sueleyman.suelostodo.io.CalEvent
import com.sueleyman.suelostodo.io.GoogleCalendar
import com.sueleyman.suelostodo.sync.GoogleAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private sealed interface CalState {
    data object Idle : CalState
    data object Loading : CalState
    data class Error(val msg: String) : CalState
    data class Loaded(val events: List<CalEvent>) : CalState
}

/** Google Calendar → tasks: shows the appointments of the next 2 months, selected ones become tasks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarImportScreen(initialList: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = (context.applicationContext as SuelosToDoApp).repo
    val scope = rememberCoroutineScope()
    val lists by repo.observeLists().collectAsState(initial = emptyList())
    var state by remember { mutableStateOf<CalState>(CalState.Idle) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var imported by remember { mutableStateOf(GoogleCalendar.importedIds(context)) }
    var target by remember { mutableStateOf(initialList ?: INBOX_ID) }
    var remind by remember { mutableStateOf(false) }
    var listMenu by remember { mutableStateOf(false) }

    fun load(token: String) {
        state = CalState.Loading
        scope.launch {
            state = runCatching { CalState.Loaded(GoogleCalendar.upcoming(token)) }
                .getOrElse { CalState.Error(it.message ?: it.javaClass.simpleName) }
        }
    }

    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(res.data) }
            .onSuccess { r -> r.accessToken?.let { load(it) } ?: run { state = CalState.Error("Kein Zugriffstoken erhalten") } }
            .onFailure { state = CalState.Error(GoogleAuth.explain(it, res.resultCode == Activity.RESULT_OK)) }
    }

    /** interactive: shows the Google consent screen when needed */
    fun connect() {
        state = CalState.Loading
        scope.launch {
            runCatching { Identity.getAuthorizationClient(context).authorize(GoogleCalendar.request()).await() }
                .onSuccess { r ->
                    val pi = r.pendingIntent
                    if (r.hasResolution() && pi != null) authLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    else r.accessToken?.let { load(it) } ?: run { state = CalState.Error("Kein Zugriffstoken erhalten") }
                }
                .onFailure { state = CalState.Error(GoogleAuth.explain(it, true)) }
        }
    }

    // already connected? then load directly without any dialog
    LaunchedEffect(Unit) {
        runCatching { GoogleCalendar.silentToken(context) }.getOrNull()?.let { load(it) }
    }

    val events = (state as? CalState.Loaded)?.events.orEmpty()
    val selectable = events.filter { it.id !in imported }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Google Kalender") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    if (state is CalState.Loaded) {
                        TextButton(onClick = {
                            selected = if (selected.size == selectable.size) emptySet() else selectable.mapTo(HashSet()) { it.id }
                        }) { Text(if (selected.size == selectable.size && selectable.isNotEmpty()) "Keine" else "Alle") }
                        IconButton(onClick = { connect() }) { Icon(Icons.Outlined.Refresh, "Neu laden") }
                    }
                }
            )
        },
        bottomBar = {
            if (state is CalState.Loaded) Surface(shadowElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ziel-Liste", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Box {
                            val l = lists.firstOrNull { it.id == target }
                            TextButton(onClick = { listMenu = true }) {
                                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(l?.let { listColor(it.color) } ?: Brand))
                                Spacer(Modifier.width(8.dp))
                                Text(l?.name ?: "Aufgaben", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            DropdownMenu(expanded = listMenu, onDismissRequest = { listMenu = false }) {
                                lists.forEach { li ->
                                    DropdownMenuItem(
                                        text = { Text(li.name) },
                                        leadingIcon = { Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(listColor(li.color))) },
                                        onClick = { target = li.id; listMenu = false })
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Erinnerung zur Startzeit", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = remind, onCheckedChange = { remind = it })
                    }
                    Button(
                        enabled = selected.isNotEmpty(),
                        onClick = {
                            val chosen = events.filter { it.id in selected }
                            scope.launch {
                                val n = repo.importCalendarEvents(target, chosen, remind)
                                GoogleCalendar.markImported(context, chosen.map { it.id })
                                imported = GoogleCalendar.importedIds(context)
                                selected = emptySet()
                                Toast.makeText(context, "$n Aufgabe(n) angelegt", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (selected.isEmpty()) "Termine auswählen" else "${selected.size} Termin(e) als Aufgabe importieren") }
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (val s = state) {
                CalState.Idle, is CalState.Error -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.CalendarMonth, null, tint = Brand, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Termine der nächsten 2 Monate aus deinem Google Kalender anzeigen und ausgewählte als Aufgaben übernehmen.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { connect() }) { Text("Mit Google Kalender verbinden") }
                    if (s is CalState.Error) {
                        Spacer(Modifier.height(16.dp))
                        Text(s.msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("Nur Lesezugriff – die App verändert deinen Kalender nicht. Einmalig nötig: im Google-Cloud-Projekt die " +
                        "„Google Calendar API“ aktivieren und unter „Datenzugriff“ den Bereich calendar.readonly hinzufügen.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                CalState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is CalState.Loaded -> if (s.events.isEmpty()) Text("Keine Termine in den nächsten 2 Monaten.",
                    Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else LazyColumn(Modifier.fillMaxSize()) {
                    val byDay = s.events.groupBy { it.day }
                    byDay.forEach { (day, evs) ->
                        item(key = "d$day") { DayHeader(day) }
                        items(evs, key = { "e" + it.id + it.calendar }) { e ->
                            EventRow(e, e.id in selected, e.id in imported) {
                                selected = if (e.id in selected) selected - e.id else selected + e.id
                            }
                            HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: Long) {
    val d = LocalDate.ofEpochDay(day)
    val today = LocalDate.now()
    val label = when (d) {
        today -> "Heute"
        today.plusDays(1) -> "Morgen"
        else -> d.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN))
    }
    Text(
        label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun EventRow(e: CalEvent, checked: Boolean, done: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !done, onClick = onToggle).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked || done, onCheckedChange = if (done) null else { _ -> onToggle() }, enabled = !done)
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (done) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(e.color)))
                Spacer(Modifier.width(6.dp))
                Text(
                    listOf(e.timeLabel, e.location.takeIf { it.isNotBlank() }, e.calendar).filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (done) Text("importiert", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp))
    }
}
