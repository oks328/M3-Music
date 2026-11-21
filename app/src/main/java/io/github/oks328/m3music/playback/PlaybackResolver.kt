package io.github.oks328.m3music.playback

import io.github.oks328.m3music.music.AudioSource

internal class PlaybackResolver(
    private val downloaded: suspend (String) -> AudioSource?,
    private val network: suspend (String) -> AudioSource,
) {
    suspend fun resolve(id: String): AudioSource = downloaded(id) ?: network(id)
}
