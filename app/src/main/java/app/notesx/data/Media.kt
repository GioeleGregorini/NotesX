package app.notesx.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

object Media {
    private const val UA = "NotesX/1.0 (Android notes app)"

    private fun target(ctx: Context, dir: String) =
        File(File(ctx.filesDir, dir).apply { mkdirs() }, "${UUID.randomUUID()}.img")

    suspend fun import(ctx: Context, uri: Uri, dir: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val f = target(ctx, dir)
            ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { i.copyTo(it) } }
            f.absolutePath
        }.getOrNull()
    }

    suspend fun download(ctx: Context, url: String, dir: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 20000
            c.setRequestProperty("User-Agent", UA)
            val f = target(ctx, dir)
            c.inputStream.use { i -> f.outputStream().use { i.copyTo(it) } }
            f.absolutePath
        }.getOrNull()
    }

    /** Ricerca immagini su Wikimedia Commons (nessuna chiave API richiesta). */
    suspend fun search(q: String): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val u = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrlimit=24" +
                "&gsrsearch=" + URLEncoder.encode("$q filetype:bitmap", "UTF-8") +
                "&prop=imageinfo&iiprop=url&iiurlwidth=1000&format=json"
            val c = URL(u).openConnection() as HttpURLConnection
            c.setRequestProperty("User-Agent", UA)
            val json = JSONObject(c.inputStream.bufferedReader().readText())
            val pages = json.getJSONObject("query").getJSONObject("pages")
            pages.keys().asSequence().mapNotNull { k ->
                pages.getJSONObject(k).optJSONArray("imageinfo")?.optJSONObject(0)?.optString("thumburl")
            }.filter { it.isNotBlank() }.toList()
        }.getOrDefault(emptyList())
    }
}
