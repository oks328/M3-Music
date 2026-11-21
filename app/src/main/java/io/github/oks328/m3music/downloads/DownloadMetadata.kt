package io.github.oks328.m3music.downloads

import io.github.oks328.m3music.music.Track
import org.json.JSONObject

internal object DownloadMetadata {
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
