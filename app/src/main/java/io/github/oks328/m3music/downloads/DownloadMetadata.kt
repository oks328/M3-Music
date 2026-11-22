package io.github.oks328.m3music.downloads

import io.github.oks328.m3music.music.Track
import org.json.JSONObject
import org.json.JSONArray

internal object DownloadMetadata {
    fun encodeAlbums(albums: List<DownloadedAlbum>): String = JSONArray(albums.map { album ->
        JSONObject().put("id", album.id).put("title", album.title).put("artist", album.artist)
            .put("artwork", album.artworkUrl).put("songs", JSONArray(album.trackIds))
    }).toString()

    fun decodeAlbums(raw: String?): List<DownloadedAlbum> {
        val json = runCatching { JSONArray(raw ?: "[]") }.getOrNull() ?: return emptyList()
        return (0 until json.length()).mapNotNull { index ->
            runCatching {
                val album = json.getJSONObject(index)
                val songs = album.getJSONArray("songs")
                DownloadedAlbum(album.getString("id"), album.getString("title"),
                    album.optString("artist").ifBlank { null }, album.optString("artwork").ifBlank { null },
                    List(songs.length()) { songs.getString(it) })
            }.getOrNull()
        }
    }

    fun encode(track: Track): ByteArray = JSONObject().apply {
        put("version", 1)
        put("id", track.id)
        put("title", track.title)
        put("artist", track.artist)
        put("artwork", track.artworkUrl ?: JSONObject.NULL)
        put("duration", track.durationSeconds)
    }.toString().toByteArray(Charsets.UTF_8)

    fun decode(data: ByteArray): Track? = runCatching {
        val json = JSONObject(data.toString(Charsets.UTF_8))
        require(json.getInt("version") == 1)
        Track(json.getString("id"), json.getString("title"), json.getString("artist"),
            if (json.isNull("artwork")) null else json.getString("artwork"), json.getLong("duration"))
    }.getOrNull()
}
