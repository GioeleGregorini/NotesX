package app.notesx.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.FormatListNumbered
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.notesx.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: NotesViewModel, modifier: Modifier = Modifier) {
    val n = vm.editing ?: return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val folders by vm.folders.collectAsState()
    val tags by vm.tags.collectAsState()

    var mode by remember(n.id) { mutableIntStateOf(if (n.type == 2) 1 else 0) } // 0 testo, 1 disegno, 2 anteprima
    var sheet by remember { mutableIntStateOf(0) }                              // 1 sfondo, 2 sticker
    var more by remember { mutableStateOf(false) }
    var dlg by remember { mutableIntStateOf(0) }                                // 1 cartella, 2 tag, 3 colore
    var tf by remember(n.id) { mutableStateOf(TextFieldValue(n.content)) }
    val strokes = remember(n.id) { mutableStateListOf<Ink>().apply { addAll(Codec.inks(n.strokes)) } }
    val stickers = remember(n.id) { mutableStateListOf<PlacedSticker>().apply { addAll(Codec.stickers(n.stickers)) } }
    val live = remember { mutableStateListOf<Float>() }
    var pen by remember { mutableStateOf(Color.Black) }
    var penW by remember { mutableFloatStateOf(0.006f) }
    var eraser by remember { mutableStateOf(false) }

    fun applyTf(v: TextFieldValue) {
        val nv = Markup.autoList(tf, v)
        tf = nv
        if (nv.text != n.content) vm.edit { it.copy(content = nv.text) }
    }
    fun saveStrokes() = vm.edit { it.copy(strokes = Codec.inksJson(strokes.toList())) }
    fun saveStickers() = vm.edit { it.copy(stickers = Codec.stickersJson(stickers.toList())) }
    fun export(fmt: Int) {
        val snap = vm.editing ?: return
        scope.launch {
            val r = withContext(Dispatchers.Default) { runCatching { Exporter.export(ctx, snap, fmt) }.getOrNull() }
            if (r != null) Exporter.share(ctx, r.first, r.second)
        }
    }

    Column(modifier.imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ vm.close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Testo", "Disegno", "Anteprima").forEachIndexed { i, l -> FilterChip(mode == i, { mode = i }, { Text(l) }) }
            }
            IconButton({ sheet = 1 }) { Icon(Icons.Filled.Image, "Sfondo") }
            IconButton({ sheet = 2 }) { Icon(Icons.Filled.EmojiEmotions, "Sticker") }
            Box {
                IconButton({ more = true }) { Icon(Icons.Filled.MoreVert, "Altro") }
                DropdownMenu(more, { more = false }) {
                    DropdownMenuItem({ Text(if (n.pinned) "Rimuovi pin" else "Fissa in alto") }, { more = false; vm.edit { it.copy(pinned = !it.pinned) } })
                    DropdownMenuItem({ Text("Cartella…") }, { more = false; dlg = 1 })
                    DropdownMenuItem({ Text("Tag…") }, { more = false; dlg = 2 })
                    if (n.type == 1) DropdownMenuItem({ Text("Colore nota…") }, { more = false; dlg = 3 })
                    HorizontalDivider()
                    DropdownMenuItem({ Text("Esporta PDF") }, { more = false; export(0) })
                    DropdownMenuItem({ Text("Esporta immagine PNG") }, { more = false; export(1) })
                    DropdownMenuItem({ Text("Esporta Markdown") }, { more = false; export(2) })
                    DropdownMenuItem({ Text("Esporta testo") }, { more = false; export(3) })
                    HorizontalDivider()
                    DropdownMenuItem({ Text("Elimina nota") }, { more = false; vm.deleteCurrent() })
                }
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val pageW = minOf(maxWidth - 16.dp, (maxHeight - 16.dp) / 1.414f, 720.dp).coerceAtLeast(120.dp)
            val pageH = pageW * 1.414f
            val density = LocalDensity.current
            val wPx = with(density) { pageW.toPx() }
            val hPx = with(density) { pageH.toPx() }
            val scale = pageW.value / 400f
            val textColor = if (n.darkPage()) Color.White else Color(0xFF1B1B1F)
            val fs = (16f * scale).sp
            val paper = if (n.type == 1) Color(n.color) else Color.White

            Box(Modifier.size(pageW, pageH).shadow(4.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).background(paper)) {
                BgImage(n.bgUri, n.bgOpacity, n.bgBlur, n.bgTint, n.bgSat, Modifier.fillMaxSize())

                // testo
                Column(Modifier.fillMaxSize().padding((24f * scale).dp).verticalScroll(rememberScrollState())) {
                    BasicTextField(
                        value = n.title, onValueChange = { t -> vm.edit { it.copy(title = t) } },
                        readOnly = mode != 0, singleLine = true,
                        textStyle = TextStyle(fontSize = (22.4f * scale).sp, fontWeight = FontWeight.Bold, color = textColor),
                        cursorBrush = SolidColor(textColor),
                        decorationBox = { inner ->
                            if (n.title.isEmpty()) Text("Titolo", fontSize = (22.4f * scale).sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.4f))
                            inner()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (mode == 0) {
                        BasicTextField(
                            value = tf, onValueChange = { applyTf(it) },
                            textStyle = TextStyle(fontSize = fs, color = textColor),
                            cursorBrush = SolidColor(textColor),
                            visualTransformation = remember(textColor) { MarkupTransformation(textColor.copy(alpha = 0.35f)) },
                            decorationBox = { inner ->
                                if (tf.text.isEmpty()) Text("Scrivi qualcosa…", fontSize = fs, color = textColor.copy(alpha = 0.4f))
                                inner()
                            },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 200.dp)
                        )
                    } else {
                        Preview(n.content, fs, textColor, interactive = mode == 2) { idx ->
                            val t = Markup.toggleLine(n.content, idx)
                            tf = TextFieldValue(t)
                            vm.edit { it.copy(content = t) }
                        }
                    }
                }

                // tratti
                Canvas(Modifier.fillMaxSize()) {
                    fun draw(color: Int, w: Float, pts: List<Float>) {
                        if (pts.size < 4) return
                        val p = Path()
                        p.moveTo(pts[0] * size.width, pts[1] * size.height)
                        for (i in 1 until pts.size / 2) p.lineTo(pts[2 * i] * size.width, pts[2 * i + 1] * size.height)
                        drawPath(p, Color(color), style = Stroke(width = w * size.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    strokes.forEach { draw(it.color, it.width, it.pts) }
                    if (live.isNotEmpty()) draw(pen.toArgb(), penW, live.toList())
                }

                // sticker
                stickers.toList().forEach { st ->
                    key(st.id) {
                        StickerView(st, wPx, hPx,
                            onChange = { ns -> val i = stickers.indexOfFirst { it.id == st.id }; if (i >= 0) { stickers[i] = ns; saveStickers() } },
                            onDelete = { stickers.removeAll { it.id == st.id }; saveStickers() })
                    }
                }

                // input disegno (stilo o dito)
                if (mode == 1) {
                    fun erase(o: Offset) {
                        val r = 0.03f * wPx
                        val before = strokes.size
                        strokes.removeAll { s -> (0 until s.pts.size / 2).any { i -> hypot(s.pts[2 * i] * wPx - o.x, s.pts[2 * i + 1] * hPx - o.y) < r } }
                        if (strokes.size != before) saveStrokes()
                    }
                    Box(Modifier.fillMaxSize().pointerInput(eraser, pen, penW) {
                        detectDragGestures(
                            onDragStart = { o ->
                                if (eraser) erase(o) else { live.clear(); live.add(o.x / size.width); live.add(o.y / size.height) }
                            },
                            onDrag = { ch, _ ->
                                ch.consume()
                                val p = ch.position
                                if (eraser) erase(p) else { live.add(p.x / size.width); live.add(p.y / size.height) }
                            },
                            onDragEnd = {
                                if (!eraser && live.size >= 4) { strokes.add(Ink(pen.toArgb(), penW, live.toList())); saveStrokes() }
                                live.clear()
                            },
                            onDragCancel = { live.clear() }
                        )
                    })
                }
            }
        }

        // barra strumenti
        Surface(tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                when (mode) {
                    0 -> {
                        IconButton({ applyTf(Markup.wrap(tf, "**")) }) { Icon(Icons.Filled.FormatBold, "Grassetto") }
                        IconButton({ applyTf(Markup.wrap(tf, "*")) }) { Icon(Icons.Filled.FormatItalic, "Corsivo") }
                        IconButton({ applyTf(Markup.wrap(tf, "__")) }) { Icon(Icons.Filled.FormatUnderlined, "Sottolineato") }
                        IconButton({ applyTf(Markup.prefixLine(tf, "- ")) }) { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, "Elenco puntato") }
                        IconButton({ applyTf(Markup.prefixLine(tf, "1. ")) }) { Icon(Icons.AutoMirrored.Filled.FormatListNumbered, "Elenco numerato") }
                        IconButton({ applyTf(Markup.prefixLine(tf, "- [ ] ")) }) { Icon(Icons.Filled.CheckBox, "To-do") }
                    }
                    1 -> {
                        listOf(Color.Black, Color(0xFFD32F2F), Color(0xFF1976D2), Color(0xFF388E3C), Color(0xFFF57C00), Color.White).forEach { c ->
                            Box(Modifier.padding(4.dp).size(30.dp).background(c, CircleShape)
                                .border(if (c == pen && !eraser) 3.dp else 1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (c == pen && !eraser) 1f else 0.3f), CircleShape)
                                .clickable { pen = c; eraser = false })
                        }
                        Slider(penW, { penW = it }, valueRange = 0.002f..0.03f, modifier = Modifier.width(120.dp))
                        FilterChip(eraser, { eraser = !eraser }, { Text("Gomma") })
                        IconButton({ if (strokes.isNotEmpty()) { strokes.removeAt(strokes.lastIndex); saveStrokes() } }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, "Annulla")
                        }
                        TextButton({ strokes.clear(); saveStrokes() }) { Text("Pulisci") }
                    }
                    else -> Text("Tocca ☐ per spuntare i to-do · tieni premuto uno sticker per eliminarlo",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }

    if (sheet == 1) ModalBottomSheet({ sheet = 0 }) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Sfondo della nota", style = MaterialTheme.typography.titleMedium)
            BackgroundEditor(n.bgUri, n.bgOpacity, n.bgBlur, n.bgTint, n.bgSat) { p, o, b, t, sa ->
                vm.edit { it.copy(bgUri = p, bgOpacity = o, bgBlur = b, bgTint = t, bgSat = sa) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (sheet == 2) StickerSheet(vm, onPick = { src ->
        stickers.add(PlacedSticker(UUID.randomUUID().toString(), src, 0.5f, 0.5f, 1f, 0f)); saveStickers()
    }, onDismiss = { sheet = 0 })

    if (dlg == 1) AlertDialog(
        onDismissRequest = { dlg = 0 }, title = { Text("Cartella") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth().clickable { vm.edit { it.copy(folderId = null) } }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(n.folderId == null, { vm.edit { it.copy(folderId = null) } }); Text("Nessuna")
                }
                folders.forEach { f ->
                    Row(Modifier.fillMaxWidth().clickable { vm.edit { it.copy(folderId = f.id) } }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(n.folderId == f.id, { vm.edit { it.copy(folderId = f.id) } }); Text(f.name)
                    }
                }
            }
        },
        confirmButton = { TextButton({ dlg = 0 }) { Text("Fatto") } }
    )
    if (dlg == 2) AlertDialog(
        onDismissRequest = { dlg = 0 }, title = { Text("Tag") },
        text = {
            Column {
                if (tags.isEmpty()) Text("Crea i tag dalla scheda Organizza.")
                val ids = n.tagIds.split(",").filter { it.isNotBlank() }.toSet()
                tags.forEach { t ->
                    val on = ids.contains(t.id.toString())
                    val toggle = {
                        val ni = if (on) ids - t.id.toString() else ids + t.id.toString()
                        vm.edit { it.copy(tagIds = ni.joinToString(",")) }
                    }
                    Row(Modifier.fillMaxWidth().clickable { toggle() }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(on, { toggle() })
                        Box(Modifier.size(12.dp).background(Color(t.color), CircleShape)); Spacer(Modifier.width(8.dp)); Text(t.name)
                    }
                }
            }
        },
        confirmButton = { TextButton({ dlg = 0 }) { Text("Fatto") } }
    )
    if (dlg == 3) AlertDialog(
        onDismissRequest = { dlg = 0 }, title = { Text("Colore nota") },
        text = {
            Row {
                StickyColors.forEach { c ->
                    Box(Modifier.padding(4.dp).size(36.dp).background(Color(c), CircleShape)
                        .then(if (c == n.color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                        .clickable { vm.edit { it.copy(color = c) } })
                }
            }
        },
        confirmButton = { TextButton({ dlg = 0 }) { Text("Fatto") } }
    )
}
