package app.notesx.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.notesx.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

val StickyColors = listOf(0xFFFFF59D, 0xFFFFCCBC, 0xFFC8E6C9, 0xFFB3E5FC, 0xFFE1BEE7, 0xFFFFE0B2).map { it.toInt() }

class NotesViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    val settings = Settings(app)

    val query = MutableStateFlow("")
    val folderFilter = MutableStateFlow<Long?>(null)
    val tagFilter = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val notes = combine(query.flatMapLatest { dao.search(it) }, folderFilter, tagFilter) { l, f, t ->
        l.filter { n -> (f == null || n.folderId == f) && (t == null || n.tagIds.split(",").contains(t.toString())) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders = dao.folders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tags = dao.tags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val stickers = dao.stickers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Nota attualmente aperta nell'editor (stato live, salvato in automatico). */
    var editing by mutableStateOf<Note?>(null)
        private set
    private var saveJob: Job? = null

    fun open(id: Long) {
        close()
        viewModelScope.launch { editing = dao.note(id) }
    }

    fun create(type: Int) {
        close()
        viewModelScope.launch {
            val n = Note(type = type, folderId = folderFilter.value, color = StickyColors.random())
            editing = n.copy(id = dao.insert(n))
        }
    }

    /** Modifica con auto-save (debounce 400 ms). */
    fun edit(f: (Note) -> Note) {
        val n = editing ?: return
        editing = f(n).copy(updatedAt = System.currentTimeMillis())
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            editing?.let { dao.update(it) }
        }
    }

    fun close() {
        saveJob?.cancel()
        val n = editing ?: return
        editing = null
        viewModelScope.launch(NonCancellable) {
            if (n.title.isBlank() && n.content.isBlank() && n.strokes == "[]" && n.stickers == "[]") dao.delete(n)
            else dao.update(n)
        }
    }

    fun deleteCurrent() {
        saveJob?.cancel()
        val n = editing ?: return
        editing = null
        viewModelScope.launch(NonCancellable) { dao.delete(n) }
    }

    fun delete(n: Note) = viewModelScope.launch { dao.delete(n) }
    fun togglePin(n: Note) = viewModelScope.launch { dao.update(n.copy(pinned = !n.pinned)) }

    fun addFolder(name: String) = viewModelScope.launch { dao.insertFolder(Folder(name = name)) }
    fun deleteFolder(f: Folder) = viewModelScope.launch {
        dao.clearFolder(f.id); dao.deleteFolder(f)
        if (folderFilter.value == f.id) folderFilter.value = null
    }
    fun addTag(name: String, color: Int) = viewModelScope.launch { dao.insertTag(Tag(name = name, color = color)) }
    fun deleteTag(t: Tag) = viewModelScope.launch {
        dao.deleteTag(t)
        if (tagFilter.value == t.id) tagFilter.value = null
    }
    fun addSticker(path: String, tags: String) = viewModelScope.launch { dao.insertSticker(CustomSticker(path = path, tags = tags.lowercase())) }
    fun deleteSticker(s: CustomSticker) = viewModelScope.launch { dao.deleteSticker(s) }
}
