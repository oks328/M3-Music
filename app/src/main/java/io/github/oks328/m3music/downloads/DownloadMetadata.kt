package io.github.oks328.m3music.downloads

import io.github.oks328.m3music.music.Track
import org.json.JSONObject
import org.json.JSONArray

internal object DownloadMetadata {
    fun encodeCollections(collections: List<DownloadedCollection>): String = JSONArray(collections.map { collection ->
        JSONObject().put("id", collection.id).put("title", collection.title).put("artist", collection.artist)
            .put("artwork", collection.artworkUrl).put("songs", JSONArray(collection.trackIds))
    }).toString()

    fun decodeCollections(raw: String?): List<DownloadedCollection> {
        val json = runCatching { JSONArray(raw ?: "[]") }.getOrNull() ?: return emptyList()
        return (0 until json.length()).mapNotNull { index ->
            runCatching {
                val collection = json.getJSONObject(index)
                val songs = collection.getJSONArray("songs")
                DownloadedCollection(collection.getString("id"), collection.getString("title"),
                    collection.optString("artist").ifBlank { null }, collection.optString("artwork").ifBlank { null },
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
