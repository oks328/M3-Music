@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package io.github.oks328.m3music.downloads

import android.net.Uri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class OfflinePlaybackTest {
    @Test fun persistedDownloadReadsOfflineWithoutCreatingANetworkSource() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "offline-test-${UUID.randomUUID()}")
        val db = StandaloneDatabaseProvider(context)
        val bytes = ByteArray(128_000) { (it % 127).toByte() }
        var cache = SimpleCache(directory, NoOpCacheEvictor(), db)
        try {
            val downloader = CacheDataSource.Factory().setCache(cache)
                .setUpstreamDataSourceFactory { ByteArrayDataSource(bytes) }
            CacheWriter(downloader.createDataSourceForDownloading(),
                DataSpec.Builder().setUri("https://example.com/song").setKey("song-id").build(),
                null, null).cache()
            cache.release()
            cache = SimpleCache(directory, NoOpCacheEvictor(), db)
            val factory = OfflineDataSourceFactory(cache, DataSource.Factory {
                error("Offline playback must never create a network source")
            })
            val source = factory.createDataSource()
            try {
                source.open(DataSpec.Builder().setUri(Uri.parse("offline:///song-id")).setKey("song-id").build())
                val result = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = source.read(buffer, 0, buffer.size)
                    if (count == -1) break
                    result.write(buffer, 0, count)
                }
                assertArrayEquals(bytes, result.toByteArray())
            } finally { source.close() }
        } finally {
            cache.release()
            db.close()
            directory.deleteRecursively()
        }
    }

    @Test fun missingOfflineDownloadFailsInsteadOfUsingNetwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "offline-test-${UUID.randomUUID()}")
        val db = StandaloneDatabaseProvider(context)
        val cache = SimpleCache(directory, NoOpCacheEvictor(), db)
        try {
            val source = OfflineDataSourceFactory(cache, DataSource.Factory {
                error("A missing download must not silently stream")
            }).createDataSource()
            try {
                assertThrows(java.io.IOException::class.java) {
                    source.open(DataSpec.Builder().setUri("offline:///missing").setKey("missing").build())
                }
            } finally { source.close() }
        } finally {
            cache.release()
            db.close()
            directory.deleteRecursively()
        }
    }
}
