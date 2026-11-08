package app.notesx.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.notesx.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun App(vm: NotesViewModel, act: Activity) {
    val w = calculateWindowSizeClass(act).widthSizeClass
    val compact = w == WindowWidthSizeClass.Compact
    val expanded = w == WindowWidthSizeClass.Expanded
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val editing = vm.editing
    val s = vm.settings
    BackHandler(editing != null) { vm.close() }
    val items = listOf("Note" to Icons.Filled.Description, "Organizza" to Icons.Filled.Folder, "Impostazioni" to Icons.Filled.Settings)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BgImage(s.homeBg.value, s.homeOpacity.value, s.homeBlur.value, s.homeTint.value, s.homeSat.value, Modifier.fillMaxSize())
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (compact && editing == null) NavigationBar {
                    items.forEachIndexed { i, (l, ic) ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(ic, null) }, label = { Text(l) })
                    }
                }
            }
        ) { pad ->
            Row(Modifier.padding(pad).fillMaxSize()) {
                if (!compact) NavigationRail(containerColor = Color.Transparent) {
                    items.forEachIndexed { i, (l, ic) ->
                        NavigationRailItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(ic, null) }, label = { Text(l) })
                    }
                }
                when {
                    expanded -> {
                        Box(Modifier.width(400.dp).fillMaxHeight()) { Tabs(vm, tab) { tab = it } }
                        VerticalDivider()
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            if (editing != null) EditorScreen(vm, Modifier.fillMaxSize())
                            else Text("Seleziona o crea una nota", Modifier.align(Alignment.Center),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    editing != null -> EditorScreen(vm, Modifier.weight(1f).fillMaxHeight())
                    else -> Box(Modifier.weight(1f).fillMaxHeight()) { Tabs(vm, tab) { tab = it } }
                }
            }
        }
    }
}

@Composable
fun Tabs(vm: NotesViewModel, tab: Int, onTab: (Int) -> Unit) {
    when (tab) {
        0 -> NotesList(vm)
        1 -> Organize(vm) { onTab(0) }
        else -> SettingsScreen(vm)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesList(vm: NotesViewModel) {
    val notes by vm.notes.collectAsState()
    val folders by vm.folders.collectAsState()
    val tags by vm.tags.collectAsState()
    val q by vm.query.collectAsState()
    val ff by vm.folderFilter.collectAsState()
    val tf by vm.tagFilter.collectAsState()
    var fab by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                q, { vm.query.value = it }, singleLine = true, shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Cerca nel contenuto…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (q.isNotEmpty()) IconButton({ vm.query.value = "" }) { Icon(Icons.Filled.Clear, null) } },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(ff == null && tf == null, { vm.folderFilter.value = null; vm.tagFilter.value = null }, { Text("Tutte") }) }
                items(folders) { f ->
                    FilterChip(ff == f.id, { vm.folderFilter.value = if (ff == f.id) null else f.id }, { Text(f.name) },
                        leadingIcon = { Icon(Icons.Filled.Folder, null, Modifier.size(16.dp)) })
                }
                items(tags) { t ->
                    FilterChip(tf == t.id, { vm.tagFilter.value = if (tf == t.id) null else t.id }, { Text(t.name) },
                        leadingIcon = { Box(Modifier.size(10.dp).background(Color(t.color), CircleShape)) })
                }
            }
            LazyVerticalStaggeredGrid(
                StaggeredGridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 96.dp),
                verticalItemSpacing = 8.dp, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notes, key = { it.id }) { n -> NoteCard(n, tags, vm) }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            FloatingActionButton(onClick = { fab = true }) { Icon(Icons.Filled.Add, "Nuova nota") }
            DropdownMenu(fab, { fab = false }) {
                DropdownMenuItem({ Text("Nota di testo") }, { fab = false; vm.create(0) })
                DropdownMenuItem({ Text("Sticky note") }, { fab = false; vm.create(1) })
                DropdownMenuItem({ Text("Canvas / disegno") }, { fab = false; vm.create(2) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(n: Note, tags: List<Tag>, vm: NotesViewModel) {
    var menu by remember { mutableStateOf(false) }
    val sticky = n.type == 1
    val bg = if (sticky) Color(n.color) else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (sticky) Color(0xFF1B1B1F) else MaterialTheme.colorScheme.onSurface
    Box {
        Card(
            colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg),
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { vm.open(n.id) }, onLongClick = { menu = true })
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (n.pinned) Icon(Icons.Filled.PushPin, null, Modifier.size(16.dp))
                    Text(n.title.ifBlank { "Senza titolo" }, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                val snippet = Markup.plain(n.content).take(200)
                if (snippet.isNotBlank()) Text(snippet, style = MaterialTheme.typography.bodySmall, maxLines = 6,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                if (n.type == 2 || n.strokes != "[]") Text("✏ Disegno", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                val ids = n.tagIds.split(",")
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tags.filter { ids.contains(it.id.toString()) }.forEach {
                        Box(Modifier.size(10.dp).background(Color(it.color), CircleShape))
                    }
                }
            }
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text(if (n.pinned) "Rimuovi pin" else "Fissa in alto") }, { vm.togglePin(n); menu = false })
            DropdownMenuItem({ Text("Elimina") }, { vm.delete(n); menu = false })
        }
    }
}

@Composable
fun Organize(vm: NotesViewModel, onGo: () -> Unit) {
    val folders by vm.folders.collectAsState()
    val tags by vm.tags.collectAsState()
    var dlg by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var col by remember { mutableStateOf(Palette[0]) }
    val none = ListItemDefaults.colors(containerColor = Color.Transparent)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item { Text("Cartelle", style = MaterialTheme.typography.titleMedium) }
        items(folders) { f ->
            ListItem({ Text(f.name) }, Modifier.clickable { vm.folderFilter.value = f.id; vm.tagFilter.value = null; onGo() },
                leadingContent = { Icon(Icons.Filled.Folder, null) },
                trailingContent = { IconButton({ vm.deleteFolder(f) }) { Icon(Icons.Filled.Delete, null) } }, colors = none)
        }
        item { TextButton({ dlg = 1 }) { Text("+ Nuova cartella") } }
        item { Spacer(Modifier.height(16.dp)); Text("Tag colorati", style = MaterialTheme.typography.titleMedium) }
        items(tags) { t ->
            ListItem({ Text(t.name) }, Modifier.clickable { vm.tagFilter.value = t.id; vm.folderFilter.value = null; onGo() },
                leadingContent = { Box(Modifier.size(16.dp).background(Color(t.color), CircleShape)) },
                trailingContent = { IconButton({ vm.deleteTag(t) }) { Icon(Icons.Filled.Delete, null) } }, colors = none)
        }
        item { TextButton({ dlg = 2 }) { Text("+ Nuovo tag") } }
    }
    if (dlg != 0) AlertDialog(
        onDismissRequest = { dlg = 0 },
        title = { Text(if (dlg == 1) "Nuova cartella" else "Nuovo tag") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, singleLine = true)
                if (dlg == 2) Row(Modifier.padding(top = 8.dp)) {
                    Palette.forEach { c ->
                        Box(Modifier.padding(4.dp).size(32.dp).background(c, CircleShape)
                            .then(if (c == col) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { col = c })
                    }
                }
            }
        },
        confirmButton = {
            TextButton({
                if (name.isNotBlank()) {
                    if (dlg == 1) vm.addFolder(name.trim()) else vm.addTag(name.trim(), col.toArgb())
                    name = ""; dlg = 0
                }
            }) { Text("Aggiungi") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: NotesViewModel) {
    val s = vm.settings
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val backupL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { Backup.create(ctx, uri) }.isSuccess }
            Toast.makeText(ctx, if (ok) "Backup salvato" else "Backup non riuscito", Toast.LENGTH_SHORT).show()
        }
    }
    val restoreL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreUri = it }
    val modes = listOf("Sistema", "Chiaro", "Scuro")

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Tema", style = MaterialTheme.typography.titleMedium) }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { i, l ->
                    SegmentedButton(s.themeMode.value == i, { s.themeMode.set(i) }, SegmentedButtonDefaults.itemShape(i, modes.size)) { Text(l) }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AMOLED True Black (tema scuro)", Modifier.weight(1f))
                Switch(s.amoled.value, { s.amoled.set(it) })
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Colori dinamici (Android 12+)", Modifier.weight(1f))
                Switch(s.dynamic.value, { s.dynamic.set(it) })
            }
        }
        item { HorizontalDivider(); Text("Sfondo schermata principale", style = MaterialTheme.typography.titleMedium) }
        item {
            BackgroundEditor(s.homeBg.value, s.homeOpacity.value, s.homeBlur.value, s.homeTint.value, s.homeSat.value) { p, o, b, t, sa ->
                s.homeBg.set(p); s.homeOpacity.set(o); s.homeBlur.set(b); s.homeTint.set(t); s.homeSat.set(sa)
            }
        }
        item { HorizontalDivider(); Text("Backup locale", style = MaterialTheme.typography.titleMedium) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ backupL.launch("notesx-backup.zip") }) { Text("Crea backup") }
                OutlinedButton({ restoreL.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Ripristina") }
            }
        }
    }
    restoreUri?.let { u ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Ripristinare il backup?") },
            text = { Text("Tutte le note attuali verranno sostituite e l'app si riavvierà.") },
            confirmButton = {
                TextButton({
                    restoreUri = null
                    scope.launch(Dispatchers.IO) { runCatching { Backup.restore(ctx, u) } }
                }) { Text("Ripristina") }
            },
            dismissButton = { TextButton({ restoreUri = null }) { Text("Annulla") } }
        )
    }
}
