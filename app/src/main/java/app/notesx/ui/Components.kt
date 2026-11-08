package app.notesx.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.notesx.data.Markup
import app.notesx.data.Media
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

val Palette = listOf(
    Color(0xFFE57373), Color(0xFFFFB74D), Color(0xFFFFF176),
    Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFBA68C8)
)

/** Sfondo con opacità, blur, desaturazione e filtro colore. */
@Composable
fun BgImage(path: String?, opacity: Float, blur: Float, tint: Int, sat: Float, modifier: Modifier = Modifier) {
    Box(modifier) {
        if (path != null) {
            AsyncImage(
                model = File(path), contentDescription = null, contentScale = ContentScale.Crop,
                alpha = opacity,
                colorFilter = if (sat < 1f) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(sat) }) else null,
                modifier = Modifier.fillMaxSize().blur(blur.dp)
            )
        }
        if (tint != 0) Box(Modifier.fillMaxSize().background(Color(tint)))
    }
}

@Composable
fun LabeledSlider(label: String, v: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Slider(value = v, onValueChange = onChange, valueRange = range)
    }
}

/** Editor sfondo: galleria, fotocamera, URL/ricerca web + opacità, sfocatura, saturazione, filtro colore. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackgroundEditor(
    path: String?, opacity: Float, blur: Float, tint: Int, sat: Float,
    onChange: (String?, Float, Float, Int, Float) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var web by remember { mutableStateOf(false) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { Media.import(ctx, uri, "bg")?.let { onChange(it, opacity, blur, tint, sat) } }
    }
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = camUri
        if (ok && u != null) scope.launch { Media.import(ctx, u, "bg")?.let { onChange(it, opacity, blur, tint, sat) } }
    }
    fun shoot() {
        val f = File(ctx.cacheDir, "cam_${System.nanoTime()}.jpg")
        val u = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        camUri = u
        cam.launch(u)
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) shoot() }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                label = { Text("Galleria") }, leadingIcon = { Icon(Icons.Filled.Image, null) })
            AssistChip(onClick = {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) shoot()
                else perm.launch(Manifest.permission.CAMERA)
            }, label = { Text("Fotocamera") }, leadingIcon = { Icon(Icons.Filled.PhotoCamera, null) })
            AssistChip(onClick = { web = true }, label = { Text("URL / Cerca") }, leadingIcon = { Icon(Icons.Filled.Link, null) })
            if (path != null) AssistChip(onClick = { onChange(null, 1f, 0f, 0, 1f) },
                label = { Text("Rimuovi") }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
        }
        LabeledSlider("Opacità", opacity, 0f..1f) { onChange(path, it, blur, tint, sat) }
        LabeledSlider("Sfocatura (Android 12+)", blur, 0f..25f) { onChange(path, opacity, it, tint, sat) }
        LabeledSlider("Saturazione (0 = bianco e nero)", sat, 0f..1f) { onChange(path, opacity, blur, tint, it) }
        Text("Filtro colore", style = MaterialTheme.typography.bodySmall)
        val strength = Color(tint).alpha
        val base = if (tint == 0) Color.Black else Color(tint).copy(alpha = 1f)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onChange(path, opacity, blur, 0, sat) }) { Text("Nessuno") }
            listOf(Color.Black, Color.White, Color(0xFF704214), Color(0xFF0D1B3E), Color(0xFF1B5E20)).forEach { c ->
                Box(Modifier.size(32.dp).background(c, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onChange(path, opacity, blur, c.copy(alpha = if (strength < 0.05f) 0.4f else strength).toArgb(), sat) })
            }
        }
        LabeledSlider("Intensità filtro", strength, 0f..0.9f) {
            onChange(path, opacity, blur, if (it < 0.02f) 0 else base.copy(alpha = it).toArgb(), sat)
        }
    }
    if (web) WebImageDialog(onDismiss = { web = false }) { u ->
        web = false
        scope.launch {
            val p = Media.download(ctx, u, "bg")
            if (p != null) onChange(p, opacity, blur, tint, sat)
            else Toast.makeText(ctx, "Download non riuscito", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun WebImageDialog(onDismiss: () -> Unit, onUrl: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
        title = { Text("Immagine dal web") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Incolla un URL o cerca…") })
                Spacer(Modifier.height(8.dp))
                if (q.startsWith("http")) Button(onClick = { onUrl(q.trim()) }) { Text("Usa URL") }
                else Button(enabled = q.isNotBlank() && !busy, onClick = {
                    busy = true
                    scope.launch { res = Media.search(q.trim()); busy = false }
                }) { Text("Cerca") }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                LazyVerticalGrid(GridCells.Adaptive(90.dp), Modifier.heightIn(max = 300.dp).padding(top = 8.dp)) {
                    items(res) { u ->
                        AsyncImage(u, null, Modifier.aspectRatio(1f).padding(2.dp).clickable { onUrl(u) },
                            contentScale = ContentScale.Crop)
                    }
                }
            }
        }
    )
}

/** Mostra **grassetto**, *corsivo*, __sottolineato__ mentre si scrive (i simboli restano visibili ma attenuati). */
class MarkupTransformation(private val dim: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val b = AnnotatedString.Builder(s)
        Markup.rx.findAll(s).forEach { m ->
            val st = m.range.first
            val en = m.range.last + 1
            val g = m.groups
            val (style, mk) = when {
                g[1] != null -> SpanStyle(fontWeight = FontWeight.Bold) to 2
                g[2] != null -> SpanStyle(textDecoration = TextDecoration.Underline) to 2
                else -> SpanStyle(fontStyle = FontStyle.Italic) to 1
            }
            b.addStyle(style, st, en)
            b.addStyle(SpanStyle(color = dim), st, st + mk)
            b.addStyle(SpanStyle(color = dim), en - mk, en)
        }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}

fun annotated(body: String): AnnotatedString = buildAnnotatedString {
    Markup.runs(body).forEach {
        withStyle(SpanStyle(
            fontWeight = if (it.b) FontWeight.Bold else null,
            fontStyle = if (it.i) FontStyle.Italic else null,
            textDecoration = if (it.u) TextDecoration.Underline else null
        )) { append(it.text) }
    }
}

@Composable
fun Preview(content: String, fs: TextUnit, color: Color, interactive: Boolean, onToggle: (Int) -> Unit) {
    Column {
        content.lines().forEachIndexed { idx, ln ->
            val l = Markup.parseLine(ln)
            Row(verticalAlignment = Alignment.Top) {
                if (l.kind != 0) Text(
                    l.prefix(), fontSize = fs, color = color,
                    modifier = Modifier.clickable(enabled = interactive && l.kind == 3) { onToggle(idx) }
                )
                Text(
                    annotated(l.body), fontSize = fs,
                    color = if (l.kind == 3 && l.checked) color.copy(alpha = 0.55f) else color,
                    textDecoration = if (l.kind == 3 && l.checked) TextDecoration.LineThrough else null
                )
            }
        }
    }
}
