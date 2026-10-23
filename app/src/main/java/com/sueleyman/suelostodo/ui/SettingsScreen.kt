package com.sueleyman.suelostodo.ui

import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.Identity
import com.sueleyman.suelostodo.sync.GoogleAuth
import com.sueleyman.suelostodo.sync.SyncManager
import com.sueleyman.suelostodo.sync.SyncPrefs
import com.sueleyman.suelostodo.sync.SyncScheduler
import com.sueleyman.suelostodo.sync.SyncState
import com.sueleyman.suelostodo.sync.SyncStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onTransfer: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by SyncState.status.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val connected = remember(refresh, status) { SyncPrefs.isConnected(context) }
    val account = remember(refresh, status) { SyncPrefs.account(context) }
    val lastSync = remember(refresh, status) { SyncPrefs.lastSync(context) }
    val lastError = remember(refresh, status) { SyncPrefs.lastError(context) }
    var auto by remember { mutableStateOf(SyncPrefs.autoSync(context)) }
    var connectError by remember { mutableStateOf<String?>(null) }

    fun onAuthorized() {
        SyncPrefs.setConnected(context, true)
        SyncPrefs.setAccount(context, null)
        SyncPrefs.setLastError(context, null)
        SyncScheduler.configurePeriodic(context)
        refresh++
        scope.launch { SyncManager.sync(context); refresh++ }
    }

    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(res.data) }
                .onSuccess { if (it.accessToken != null) onAuthorized() else connectError = "Kein Zugriffstoken erhalten" }
                .onFailure { connectError = it.message }
        } else connectError = "Anmeldung abgebrochen"
    }

    fun connect() {
        connectError = null
        scope.launch {
            runCatching { Identity.getAuthorizationClient(context).authorize(GoogleAuth.request()).await() }
                .onSuccess { r ->
                    val pi = r.pendingIntent
                    if (r.hasResolution() && pi != null) {
                        authLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    } else onAuthorized()
                }
                .onFailure { connectError = "Verbindung fehlgeschlagen: ${it.message}" }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } }
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Design der Startseite", style = MaterialTheme.typography.titleMedium)
            val hStyle by UiPrefs.homeStyle.collectAsState()
            HomeStyle.entries.forEach { st ->
                Row(
                    Modifier.fillMaxWidth().clickable { UiPrefs.setHomeStyle(context, st) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = hStyle == st, onClick = { UiPrefs.setHomeStyle(context, st) })
                    Column {
                        Text(st.title, style = MaterialTheme.typography.bodyLarge)
                        Text(st.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val uName by UiPrefs.userName.collectAsState()
            androidx.compose.material3.OutlinedTextField(
                value = uName, onValueChange = { UiPrefs.setUserName(context, it) }, singleLine = true,
                label = { Text("Dein Name (für die Begrüßung)") }, modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Design der Aufgabenübersicht", style = MaterialTheme.typography.titleMedium)
            val style by UiPrefs.style.collectAsState()
            TaskStyle.entries.forEach { st ->
                Row(
                    Modifier.fillMaxWidth().clickable { UiPrefs.setStyle(context, st) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = style == st, onClick = { UiPrefs.setStyle(context, st) })
                    Column {
                        Text(st.title, style = MaterialTheme.typography.bodyLarge)
                        Text(st.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Design der Aufgabendetails", style = MaterialTheme.typography.titleMedium)
            val dStyle by UiPrefs.detailStyle.collectAsState()
            DetailStyle.entries.forEach { st ->
                Row(
                    Modifier.fillMaxWidth().clickable { UiPrefs.setDetailStyle(context, st) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = dStyle == st, onClick = { UiPrefs.setDetailStyle(context, st) })
                    Column {
                        Text(st.title, style = MaterialTheme.typography.bodyLarge)
                        Text(st.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                "Tipp in der Übersicht: linke Hälfte einer Aufgabe klappt die Unteraufgaben auf/zu, rechte Hälfte öffnet die Details.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Import & Export", style = MaterialTheme.typography.titleMedium)
            Text(
                "Microsoft To Do (direkt per Konto), Todoist, iCalendar, Google Tasks und Sülos-ToDo-Backup.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onTransfer, modifier = Modifier.fillMaxWidth()) { Text("Import & Export öffnen") }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Google-Drive-Synchronisation", style = MaterialTheme.typography.titleMedium)
            Text(
                "Listen, Aufgaben und Anhänge werden lokal gespeichert. Optional werden sie in einem versteckten " +
                    "App-Ordner deines Google Drive gesichert und zwischen Geräten abgeglichen (neueste Änderung gewinnt).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (connected) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff, null,
                    tint = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (connected) "Verbunden" else "Nicht verbunden", style = MaterialTheme.typography.bodyLarge)
                    if (connected && account != null) Text(account, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (!connected) {
                Button(onClick = { connect() }, modifier = Modifier.fillMaxWidth()) { Text("Mit Google Drive verbinden") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Automatisch synchronisieren", modifier = Modifier.weight(1f))
                    Switch(checked = auto, onCheckedChange = {
                        auto = it
                        SyncPrefs.setAutoSync(context, it)
                        SyncScheduler.configurePeriodic(context)
                    })
                }
                Text(
                    "Stündlich im Hintergrund sowie kurz nach jeder Änderung.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = status !is SyncStatus.Running,
                        onClick = { scope.launch { SyncManager.sync(context); refresh++ } }
                    ) {
                        if (status is SyncStatus.Running) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.Sync, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Jetzt synchronisieren")
                    }
                }
                Text(
                    if (lastSync > 0) "Zuletzt synchronisiert: " +
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(lastSync))
                    else "Noch nicht synchronisiert",
                    style = MaterialTheme.typography.bodySmall
                )
                lastError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    if (it.contains("Anmeldung")) OutlinedButton(onClick = { connect() }) { Text("Neu verbinden") }
                }
                OutlinedButton(onClick = {
                    SyncPrefs.setConnected(context, false)
                    SyncPrefs.setAccount(context, null)
                    SyncScheduler.configurePeriodic(context)
                    refresh++
                }) { Text("Verbindung trennen") }
            }
            connectError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Erinnerungen", style = MaterialTheme.typography.titleMedium)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val am = context.getSystemService(AlarmManager::class.java)
                var exact by remember { mutableStateOf(am.canScheduleExactAlarms()) }
                LaunchedEffect(refresh) { exact = am.canScheduleExactAlarms() }
                Text(
                    if (exact) "Exakte Erinnerungen sind erlaubt."
                    else "Exakte Erinnerungen sind nicht erlaubt – Erinnerungen können sich um einige Minuten verzögern.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (!exact) OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))
                    )
                }) { Text("Exakte Erinnerungen erlauben") }
            } else {
                Text("Erinnerungen sind aktiv.", style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Widget", style = MaterialTheme.typography.titleMedium)
            Text(
                "Lange auf den Startbildschirm drücken → Widgets → Sülos ToDo. Tippe auf den Listennamen im Widget, " +
                    "um zur nächsten Liste zu wechseln; der Kreis hakt eine Aufgabe ab.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
