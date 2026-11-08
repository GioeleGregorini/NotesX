package app.notesx.data

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max

object Exporter {
    const val W = 1240
    val H = (W * 1.414f).toInt()

    private fun decode(path: String, reqW: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= reqW) s *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    fun bitmap(n: Note): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(if (n.type == 1) n.color else Color.WHITE)

        // sfondo
        n.bgUri?.let { path ->
            decode(path, W)?.let { b ->
                var src = b
                if (n.bgBlur > 0.5f) {
                    val f = 1f + n.bgBlur / 3f
                    val small = Bitmap.createScaledBitmap(b, (b.width / f).toInt().coerceAtLeast(1), (b.height / f).toInt().coerceAtLeast(1), true)
                    src = Bitmap.createScaledBitmap(small, b.width, b.height, true)
                }
                val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = (n.bgOpacity * 255).toInt()
                    if (n.bgSat < 1f) colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(n.bgSat) })
                }
                val sc = max(W / src.width.toFloat(), H / src.height.toFloat())
                val sw = W / sc
                val sh = H / sc
                val sx = (src.width - sw) / 2
                val sy = (src.height - sh) / 2
                c.drawBitmap(src, Rect(sx.toInt(), sy.toInt(), (sx + sw).toInt(), (sy + sh).toInt()), Rect(0, 0, W, H), p)
            }
        }
        if (n.bgTint != 0) c.drawColor(n.bgTint)

        // testo
        val pad = W * 24f / 400f
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (n.darkPage()) Color.WHITE else 0xFF1B1B1F.toInt()
            textSize = W * 16f / 400f
        }
        val sb = SpannableStringBuilder()
        if (n.title.isNotBlank()) {
            sb.append(n.title).append("\n")
            sb.setSpan(StyleSpan(Typeface.BOLD), 0, n.title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(1.4f), 0, n.title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        n.content.lines().forEach { ln ->
            val l = Markup.parseLine(ln)
            sb.append(l.prefix())
            Markup.runs(l.body).forEach { r ->
                val st = sb.length
                sb.append(r.text)
                if (r.b) sb.setSpan(StyleSpan(Typeface.BOLD), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (r.i) sb.setSpan(StyleSpan(Typeface.ITALIC), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (r.u) sb.setSpan(UnderlineSpan(), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            sb.append("\n")
        }
        val layout = StaticLayout.Builder.obtain(sb, 0, sb.length, tp, (W - 2 * pad).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        c.save()
        c.translate(pad, pad)
        c.clipRect(0f, 0f, W - 2 * pad, H - 2 * pad)
        layout.draw(c)
        c.restore()

        // tratti
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        Codec.inks(n.strokes).forEach { ink ->
            if (ink.pts.size < 4) return@forEach
            sp.color = ink.color
            sp.strokeWidth = ink.width * W
            val path = Path().apply {
                moveTo(ink.pts[0] * W, ink.pts[1] * H)
                for (i in 1 until ink.pts.size / 2) lineTo(ink.pts[2 * i] * W, ink.pts[2 * i + 1] * H)
            }
            c.drawPath(path, sp)
        }

        // sticker
        val base = W * 0.2f
        val ep = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = base * 0.8f }
        Codec.stickers(n.stickers).forEach { st ->
            c.save()
            c.translate(st.x * W, st.y * H)
            c.rotate(st.r)
            c.scale(st.s, st.s)
            if (st.src.startsWith("emoji:")) {
                val fm = ep.fontMetrics
                c.drawText(st.src.removePrefix("emoji:"), 0f, -(fm.ascent + fm.descent) / 2f, ep)
            } else {
                decode(st.src.removePrefix("file:"), 512)?.let { b ->
                    val k = minOf(base / b.width, base / b.height)
                    val w = b.width * k
                    val h = b.height * k
                    c.drawBitmap(b, null, RectF(-w / 2, -h / 2, w / 2, h / 2), Paint(Paint.FILTER_BITMAP_FLAG))
                }
            }
            c.restore()
        }
        return bmp
    }

    private fun safeName(n: Note) = n.title.ifBlank { "nota" }.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)

    /** fmt: 0 PDF, 1 PNG, 2 Markdown, 3 Testo */
    fun export(ctx: Context, n: Note, fmt: Int): Pair<File, String> {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        return when (fmt) {
            0 -> {
                val f = File(dir, safeName(n) + ".pdf")
                val bmp = bitmap(n)
                val doc = PdfDocument()
                val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
                page.canvas.drawBitmap(bmp, null, RectF(0f, 0f, 595f, 842f), Paint(Paint.FILTER_BITMAP_FLAG))
                doc.finishPage(page)
                f.outputStream().use { doc.writeTo(it) }
                doc.close()
                f to "application/pdf"
            }
            1 -> {
                val f = File(dir, safeName(n) + ".png")
                f.outputStream().use { bitmap(n).compress(Bitmap.CompressFormat.PNG, 100, it) }
                f to "image/png"
            }
            2 -> {
                val f = File(dir, safeName(n) + ".md")
                f.writeText((if (n.title.isNotBlank()) "# ${n.title}\n\n" else "") + n.content.replace(Regex("__(.+?)__"), "<u>\$1</u>"))
                f to "text/markdown"
            }
            else -> {
                val f = File(dir, safeName(n) + ".txt")
                f.writeText((if (n.title.isNotBlank()) n.title + "\n\n" else "") + Markup.plain(n.content))
                f to "text/plain"
            }
        }
    }

    fun share(ctx: Context, f: File, mime: String) {
        val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

object Backup {
    private const val DB_ENTRY = "database/notes.db"

    fun create(ctx: Context, out: Uri) {
        AppDb.get(ctx).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        ZipOutputStream(ctx.contentResolver.openOutputStream(out)!!.buffered()).use { z ->
            z.putNextEntry(ZipEntry(DB_ENTRY))
            ctx.getDatabasePath(AppDb.NAME).inputStream().use { it.copyTo(z) }
            z.closeEntry()
            for (d in listOf("bg", "stickers")) {
                File(ctx.filesDir, d).listFiles()?.forEach { f ->
                    z.putNextEntry(ZipEntry("files/$d/${f.name}"))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
    }

    fun restore(ctx: Context, input: Uri) {
        AppDb.close()
        val db = ctx.getDatabasePath(AppDb.NAME)
        listOf(db, File(db.path + "-wal"), File(db.path + "-shm")).forEach { it.delete() }
        val root = ctx.filesDir.canonicalPath
        ZipInputStream(ctx.contentResolver.openInputStream(input)!!.buffered()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val dest = when {
                    e.name == DB_ENTRY -> db
                    e.name.startsWith("files/") -> File(ctx.filesDir, e.name.removePrefix("files/"))
                    else -> null
                }
                if (dest != null && !e.isDirectory && (dest == db || dest.canonicalPath.startsWith(root))) {
                    dest.parentFile?.mkdirs()
                    dest.outputStream().use { z.copyTo(it) }
                }
                z.closeEntry()
                e = z.nextEntry
            }
        }
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!
        ctx.startActivity(Intent.makeRestartActivityTask(launch.component))
        Runtime.getRuntime().exit(0)
    }
}
