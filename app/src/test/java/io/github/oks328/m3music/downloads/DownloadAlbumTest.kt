package io.github.oks328.m3music.downloads

import io.github.oks328.m3music.music.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadAlbumTest {
    @Test fun persistedAlbumRetainsItsOrderedTrackIds() {
        val album = DownloadedCollection("MPREtest", "Album", null, null, listOf("second", "first"))
        assertEquals(listOf(album), DownloadMetadata.decodeCollections(DownloadMetadata.encodeCollections(listOf(album))))
        assertEquals(emptyList<DownloadedCollection>(), DownloadMetadata.decodeCollections(null))
    }

    @Test fun albumProgressCountsCompletedPartialAndMissingTracks() {
        val saved = Track("saved", "Saved song", "Artist", null, 0)
        val running = Track("running", "Downloading song", "Artist", null, 0)
        val entries = listOf(DownloadEntry(saved, DownloadStatus.COMPLETED),
            DownloadEntry(running, DownloadStatus.DOWNLOADING, percent = 50)).associateBy { it.track.id }
        assertEquals(0.5f, downloadProgress(listOf("saved", "running", "missing"), entries), 0.0001f)
    }
}
