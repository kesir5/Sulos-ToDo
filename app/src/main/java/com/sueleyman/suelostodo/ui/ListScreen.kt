package com.sueleyman.suelostodo.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.SubdirectoryArrowRight
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sueleyman.suelostodo.data.INBOX_ID
import com.sueleyman.suelostodo.data.MAX_DEPTH
import com.sueleyman.suelostodo.data.Task
import com.sueleyman.suelostodo.data.TaskList
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Callbacks shared by all row designs. */
class RowActions(
    val open: (String) -> Unit,
    val toggleDone: (String) -> Unit,
    val toggleImportant: (String) -> Unit,
    val toggleExpand: (Task) -> Unit,
    val addSubtask: (String) -> Unit,
    val move: (String) -> Unit,
    val delete: (String) -> Unit,
)

/** Everything a row needs besides the task itself. */
class RowCtx(
    val style: TaskStyle,
    val accent: Color,
    val tree: Boolean,
    val showMyDay: Boolean,
    val listNames: Map<String, String>,
    val withAttachments: Set<String>,
    val actions: RowActions,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    listId: String,
    autoAdd: Boolean,
    onBack: () -> Unit,
    onOpenTask: (String) -> Unit,
    onExport: () -> Unit = {},
) {
    val vm: ListViewModel = viewModel(key = "list-$listId") { ListViewModel(listId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val style by UiPrefs.style.collectAsState()
    val h = state.header
    val accent = h?.color ?: Brand
    val isSmart = vm.smart != null
    val colored = style == TaskStyle.CLASSIC
    val headerBlock = style == TaskStyle.TREE || style == TaskStyle.RING
    val onHeader = if (colored || headerBlock) Color.White else MaterialTheme.colorScheme.onSurface
    val bg = when (style) {
        TaskStyle.CLASSIC -> accent
        TaskStyle.TREE, TaskStyle.OUTLINER, TaskStyle.RING -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.background
    }

    var showDone by rememberSaveable { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var groupDialog by remember { mutableStateOf(false) }
    val groups by vm.groups.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentGroup by vm.groupId.collectAsStateWithLifecycle(initialValue = null)
    var subtaskFor by remember { mutableStateOf<String?>(null) }
    var moveFor by remember { mutableStateOf<String?>(null) }
    var deleteTask by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(h) { if (h != null && !h.exists) onBack() }

    val ctx = RowCtx(
        style = style, accent = accent, tree = !isSmart,
        showMyDay = vm.smart != SmartList.MY_DAY,
        listNames = if (isSmart) state.listNames else emptyMap(),
        withAttachments = state.withAttachments,
        actions = RowActions(
            open = onOpenTask,
            toggleDone = { vm.toggleDone(it) },
            toggleImportant = { vm.toggleImportant(it) },
            toggleExpand = { vm.setExpanded(it.id, !it.expanded) },
            addSubtask = { subtaskFor = it },
            move = { moveFor = it },
            delete = { deleteTask = it },
        )
    )
    val grouped = !isSmart && (style == TaskStyle.GROUPS || style == TaskStyle.RING)

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (headerBlock) accent else Color.Transparent,
                    navigationIconContentColor = onHeader,
                    actionIconContentColor = onHeader,
                ),
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    if (!isSmart) {
                        val parents = (state.openRows + state.doneRows).filter { it.hasChildren }
                        val allExpanded = parents.isNotEmpty() && parents.all { it.task.expanded }
                        if (parents.isNotEmpty()) IconButton(onClick = {
                            parents.forEach { vm.setExpanded(it.task.id, !allExpanded) }
                        }) {
                            Icon(
                                if (allExpanded) Icons.Outlined.UnfoldLess else Icons.Outlined.UnfoldMore,
                                if (allExpanded) "Alle einklappen" else "Alle ausklappen"
                            )
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menü") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Liste bearbeiten") },
                                    leadingIcon = { Icon(Icons.Outlined.Palette, null) },
                                    onClick = { menu = false; renameDialog = true })
                                if (listId != INBOX_ID) DropdownMenuItem(
                                    text = { Text("In Gruppe verschieben") },
                                    leadingIcon = { Icon(Icons.Outlined.Folder, null) },
                                    onClick = { menu = false; groupDialog = true })
                                DropdownMenuItem(
                                    text = { Text("Importieren / Exportieren") },
                                    leadingIcon = { Icon(Icons.Outlined.FileDownload, null) },
                                    onClick = { menu = false; onExport() })
                                if (listId != INBOX_ID) DropdownMenuItem(
                                    text = { Text("Liste löschen") },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                    onClick = { menu = false; deleteDialog = true })
                            }
                        }
                    }
                }
            )
        },
        bottomBar = { AddTaskBar(autoAdd, accent, colored) { vm.addTask(it) } }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "header") { ListHeaderBlock(style, h?.title ?: "", accent, onHeader, headerBlock, vm.smart, state) }

            if (grouped) {
                items(state.openGroups, key = { "o" + it.first().task.id }) { g ->
                    GroupCard(g, ctx, Modifier.animateItem())
                }
            } else {
                items(state.openRows, key = { "o" + it.task.id }) { row ->
                    FlatRow(row, ctx, Modifier.animateItem())
                }
            }

            if (state.doneCount > 0) {
                item(key = "doneHeader") {
                    DoneHeader(state.doneCount, showDone, accent, colored) { showDone = !showDone }
                }
                if (showDone) {
                    if (grouped) items(state.doneGroups, key = { "d" + it.first().task.id }) { g ->
                        GroupCard(g, ctx, Modifier.animateItem())
                    } else items(state.doneRows, key = { "d" + it.task.id }) { row ->
                        FlatRow(row, ctx, Modifier.animateItem())
                    }
                }
            }
        }
    }

    if (renameDialog && h != null) {
        TextInputDialog(
            title = "Liste bearbeiten", initial = h.title, withColor = h.colorIndex.coerceAtLeast(0),
            onDismiss = { renameDialog = false },
            onConfirm = { name, color -> renameDialog = false; vm.renameList(name, color) }
        )
    }
    if (groupDialog) {
        GroupPickDialog(
            title = "\"${h?.title}\" verschieben", groups = groups, current = currentGroup,
            onDismiss = { groupDialog = false },
            onPick = { groupDialog = false; vm.moveToGroup(it) }
        )
    }
    if (deleteDialog) {
        ConfirmDialog(
            title = "Liste löschen?", text = "\"${h?.title}\" und alle Aufgaben darin werden gelöscht.",
            onDismiss = { deleteDialog = false },
            onConfirm = { deleteDialog = false; vm.deleteList() }
        )
    }
    subtaskFor?.let { pid ->
        TextInputDialog(
            title = "Unteraufgabe hinzufügen", confirm = "Hinzufügen",
            onDismiss = { subtaskFor = null },
            onConfirm = { text, _ -> subtaskFor = null; vm.addSubtask(pid, text) }
        )
    }
    deleteTask?.let { id ->
        ConfirmDialog(
            title = "Aufgabe löschen?", text = "Die Aufgabe wird mit allen Unteraufgaben und Anhängen gelöscht.",
            onDismiss = { deleteTask = null },
            onConfirm = { deleteTask = null; vm.delete(id) }
        )
    }
    moveFor?.let { id ->
        MoveDialog(state.allLists, onDismiss = { moveFor = null }) { target -> moveFor = null; vm.move(id, target) }
    }
}

// ============================================================ header / done header

@Composable
private fun ListHeaderBlock(
    style: TaskStyle, title: String, accent: Color, onHeader: Color, block: Boolean,
    smart: SmartList?, state: ListUiState,
) {
    val subtitle = when {
        smart == SmartList.MY_DAY -> LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN))
        style == TaskStyle.TREE -> "${state.openCount} offen · ${state.doneCount} erledigt"
        style == TaskStyle.RING -> "${(state.progress * 100).toInt()} % erledigt"
        style == TaskStyle.GROUPS -> "Gruppiert nach Hauptaufgabe"
        else -> null
    }
    val titleColor = if (block || style == TaskStyle.CLASSIC) onHeader
        else if (style == TaskStyle.OUTLINER) MaterialTheme.colorScheme.onSurface else accent
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (block) Modifier.background(accent) else Modifier)
            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp, top = if (block) 0.dp else 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (style == TaskStyle.STRIPES) {
                Box(Modifier.width(5.dp).height(30.dp).background(accent))
                Spacer(Modifier.width(10.dp))
            }
            Text(title, color = titleColor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        if (subtitle != null) Text(
            subtitle, color = titleColor.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium
        )
        if (style == TaskStyle.RING) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                color = Color.White, trackColor = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth().height(4.dp)
            )
        }
    }
    if (block) Spacer(Modifier.height(8.dp))
}

@Composable
private fun DoneHeader(count: Int, open: Boolean, accent: Color, colored: Boolean, onClick: () -> Unit) {
    val fg = if (colored) Color.White else accent
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (colored) Color.Black.copy(alpha = 0.18f) else accent.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val rot by animateFloatAsState(if (open) 90f else 0f, label = "rot")
        Icon(Icons.Filled.ChevronRight, null, tint = fg, modifier = Modifier.rotate(rot))
        Spacer(Modifier.width(6.dp))
        Text("Erledigt  $count", color = fg, fontWeight = FontWeight.SemiBold)
    }
}

// ============================================================ expand toggles (plus / minus)

/** The plus/minus toggle in the look of the active design. */
@Composable
fun ExpandToggle(row: TreeRow, ctx: RowCtx, small: Boolean = false) {
    val t = row.task
    val accent = ctx.accent
    val icon = if (t.expanded) Icons.Filled.Remove else Icons.Filled.Add
    val desc = if (t.expanded) "Unteraufgaben zuklappen" else "Unteraufgaben aufklappen"
    val onClick = { ctx.actions.toggleExpand(t) }
    when (ctx.style) {
        TaskStyle.CLASSIC -> Box(
            Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(20.dp).clip(CircleShape)
                    .background(if (t.expanded) accent else Color.Transparent)
                    .then(if (t.expanded) Modifier else Modifier.background(Color.Transparent)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(20.dp)) {
                    if (!t.expanded) drawCircle(accent, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                }
                Icon(icon, desc, tint = if (t.expanded) Color.White else accent, modifier = Modifier.size(14.dp))
            }
        }
        TaskStyle.TREE -> Row(
            Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${row.childCount}", style = MaterialTheme.typography.labelMedium, color = accent)
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, desc, tint = accent, modifier = Modifier.size(16.dp)) }
        }
        TaskStyle.GROUPS -> Box(
            Modifier.size(if (small) 28.dp else 32.dp).clip(CircleShape)
                .background(if (small) Color.Transparent else accent.copy(alpha = 0.14f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Icon(icon, desc, tint = accent, modifier = Modifier.size(if (small) 16.dp else 18.dp)) }
        TaskStyle.OUTLINER -> Box(
            Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Icon(icon, desc, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
        TaskStyle.STRIPES -> Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.14f))
                .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, desc, tint = accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(2.dp))
            Text("${row.childCount}", style = MaterialTheme.typography.labelMedium, color = accent)
        }
        TaskStyle.RING -> {
            val frac = if (row.childCount == 0) 0f else row.doneChildCount.toFloat() / row.childCount
            val track = accent.copy(alpha = 0.18f)
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(28.dp)) {
                    val sw = 3.dp.toPx()
                    val d = size.minDimension - sw
                    val tl = Offset(sw / 2, sw / 2)
                    drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(sw))
                    drawArc(accent, -90f, 360f * frac, false, tl, Size(d, d), style = Stroke(sw))
                }
                Icon(icon, desc, tint = accent, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ============================================================ flat rows (A, B, D, E + smart lists)

/**
 * Tap handling for a task row: for tasks with subtasks the left half toggles the subtasks,
 * the right half opens the details. Tasks without subtasks open the details everywhere.
 * Long press opens the context menu.
 */
@Composable
fun Modifier.splitTap(row: TreeRow, ctx: RowCtx, onLongPress: () -> Unit): Modifier {
    val r by rememberUpdatedState(row)
    val c by rememberUpdatedState(ctx)
    val long by rememberUpdatedState(onLongPress)
    val interaction = remember { MutableInteractionSource() }
    return this
        .indication(interaction, LocalIndication.current)
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = { off ->
                    val press = PressInteraction.Press(off)
                    interaction.emit(press)
                    val released = tryAwaitRelease()
                    interaction.emit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                },
                onLongPress = { long() },
                onTap = { off ->
                    if (r.hasChildren && c.tree && off.x < size.width / 2f) c.actions.toggleExpand(r.task)
                    else c.actions.open(r.task.id)
                }
            )
        }
}

@Composable
private fun RowMenuBox(row: TreeRow, ctx: RowCtx, modifier: Modifier, content: @Composable (Modifier) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        content(Modifier.splitTap(row, ctx) { menu = true })
        TaskContextMenu(row.task, ctx, menu) { menu = false }
    }
}

@Composable
private fun TaskContextMenu(t: Task, ctx: RowCtx, expanded: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (t.depth < MAX_DEPTH) DropdownMenuItem(
            text = { Text("Unteraufgabe hinzufügen") },
            leadingIcon = { Icon(Icons.Outlined.SubdirectoryArrowRight, null) },
            onClick = { onDismiss(); ctx.actions.addSubtask(t.id) })
        DropdownMenuItem(
            text = { Text("Öffnen / bearbeiten") },
            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
            onClick = { onDismiss(); ctx.actions.open(t.id) })
        if (t.depth == 0) DropdownMenuItem(
            text = { Text("In Liste verschieben") },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) },
            onClick = { onDismiss(); ctx.actions.move(t.id) })
        DropdownMenuItem(
            text = { Text("Löschen", color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
            onClick = { onDismiss(); ctx.actions.delete(t.id) })
    }
}

@Composable
private fun FlatRow(row: TreeRow, ctx: RowCtx, modifier: Modifier) {
    val t = row.task
    val depth = if (ctx.tree) t.depth else 0
    when (ctx.style) {
        TaskStyle.CLASSIC, TaskStyle.GROUPS, TaskStyle.RING -> {
            // GROUPS/RING only reach here for smart lists -> card look
            val surface = MaterialTheme.colorScheme.surface
            val cardColor = if (depth == 0) surface else ctx.accent.copy(alpha = 0.10f).compositeOver(surface)
            RowMenuBox(row, ctx, modifier.padding(start = 10.dp + (depth * 22).dp, end = 10.dp, top = 2.dp, bottom = 2.dp)) { click ->
                Surface(
                    shape = RoundedCornerShape(if (ctx.style == TaskStyle.CLASSIC) 8.dp else 12.dp),
                    color = cardColor,
                    border = if (ctx.style == TaskStyle.CLASSIC) null else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().animateContentSize().then(click)
                ) {
                    Row(Modifier.padding(start = 2.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (ctx.tree) {
                            if (row.hasChildren) ExpandToggle(row, ctx) else Spacer(Modifier.width(36.dp))
                        } else Spacer(Modifier.width(6.dp))
                        CheckCircle(t.done, ctx.accent, if (depth == 0) 20.dp else 17.dp) { ctx.actions.toggleDone(t.id) }
                        TitleAndMeta(row, ctx, Modifier.weight(1f), depth)
                        StarButton(t, ctx)
                    }
                }
            }
        }

        TaskStyle.TREE -> {
            val line = ctx.accent.copy(alpha = 0.45f)
            val divider = MaterialTheme.colorScheme.outlineVariant
            RowMenuBox(row, ctx, modifier) { click ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(click)
                        .drawBehind {
                            drawLine(divider, Offset(0f, size.height), Offset(size.width, size.height), 0.5.dp.toPx())
                            if (depth > 0) {
                                val x = (14 + (depth - 1) * 22 + 10).dp.toPx()
                                val y = size.height / 2
                                drawLine(line, Offset(x, 0f), Offset(x, y), 1.dp.toPx())
                                drawLine(line, Offset(x, y), Offset(x + 10.dp.toPx(), y), 1.dp.toPx())
                            }
                        }
                        .padding(start = (14 + depth * 22).dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CheckCircle(t.done, ctx.accent, if (depth == 0) 18.dp else 15.dp) { ctx.actions.toggleDone(t.id) }
                    TitleAndMeta(row, ctx, Modifier.weight(1f), depth, showChildCount = false)
                    if (ctx.tree && row.hasChildren) ExpandToggle(row, ctx)
                    StarButton(t, ctx)
                }
            }
        }

        TaskStyle.OUTLINER -> {
            val guide = MaterialTheme.colorScheme.outlineVariant
            RowMenuBox(row, ctx, modifier) { click ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(click)
                        .drawBehind {
                            for (i in 0 until depth) {
                                val x = (8 + i * 22 + 14).dp.toPx()
                                drawLine(guide, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                            }
                        }
                        .padding(start = (8 + depth * 22).dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (ctx.tree && row.hasChildren) ExpandToggle(row, ctx)
                    else Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline))
                    }
                    CheckCircle(t.done, MaterialTheme.colorScheme.outline, 15.dp) { ctx.actions.toggleDone(t.id) }
                    TitleAndMeta(row, ctx, Modifier.weight(1f), depth, showChildCount = false, big = depth == 0)
                    if (t.important) StarButton(t, ctx)
                }
            }
        }

        TaskStyle.STRIPES -> {
            val stripe = ctx.accent.copy(alpha = when (depth) { 0 -> 1f; 1 -> 0.55f; else -> 0.3f })
            RowMenuBox(row, ctx, modifier.padding(start = 10.dp + (depth * 14).dp, end = 10.dp, top = 2.dp, bottom = 2.dp)) { click ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().animateContentSize().then(click)
                ) {
                    Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(4.dp).fillMaxHeight().background(stripe))
                        Spacer(Modifier.width(4.dp))
                        CheckCircle(t.done, ctx.accent, if (depth == 0) 18.dp else 16.dp) { ctx.actions.toggleDone(t.id) }
                        TitleAndMeta(row, ctx, Modifier.weight(1f), depth, showChildCount = false)
                        if (ctx.tree && row.hasChildren) ExpandToggle(row, ctx)
                        StarButton(t, ctx)
                    }
                }
            }
        }
    }
}

// ============================================================ grouped cards (C, F)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupCard(group: List<TreeRow>, ctx: RowCtx, modifier: Modifier) {
    val root = group.first()
    val t = root.task
    val ring = ctx.style == TaskStyle.RING
    var menu by remember { mutableStateOf(false) }
    Box(modifier.padding(horizontal = 10.dp, vertical = if (ring) 3.dp else 5.dp)) {
        Surface(
            shape = RoundedCornerShape(if (ring) 8.dp else 14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().animateContentSize()
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .splitTap(root, ctx) { menu = true }
                        .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CheckCircle(t.done, ctx.accent, 20.dp) { ctx.actions.toggleDone(t.id) }
                    TitleAndMeta(root, ctx, Modifier.weight(1f), 0, showChildCount = ring, bold = true)
                    if (root.hasChildren) ExpandToggle(root, ctx)
                    StarButton(t, ctx)
                }
                if (!ring && root.hasChildren) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                        LinearProgressIndicator(
                            progress = { root.doneChildCount.toFloat() / root.childCount },
                            color = ctx.accent, trackColor = ctx.accent.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${root.doneChildCount} von ${root.childCount} erledigt",
                            style = MaterialTheme.typography.labelSmall, color = ctx.accent
                        )
                    }
                }
                if (group.size > 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .then(if (ring) Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) else Modifier)
                            .padding(vertical = 4.dp)
                    ) {
                        group.drop(1).forEach { r -> GroupChildRow(r, ctx) }
                    }
                }
            }
        }
        TaskContextMenu(t, ctx, menu) { menu = false }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupChildRow(row: TreeRow, ctx: RowCtx) {
    val t = row.task
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .splitTap(row, ctx) { menu = true }
                .padding(start = (8 + (t.depth - 1) * 20).dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CheckCircle(t.done, ctx.accent, 15.dp) { ctx.actions.toggleDone(t.id) }
            TitleAndMeta(row, ctx, Modifier.weight(1f), t.depth, showChildCount = false)
            if (row.hasChildren) ExpandToggle(row, ctx, small = true)
            if (t.important) StarButton(t, ctx)
        }
        TaskContextMenu(t, ctx, menu) { menu = false }
    }
}

// ============================================================ shared row parts

@Composable
private fun StarButton(t: Task, ctx: RowCtx) {
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(onClick = { ctx.actions.toggleImportant(t.id) }, modifier = Modifier.size(40.dp)) {
        if (t.important) Icon(Icons.Filled.Star, "Wichtig", tint = ctx.accent, modifier = Modifier.size(20.dp))
        else Icon(Icons.Outlined.StarOutline, "Als wichtig markieren", tint = sub, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TitleAndMeta(
    row: TreeRow,
    ctx: RowCtx,
    modifier: Modifier,
    depth: Int,
    showChildCount: Boolean = true,
    bold: Boolean = false,
    big: Boolean = depth == 0,
) {
    val t = row.task
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    val today = LocalDate.now().toEpochDay()
    val listName = ctx.listNames[t.listId]
    val hasAttachment = t.id in ctx.withAttachments
    Column(modifier.padding(vertical = 9.dp, horizontal = 4.dp)) {
        Text(
            t.title,
            style = if (big) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else null,
            textDecoration = if (t.done) TextDecoration.LineThrough else null,
            color = if (t.done) sub else if (depth > 0 && ctx.style != TaskStyle.CLASSIC) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.onSurface,
            maxLines = 3, overflow = TextOverflow.Ellipsis
        )
        val childMeta = showChildCount && row.childCount > 0
        val hasMeta = listName != null || childMeta || (ctx.showMyDay && t.myDay == today) ||
            t.dueDate != null || t.reminderAt != null || hasAttachment || t.note.isNotBlank()
        if (hasMeta) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            if (listName != null) Text("$listName  ", style = MaterialTheme.typography.labelSmall, color = sub)
            if (ctx.showMyDay && t.myDay == today) MetaChip(Icons.Outlined.WbSunny, "Mein Tag", sub)
            if (childMeta) MetaChip(Icons.Outlined.AccountTree, "${row.doneChildCount} von ${row.childCount}", if (ctx.style == TaskStyle.RING) ctx.accent else sub)
            t.dueDate?.let { d ->
                val overdue = d < today && !t.done
                MetaChip(Icons.Outlined.CalendarToday, formatDue(d), if (overdue) MaterialTheme.colorScheme.error else if (d == today) ctx.accent else sub)
            }
            if (t.reminderAt != null) MetaChip(Icons.Outlined.NotificationsNone, null, sub)
            if (hasAttachment) MetaChip(Icons.Outlined.AttachFile, null, sub)
            if (t.note.isNotBlank()) MetaChip(Icons.AutoMirrored.Outlined.StickyNote2, null, sub)
        }
    }
}

@Composable
fun MoveDialog(lists: List<TaskList>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("In Liste verschieben") },
        text = {
            LazyColumn {
                items(lists, key = { it.id }) { l ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(l.id) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(14.dp).background(listColor(l.color), RoundedCornerShape(7.dp)))
                        Spacer(Modifier.width(12.dp))
                        Text(l.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun AddTaskBar(autoFocus: Boolean, accent: Color, colored: Boolean, onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    val fg = if (colored) MaterialTheme.colorScheme.primary else accent
    fun submit() {
        if (text.isNotBlank()) { onAdd(text); text = "" }
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        border = if (colored) null else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(10.dp)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Add, null, tint = fg)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                if (text.isEmpty()) Text("Aufgabe hinzufügen", color = fg)
                BasicTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(fg),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(fr)
                )
            }
            if (text.isNotBlank()) IconButton(onClick = { submit() }) {
                Icon(Icons.AutoMirrored.Filled.Send, "Hinzufügen", tint = fg)
            }
        }
    }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { fr.requestFocus() } }
}
