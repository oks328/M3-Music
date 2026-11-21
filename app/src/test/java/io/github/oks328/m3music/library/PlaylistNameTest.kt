package io.github.oks328.m3music.library

import org.junit.Assert.*
import org.junit.Test

class PlaylistNameTest {
    @Test fun trimsWhitespaceWithoutChangingTheName() {
        assertEquals("Road trip 🎵", normalizePlaylistName("  Road trip 🎵  "))
    }
    @Test fun rejectsBlankNames() {
        assertThrows(IllegalArgumentException::class.java) { normalizePlaylistName(" \n ") }
    }
    @Test fun acceptsNamesAtTheLengthBoundary() {
        listOf(79, 80).forEach { assertEquals("x".repeat(it), normalizePlaylistName("x".repeat(it))) }
    }
    @Test fun rejectsNamesOverTheLengthBoundary() {
        assertThrows(IllegalArgumentException::class.java) { normalizePlaylistName("x".repeat(81)) }
    }
}
