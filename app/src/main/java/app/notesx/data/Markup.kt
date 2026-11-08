package app.notesx.data

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Formato testo: **grassetto**, *corsivo*, __sottolineato__,
 * "- " elenco puntato, "1. " elenco numerato, "- [ ] " / "- [x] " to-do.
 */
data class Run(val text: String, val b: Boolean, val i: Boolean, val u: Boolean)

data class Line(val kind: Int, val checked: Boolean, val num: String, val body: String) {
    fun prefix(): String = when (kind) {
        1 -> "• "
        2 -> "$num. "
        3 -> if (checked) "☑ " else "☐ "
        else -> ""
    }
}

object Markup {
    val rx = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__|\\*(.+?)\\*")
    private val numRx = Regex("^(\\d+)\\. ")

    fun runs(line: String): List<Run> {
        val out = mutableListOf<Run>()
        var last = 0
        for (m in rx.findAll(line)) {
            if (m.range.first > last) out += Run(line.substring(last, m.range.first), false, false, false)
            val g = m.groups
            out += when {
                g[1] != null -> Run(g[1]!!.value, true, false, false)
                g[2] != null -> Run(g[2]!!.value, false, false, true)
                else -> Run(g[3]!!.value, false, true, false)
            }
            last = m.range.last + 1
        }
        if (last < line.length) out += Run(line.substring(last), false, false, false)
        return out
    }

    fun parseLine(l: String): Line {
        if (l.startsWith("- [ ] ")) return Line(3, false, "", l.drop(6))
        if (l.startsWith("- [x] ")) return Line(3, true, "", l.drop(6))
        if (l.startsWith("- ")) return Line(1, false, "", l.drop(2))
        val m = numRx.find(l)
        if (m != null) return Line(2, false, m.groupValues[1], l.substring(m.value.length))
        return Line(0, false, "", l)
    }

    fun plain(content: String): String = content.lines().joinToString("\n") {
        val l = parseLine(it)
        l.prefix() + runs(l.body).joinToString("") { r -> r.text }
    }

    fun toggleLine(text: String, idx: Int): String = text.lines().mapIndexed { i, l ->
        if (i != idx) l else when {
            l.startsWith("- [ ] ") -> "- [x] " + l.drop(6)
            l.startsWith("- [x] ") -> "- [ ] " + l.drop(6)
            else -> l
        }
    }.joinToString("\n")

    fun wrap(tf: TextFieldValue, mk: String): TextFieldValue {
        val s = tf.selection.min
        val e = tf.selection.max
        val t = tf.text
        return if (tf.selection.collapsed)
            TextFieldValue(t.substring(0, s) + mk + mk + t.substring(s), TextRange(s + mk.length))
        else
            TextFieldValue(t.substring(0, s) + mk + t.substring(s, e) + mk + t.substring(e), TextRange(s + mk.length, e + mk.length))
    }

    fun prefixLine(tf: TextFieldValue, p: String): TextFieldValue {
        val t = tf.text
        val s = tf.selection.min
        val ls = t.lastIndexOf('\n', s - 1) + 1
        return if (t.startsWith(p, ls))
            TextFieldValue(t.removeRange(ls, ls + p.length), TextRange((s - p.length).coerceAtLeast(ls)))
        else
            TextFieldValue(t.substring(0, ls) + p + t.substring(ls), TextRange(s + p.length))
    }

    /** Continua automaticamente gli elenchi quando si preme Invio. */
    fun autoList(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
        val c = new.selection.start
        if (new.selection.collapsed && new.text.length == old.text.length + 1 && c > 0 && new.text[c - 1] == '\n') {
            val ls = new.text.lastIndexOf('\n', c - 2) + 1
            val l = parseLine(new.text.substring(ls, c - 1))
            if (l.kind != 0) {
                if (l.body.isBlank()) {
                    return TextFieldValue(new.text.substring(0, ls) + new.text.substring(c), TextRange(ls))
                }
                val p = when (l.kind) { 1 -> "- "; 2 -> "${(l.num.toIntOrNull() ?: 0) + 1}. "; else -> "- [ ] " }
                return TextFieldValue(new.text.substring(0, c) + p + new.text.substring(c), TextRange(c + p.length))
            }
        }
        return new
    }
}
