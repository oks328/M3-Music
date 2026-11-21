package io.github.oks328.m3music.music

import org.junit.Assert.*
import org.junit.Test

class AudioSourceTest {
    @Test fun selectsHighestBitrateUsableAudio() {
        val low = AudioSource("https://example.com/low", "audio/mp4", 64)
        val high = AudioSource("https://example.com/high", "audio/webm", 128)
        val invalid = AudioSource("http://example.com/insecure", "audio/mp4", 256)
        assertEquals(high, selectAudioSource(listOf(invalid, low, high)))
    }

    @Test fun rejectsMissingAudioWithUsefulError() {
        val failure = assertThrows(IllegalStateException::class.java) {
            selectAudioSource(emptyList())
        }
        assertTrue(failure.message.orEmpty().contains("unavailable", ignoreCase = true))
    }
}
