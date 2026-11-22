package io.github.oks328.m3music.downloads

import io.github.oks328.m3music.music.Track

enum class DownloadStatus { PREPARING, QUEUED, DOWNLOADING, COMPLETED, FAILED, REMOVING, PAUSED }

data class DownloadEntry(
    val track: Track,
    val status: DownloadStatus,
    val percent: Int? = null,
    val bytes: Long = 0,
)

data class DownloadedAlbum(
    val id: String,
    val title: String,
    val artist: String?,
    val artworkUrl: String?,
    val trackIds: List<String>,
)

data class DownloadsState(
    val items: List<DownloadEntry> = emptyList(),
    val wifiOnly: Boolean = true,
    val waitingForNetwork: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
    val albums: List<DownloadedAlbum> = emptyList(),
)

internal val DownloadEntry.active: Boolean
    get() = status == DownloadStatus.PREPARING || status == DownloadStatus.QUEUED ||
        status == DownloadStatus.DOWNLOADING

internal fun downloadProgress(ids: List<String>, entries: Map<String, DownloadEntry>): Float =
    if (ids.isEmpty()) 0f else ids.sumOf { id ->
        val entry = entries[id]
        when (entry?.status) {
            DownloadStatus.COMPLETED -> 100
            DownloadStatus.DOWNLOADING -> entry.percent?.coerceIn(0, 100) ?: 0
            else -> 0
        }
    } / (ids.size * 100f)
