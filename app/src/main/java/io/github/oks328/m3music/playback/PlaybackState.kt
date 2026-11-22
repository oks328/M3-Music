package io.github.oks328.m3music.playback

import io.github.oks328.m3music.music.Track

enum class RepeatMode { OFF, ALL, ONE }

data class PlaybackState(
    val connected: Boolean = false,
    val track: Track? = null,
    val resolving: Boolean = false,
    val buffering: Boolean = false,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val error: String? = null,
    val offline: Boolean = false,
    val canPrevious: Boolean = false,
    val canNext: Boolean = false,
    val queueIndex: Int = 0,
    val queueSize: Int = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val queue: List<Track> = emptyList(),
    val queueEntryIds: List<String> = emptyList(),
)
