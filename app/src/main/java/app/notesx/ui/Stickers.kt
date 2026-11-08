package app.notesx.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.notesx.data.Media
import app.notesx.data.PlacedSticker
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

val Builtin = listOf(
    "❤️" to "cuore amore love heart", "⭐" to "stella star", "🔥" to "fuoco fire caldo",
    "😀" to "sorriso felice smile happy", "😂" to "risata ridere lol", "😍" to "innamorato love occhi",
    "😎" to "cool occhiali sole", "😢" to "triste piangere sad", "😡" to "arrabbiato rabbia angry",
    "👍" to "ok pollice bene like", "👏" to "applauso bravo", "🙏" to "grazie prego thanks",
    "🎉" to "festa party coriandoli", "🎂" to "torta compleanno birthday", "🎁" to "regalo gift",
    "📌" to "pin spillo importante", "📎" to "graffetta allegato", "✅" to "fatto check ok done",
    "❗" to "attenzione importante alert", "❓" to "domanda question", "💡" to "idea lampadina",
    "📅" to "calendario data agenda", "⏰" to "sveglia orologio tempo", "📚" to "libri studio book",
    "✏️" to "matita scrivere pencil", "💼" to "lavoro work", "🏠" to "casa home",
    "🛒" to "spesa shopping carrello", "🍕" to "pizza cibo food", "☕" to "caffe coffee",
    "🐱" to "gatto cat animale", "🐶" to "cane dog animale", "🌸" to "fiore flower primavera",
    "🌞" to "sole sun estate", "🌙" to "luna moon notte", "☁️" to "nuvola cloud", "🌈" to "arcobaleno rainbow",
    "✈️" to "aereo viaggio travel", "🎵" to "musica music nota", "💰" to "soldi money"
)

@Composable
fun StickerView(st: PlacedSticker, wPx: Float, hPx: Float, onChange: (PlacedSticker) -> Unit, onDelete: () -> Unit) {
    val cur by rememberUpdatedState(st)
    val chg by rememberUpdatedState(onChange)
    val del by rememberUpdatedState(onDelete)
    val density = LocalDensity.current
    val bPx = wPx * 0.2f
    val base = with(density) { bPx.toDp() }
    Box(
        Modifier
            .offset { IntOffset((st.x * wPx - bPx / 2).roundToInt(), (st.y * hPx - bPx / 2).roundToInt()) }
            .size(base)
            .graphicsLayer { scaleX = st.s; scaleY = st.s; rotationZ = st.r }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { del() }) }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rot ->
                    val s0 = cur
                    val rad = Math.toRadians(s0.r.toDouble())
                    val c = cos(rad).toFloat()
                    val sn = sin(rad).toFloat()
                    val dx = (pan.x * c - pan.y * sn) * s0.s
                    val dy = (pan.x * sn + pan.y * c) * s0.s
                    chg(s0.copy(x = s0.x + dx / wPx, y = s0.y + dy / hPx,
                        s = (s0.s * zoom).coerceIn(0.2f, 6f), r = s0.r + rot))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (st.src.startsWith("emoji:")) {
            Text(st.src.removePrefix("emoji:"), fontSize = with(density) { (bPx * 0.8f).toSp() })
        } else {
            AsyncImage(File(st.src.removePrefix("file:")), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StickerSheet(vm: NotesViewModel, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val custom by vm.stickers.collectAsState()
    val ctx: Context = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<String?>(null) }
    var tagsText by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { pending = Media.import(ctx, uri, "stickers") }
    }
    fun match(tags: String) = q.isBlank() || q.lowercase().trim().split(" ").all { tags.contains(it) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(q, { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Cerca sticker (cuore, gatto, idea…)") })
            TextButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Importa sticker dalla galleria")
            }
            Text("Tocca per aggiungere · tieni premuto uno sticker personalizzato per eliminarlo",
                style = MaterialTheme.typography.bodySmall)
            LazyVerticalGrid(GridCells.Adaptive(64.dp), Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                items(custom.filter { match(it.tags) }) { s ->
                    AsyncImage(File(s.path), null,
                        Modifier.size(64.dp).padding(4.dp).combinedClickable(
                            onClick = { onPick("file:" + s.path); onDismiss() },
                            onLongClick = { vm.deleteSticker(s) }),
                        contentScale = ContentScale.Fit)
                }
                items(Builtin.filter { match(it.second) }) { (e, _) ->
                    Text(e, fontSize = 36.sp, modifier = Modifier.padding(6.dp).clickable { onPick("emoji:$e"); onDismiss() })
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    pending?.let { path ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Tag dello sticker") },
            text = { OutlinedTextField(tagsText, { tagsText = it }, placeholder = { Text("es. gatto animale carino") }) },
            confirmButton = {
                TextButton(onClick = { vm.addSticker(path, tagsText); tagsText = ""; pending = null }) { Text("Salva") }
            }
        )
    }
}
