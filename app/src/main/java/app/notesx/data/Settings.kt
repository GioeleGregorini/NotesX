package app.notesx.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class P<T>(
    private val sp: SharedPreferences,
    private val key: String,
    def: T,
    private val rd: (SharedPreferences, String, T) -> T,
    private val wr: (SharedPreferences.Editor, String, T) -> Unit
) {
    var value by mutableStateOf(rd(sp, key, def))
        private set

    fun set(v: T) {
        value = v
        sp.edit().also { wr(it, key, v) }.apply()
    }
}

class Settings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private fun int(k: String, d: Int) = P(sp, k, d, { s, key, df -> s.getInt(key, df) }, { e, key, v -> e.putInt(key, v) })
    private fun bool(k: String, d: Boolean) = P(sp, k, d, { s, key, df -> s.getBoolean(key, df) }, { e, key, v -> e.putBoolean(key, v) })
    private fun float(k: String, d: Float) = P(sp, k, d, { s, key, df -> s.getFloat(key, df) }, { e, key, v -> e.putFloat(key, v) })
    private fun str(k: String) = P<String?>(sp, k, null, { s, key, _ -> s.getString(key, null) },
        { e, key, v -> if (v == null) e.remove(key) else e.putString(key, v) })

    val themeMode = int("tm", 0)          // 0 sistema, 1 chiaro, 2 scuro
    val amoled = bool("am", false)
    val dynamic = bool("dy", true)
    val homeBg = str("hb")
    val homeOpacity = float("ho", 1f)
    val homeBlur = float("hbl", 0f)
    val homeTint = int("ht", 0)
    val homeSat = float("hs", 1f)
}
