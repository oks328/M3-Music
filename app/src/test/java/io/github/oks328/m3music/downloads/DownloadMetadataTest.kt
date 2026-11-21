package io.github.oks328.m3music.downloads

import io.github.oks328.m3music.music.Track
import org.junit.Assert.*
import org.junit.Test

class DownloadMetadataTest {
    @Test fun savedSongRetainsMetadataAcrossSerialization() {
        val track = Track("abcdefghijk", "Björk — Jóga 🎵", "An artist", "https://example.com/art.jpg", 300)
        assertEquals(track, DownloadMetadata.decode(DownloadMetadata.encode(track)))
    }
    @Test fun missingArtworkRemainsAbsent() {
        val track = Track("abcdefghijk", "A song", "An artist", null, 0)
        assertEquals(track, DownloadMetadata.decode(DownloadMetadata.encode(track)))
    }
    @Test fun damagedMetadataDoesNotCrashTheLibrary() {
        assertNull(DownloadMetadata.decode("not json".toByteArray()))
        assertNull(DownloadMetadata.decode("{}".toByteArray()))
    }
}
