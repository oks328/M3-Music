package io.github.oks328.m3music.downloads

import io.github.oks328.m3music.music.Track

enum class DownloadStatus { PREPARING, QUEUED, DOWNLOADING, COMPLETED, FAILED, REMOVING, PAUSED }

data class DownloadEntry(
    val track: Track,
    val status: DownloadStatus,
    val percent: Int? = null,
    val bytes: Long = 0,
)

data class DownloadsState(
    val items: List<DownloadEntry> = emptyList(),
    val wifiOnly: Boolean = true,
    val waitingForNetwork: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
)
