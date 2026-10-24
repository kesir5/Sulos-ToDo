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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.launch
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
    val print: () -> Unit,
    val openDesign: () -> Unit,
    val openColor: () -> Unit,
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
    val listAccent = state.list?.let { listColor(it.color) } ?: Brand
    val customColor by UiPrefs.detailColor.collectAsState()
    val accent = customColor?.let { Color(it) } ?: listAccent

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
    var designDialog by remember { mutableStateOf(false) }
    var colorDialog by remember { mutableStateOf(false) }
    val printScope = androidx.compose.runtime.rememberCoroutineScope()

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
        print = {
            printScope.launch { vm.printHtml()?.let { com.sueleyman.suelostodo.util.TaskPrinter.print(context, it, task.title) } }
        },
        openDesign = { designDialog = true },
        openColor = { colorDialog = true },
        onBack = onBack,
    )

    when (style) {
        DetailStyle.HERO -> HeroDetail(ctx)
        DetailStyle.DOCUMENT -> DocumentDetail(ctx)
        DetailStyle.ACTIONBAR -> ActionBarDetail(ctx)
        DetailStyle.SHEET -> SheetDetail(ctx)
        DetailStyle.TIMELINE -> TimelineDetail(ctx)
        DetailStyle.TABS -> TabsDetail(ctx)
        DetailStyle.NOTEPAD -> NotepadDetail(ctx)
    }

    if (designDialog) StyleDialog(
        "Design der Aufgabendetails", DetailStyle.entries, style, { it.title }, { it.description },
        onDismiss = { designDialog = false }
    ) { UiPrefs.setDetailStyle(context, it); designDialog = false }
    if (colorDialog) GroupColorDialog(
        "Aufgabendetails", customColor ?: (listAccent.toArgb().toLong() and 0xFFFFFFFFL),
        onDismiss = { colorDialog = false }
    ) { UiPrefs.setDetailColor(context, it); colorDialog = false }

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroDetail(c: DetailCtx) {
    val t = c.task
    val light = c.accent.copy(alpha = 0.72f).compositeOver(Color.White)
    val n = c.state.children.size
    val frac = if (n == 0) 0f else c.state.doneChildren.toFloat() / n
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f).compositeOver(MaterialTheme.colorScheme.background),
        bottomBar = {
            // fixed action bar: complete + delete
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp), color = c.accent, shadowElevation = 4.dp,
                    modifier = Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(16.dp)).clickable { c.vm.toggleDone() }
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(if (t.done) "Wieder öffnen" else "Als erledigt markieren", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = c.askDelete)
                ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Delete, "Löschen") } }
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            // ---- rounded hero card with gradient
            Surface(
                shape = RoundedCornerShape(26.dp), shadowElevation = 6.dp, color = Color.Transparent,
                modifier = Modifier.fillMaxWidth().statusBarsPaddingCompat().padding(10.dp)
            ) {
                Column(
                    Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(c.accent, light)))
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassButton(onClick = c.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Color.White, modifier = Modifier.size(20.dp)) }
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) { Breadcrumb(c, Color.White.copy(alpha = 0.9f)) }
                        GlassButton(onClick = { c.vm.toggleImportant() }) {
                            Icon(if (t.important) Icons.Filled.Star else Icons.Outlined.StarOutline, "Wichtig", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        if (t.depth == 0) {
                            Spacer(Modifier.width(6.dp))
                            GlassButton(onClick = c.askMove) {
                                Icon(Icons.AutoMirrored.Outlined.DriveFileMove, "In Liste verschieben", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        GlassButton(onClick = c.print) {
                            Icon(Icons.Outlined.Print, "Aufgabe drucken", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        DetailMenu(c) { open ->
                            GlassButton(onClick = open) {
                                Icon(Icons.Filled.MoreVert, "Menü", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // progress ring of the subtasks (or check circle without subtasks)
                        Box(Modifier.size(52.dp).clip(CircleShape).clickable { c.vm.toggleDone() }, contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(48.dp)) {
                                val sw = 5.dp.toPx(); val d = size.minDimension - sw; val tl = Offset(sw / 2, sw / 2)
                                drawArc(Color.White.copy(alpha = 0.3f), 0f, 360f, false, tl, Size(d, d), style = Stroke(sw))
                                drawArc(Color.White, -90f, 360f * (if (t.done) 1f else frac), false, tl, Size(d, d),
                                    style = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                            }
                            if (n > 0 && !t.done) Text("${c.state.doneChildren}/$n", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            else Icon(Icons.Filled.Check, null,
                                tint = if (t.done) Color.White else Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        TitleField(c, MaterialTheme.typography.headlineSmall.copy(color = Color.White, fontWeight = FontWeight.Bold), Modifier.weight(1f))
                    }
                    FlowRow(
                        Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HeroChip(Icons.Outlined.WbSunny, if (c.inMyDay) "Mein Tag" else "Zu Mein Tag", c.inMyDay, c.accent) { c.vm.toggleMyDay() }
                        HeroChip(Icons.Outlined.CalendarToday, c.dueLabel ?: "Fällig", c.dueLabel != null,
                            if (c.overdue) Color(0xFFC4314B) else c.accent, c.pickDue)
                        HeroChip(Icons.Outlined.NotificationsNone, c.reminderLabel?.substringBefore(',') ?: "Erinnern", c.reminderLabel != null, c.accent, c.pickReminder)
                    }
                }
            }
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HeroSection("Unteraufgaben", if (n > 0) "${c.state.doneChildren}/$n" else null)
                HeroCard {
                    SubtaskTree(c, guide = false)
                    AddSubtaskField(c, Modifier.padding(top = 2.dp))
                }
                HeroSection("Notiz")
                Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFFFF8E5), modifier = Modifier.fillMaxWidth()) {
                    NoteField(c, Modifier.padding(14.dp).heightIn(min = 72.dp), Color(0xFF5C4400))
                }
                HeroSection("Anhänge", c.state.attachments.size.takeIf { it > 0 }?.toString())
                AttachmentStrip(c)
                CreatedText(c)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun Modifier.statusBarsPaddingCompat() = this.then(Modifier.windowInsetsPadding(WindowInsets.statusBars))

/** Round, semi-transparent "glass" button on the gradient header. */
@Composable
private fun GlassButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun HeroSection(title: String, meta: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 2.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        meta?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun HeroChip(icon: ImageVector, text: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    val bg = if (active) Color.White else Color.White.copy(alpha = 0.2f)
    val fg = if (active) accent else Color.White
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
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
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) { content() }
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
    IconButton(onClick = c.print) { Icon(Icons.Outlined.Print, "Aufgabe drucken") }
    DetailMenu(c)
}

/** ⋮ menu of the detail page (always the right-most icon): design + accent colour. */
@Composable
private fun DetailMenu(
    c: DetailCtx,
    button: @Composable (open: () -> Unit) -> Unit = { open ->
        IconButton(onClick = open) { Icon(Icons.Filled.MoreVert, "Menü", tint = LocalContentColor.current) }
    },
) {
    var open by remember { mutableStateOf(false) }
    Box {
        button { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Design der Details") }, leadingIcon = { Icon(Icons.Outlined.Dashboard, null) },
                onClick = { open = false; c.openDesign() })
            DropdownMenuItem(text = { Text("Farbe ändern") }, leadingIcon = { Icon(Icons.Outlined.Palette, null, tint = c.accent) },
                onClick = { open = false; c.openColor() })
        }
    }
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
    placeholder: String = "Notiz hinzufügen", big: Boolean = false, serif: Boolean = false,
) {
    val base = (if (big) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium).copy(color = color, lineHeight = if (big) 26.sp else 21.sp)
    val st = if (serif) base.copy(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic) else base
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
private fun SubtaskTree(c: DetailCtx, guide: Boolean, divided: Boolean = false) {
    val line = c.accent.copy(alpha = 0.35f)
    c.state.subRows.forEachIndexed { i, r ->
        if (divided && i > 0) HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
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

// =====================================================================================
// 5 · TIMELINE – created → subtasks → My Day → reminder → due on one vertical line
// =====================================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TimelineDetail(c: DetailCtx) {
    val t = c.task
    val red = Color(0xFFC4314B)
    val amber = Color(0xFFE0A100)
    val lineColor = c.accent.copy(alpha = 0.3f)
    val n = c.state.children.size
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Breadcrumb(c, c.accent) },
                navigationIcon = { IconButton(onClick = c.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    IconButton(onClick = { c.vm.toggleImportant() }) {
                        Icon(if (t.important) Icons.Filled.Star else Icons.Outlined.StarOutline, "Wichtig",
                            tint = if (t.important) red else LocalContentColorOrDefault())
                    }
                    DetailActions(c, withDelete = true)
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CheckCircle(t.done, c.accent, 24.dp) { c.vm.toggleDone() }
                TitleField(c, MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            // the vertical line is drawn behind all entries
            Column(
                Modifier.fillMaxWidth().drawBehind {
                    val x = 11.dp.toPx()
                    drawLine(lineColor, Offset(x, 10.dp.toPx()), Offset(x, size.height - 14.dp.toPx()), 2.dp.toPx())
                }
            ) {
                val created = Instant.ofEpochMilli(t.createdAt).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", Locale.GERMAN))
                TimelineItem("Erstellt", c.accent, filled = true) { Text(created, style = MaterialTheme.typography.bodyMedium) }
                TimelineItem(if (n > 0) "Unteraufgaben · ${c.state.doneChildren}/$n" else "Unteraufgaben", c.accent, filled = n > 0 && c.state.doneChildren == n) {
                    SubtaskTree(c, guide = true, divided = true)
                    if (n > 0) HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    AddSubtaskField(c, Modifier.padding(top = 2.dp))
                }
                TimelineItem("Mein Tag", c.accent, filled = c.inMyDay, onClick = { c.vm.toggleMyDay() }) {
                    TimelineValue(if (c.inMyDay) "Heute in „Mein Tag“" else "Zu „Mein Tag“ hinzufügen", c.inMyDay)
                }
                TimelineItem("Erinnerung", amber, filled = false, onClick = c.pickReminder) {
                    TimelineValue(c.reminderLabel ?: "Erinnerung festlegen", c.reminderLabel != null)
                }
                TimelineItem("Fällig", if (c.overdue) red else c.accent, filled = false, onClick = c.pickDue, labelColor = if (c.overdue) red else null) {
                    TimelineValue(c.dueLabel ?: "Fälligkeitsdatum festlegen", c.dueLabel != null, if (c.overdue) red else null)
                }
            }
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = c.accent.copy(alpha = 0.08f).compositeOver(MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.StickyNote2, null, tint = c.accent, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(8.dp))
                    NoteField(c, Modifier.weight(1f).heightIn(min = 56.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                c.state.attachments.forEach { a -> AttachmentChip(c, a) }
                AddAttachmentButton(c) { open ->
                    Row(
                        Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = open).padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.AttachFile, null, tint = c.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Anhang", color = c.accent, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun TimelineItem(
    label: String, dot: Color, filled: Boolean, onClick: (() -> Unit)? = null, labelColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(bottom = 12.dp)
    ) {
        Box(Modifier.width(24.dp).padding(top = 3.dp), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier.size(12.dp).clip(CircleShape).background(if (filled) dot else surface)
                    .border(2.dp, dot, CircleShape)
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
                color = labelColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun TimelineValue(text: String, set: Boolean, color: Color? = null) {
    Text(text, style = MaterialTheme.typography.bodyMedium,
        color = color ?: if (set) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
}

// =====================================================================================
// 6 · TABS – dates as chips, switchable tabs for subtasks / note / attachments
// =====================================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TabsDetail(c: DetailCtx) {
    val t = c.task
    val n = c.state.children.size
    var tab by rememberSaveable(t.id) { mutableStateOf(0) }
    val surface = MaterialTheme.colorScheme.surface
    Scaffold(
        containerColor = surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Breadcrumb(c, c.accent) },
                navigationIcon = { IconButton(onClick = c.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    IconButton(onClick = { c.vm.toggleImportant() }) {
                        Icon(if (t.important) Icons.Filled.Star else Icons.Outlined.StarOutline, "Wichtig",
                            tint = if (t.important) Color(0xFFC4314B) else LocalContentColorOrDefault())
                    }
                    DetailActions(c, withDelete = true)
                }
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().clickable { c.vm.toggleDone() }.padding(14.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Check, null, tint = c.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (t.done) "Wieder öffnen" else "Als erledigt markieren", color = c.accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CheckCircle(t.done, c.accent, 24.dp) { c.vm.toggleDone() }
                TitleField(c, MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold), Modifier.weight(1f))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 6.dp)) {
                val red = Color(0xFFA3283D); val amber = Color(0xFF8A6100)
                TabChip(Icons.Outlined.CalendarToday, c.dueLabel ?: "Fällig", if (c.dueLabel == null) null else if (c.overdue) red else c.accent.deepen(), c.pickDue)
                TabChip(Icons.Outlined.NotificationsNone, c.reminderLabel?.substringBefore(',') ?: "Erinnern", if (c.reminderLabel == null) null else amber, c.pickReminder)
                TabChip(Icons.Outlined.WbSunny, if (c.inMyDay) "Mein Tag" else "Zu Mein Tag", if (c.inMyDay) c.accent.deepen() else null) { c.vm.toggleMyDay() }
            }
            // segmented tab switcher
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).padding(3.dp)
            ) {
                val labels = listOf(
                    if (n > 0) "Unteraufg. ${c.state.doneChildren}/$n" else "Unteraufgaben",
                    "Notiz",
                    if (c.state.attachments.isEmpty()) "Anhänge" else "Anhänge ${c.state.attachments.size}",
                )
                labels.forEachIndexed { i, l ->
                    val sel = tab == i
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (sel) surface else Color.Transparent)
                            .clickable { tab = i }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(l, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (sel) c.accent else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            when (tab) {
                0 -> {
                    if (n > 0) LinearProgressIndicator(
                        progress = { c.state.doneChildren.toFloat() / n }, color = c.accent, trackColor = c.accent.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                    SubtaskTree(c, guide = true, divided = true)
                    if (n > 0) HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    AddSubtaskField(c, Modifier.padding(top = 2.dp))
                }
                1 -> Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFF8E5), modifier = Modifier.fillMaxWidth()) {
                    NoteField(c, Modifier.padding(14.dp).heightIn(min = 180.dp), Color(0xFF5C4400), big = true)
                }
                else -> AttachmentGrid(c)
            }
            CreatedText(c)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TabChip(icon: ImageVector, text: String, color: Color?, onClick: () -> Unit) {
    val fg = color ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.clip(RoundedCornerShape(14.dp))
            .background(if (color != null) color.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

// =====================================================================================
// 7 · NOTEPAD – ruled paper, serif title, attachments pinned as notes
// =====================================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NotepadDetail(c: DetailCtx) {
    val t = c.task
    val paperBg = Color(0xFFFFFCF2)
    val ink = Color(0xFF2A2A2A)
    val rule = Color(0xFFEDE6D3)
    val margin = Color(0xFFE8A0A0)
    val n = c.state.children.size
    Scaffold(
        containerColor = paperBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent, navigationIconContentColor = ink, actionIconContentColor = ink
                ),
                title = { Breadcrumb(c, c.accent.deepen()) },
                navigationIcon = { IconButton(onClick = c.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    IconButton(onClick = { c.vm.toggleImportant() }) {
                        Icon(if (t.important) Icons.Filled.Star else Icons.Outlined.StarOutline, "Wichtig",
                            tint = if (t.important) Color(0xFFC4314B) else ink)
                    }
                    DetailActions(c, withDelete = true)
                }
            )
        }
    ) { pad ->
        // the paper always stays light, also in dark mode
        MaterialTheme(colorScheme = lightColorScheme()) {
            Column(Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                Surface(
                    shape = RoundedCornerShape(4.dp), color = Color.White, shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.drawBehind {
                            val step = 28.dp.toPx()
                            var y = step
                            while (y < size.height) { drawLine(rule, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()); y += step }
                            val mx = 30.dp.toPx()
                            drawLine(margin, Offset(mx, 0f), Offset(mx, size.height), 1.5.dp.toPx())
                        }.padding(start = 38.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CheckCircle(t.done, c.accent, 20.dp) { c.vm.toggleDone() }
                            TitleField(c, TextStyle(fontFamily = FontFamily.Serif, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ink), Modifier.weight(1f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PaperLink(c.dueLabel?.let { "fällig $it" } ?: "+ Fälligkeit", if (c.overdue) Color(0xFFC4314B) else c.accent.deepen(), c.pickDue)
                            Text("  ·  ", color = Color(0xFF999999), fontSize = 12.sp)
                            PaperLink(c.reminderLabel?.let { "⏰ " + it.substringBefore(',') } ?: "+ Erinnerung", c.accent.deepen(), c.pickReminder)
                            Text("  ·  ", color = Color(0xFF999999), fontSize = 12.sp)
                            PaperLink(if (c.inMyDay) "☀ Heute" else "+ Mein Tag", c.accent.deepen()) { c.vm.toggleMyDay() }
                        }
                        PaperLabel(if (n > 0) "Zu erledigen · ${c.state.doneChildren}/$n" else "Zu erledigen")
                        SubtaskTree(c, guide = false)
                        AddSubtaskField(c, Modifier)
                        PaperLabel("Notiz")
                        NoteField(c, Modifier.fillMaxWidth().heightIn(min = 84.dp), Color(0xFF444444), placeholder = "Hier schreiben …", big = true, serif = true)
                        PaperLabel("Angeheftet")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            c.state.attachments.forEachIndexed { i, a ->
                                Surface(shadowElevation = 2.dp, shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.rotate(if (i % 2 == 0) -3f else 2.5f)) {
                                    Thumb(c, a, Modifier.size(width = 78.dp, height = 62.dp))
                                }
                            }
                            AddTile(c, Modifier.size(width = 78.dp, height = 62.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        CreatedText(c)
                    }
                }
                Spacer(Modifier.height(24.dp).navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun PaperLabel(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = Color(0xFF888888),
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
}

@Composable
private fun PaperLink(text: String, color: Color, onClick: () -> Unit) {
    Text(text, color = color, fontSize = 12.sp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(vertical = 4.dp))
}
