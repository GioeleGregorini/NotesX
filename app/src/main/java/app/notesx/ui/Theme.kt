package app.notesx.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.notesx.data.Settings

@Composable
fun NotesTheme(s: Settings, content: @Composable () -> Unit) {
    val dark = when (s.themeMode.value) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    var cs = when {
        s.dynamic.value && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    if (dark && s.amoled.value) {
        val k = Color(0xFF080808)
        cs = cs.copy(
            background = Color.Black, surface = Color.Black,
            surfaceContainer = k, surfaceContainerLow = k,
            surfaceContainerHigh = Color(0xFF121212), surfaceContainerHighest = Color(0xFF181818)
        )
    }
    MaterialTheme(colorScheme = cs, content = content)
}
