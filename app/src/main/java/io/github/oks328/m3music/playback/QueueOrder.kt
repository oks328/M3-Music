@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package io.github.oks328.m3music.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.SessionCommand
import io.github.oks328.m3music.music.Track
import java.util.UUID

internal val InsertShuffledQueue = SessionCommand("io.github.oks328.m3music.INSERT_QUEUE", Bundle.EMPTY)

internal const val QueueEntryId = "io.github.oks328.m3music.QUEUE_ENTRY_ID"
internal const val AutoplayPreferences = "playback"
internal const val AutoplayEnabled = "autoplay"
internal const val DownloadQueueItem = "io.github.oks328.m3music.DOWNLOAD_QUEUE_ITEM"

internal fun Timeline.queueOrder(shuffled: Boolean): List<Int> = buildList(windowCount) {
    var index = getFirstWindowIndex(shuffled)
    while (index != C.INDEX_UNSET) {
        add(index)
        index = getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffled)
    }
}

internal fun List<MediaItem>.isDownloadQueue(): Boolean =
    isNotEmpty() && all { it.mediaMetadata.extras?.getBoolean(DownloadQueueItem) == true }

internal fun Track.queueItem(saved: Boolean, downloadQueue: Boolean = false): MediaItem =
    MediaItem.Builder().setMediaId(id)
        .setUri(if (saved) "offline:///$id" else "track:///$id")
        .setCustomCacheKey(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist)
            .setArtworkUri(artworkUrl?.let(Uri::parse))
            .setExtras(Bundle().apply {
                putString(QueueEntryId, UUID.randomUUID().toString())
                putBoolean(DownloadQueueItem, downloadQueue)
            })
            .build())
        .build()
