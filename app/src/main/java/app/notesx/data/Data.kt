package app.notesx.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

/** type: 0 = testo, 1 = sticky, 2 = canvas */
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val type: Int = 0,
    val folderId: Long? = null,
    val tagIds: String = "",
    val color: Int = 0xFFFFF59D.toInt(),
    val pinned: Boolean = false,
    val bgUri: String? = null,
    val bgOpacity: Float = 1f,
    val bgBlur: Float = 0f,
    val bgTint: Int = 0,
    val bgSat: Float = 1f,
    val strokes: String = "[]",
    val stickers: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** true se il filtro scuro rende necessario il testo chiaro */
fun Note.darkPage(): Boolean {
    val c = Color(bgTint)
    return c.alpha >= 0.5f && c.luminance() < 0.5f
}

@Entity(tableName = "folders")
data class Folder(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Entity(tableName = "tags")
data class Tag(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val color: Int)

@Entity(tableName = "custom_stickers")
data class CustomSticker(@PrimaryKey(autoGenerate = true) val id: Long = 0, val path: String, val tags: String)

@Dao
interface NotesDao {
    @Query("SELECT * FROM notes WHERE title LIKE '%' || :q || '%' OR content LIKE '%' || :q || '%' ORDER BY pinned DESC, updatedAt DESC")
    fun search(q: String): Flow<List<Note>>
    @Query("SELECT * FROM notes WHERE id = :id") suspend fun note(id: Long): Note?
    @Insert suspend fun insert(n: Note): Long
    @Update suspend fun update(n: Note)
    @Delete suspend fun delete(n: Note)

    @Query("SELECT * FROM folders ORDER BY name") fun folders(): Flow<List<Folder>>
    @Insert suspend fun insertFolder(f: Folder)
    @Delete suspend fun deleteFolder(f: Folder)
    @Query("UPDATE notes SET folderId = NULL WHERE folderId = :id") suspend fun clearFolder(id: Long)

    @Query("SELECT * FROM tags ORDER BY name") fun tags(): Flow<List<Tag>>
    @Insert suspend fun insertTag(t: Tag)
    @Delete suspend fun deleteTag(t: Tag)

    @Query("SELECT * FROM custom_stickers ORDER BY id DESC") fun stickers(): Flow<List<CustomSticker>>
    @Insert suspend fun insertSticker(s: CustomSticker)
    @Delete suspend fun deleteSticker(s: CustomSticker)
}

@Database(entities = [Note::class, Folder::class, Tag::class, CustomSticker::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): NotesDao

    companion object {
        const val NAME = "notes.db"
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, NAME).build().also { inst = it }
        }
        fun close() { inst?.close(); inst = null }
    }
}

/** Tratto a mano libera: coordinate normalizzate (0..1) rispetto alla pagina, larghezza = frazione della larghezza pagina. */
data class Ink(val color: Int, val width: Float, val pts: List<Float>)

/** Sticker posizionato: x,y = centro normalizzato; s = scala; r = rotazione (gradi). */
data class PlacedSticker(val id: String, val src: String, val x: Float, val y: Float, val s: Float, val r: Float)

object Codec {
    fun inks(j: String): List<Ink> = runCatching {
        val a = JSONArray(j)
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            val p = o.getJSONArray("p")
            Ink(o.getInt("c"), o.getDouble("w").toFloat(), List(p.length()) { k -> p.getDouble(k).toFloat() })
        }
    }.getOrDefault(emptyList())

    fun inksJson(l: List<Ink>): String {
        val a = JSONArray()
        l.forEach { ink ->
            val p = JSONArray()
            ink.pts.forEach { v -> p.put(v.toDouble()) }
            a.put(JSONObject().put("c", ink.color).put("w", ink.width.toDouble()).put("p", p))
        }
        return a.toString()
    }

    fun stickers(j: String): List<PlacedSticker> = runCatching {
        val a = JSONArray(j)
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            PlacedSticker(o.getString("id"), o.getString("src"), o.getDouble("x").toFloat(),
                o.getDouble("y").toFloat(), o.getDouble("s").toFloat(), o.getDouble("r").toFloat())
        }
    }.getOrDefault(emptyList())

    fun stickersJson(l: List<PlacedSticker>): String {
        val a = JSONArray()
        l.forEach {
            a.put(JSONObject().put("id", it.id).put("src", it.src).put("x", it.x.toDouble())
                .put("y", it.y.toDouble()).put("s", it.s.toDouble()).put("r", it.r.toDouble()))
        }
        return a.toString()
    }
}
