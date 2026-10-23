package com.sueleyman.suelostodo.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sueleyman.suelostodo.SuelosToDoApp
import com.sueleyman.suelostodo.data.TaskList
import com.sueleyman.suelostodo.io.Backup
import com.sueleyman.suelostodo.io.FileFormat
import com.sueleyman.suelostodo.io.FormatDetector
import com.sueleyman.suelostodo.io.ICal
import com.sueleyman.suelostodo.io.ImportItem
import com.sueleyman.suelostodo.io.MsAuth
import com.sueleyman.suelostodo.io.MsList
import com.sueleyman.suelostodo.io.MsTodo
import com.sueleyman.suelostodo.io.Parsed
import com.sueleyman.suelostodo.io.TodoistCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class TransferState(
    val busy: Boolean = false,
    val progress: String? = null,
    val message: String? = null,
    val error: String? = null,
    val msLists: List<MsList>? = null,
    val pending: Parsed? = null,
    val pendingName: String = "",
)

/** Process-wide transfer state, so a running import survives leaving the screen. */
private object TransferBus { val state = MutableStateFlow(TransferState()) }

private fun friendlyError(e: Throwable): String = when (e) {
    is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException,
    is java.net.SocketException, is javax.net.ssl.SSLException ->
        "Keine stabile Verbindung zu Microsoft (${e.javaClass.simpleName}). Bereits fertig importierte Listen " +
            "sind gespeichert – starte den Import einfach noch einmal, bereits importierte Einträge werden nicht verdoppelt. " +
            "Tipp: WLAN nutzen und unter Einstellungen → Netzwerk „Privates DNS“ bzw. VPN/Werbeblocker prüfen."
    else -> e.message ?: e.javaClass.simpleName
}

class TransferViewModel(val preselectedList: String?) : ViewModel() {
    private val repo get() = SuelosToDoApp.instance.repo
    private val app get() = SuelosToDoApp.instance
    private val _s = TransferBus.state
    val state: StateFlow<TransferState> = _s
    val lists = repo.observeLists()

    private fun run(block: suspend () -> String) {
        if (_s.value.busy) return
        _s.value = _s.value.copy(busy = true, message = null, error = null, progress = null)
        // App scope: the transfer continues when the screen is left or rotated.
        app.appScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { block() } }
            _s.update {
                it.copy(
                    busy = false, progress = null,
                    message = r.getOrNull(),
                    error = r.exceptionOrNull()?.let { e -> friendlyError(e) }
                )
            }
        }
    }

    private fun progress(p: String) = _s.update { it.copy(progress = p) }
    fun clearPending() = _s.update { it.copy(pending = null) }
    fun dismissMsLists() = _s.update { it.copy(msLists = null) }

    // ---------- files ----------
    fun exportBackup(c: Context, uri: Uri) = run {
        val snap = repo.snapshot()
        c.contentResolver.openOutputStream(uri)!!.use { Backup.write(it, snap) { id -> repo.attachmentFile(id) } }
        "Backup gespeichert: ${snap.lists.count { !it.deleted }} Listen, ${snap.tasks.count { !it.deleted }} Aufgaben."
    }

    fun exportTodoist(c: Context, uri: Uri, listId: String) = run {
        val l = repo.exportTree(setOf(listId)).first()
        c.contentResolver.openOutputStream(uri)!!.use { it.write(TodoistCsv.write(l).toByteArray(Charsets.UTF_8)) }
        "\"${l.list.name}\" als Todoist-CSV gespeichert (nur offene Aufgaben – Todoist importiert keine erledigten)."
    }

    fun exportIcs(c: Context, uri: Uri, listIds: Set<String>) = run {
        val l = repo.exportTree(listIds)
        c.contentResolver.openOutputStream(uri)!!.use { it.write(ICal.write(l).toByteArray(Charsets.UTF_8)) }
        "${l.size} Liste(n) als iCalendar gespeichert."
    }

    fun readImport(c: Context, uri: Uri) = run {
        val name = displayName(c, uri)
        val bytes = c.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val parsed = FormatDetector.parse(bytes, name)
        _s.update { it.copy(pending = parsed, pendingName = name) }
        "Datei gelesen – bitte Import bestätigen."
    }

    fun confirmImport(intoList: String?) {
        val p = _s.value.pending ?: return
        _s.update { it.copy(pending = null) }
        run {
            when (p) {
                is Parsed.Backup -> repo.importBackup(p.snapshot, p.files).describe()
                is Parsed.Lists -> repo.importLists(p.lists, intoList).describe()
            }
        }
    }

    // ---------- Microsoft ----------
    fun msLogin(c: Context) = runCatching { MsAuth.startLogin(c) }
        .onFailure { e -> _s.update { it.copy(error = e.message) } }

    fun msLoadLists() = run {
        val l = MsTodo.lists(app)
        _s.update { it.copy(msLists = l) }
        "${l.size} Listen in Microsoft To Do gefunden."
    }

    fun msImport(selected: List<MsList>, withAttachments: Boolean) {
        _s.update { it.copy(msLists = null) }
        run { MsTodo.import(app, selected, withAttachments, ::progress).describe() }
    }

    fun msExport(listIds: Set<String>) = run {
        MsTodo.export(app, repo.exportTree(listIds), ::progress)
    }

    private fun displayName(c: Context, uri: Uri): String =
        c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Import"
}

fun Parsed.summary(): String = when (this) {
    is Parsed.Backup -> "Sülos-ToDo-Backup: ${snapshot.lists.count { !it.deleted }} Listen, " +
        "${snapshot.tasks.count { !it.deleted }} Aufgaben, ${files.size} Anhänge"
    is Parsed.Lists -> {
        fun count(i: List<ImportItem>): Int = i.sumOf { 1 + count(it.children) }
        "${format.label}: ${lists.size} Liste(n), ${lists.sumOf { count(it.items) }} Aufgaben\n" +
            lists.take(6).joinToString("\n") { "• ${it.name}" } + if (lists.size > 6) "\n…" else ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(preselectedList: String?, onBack: () -> Unit) {
    val vm: TransferViewModel = viewModel(key = "transfer-$preselectedList") { TransferViewModel(preselectedList) }
    val state by vm.state.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle(initialValue = emptyList())
    val ms by MsAuth.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { MsAuth.refreshState(context) }
    // keep the display on during long transfers – otherwise Android cuts the network in standby
    val view = LocalView.current
    DisposableEffect(state.busy) {
        view.keepScreenOn = state.busy
        onDispose { view.keepScreenOn = false }
    }

    var todoistList by remember { mutableStateOf<String?>(null) }
    var pickFor by remember { mutableStateOf<String?>(null) } // "todoist" | "ics" | "msExport"
    var icsLists by remember { mutableStateOf<Set<String>>(emptySet()) }
    var clientId by remember { mutableStateOf(MsAuth.clientId(context)) }

    val today = LocalDate.now().toString()
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FileFormat.BACKUP.mime)) { it?.let { u -> vm.exportBackup(context, u) } }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FileFormat.TODOIST.mime)) { u ->
        val l = todoistList; if (u != null && l != null) vm.exportTodoist(context, u, l)
    }
    val icsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FileFormat.ICAL.mime)) { u ->
        if (u != null) vm.exportIcs(context, u, icsLists)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> vm.readImport(context, u) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import & Export") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } }
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(state, ms.error)

            // ------------------------------------------------ Microsoft To Do
            Section("Microsoft To Do") {
                if (!ms.signedIn) {
                    Text(
                        "Melde dich mit deinem Microsoft-Konto an, um Listen, Aufgaben, Schritte und Anhänge direkt " +
                            "zu übertragen. Microsoft To Do kennt nur eine Unterebene (Schritte): Ebene 3 wird beim Export als „↳ Schritt“ abgelegt.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = clientId, onValueChange = { clientId = it; MsAuth.setClientId(context, it) },
                        label = { Text("Azure Client-ID (Anwendungs-ID)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(enabled = !state.busy, onClick = { vm.msLogin(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Mit Microsoft anmelden")
                    }
                } else {
                    Text("Angemeldet als ${ms.account ?: "Microsoft-Konto"}", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !state.busy, onClick = { vm.msLoadLists() }) { Text("Importieren") }
                        OutlinedButton(enabled = !state.busy, onClick = { pickFor = "msExport" }) { Text("Exportieren") }
                    }
                    TextButton(onClick = { MsAuth.signOut(context) }) { Text("Abmelden") }
                }
            }

            // ------------------------------------------------ Export
            Section("In Datei exportieren") {
                FileRow(Icons.Outlined.Archive, "Sülos-ToDo-Backup", "Alles inkl. Anhänge (ZIP). Zum Umzug oder als Sicherung.") {
                    backupLauncher.launch("SuelosToDo-Backup-$today.zip")
                }
                HorizontalDivider()
                FileRow(Icons.Outlined.TableChart, "Todoist (CSV)", "Eine Liste als Todoist-Projektvorlage, Ebenen bleiben erhalten.") {
                    if (preselectedList != null && lists.any { it.id == preselectedList }) {
                        todoistList = preselectedList
                        csvLauncher.launch("${lists.first { it.id == preselectedList }.name}.csv")
                    } else pickFor = "todoist"
                }
                HorizontalDivider()
                FileRow(Icons.Outlined.CalendarMonth, "iCalendar (.ics)", "Für Tasks.org, Thunderbird, Nextcloud, Apple Erinnerungen u. a.") {
                    pickFor = "ics"
                }
            }

            // ------------------------------------------------ Import
            Section("Aus Datei importieren") {
                Text(
                    "Unterstützt: Sülos-ToDo-Backup (.zip/.json), Todoist-CSV, iCalendar (.ics), Google Tasks aus Google Takeout " +
                        "(Tasks.json oder das Takeout-ZIP). Das Format wird automatisch erkannt.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(enabled = !state.busy, onClick = { openLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("Datei auswählen")
                }
            }
        }
    }

    // ------------------------------------------------ dialogs
    when (pickFor) {
        "todoist" -> ListPickDialog("Liste für Todoist-Export", lists, multi = false, initial = emptySet(),
            onDismiss = { pickFor = null }) { sel ->
            pickFor = null
            sel.firstOrNull()?.let { id -> todoistList = id; csvLauncher.launch("${lists.first { it.id == id }.name}.csv") }
        }
        "ics" -> ListPickDialog("Listen für iCalendar", lists, multi = true,
            initial = preselectedList?.let { setOf(it) } ?: lists.map { it.id }.toSet(),
            onDismiss = { pickFor = null }) { sel ->
            pickFor = null
            if (sel.isNotEmpty()) { icsLists = sel; icsLauncher.launch("SuelosToDo-$today.ics") }
        }
        "msExport" -> ListPickDialog("Nach Microsoft To Do exportieren", lists, multi = true,
            initial = preselectedList?.let { setOf(it) } ?: emptySet(),
            onDismiss = { pickFor = null }) { sel ->
            pickFor = null
            if (sel.isNotEmpty()) vm.msExport(sel)
        }
    }

    state.msLists?.let { msl ->
        MsImportDialog(msl, onDismiss = { vm.dismissMsLists() }) { sel, att -> vm.msImport(sel, att) }
    }

    state.pending?.let { p ->
        var target by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { vm.clearPending() },
            title = { Text("Importieren?") },
            text = {
                Column {
                    Text(p.summary())
                    if (p is Parsed.Lists && preselectedList != null && lists.any { it.id == preselectedList }) {
                        Spacer(Modifier.size(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { target = null }) {
                            RadioButton(selected = target == null, onClick = { target = null }); Text("Als neue Liste(n)")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { target = preselectedList }) {
                            RadioButton(selected = target == preselectedList, onClick = { target = preselectedList })
                            Text("In \"${lists.first { it.id == preselectedList }.name}\"")
                        }
                    }
                    if (p is Parsed.Backup) {
                        Spacer(Modifier.size(8.dp))
                        Text("Vorhandene Einträge werden zusammengeführt – die jeweils neuere Version bleibt.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.confirmImport(target) }) { Text("Importieren") } },
            dismissButton = { TextButton(onClick = { vm.clearPending() }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun StatusCard(state: TransferState, msError: String?) {
    val err = state.error ?: msError
    if (!state.busy && state.message == null && err == null) return
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (err != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                state.busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                err != null -> Icon(Icons.Outlined.ErrorOutline, null)
                else -> Icon(Icons.Outlined.CheckCircle, null)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                when {
                    state.busy -> state.progress ?: "Bitte warten …"
                    err != null -> err
                    else -> state.message ?: ""
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun FileRow(icon: ImageVector, title: String, desc: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ListPickDialog(
    title: String,
    lists: List<TaskList>,
    multi: Boolean,
    initial: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val sel = remember { mutableStateListOf<String>().also { it.addAll(initial) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(lists, key = { it.id }) { l ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (multi) { if (l.id in sel) sel.remove(l.id) else sel.add(l.id) }
                            else { sel.clear(); sel.add(l.id) }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (multi) Checkbox(checked = l.id in sel, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                        else RadioButton(selected = l.id in sel, onClick = null, modifier = Modifier.padding(12.dp))
                        Text(l.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = sel.isNotEmpty(), onClick = { onConfirm(sel.toSet()) }) { Text("Weiter") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun MsImportDialog(lists: List<MsList>, onDismiss: () -> Unit, onConfirm: (List<MsList>, Boolean) -> Unit) {
    val sel = remember { mutableStateListOf<String>().also { s -> s.addAll(lists.map { it.id }) } }
    var att by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aus Microsoft To Do importieren") },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(lists, key = { it.id }) { l ->
                        Row(
                            Modifier.fillMaxWidth().clickable { if (l.id in sel) sel.remove(l.id) else sel.add(l.id) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = l.id in sel, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                            Text(l.name + if (l.wellKnown == "flaggedEmails") "  (markierte E-Mails)" else "")
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Anhänge mit übernehmen", Modifier.weight(1f))
                    Switch(checked = att, onCheckedChange = { att = it })
                }
                Text(
                    "Erneutes Importieren aktualisiert bereits importierte Einträge statt sie zu verdoppeln.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(enabled = sel.isNotEmpty(), onClick = { onConfirm(lists.filter { it.id in sel }, att) }) { Text("Importieren") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
