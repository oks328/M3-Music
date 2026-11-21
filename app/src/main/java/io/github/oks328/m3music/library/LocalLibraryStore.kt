package io.github.oks328.m3music.library

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.oks328.m3music.music.Track
import java.util.UUID

internal class LocalLibraryStore(context: Context, name: String = "music_library.db") : AutoCloseable {
    private val helper = object : SQLiteOpenHelper(context, name, null, 1) {
        override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE tracks(id TEXT PRIMARY KEY, title TEXT NOT NULL, artist TEXT NOT NULL, artwork TEXT, duration INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE favorites(sequence INTEGER PRIMARY KEY AUTOINCREMENT, track_id TEXT NOT NULL UNIQUE REFERENCES tracks(id))")
            db.execSQL("CREATE TABLE history(sequence INTEGER PRIMARY KEY AUTOINCREMENT, track_id TEXT NOT NULL UNIQUE REFERENCES tracks(id))")
            db.execSQL("CREATE TABLE playlists(id TEXT PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE UNIQUE, sequence INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE playlist_tracks(playlist_id TEXT NOT NULL REFERENCES playlists(id) ON DELETE CASCADE, track_id TEXT NOT NULL REFERENCES tracks(id), position INTEGER NOT NULL, PRIMARY KEY(playlist_id,track_id), UNIQUE(playlist_id,position))")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            error("Unsupported library migration: $oldVersion to $newVersion")
        }
    }

    fun snapshot(): LibraryState {
        val db = helper.readableDatabase
        val tracks = linkedMapOf<String, Track>()
        db.rawQuery("SELECT id,title,artist,artwork,duration FROM tracks", null).use { cursor ->
            while (cursor.moveToNext()) tracks[cursor.getString(0)] = Track(cursor.getString(0),
                cursor.getString(1), cursor.getString(2), if (cursor.isNull(3)) null else cursor.getString(3), cursor.getLong(4))
        }
        fun orderedTracks(table: String) = ids(db, "SELECT track_id FROM $table ORDER BY sequence DESC").mapNotNull(tracks::get)
        val lists = mutableListOf<LocalPlaylist>()
        db.rawQuery("SELECT id,name FROM playlists ORDER BY sequence DESC,rowid DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                lists += LocalPlaylist(id, cursor.getString(1),
                    ids(db, "SELECT track_id FROM playlist_tracks WHERE playlist_id=? ORDER BY position", arrayOf(id)).mapNotNull(tracks::get))
            }
        }
        return LibraryState(orderedTracks("favorites"), orderedTracks("history"), lists, loading = false)
    }

    fun createPlaylist(name: String, initialTrack: Track? = null): String = transaction { db ->
        val normalized = normalizePlaylistName(name)
        checkName(db, normalized)
        val id = UUID.randomUUID().toString()
        db.insertOrThrow("playlists", null, ContentValues().apply {
            put("id", id); put("name", normalized); put("sequence", System.currentTimeMillis())
        })
        if (initialTrack != null) {
            saveTrack(db, initialTrack)
            db.insertOrThrow("playlist_tracks", null, ContentValues().apply {
                put("playlist_id", id); put("track_id", initialTrack.id); put("position", 0)
            })
        }
        id
    }

    fun renamePlaylist(id: String, name: String) = transaction { db ->
        requirePlaylist(db, id)
        val normalized = normalizePlaylistName(name)
        checkName(db, normalized, id)
        db.update("playlists", ContentValues().apply { put("name", normalized) }, "id=?", arrayOf(id))
        Unit
    }

    fun deletePlaylist(id: String) = transaction { db ->
        db.delete("playlists", "id=?", arrayOf(id))
        prune(db)
    }

    fun setFavorite(track: Track, favorite: Boolean) = transaction { db ->
        saveTrack(db, track)
        if (favorite) db.insertWithOnConflict("favorites", null,
            ContentValues().apply { put("track_id", track.id) }, SQLiteDatabase.CONFLICT_IGNORE)
        else db.delete("favorites", "track_id=?", arrayOf(track.id))
        prune(db)
    }

    fun recordPlayed(track: Track) = transaction { db ->
        saveTrack(db, track)
        db.delete("history", "track_id=?", arrayOf(track.id))
        db.insertOrThrow("history", null, ContentValues().apply { put("track_id", track.id) })
        db.execSQL("DELETE FROM history WHERE sequence NOT IN (SELECT sequence FROM history ORDER BY sequence DESC LIMIT 200)")
        prune(db)
    }

    fun clearHistory() = transaction { db -> db.delete("history", null, null); prune(db) }

    fun addToPlaylist(id: String, track: Track) = transaction { db ->
        requirePlaylist(db, id)
        saveTrack(db, track)
        val position = db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM playlist_tracks WHERE playlist_id=?", arrayOf(id)).use {
            it.moveToFirst(); it.getInt(0)
        }
        db.insertWithOnConflict("playlist_tracks", null, ContentValues().apply {
            put("playlist_id", id); put("track_id", track.id); put("position", position)
        }, SQLiteDatabase.CONFLICT_IGNORE)
        Unit
    }

    fun removeFromPlaylist(id: String, trackId: String) = transaction { db ->
        db.delete("playlist_tracks", "playlist_id=? AND track_id=?", arrayOf(id, trackId))
        prune(db)
    }

    fun moveTrack(id: String, trackId: String, direction: Int) = transaction { db ->
        require(direction == -1 || direction == 1) { "Choose the previous or next position." }
        require(direction == -1 || direction == 1)
        val order = ids(db, "SELECT track_id FROM playlist_tracks WHERE playlist_id=? ORDER BY position", arrayOf(id)).toMutableList()
        val from = order.indexOf(trackId)
        val to = from + direction
        if (from >= 0 && to in order.indices) {
            order.add(to, order.removeAt(from))
            db.execSQL("UPDATE playlist_tracks SET position=-position-1 WHERE playlist_id=?", arrayOf(id))
            order.forEachIndexed { index, track ->
                db.update("playlist_tracks", ContentValues().apply { put("position", index) },
                    "playlist_id=? AND track_id=?", arrayOf(id, track))
            }
        }
    }

    private fun saveTrack(db: SQLiteDatabase, track: Track) {
        val values = ContentValues().apply {
            put("id", track.id); put("title", track.title); put("artist", track.artist)
            put("artwork", track.artworkUrl); put("duration", track.durationSeconds)
        }
        if (db.update("tracks", values, "id=?", arrayOf(track.id)) == 0) db.insertOrThrow("tracks", null, values)
    }

    private fun requirePlaylist(db: SQLiteDatabase, id: String) {
        require(ids(db, "SELECT id FROM playlists WHERE id=?", arrayOf(id)).isNotEmpty()) { "This playlist no longer exists." }
    }

    private fun checkName(db: SQLiteDatabase, name: String, excluding: String = "") {
        require(ids(db, "SELECT id FROM playlists WHERE name=? COLLATE NOCASE AND id<>?", arrayOf(name, excluding)).isEmpty()) {
            "A playlist with this name already exists."
        }
    }

    private fun prune(db: SQLiteDatabase) {
        db.execSQL("DELETE FROM tracks WHERE id NOT IN (SELECT track_id FROM favorites UNION SELECT track_id FROM history UNION SELECT track_id FROM playlist_tracks)")
    }

    private fun ids(db: SQLiteDatabase, sql: String, args: Array<String>? = null): List<String> =
        db.rawQuery(sql, args).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = helper.writableDatabase
        db.beginTransaction()
        try { return block(db).also { db.setTransactionSuccessful() } }
        finally { db.endTransaction() }
    }

    override fun close() = helper.close()
}
