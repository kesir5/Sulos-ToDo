package com.sueleyman.suelostodo.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sueleyman.suelostodo.data.Attachment
import com.sueleyman.suelostodo.data.MAX_DEPTH
import com.sueleyman.suelostodo.data.Task
import kotlinx.coroutines.delay
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the three detail layouts need. */
class DetailCtx(
    val task: Task,
    val state: DetailState,
    val accent: Color,
    val title: String,
    val onTitle: (String) -> Unit,
    val note: String,
    val onNote: (String) -> Unit,
    val vm: TaskDetailViewModel,
    val openTask: (String) -> Unit,
    val pickDue: () -> Unit,
    val pickReminder: () -> Unit,
    val addImage: () -> Unit,
    val addFile: () -> Unit,
    val openAttachment: (Attachment) -> Unit,
    val removeAttachment: (Attachment) -> Unit,
    val askDelete: () -> Unit,
    val askMove: () -> Unit,
    val onBack: () -> Unit,
) {
    val today: Long = LocalDate.now().toEpochDay()
    val inMyDay get() = task.myDay == today
    val dueLabel: String? get() = task.dueDate?.let { formatDue(it) }
    val reminderLabel: String? get() = task.reminderAt?.let {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm, EEE d. MMM", Locale.GERMAN))
    }
    val overdue get() = task.dueDate != null && task.dueDate < today && !task.done
    fun file(a: Attachment): File = vm.attachmentFile(a.id)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(taskId: String, onBack: () -> Unit, onOpenTask: (String) -> Unit) {
    val vm: TaskDetailViewModel = viewModel(key = "task-$taskId") { TaskDetailViewModel(taskId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val style by UiPrefs.detailStyle.collectAsState()
    val context = LocalContext.current
    val task = state.task
    val accent = state.list?.let { listColor(it.color) } ?: Brand

    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(task) {
        if (task != null) loaded = true
        if (loaded && (task == null || task.deleted)) onBack()
    }
    if (task == null) { Box(Modifier.fillMaxSize()); return }

    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var note by rememberSaveable(task.id) { mutableStateOf(task.note) }
    LaunchedEffect(title) { delay(500); if (title != task.title) vm.setTitle(title) }
    LaunchedEffect(note) { delay(700); if (note != task.note) vm.setNote(note) }

    val today = LocalDate.now().toEpochDay()
    var dueDialog by remember { mutableStateOf(false) }
    var reminderDate by remember { mutableStateOf(false) }
    var reminderTime by remember { mutableStateOf<LocalDate?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var moveDialog by remember { mutableStateOf(false) }
    var removeAtt by remember { mutableStateOf<Attachment?>(null) }

    val toastFail = { ok: Boolean -> if (!ok) Toast.makeText(context, "Anhang konnte nicht gespeichert werden", Toast.LENGTH_SHORT).show() }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.addAttachment(it, toastFail) }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.addAttachment(it, toastFail) }
    }

    val ctx = DetailCtx(
        task = task, state = state, accent = accent,
        title = title, onTitle = { title = it.replace("\n", "") },
        note = note, onNote = { note = it },
        vm = vm, openTask = onOpenTask,
        pickDue = { dueDialog = true },
        pickReminder = { reminderDate = true },
        addImage = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        addFile = { pickFile.launch(arrayOf("*/*")) },
        openAttachment = { a ->
            val f = vm.attachmentFile(a.id)
            if (!f.exists()) {
                Toast.makeText(context, "Datei noch nicht synchronisiert", Toast.LENGTH_SHORT).show()
            } else {
                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
                val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, a.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try { context.startActivity(Intent.createChooser(i, a.fileName)) }
                catch (_: ActivityNotFoundException) { Toast.makeText(context, "Keine App zum Öffnen gefunden", Toast.LENGTH_SHORT).show() }
            }
        },
        removeAttachment = { removeAtt = it },
        askDelete = { confirmDelete = true },
        askMove = { moveDialog = true },
        onBack = onBack,
    )

    when (style) {
        DetailStyle.HERO -> HeroDetail(ctx)
        DetailStyle.TILES -> TilesDetail(ctx)
        DetailStyle.DOCUMENT -> DocumentDetail(ctx)
        DetailStyle.ACTIONBAR -> ActionBarDetail(ctx)
        DetailStyle.SHEET -> SheetDetail(ctx)
    }

    // ---------- dialogs ----------
    if (dueDialog) {
        val ds = rememberDatePickerState(initialSelectedDateMillis = (task.dueDate ?: today) * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { dueDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    ds.selectedDateMillis?.let { vm.setDue(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) }
                    dueDialog = false
                }) { Text("Speichern") }
            },
            dismissButton = {
                Row {
                    if (task.dueDate != null) TextButton(onClick = { vm.setDue(null); dueDialog = false }) { Text("Entfernen") }
                    TextButton(onClick = { dueDialog = false }) { Text("Abbrechen") }
                }
            }
        ) { DatePicker(ds) }
    }
    if (reminderDate) {
        val init = task.reminderAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() } ?: today
        val ds = rememberDatePickerState(initialSelectedDateMillis = init * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { reminderDate = false },
            confirmButton = {
                TextButton(onClick = {
                    reminderTime = ds.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    reminderDate = false
                }) { Text("Weiter") }
            },
            dismissButton = {
                Row {
                    if (task.reminderAt != null) TextButton(onClick = { vm.setReminder(null); reminderDate = false }) { Text("Entfernen") }
                    TextButton(onClick = { reminderDate = false }) { Text("Abbrechen") }
                }
            }
        ) { DatePicker(ds) }
    }
    reminderTime?.let { date ->
        val ts = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { reminderTime = null },
            title = { Text("Uhrzeit") },
            text = { TimePicker(ts) },
            confirmButton = {
                TextButton(onClick = {
                    vm.setReminder(date.atTime(ts.hour, ts.minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    reminderTime = null
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { reminderTime = null }) { Text("Abbrechen") } }
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Aufgabe löschen?",
            text = "\"${task.title}\" wird mit allen Unteraufgaben und Anhängen gelöscht.",
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; vm.deleteTask(); onBack() }
        )
    }
    if (moveDialog) MoveDialog(state.lists, onDismiss = { moveDialog = false }) { moveDialog = false; vm.move(it) }
    removeAtt?.let { a ->
        ConfirmDialog("Anhang entfernen?", a.fileName, onDismiss = { removeAtt = null }) { removeAtt = null; vm.deleteAttachment(a.id) }
    }
}

// =====================================================================================
// 1 · HERO
// =====================================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HeroDetail(c: DetailCtx) {
    val t = c.task
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = c.accent, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White, actionIconContentColor = Color.White
                ),
                title = { Breadcrumb(c, Color.White) },
                navigationIcon = { IconButton(onClick = c.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = { DetailActions(c) }
            )
        },
        bottomBar = { CreatedBar(c) }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState())) {
            // colored hero
            Column(Modifier.fillMaxWidth().background(c.accent).padding(start = 8.dp, end = 8.dp, bottom = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CheckCircle(t.done, Color.White, 24.dp) { c.vm.toggleDone() }
                    TitleField(c, MaterialTheme.typography.headlineSmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold), Modifier.weight(1f))
                    IconButton(onClick = { c.vm.toggleImportant() }) {
                        Icon(if (t.important) Icons.Filled.Star else Icons.Outlined.StarOutline, "Wichtig", tint = Color.White)
                    }
                }
                FlowRow(
                    Modifier.padding(start = 12.dp, top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HeroChip(Icons.Outlined.WbSunny, if (c.inMyDay) "Mein Tag" else "Zu Mein Tag", c.inMyDay, c.accent) { c.vm.toggleMyDay() }
                    HeroChip(Icons.Outlined.CalendarToday, c.dueLabel ?: "Fällig", c.dueLabel != null, c.accent, c.pickDue)
                    HeroChip(Icons.Outlined.NotificationsNone, c.reminderLabel ?: "Erinnern", c.reminderLabel != null, c.accent, c.pickReminder)
                }
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Unteraufgaben", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (c.state.children.isNotEmpty()) Text(
                            "${c.state.doneChildren} von ${c.state.children.size}",
                            style = MaterialTheme.typography.labelMedium, color = c.accent
                        )
                    }
                    if (c.state.children.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { c.state.doneChildren.toFloat() / c.state.children.size },
                            color = c.accent, trackColor = c.accent.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    SubtaskTree(c, guide = false)
                    AddSubtaskField(c, Modifier.padding(top = 4.dp))
                }
                HeroCard {
                    Text("Notiz", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    NoteField(c, Modifier.heightIn(min = 80.dp))
                }
                HeroCard {
                    Text("Anhänge", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    AttachmentGrid(c)
                }
            }
        }
    }
}

@Composable
private fun HeroChip(icon: ImageVector, text: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    val bg = if (active) Color.White else Color.Transparent
    val fg = if (active) accent else Color.White
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .then(if (active) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp)))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun HeroCard(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

// =====================================================================================
// 2 · TILES
// =====================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TilesDetail(c: DetailCtx) {
    val t = c.task
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {},
                navigationIcon = { IconButton(onClick = c.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = { DetailActions(c, withDelete = true) }
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Breadcrumb(c, c.accent)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CheckCircle(t.done, c.accent, 24.dp) { c.vm.toggleDone() }
                    TitleField(c, MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold), Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(Icons.Outlined.CalendarToday, c.dueLabel ?: "–", "Fällig", c.dueLabel != null,
                    if (c.overdue) MaterialTheme.colorScheme.error else c.accent, Modifier.weight(1f), c.pickDue)
                Tile(Icons.Outlined.NotificationsNone, c.reminderLabel ?: "–", "Erinnerung", c.reminderLabel != null,
                    c.accent, Modifier.weight(1f), c.pickReminder)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(Icons.Outlined.WbSunny, if (c.inMyDay) "Heute" else "Nein", "Mein Tag", c.inMyDay, c.accent, Modifier.weight(1f)) { c.vm.toggleMyDay() }
                Tile(if (t.important) Icons.Filled.Star else Icons.Outlined.StarOutline, if (t.important) "Ja" else "Nein", "Wichtig",
                    t.important, c.accent, Modifier.weight(1f)) { c.vm.toggleImportant() }
            }
            Surface(
                shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val n = c.state.children.size
                        ProgressRing(if (n == 0) 0f else c.state.doneChildren.toFloat() / n, c.accent, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Unteraufgaben", style = MaterialTheme.typography.titleSmall)
                            Text(if (n == 0) "Noch keine" else "${c.state.doneChildren} von $n erledigt",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    SubtaskTree(c, guide = true)
                    AddSubtaskField(c, Modifier.padding(top = 4.dp))
                }
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.StickyNote2, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    NoteField(c, Modifier.weight(1f).heightIn(min = 72.dp), MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
            Text("Anhänge", style = MaterialTheme.typography.titleSmall)
            AttachmentStrip(c)
            CreatedText(c)
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun Tile(
    icon: ImageVector, value: String, label: String, active: Boolean, accent: Color,
    modifier: Modifier, onClick: () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (active) accent.copy(alpha = 0.13f).compositeOver(surface) else surface,
        border = if (active) null else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, null, tint = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (active) accent else MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProgressRing(frac: Float, color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val sw = 4.dp.toPx()
        val d = this.size.minDimension - sw
        val tl = Offset(sw / 2, sw / 2)
        drawArc(color.copy(alpha = 0.15f), 0f, 360f, false, tl, Size(d, d), style = Stroke(sw))
        drawArc(color, -90f, 360f * frac, false, tl, Size(d, d), style = Stroke(sw))
    }
}

// =====================================================================================
// 3 · DOCUMENT
// =====================================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DocumentDetail(c: DetailCtx) {
    val t = c.task
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {},
                navigationIcon = { IconButton(onClick = c.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    IconButton(onClick = { c.vm.toggleImportant() }) {
                        Icon(if (t.important) Icons.Filled.Star else Icons.Outlined.StarOutline, "Wichtig",
                            tint = if (t.important) c.accent else LocalContentColorOrDefault())
                    }
                    DetailActions(c, withDelete = true)
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        ) {
            Breadcrumb(c, muted)
            Spacer(Modifier.height(6.dp))
            TitleField(
                c, MaterialTheme.typography.headlineMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null
                ), Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            PropRow(Icons.Outlined.CheckCircle, "Status") {
                val col = if (t.done) MaterialTheme.colorScheme.primary else c.accent
                Text(
                    if (t.done) "Erledigt" else "Offen", color = col, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(col.copy(alpha = 0.12f))
                        .clickable { c.vm.toggleDone() }.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            PropRow(Icons.Outlined.CalendarToday, "Fällig", onClick = c.pickDue) {
                PropValue(c.dueLabel, if (c.overdue) MaterialTheme.colorScheme.error else null)
            }
            PropRow(Icons.Outlined.NotificationsNone, "Erinnerung", onClick = c.pickReminder) { PropValue(c.reminderLabel) }
            PropRow(Icons.Outlined.WbSunny, "Mein Tag", onClick = { c.vm.toggleMyDay() }) { PropValue(if (c.inMyDay) "Heute" else null) }
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Unteraufgaben", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            SubtaskTree(c, guide = true)
            AddSubtaskField(c, Modifier.padding(top = 2.dp))
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
            NoteField(c, Modifier.fillMaxWidth().heightIn(min = 160.dp), placeholder = "Notiz schreiben …", big = true)
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
            PropRow(Icons.Outlined.AttachFile, "Anhänge") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    c.state.attachments.forEach { a -> AttachmentChip(c, a) }
                    AddAttachmentButton(c) { open ->
                        Text("+ Hinzufügen", color = muted, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = open).padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
            }
            CreatedText(c)
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun LocalContentColorOrDefault(): Color = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun PropRow(icon: ImageVector, label: String, onClick: (() -> Unit)? = null, value: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Row(Modifier.width(124.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Box(Modifier.weight(1f)) { value() }
    }
}

@Composable
private fun PropValue(text: String?, color: Color? = null) {
    Text(
        text ?: "Leer",
        color = color ?: if (text == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyMedium
    )
}

// =====================================================================================
// shared parts
// =====================================================================================

@Composable
private fun Breadcrumb(c: DetailCtx, color: Color) {
    val parts = listOfNotNull(c.state.list?.name, c.state.parent?.title)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            parts.joinToString(" › "), color = color, style = MaterialTheme.typography.labelLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.then(c.state.parent?.let { p -> Modifier.clickable { c.openTask(p.id) } } ?: Modifier)
        )
    }
}

@Composable
private fun DetailActions(c: DetailCtx, withDelete: Boolean = false) {
    if (c.task.depth == 0) IconButton(onClick = c.askMove) { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, "In Liste verschieben") }
    if (withDelete) IconButton(onClick = c.askDelete) { Icon(Icons.Outlined.Delete, "Löschen") }
}

@Composable
private fun CreatedBar(c: DetailCtx) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) { CreatedText(c) }
        IconButton(onClick = c.askDelete) { Icon(Icons.Outlined.Delete, "Löschen") }
    }
}

@Composable
private fun CreatedText(c: DetailCtx) {
    val created = Instant.ofEpochMilli(c.task.createdAt).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", Locale.GERMAN))
    Text(
        if (c.task.done) "Erledigt · erstellt am $created" else "Erstellt am $created",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun TitleField(c: DetailCtx, style: TextStyle, modifier: Modifier) {
    BasicTextField(
        value = c.title, onValueChange = c.onTitle,
        textStyle = style.copy(textDecoration = if (c.task.done) TextDecoration.LineThrough else style.textDecoration),
        cursorBrush = SolidColor(if (style.color == Color.White) Color.White else c.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
private fun NoteField(
    c: DetailCtx, modifier: Modifier, color: Color = MaterialTheme.colorScheme.onSurface,
    placeholder: String = "Notiz hinzufügen", big: Boolean = false,
) {
    val st = (if (big) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium).copy(color = color, lineHeight = if (big) 26.sp else 21.sp)
    Box(modifier) {
        if (c.note.isEmpty()) Text(placeholder, style = st.copy(color = color.copy(alpha = 0.5f)))
        BasicTextField(
            value = c.note, onValueChange = c.onNote, textStyle = st,
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Nested subtasks with plus/minus toggles. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubtaskTree(c: DetailCtx, guide: Boolean) {
    val line = c.accent.copy(alpha = 0.35f)
    c.state.subRows.forEach { r ->
        val t = r.task
        val level = t.depth - c.task.depth - 1
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .combinedClickable(
                    onClick = { if (r.hasChildren) c.vm.toggleExpanded(t) else c.openTask(t.id) },
                    onLongClick = { c.openTask(t.id) }
                )
                .padding(start = (level * 22).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (guide && level > 0) Box(Modifier.width(2.dp).height(28.dp).background(line))
            if (r.hasChildren) {
                Box(Modifier.size(28.dp).clip(CircleShape).clickable { c.vm.toggleExpanded(t) }, contentAlignment = Alignment.Center) {
                    Icon(if (t.expanded) Icons.Filled.Remove else Icons.Filled.Add,
                        if (t.expanded) "Zuklappen" else "Aufklappen", tint = c.accent, modifier = Modifier.size(16.dp))
                }
            } else Spacer(Modifier.width(if (guide && level > 0) 6.dp else 28.dp))
            CheckCircle(t.done, c.accent, 16.dp) { c.vm.toggleDone(t.id) }
            Text(
                t.title, modifier = Modifier.weight(1f).padding(vertical = 8.dp, horizontal = 4.dp),
                textDecoration = if (t.done) TextDecoration.LineThrough else null,
                color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
            if (r.hasChildren) Text("${r.doneChildCount}/${r.childCount}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { c.openTask(t.id) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Öffnen",
                    tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun AddSubtaskField(c: DetailCtx, modifier: Modifier) {
    if (c.task.depth >= MAX_DEPTH) return
    var text by rememberSaveable(c.task.id) { mutableStateOf("") }
    Row(modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Add, null, tint = c.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
            if (text.isEmpty()) Text("Unteraufgabe hinzufügen", color = c.accent, style = MaterialTheme.typography.bodyMedium)
            BasicTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) { c.vm.addSubtask(text); text = "" } }),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AddAttachmentButton(
    c: DetailCtx,
    modifier: Modifier = Modifier,
    content: @Composable (open: () -> Unit) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    // The size/weight modifier must sit on this outer Box, otherwise the parent Row can't lay it out.
    Box(modifier) {
        content { menu = true }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Bild") }, leadingIcon = { Icon(Icons.Outlined.Image, null) },
                onClick = { menu = false; c.addImage() })
            DropdownMenuItem(text = { Text("Datei") }, leadingIcon = { Icon(Icons.Outlined.AttachFile, null) },
                onClick = { menu = false; c.addFile() })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Thumb(c: DetailCtx, a: Attachment, modifier: Modifier) {
    val f = c.file(a)
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = { c.openAttachment(a) }, onLongClick = { c.removeAttachment(a) }),
        contentAlignment = Alignment.Center
    ) {
        if (a.mimeType.startsWith("image/") && f.exists()) {
            AsyncImage(model = f, contentDescription = a.fileName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                Text(a.fileName.substringAfterLast('.', "").uppercase().take(5), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(a.fileName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(3.dp).size(20.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)).clickable { c.removeAttachment(a) },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Close, "Entfernen", tint = Color.White, modifier = Modifier.size(12.dp)) }
    }
}

@Composable
private fun AddTile(c: DetailCtx, modifier: Modifier) {
    AddAttachmentButton(c, modifier) { open ->
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, c.accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .clickable(onClick = open),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.AttachFile, "Anhang hinzufügen", tint = c.accent, modifier = Modifier.size(17.dp)) }
    }
}

/** Hero: wrapping grid of compact tiles (68 dp ≈ 70 % of the former tile size) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttachmentGrid(c: DetailCtx) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        c.state.attachments.forEach { a -> Thumb(c, a, Modifier.size(HERO_TILE)) }
        AddTile(c, Modifier.size(HERO_TILE))
    }
}

private val HERO_TILE = 68.dp

/** Tiles: horizontal strip */
@Composable
private fun AttachmentStrip(c: DetailCtx) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(c.state.attachments, key = { it.id }) { a -> Thumb(c, a, Modifier.size(84.dp)) }
        item { AddTile(c, Modifier.size(84.dp)) }
    }
}

/** Document: compact chips */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AttachmentChip(c: DetailCtx, a: Attachment) {
    val f = c.file(a)
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .combinedClickable(onClick = { c.openAttachment(a) }, onLongClick = { c.removeAttachment(a) })
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(26.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            if (a.mimeType.startsWith("image/") && f.exists())
                AsyncImage(model = f, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, null, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(a.fileName, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp))
    }
}



// =====================================================================================
// 4 · ACTION BAR  –  sections clearly separated by lines
// =====================================================================================

@Composable
private fun SectionTitle(text: String, accent: Color, meta: String? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), color = accent.deepen(), fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.6.sp,
            modifier = Modifier.weight(1f))
        meta?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Clearly visible line between the sections (full width). */
@Composable
private fun SectionLine() {
    HorizontalDivider(Modifier.padding(vertical = 14.dp), thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
}

private fun Color.deepen() = Color(red = red * 0.75f, green = green * 0.75f, blue = blue * 0.75f, alpha = alpha)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionBarDetail(c: DetailCtx) {
    val t = c.task
    val surface = MaterialTheme.colorScheme.surface
    val line = MaterialTheme.colorScheme.outlineVariant
    Scaffold(
        containerColor = surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Breadcrumb(c, c.accent) },
                navigationIcon = { IconButton(onClick = c.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = { DetailActions(c, withDelete = true) }
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CheckCircle(t.done, c.accent, 24.dp) { c.vm.toggleDone() }
                TitleField(c, MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            // ---- segmented action bar
            Row(
                Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)
                    .clip(RoundedCornerShape(10.dp)).border(1.dp, line, RoundedCornerShape(10.dp))
            ) {
                @Composable
                fun Seg(icon: ImageVector, label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
                    Column(
                        modifier.fillMaxHeight()
                            .background(if (active) c.accent.copy(alpha = 0.14f).compositeOver(surface) else surface)
                            .clickable(onClick = onClick).padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(icon, null, tint = if (active) c.accent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Text(label, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (active) c.accent.deepen() else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                @Composable
                fun Sep() = Box(Modifier.width(1.dp).fillMaxHeight().background(line))
                Seg(Icons.Outlined.WbSunny, if (c.inMyDay) "Heute" else "Mein Tag", c.inMyDay, Modifier.weight(1f)) { c.vm.toggleMyDay() }
                Sep()
                Seg(Icons.Outlined.CalendarToday, c.dueLabel ?: "Fällig", c.dueLabel != null, Modifier.weight(1f), c.pickDue)
                Sep()
                Seg(Icons.Outlined.NotificationsNone, c.reminderLabel?.substringBefore(',') ?: "Erinnern", c.reminderLabel != null, Modifier.weight(1f), c.pickReminder)
                Sep()
                Seg(if (t.important) Icons.Filled.Star else Icons.Outlined.StarOutline, "Wichtig", t.important, Modifier.weight(1f)) { c.vm.toggleImportant() }
                Sep()
                AddAttachmentButton(c, Modifier.weight(1f)) { open ->
                    Seg(Icons.Outlined.AttachFile, "Anhang", c.state.attachments.isNotEmpty(), Modifier.fillMaxWidth(), open)
                }
            }
            SectionLine()
            // ---- subtasks
            SectionTitle("Unteraufgaben", c.accent,
                if (c.state.children.isEmpty()) null else "${c.state.doneChildren}/${c.state.children.size}")
            SubtaskTree(c, guide = true)
            AddSubtaskField(c, Modifier.padding(top = 2.dp))
            SectionLine()
            // ---- note
            SectionTitle("Notiz", c.accent)
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFFFF8E5), modifier = Modifier.fillMaxWidth()) {
                NoteField(c, Modifier.padding(12.dp).heightIn(min = 72.dp), Color(0xFF5C4400))
            }
            SectionLine()
            // ---- attachments
            SectionTitle("Anhänge", c.accent, c.state.attachments.size.takeIf { it > 0 }?.toString())
            AttachmentStrip(c)
            SectionLine()
            CreatedText(c)
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

// =====================================================================================
// 5 · SHEET with collapsible sections
// =====================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetDetail(c: DetailCtx) {
    val t = c.task
    var openSub by rememberSaveable { mutableStateOf(true) }
    var openNote by rememberSaveable { mutableStateOf(true) }
    var openAtt by rememberSaveable { mutableStateOf(c.state.attachments.isNotEmpty()) }
    var openDates by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        containerColor = c.accent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent, navigationIconContentColor = Color.White, actionIconContentColor = Color.White
                ),
                title = {},
                navigationIcon = { IconButton(onClick = c.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    IconButton(onClick = { c.vm.toggleImportant() }) {
                        Icon(if (t.important) Icons.Filled.Star else Icons.Outlined.StarOutline, "Wichtig", tint = Color.White)
                    }
                    DetailActions(c, withDelete = true)
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            // ---- coloured head
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 22.dp)) {
                Breadcrumb(c, Color.White.copy(alpha = 0.85f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CheckCircle(t.done, Color.White, 24.dp) { c.vm.toggleDone() }
                    TitleField(c, MaterialTheme.typography.headlineSmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold), Modifier.weight(1f))
                }
                val info = listOfNotNull(
                    if (c.inMyDay) "☀ Heute" else null,
                    c.dueLabel?.let { "📅 $it" },
                    c.reminderLabel?.let { "🔔 " + it.substringBefore(',') },
                ).joinToString("  ·  ")
                if (info.isNotEmpty()) Text(info, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 12.dp).clickable { openDates = true })
            }
            // ---- white sheet
            Surface(
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp).navigationBarsPadding()) {
                    Accordion("Unteraufgaben",
                        if (c.state.children.isEmpty()) "keine" else "${c.state.doneChildren}/${c.state.children.size}",
                        openSub, c.accent, { openSub = !openSub }) {
                        SubtaskTree(c, guide = true)
                        AddSubtaskField(c, Modifier.padding(top = 2.dp, bottom = 6.dp))
                    }
                    Accordion("Notiz", if (c.note.isBlank()) "leer" else "${c.note.lines().size} Zeile(n)", openNote, c.accent, { openNote = !openNote }) {
                        NoteField(c, Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(bottom = 8.dp))
                    }
                    Accordion("Anhänge", c.state.attachments.size.toString(), openAtt, c.accent, { openAtt = !openAtt }) {
                        AttachmentGrid(c)
                    }
                    Accordion("Termine & Erinnerung",
                        listOfNotNull(c.dueLabel, c.reminderLabel?.substringBefore(','), if (c.inMyDay) "Mein Tag" else null).size.let { if (it == 0) "nichts gesetzt" else "$it gesetzt" },
                        openDates, c.accent, { openDates = !openDates }, last = true) {
                        DateRow(Icons.Outlined.WbSunny, if (c.inMyDay) "In „Mein Tag“" else "Zu „Mein Tag“ hinzufügen", c.inMyDay, c.accent) { c.vm.toggleMyDay() }
                        DateRow(Icons.Outlined.CalendarToday, c.dueLabel?.let { "Fällig: $it" } ?: "Fälligkeitsdatum", c.dueLabel != null, c.accent, c.pickDue)
                        DateRow(Icons.Outlined.NotificationsNone, c.reminderLabel?.let { "Erinnerung $it" } ?: "Erinnern", c.reminderLabel != null, c.accent, c.pickReminder)
                    }
                    Box(Modifier.padding(horizontal = 16.dp)) { CreatedText(c) }
                }
            }
        }
    }
}

@Composable
private fun Accordion(
    title: String, meta: String, open: Boolean, accent: Color, onToggle: () -> Unit,
    last: Boolean = false, content: @Composable () -> Unit,
) {
    val rot by androidx.compose.animation.core.animateFloatAsState(if (open) 90f else 0f, label = "acc")
    Column(Modifier.fillMaxWidth().animateContentSizeCompat()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Filled.ChevronRight, if (open) "Zuklappen" else "Aufklappen",
                tint = accent, modifier = Modifier.padding(start = 4.dp).size(20.dp).graphicsRotate(rot))
        }
        if (open) Column(Modifier.padding(horizontal = 12.dp)) { content() }
        if (!last) HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    }
}

@Composable
private fun DateRow(icon: ImageVector, text: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = if (active) accent.deepen() else MaterialTheme.colorScheme.onSurface)
    }
}

private fun Modifier.graphicsRotate(deg: Float) = this.rotate(deg)
@Composable
private fun Modifier.animateContentSizeCompat() = this.then(Modifier.animateContentSize())
