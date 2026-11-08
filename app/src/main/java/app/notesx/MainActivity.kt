package app.notesx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.notesx.ui.App
import app.notesx.ui.NotesTheme
import app.notesx.ui.NotesViewModel

class MainActivity : ComponentActivity() {
    private val vm: NotesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NotesTheme(vm.settings) { App(vm, this) } }
    }
}
