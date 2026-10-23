package com.sueleyman.suelostodo.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.WbSunny
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sueleyman.suelostodo.data.INBOX_ID
import com.sueleyman.suelostodo.data.ListGroup
import com.sueleyman.suelostodo.data.ListWithCount
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Callbacks for group headers and list rows. */
private class HomeActions(
    val arrange: () -> Unit,
    val openList: (String) -> Unit,
    val toggle: (ListGroup) -> Unit,
    val rename: (ListGroup) -> Unit,
    val addSub: (ListGroup) -> Unit,
    val addList: (ListGroup) -> Unit,
    val dissolve: (ListGroup) -> Unit,
    val moveList: (ListWithCount) -> Unit,
    val color: (ListGroup) -> Unit,
)

/** Effective colour of a group: own choice → parent's choice → colour of its first list. */
private fun GroupNode.tint(parent: Color? = null): Color =
    group.color?.let { Color(it) } ?: parent ?: listColor(color)

/** Darker variant for text on a light tint. */
private fun Color.deep(): Color = Color(red = red * 0.7f, green = green * 0.7f, blue = blue * 0.7f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenList: (String) -> Unit, onSettings: () -> Unit, onArrange: () -> Unit = {}) {
    val vm: HomeViewModel = viewModel { HomeViewModel() }
    val state by vm.state.collectAsStateWithLifecycle()
    val style by UiPrefs.homeStyle.collectAsState()
    val userName by UiPrefs.userName.collectAsState()
    // null = closed; "" = top level; otherwise group id
    var newListIn by rememberSaveable { mutableStateOf<String?>(null) }
    var newGroupUnder by rememberSaveable { mutableStateOf<String?>(null) }
    var renameGroup by remember { mutableStateOf<ListGroup?>(null) }
    var dissolve by remember { mutableStateOf<ListGroup?>(null) }
    var moveList by remember { mutableStateOf<ListWithCount?>(null) }
    var newGroupFor by remember { mutableStateOf<ListWithCount?>(null) }
    var colorFor by remember { mutableStateOf<ListGroup?>(null) }

    val actions = HomeActions(
        arrange = onArrange,
        openList = onOpenList,
        toggle = { vm.toggleGroup(it) },
        rename = { renameGroup = it },
        addSub = { newGroupUnder = it.id },
        addList = { newListIn = it.id },
        dissolve = { dissolve = it },
        moveList = { moveList = it },
        color = { colorFor = it },
    )

    Scaffold(
        containerColor = if (style == HomeStyle.TILES || style == HomeStyle.DASHBOARD)
            MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface,
        topBar = {
            if (style == HomeStyle.GREETING) GreetingHeader(state, userName, onSettings, onArrange)
            else TopAppBar(
                title = { Text("Sülos ToDo", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onArrange) { Icon(Icons.Outlined.SwapVert, "Listen anordnen") }
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Einstellungen") }
                }
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clickable { newListIn = "" }.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Text("Neue Liste", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                }
                Row(
                    Modifier.clickable { newGroupUnder = "" }.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.CreateNewFolder, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Gruppe", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            // ---- smart lists / head area
            when (style) {
                HomeStyle.TILES -> item { SmartTiles(state, onOpenList) }
                HomeStyle.GREETING -> {
                    item { Spacer(Modifier.height(4.dp)) }
                    compactSmartRows(state, onOpenList)
                    item { HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                }
                HomeStyle.DASHBOARD -> item { DashboardCard(state, onOpenList) }
                else -> smartRows(state, onOpenList)
            }
            if (style == HomeStyle.BANDS || style == HomeStyle.ACCENT) item { Spacer(Modifier.height(6.dp)) }

            // ---- groups
            items(state.tree.groups, key = { "g" + it.group.id }) { node ->
                Box(Modifier.animateItem()) {
                    when (style) {
                        HomeStyle.TILES -> GroupCard(node, actions)
                        else -> GroupBlock(node, style, actions)
                    }
                }
            }
            // ---- lists without group
            items(state.tree.loose, key = { "l" + it.id }) { l ->
                Box(Modifier.animateItem()) { ListNavRow(l, 0, style, null, actions) }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    newListIn?.let { gid ->
        TextInputDialog(
            title = if (gid.isEmpty()) "Neue Liste" else "Neue Liste in \"${state.groups.firstOrNull { it.id == gid }?.name}\"",
            confirm = "Liste erstellen", withColor = 0,
            onDismiss = { newListIn = null },
            onConfirm = { name, color ->
                newListIn = null
                vm.createList(name, color ?: 0, gid.ifEmpty { null }) { onOpenList(it) }
            }
        )
    }
    newGroupUnder?.let { parent ->
        NewGroupDialog(
            groups = state.groups, presetParent = parent.ifEmpty { null },
            onDismiss = { newGroupUnder = null },
            onCreate = { name, p -> newGroupUnder = null; vm.createGroup(name, p) }
        )
    }
    colorFor?.let { g ->
        GroupColorDialog(
            name = g.name, initial = g.color,
            onDismiss = { colorFor = null },
            onPick = { c -> colorFor = null; vm.setGroupColor(g.id, c) }
        )
    }
    renameGroup?.let { g ->
        TextInputDialog(
            title = "Gruppe umbenennen", initial = g.name,
            onDismiss = { renameGroup = null },
            onConfirm = { name, _ -> renameGroup = null; vm.renameGroup(g.id, name) }
        )
    }
    dissolve?.let { g ->
        ConfirmDialog(
            title = "Gruppe auflösen?",
            text = "\"${g.name}\" wird entfernt. Listen und Untergruppen bleiben erhalten und rücken eine Ebene nach oben.",
            confirm = "Auflösen",
            onDismiss = { dissolve = null },
            onConfirm = { dissolve = null; vm.dissolveGroup(g.id) }
        )
    }
    moveList?.let { l ->
        GroupPickDialog(
            title = "\"${l.name}\" verschieben", groups = state.groups, current = l.groupId,
            onDismiss = { moveList = null },
            onPick = { moveList = null; vm.moveList(l.id, it) },
            onNewGroup = { moveList = null; newGroupFor = l }
        )
    }
    newGroupFor?.let { l ->
        NewGroupDialog(
            groups = state.groups, presetParent = null,
            onDismiss = { newGroupFor = null },
            onCreate = { name, p -> newGroupFor = null; vm.createGroupAndMove(name, p, l.id) }
        )
    }
}

// =====================================================================================
// head areas
// =====================================================================================

private data class Smart(val icon: ImageVector, val title: String, val color: Color, val count: Int, val id: String)

private fun smartItems(state: HomeState): List<Smart> {
    val inbox = state.lists.firstOrNull { it.id == INBOX_ID }
    return listOfNotNull(
        Smart(Icons.Outlined.WbSunny, "Mein Tag", SmartColors.myDay, state.smartCounts[SmartList.MY_DAY] ?: 0, SmartList.MY_DAY.id),
        Smart(Icons.Outlined.StarOutline, "Wichtig", SmartColors.important, state.smartCounts[SmartList.IMPORTANT] ?: 0, SmartList.IMPORTANT.id),
        Smart(Icons.Outlined.CalendarMonth, "Geplant", SmartColors.planned, state.smartCounts[SmartList.PLANNED] ?: 0, SmartList.PLANNED.id),
        inbox?.let { Smart(Icons.Outlined.Home, it.name, listColor(it.color), it.openCount, INBOX_ID) },
    )
}

private fun LazyListScope.smartRows(state: HomeState, open: (String) -> Unit) {
    items(smartItems(state), key = { "s" + it.id }) { s -> NavRow(s.icon, s.color, s.title, s.count) { open(s.id) } }
}

@Composable
private fun SmartTiles(state: HomeState, open: (String) -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        smartItems(state).chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { s ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = s.color.copy(alpha = 0.14f).compositeOver(surface),
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { open(s.id) }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Icon(s.icon, null, tint = s.color, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.title, color = s.color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text("${s.count}", color = s.color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun GreetingHeader(state: HomeState, name: String, onSettings: () -> Unit, onArrange: () -> Unit) {
    val hour = LocalTime.now().hour
    val hello = when { hour < 11 -> "Guten Morgen"; hour < 18 -> "Guten Tag"; else -> "Guten Abend" }
    val today = when (state.todayCount) { 0 -> "nichts fällig"; 1 -> "1 Aufgabe für heute"; else -> "${state.todayCount} Aufgaben für heute" }
    // compact: two lines, no button row
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF0C447C)).statusBarsPadding()
            .padding(start = 18.dp, end = 4.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (name.isBlank()) "$hello!" else "$hello, ${name.trim()}",
                color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1
            )
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EE, d. MMM", Locale.GERMAN)) + " · " + today,
                color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, maxLines = 1
            )
        }
        IconButton(onClick = onArrange) { Icon(Icons.Outlined.SwapVert, "Listen anordnen", tint = Color.White) }
        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Einstellungen", tint = Color.White) }
    }
}

/** Compact smart-list rows for the greeting layout. */
private fun LazyListScope.compactSmartRows(state: HomeState, open: (String) -> Unit) {
    items(smartItems(state), key = { "s" + it.id }) { s ->
        Row(
            Modifier.fillMaxWidth().clickable { open(s.id) }.padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(s.icon, null, tint = s.color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text(s.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (s.count > 0) Text("${s.count}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DashboardCard(state: HomeState, open: (String) -> Unit) {
    val c = SmartColors.myDay
    val surface = MaterialTheme.colorScheme.surface
    val frac = if (state.myDayTotal == 0) 0f else state.myDayDone.toFloat() / state.myDayTotal
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp), color = c.copy(alpha = 0.14f).compositeOver(surface),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { open(SmartList.MY_DAY.id) }
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Ring(frac, c, 52.dp, 6.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Mein Tag", color = c, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (state.myDayTotal == 0) "Noch nichts geplant" else "${state.myDayDone} von ${state.myDayTotal} erledigt",
                        color = c, style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            smartItems(state).drop(1).forEach { s ->
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .clickable { open(s.id) }.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(s.icon, null, tint = s.color, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.weight(1f))
                    Text("${s.count}", style = MaterialTheme.typography.labelLarge, color = s.color)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun Ring(frac: Float, color: Color, size: Dp, stroke: Dp) {
    Canvas(Modifier.size(size)) {
        val sw = stroke.toPx()
        val d = this.size.minDimension - sw
        val tl = Offset(sw / 2, sw / 2)
        drawArc(color.copy(alpha = 0.2f), 0f, 360f, false, tl, Size(d, d), style = Stroke(sw))
        drawArc(color, -90f, 360f * frac, false, tl, Size(d, d), style = Stroke(sw))
    }
}

// =====================================================================================
// groups
// =====================================================================================

/** Group header + content for styles BANDS, ACCENT, GREETING, DASHBOARD. */
@Composable
private fun GroupBlock(node: GroupNode, style: HomeStyle, a: HomeActions) {
    Column(Modifier.animateContentSize()) {
        val tint = node.tint()
        GroupHeader(node, style, a)
        if (node.group.expanded) {
            node.lists.forEach { ListNavRow(it, 1, style, tint, a) }
            node.subs.forEach { sub ->
                val subTint = sub.tint(tint)
                GroupHeader(sub, style, a, parentTint = tint)
                if (sub.group.expanded) sub.lists.forEach { ListNavRow(it, 2, style, subTint, a) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupHeader(node: GroupNode, style: HomeStyle, a: HomeActions, parentTint: Color? = null) {
    val g = node.group
    var menu by remember { mutableStateOf(false) }
    val sub = node.depth > 0
    val accent = node.tint(parentTint)
    val surface = MaterialTheme.colorScheme.surface
    val variant = MaterialTheme.colorScheme.surfaceVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val click = Modifier.combinedClickable(onClick = { a.toggle(g) }, onLongClick = { menu = true })
    val rot by animateFloatAsState(if (g.expanded) 90f else 0f, label = "grp")
    val chevron: @Composable (Color) -> Unit = { tint ->
        Icon(Icons.Filled.ChevronRight, if (g.expanded) "Zuklappen" else "Aufklappen",
            tint = tint, modifier = Modifier.size(20.dp).rotate(rot))
    }
    val menuButton: @Composable (Color) -> Unit = { tint ->
        IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.MoreVert, "Gruppenmenü", tint = tint, modifier = Modifier.size(18.dp))
        }
    }

    Box {
        when (style) {
            HomeStyle.ACCENT -> {
                val bg = if (sub) Color.Transparent else accent.copy(alpha = 0.14f).compositeOver(surface)
                val fg = accent.deep()
                Row(
                    Modifier.fillMaxWidth().padding(start = if (sub) 14.dp else 0.dp, top = if (sub) 0.dp else 3.dp)
                        .height(IntrinsicSize.Min).background(bg).then(click),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(if (sub) accent.copy(alpha = 0.45f) else accent))
                    Spacer(Modifier.width(10.dp))
                    chevron(fg)
                    Spacer(Modifier.width(6.dp))
                    Text(g.name, color = fg, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                    if (!g.expanded && node.open > 0) Text("${node.open}", color = fg, style = MaterialTheme.typography.bodyMedium)
                    menuButton(fg)
                    Spacer(Modifier.width(8.dp))
                }
            }
            HomeStyle.GREETING -> {
                val fg = accent.deep()
                if (!sub) Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp).clip(RoundedCornerShape(24.dp))
                        .background(accent.copy(alpha = 0.18f).compositeOver(surface)).then(click).padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    chevron(fg)
                    Spacer(Modifier.width(8.dp))
                    Text(g.name, color = fg, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(vertical = 7.dp))
                    Text("${node.open}", color = Color.White, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(accent).padding(horizontal = 8.dp, vertical = 2.dp))
                    menuButton(fg)
                } else Row(
                    Modifier.fillMaxWidth().then(click).padding(start = 34.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (g.expanded) Icons.Outlined.FolderOpen else Icons.Outlined.Folder, null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(g.name, color = fg, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
                    if (!g.expanded && node.open > 0) Text("${node.open}", color = muted, style = MaterialTheme.typography.bodySmall)
                    menuButton(muted)
                }
            }
            HomeStyle.DASHBOARD -> {
                Column(
                    Modifier.fillMaxWidth()
                        .padding(start = if (sub) 30.dp else 12.dp, end = 12.dp, top = 3.dp, bottom = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sub) variant.copy(alpha = 0.45f) else variant.copy(alpha = 0.85f))
                        .then(click).padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        chevron(muted)
                        Spacer(Modifier.width(6.dp))
                        Text(g.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${(node.progress * 100).toInt()} %", style = MaterialTheme.typography.labelMedium, color = muted)
                        menuButton(muted)
                    }
                    LinearProgressIndicator(
                        progress = { node.progress }, color = accent, trackColor = accent.copy(alpha = 0.18f),
                        modifier = Modifier.fillMaxWidth().padding(start = 26.dp, end = 10.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                    )
                }
            }
            else -> { // BANDS
                val bg = if (sub) variant.copy(alpha = 0.35f).compositeOver(surface) else variant.copy(alpha = 0.8f).compositeOver(surface)
                Column {
                    if (!sub) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    Row(
                        Modifier.fillMaxWidth().background(bg).then(click).padding(start = if (sub) 34.dp else 14.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        chevron(muted)
                        Spacer(Modifier.width(8.dp))
                        Icon(if (g.expanded) Icons.Outlined.FolderOpen else Icons.Outlined.Folder, null, tint = muted, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(g.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                        if (!g.expanded && node.open > 0) Text("${node.open}", color = muted, style = MaterialTheme.typography.bodyMedium)
                        menuButton(muted)
                    }
                    if (!sub) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                }
            }
        }
        GroupMenu(g, menu, a) { menu = false }
    }
}

/** Style TILES: each top-level group is a card with a shaded head. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupCard(node: GroupNode, a: HomeActions) {
    val g = node.group
    var menu by remember { mutableStateOf(false) }
    val variant = MaterialTheme.colorScheme.surfaceVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val rot by animateFloatAsState(if (g.expanded) 90f else 0f, label = "card")
    Surface(
        shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Column(Modifier.animateContentSize()) {
            Box {
                Row(
                    Modifier.fillMaxWidth().background(variant.copy(alpha = 0.8f))
                        .combinedClickable(onClick = { a.toggle(g) }, onLongClick = { menu = true })
                        .padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(node.tint()))
                    Spacer(Modifier.width(10.dp))
                    Text(g.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                    Text("${node.open}", color = muted, style = MaterialTheme.typography.bodyMedium)
                    Icon(Icons.Filled.ChevronRight, null, tint = muted, modifier = Modifier.padding(start = 4.dp).size(20.dp).rotate(rot))
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.MoreVert, "Gruppenmenü", tint = muted, modifier = Modifier.size(18.dp))
                    }
                }
                GroupMenu(g, menu, a) { menu = false }
            }
            if (g.expanded) {
                node.lists.forEach { ListNavRow(it, 0, HomeStyle.TILES, null, a) }
                node.subs.forEach { sub -> key(sub.group.id) {
                    var subMenu by remember { mutableStateOf(false) }
                    Box {
                        Row(
                            Modifier.fillMaxWidth().background(variant.copy(alpha = 0.35f))
                                .combinedClickable(onClick = { a.toggle(sub.group) }, onLongClick = { subMenu = true })
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(sub.group.name.uppercase(), color = muted, fontSize = 11.sp, letterSpacing = 0.8.sp,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (!sub.group.expanded) Text("${sub.open}", color = muted, fontSize = 11.sp)
                        }
                        GroupMenu(sub.group, subMenu, a) { subMenu = false }
                    }
                    if (sub.group.expanded) sub.lists.forEach { ListNavRow(it, 1, HomeStyle.TILES, null, a) }
                } }
                if (node.lists.isEmpty() && node.subs.isEmpty()) Text(
                    "Leer – über ⋮ eine Liste hinzufügen", color = muted, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

@Composable
private fun GroupMenu(g: ListGroup, expanded: Boolean, a: HomeActions, onDismiss: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Umbenennen") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
            onClick = { onDismiss(); a.rename(g) })
        DropdownMenuItem(text = { Text("Liste hinzufügen") }, leadingIcon = { Icon(Icons.Filled.Add, null) },
            onClick = { onDismiss(); a.addList(g) })
        DropdownMenuItem(text = { Text("Farbe wählen") }, leadingIcon = { Icon(Icons.Outlined.Palette, null) },
            onClick = { onDismiss(); a.color(g) })
        if (g.parentId == null) DropdownMenuItem(
            text = { Text("Untergruppe anlegen") }, leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) },
            onClick = { onDismiss(); a.addSub(g) })
        DropdownMenuItem(text = { Text("Anordnen …") }, leadingIcon = { Icon(Icons.Outlined.SwapVert, null) },
            onClick = { onDismiss(); a.arrange() })
        DropdownMenuItem(text = { Text("Gruppe auflösen") }, leadingIcon = { Icon(Icons.Outlined.FolderOff, null) },
            onClick = { onDismiss(); a.dissolve(g) })
    }
}

// =====================================================================================
// list rows
// =====================================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListNavRow(l: ListWithCount, depth: Int, style: HomeStyle, groupTint: Color?, a: HomeActions) {
    var menu by remember { mutableStateOf(false) }
    // style 3 and 5 tint the lists in the group colour
    val tint = if (groupTint != null && (style == HomeStyle.ACCENT || style == HomeStyle.DASHBOARD || style == HomeStyle.GREETING))
        groupTint else listColor(l.color)
    val vPad = if (style == HomeStyle.GREETING) 8.dp else 13.dp
    val indent = when (style) {
        HomeStyle.TILES -> 14 + depth * 16
        HomeStyle.ACCENT -> 18 + depth * 18
        else -> 20 + depth * 24
    }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { a.openList(l.id) }, onLongClick = { menu = true })
                .padding(start = indent.dp, end = 20.dp, top = vPad, bottom = vPad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Outlined.List, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Text(l.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (l.openCount > 0) Text(l.openCount.toString(), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("In Gruppe verschieben") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) },
                onClick = { menu = false; a.moveList(l) })
            DropdownMenuItem(text = { Text("Anordnen …") },
                leadingIcon = { Icon(Icons.Outlined.SwapVert, null) },
                onClick = { menu = false; a.arrange() })
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, tint: Color, title: String, count: Int?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(18.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (count != null && count > 0) {
            Text(count.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
