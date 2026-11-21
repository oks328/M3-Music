@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package io.github.oks328.m3music.playback

import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import io.github.oks328.m3music.music.AudioSource
import org.junit.Assert.*
import org.junit.Test

class TrackDataSourceFactoryTest {
    @Test fun queueEntryResolvesWhenOpenedAndReusesItsSourceWhenSeeking() {
        var calls = 0
        val requests = mutableListOf<DataSpec>()
        val bytes = byteArrayOf(1, 2, 3, 4)
        val factory = TrackDataSourceFactory(DataSource.Factory {
            val real = ByteArrayDataSource(bytes)
            object : DataSource by real {
                override fun open(dataSpec: DataSpec): Long { requests += dataSpec; return real.open(dataSpec) }
            }
        }) { id -> calls++; AudioSource("offline:///$id", "audio/wav", 0) }
        assertEquals(0, calls)
        val source = factory.createDataSource()
        source.open(DataSpec.Builder().setUri("track:///abcdefghijk").build())
        val result = ByteArray(4)
        assertEquals(4, source.read(result, 0, 4))
        assertArrayEquals(bytes, result)
        source.close()
        source.open(DataSpec.Builder().setUri("track:///abcdefghijk").setPosition(2).build())
        source.close()
        assertEquals(1, calls)
        assertEquals("offline:///abcdefghijk", requests.first().uri.toString())
        assertEquals("abcdefghijk", requests.first().key)
        assertEquals(2L, requests.last().position)
    }
}
