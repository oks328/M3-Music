package io.github.oks328.m3music.music

data class AudioSource(val url: String, val mimeType: String?, val bitrate: Int)

internal fun selectAudioSource(candidates: List<AudioSource>): AudioSource =
    candidates.filter { it.url.startsWith("https://") }
        .maxByOrNull { it.bitrate }
        ?: throw IllegalStateException("Audio is unavailable for this song. Try another result.")
