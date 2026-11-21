package io.github.oks328.m3music.library

import androidx.test.platform.app.InstrumentationRegistry
import io.github.oks328.m3music.music.Track
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LocalLibraryStoreTest {
    private val song = Track("abcdefghijk", "A song", "An artist", null, 180)
    private val other = Track("lmnopqrstuv", "Another song", "Another artist", null, 200)

    private fun withStore(test: (LocalLibraryStore, String) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "library-test-${UUID.randomUUID()}.db"
        try { LocalLibraryStore(context, name).use { test(it, name) } }
        finally { context.deleteDatabase(name) }
    }

    @Test fun playlistFavoritesAndHistorySurviveReopening() = withStore { store, name ->
        val id = store.createPlaylist("  Road trip  ")
        store.addToPlaylist(id, song)
        store.setFavorite(song, true)
        store.recordPlayed(song)
        store.close()
        LocalLibraryStore(InstrumentationRegistry.getInstrumentation().targetContext, name).use { reopened ->
            val snapshot = reopened.snapshot()
            assertEquals(listOf(song), snapshot.favorites)
            assertEquals(listOf(song), snapshot.history)
            assertEquals("Road trip", snapshot.playlists.single().name)
            assertEquals(listOf(song), snapshot.playlists.single().tracks)
        }
    }

    @Test fun addingTheSameSongTwiceDoesNotDuplicateIt() = withStore { store, _ ->
        val id = store.createPlaylist("Favorites")
        store.addToPlaylist(id, song)
        store.addToPlaylist(id, song)
        store.setFavorite(song, true)
        store.setFavorite(song, true)
        assertEquals(listOf(song), store.snapshot().playlists.single().tracks)
        assertEquals(listOf(song), store.snapshot().favorites)
    }

    @Test fun songsCanBeReorderedAndRemoved() = withStore { store, _ ->
        val id = store.createPlaylist("Road trip")
        store.addToPlaylist(id, song)
        store.addToPlaylist(id, other)
        store.moveTrack(id, other.id, -1)
        assertEquals(listOf(other, song), store.snapshot().playlists.single().tracks)
        store.moveTrack(id, other.id, -1)
        assertEquals(listOf(other, song), store.snapshot().playlists.single().tracks)
        store.removeFromPlaylist(id, other.id)
        store.addToPlaylist(id, other)
        assertEquals(listOf(song, other), store.snapshot().playlists.single().tracks)
    }

    @Test fun deletingPlaylistKeepsFavoritesAndHistory() = withStore { store, _ ->
        val id = store.createPlaylist("Road trip")
        store.addToPlaylist(id, song)
        store.setFavorite(song, true)
        store.recordPlayed(song)
        store.renamePlaylist(id, "Renamed")
        assertEquals("Renamed", store.snapshot().playlists.single().name)
        store.deletePlaylist(id)
        assertTrue(store.snapshot().playlists.isEmpty())
        assertEquals(listOf(song), store.snapshot().favorites)
        assertEquals(listOf(song), store.snapshot().history)
    }

    @Test fun recentHistoryIsDeduplicatedAndCanBeCleared() = withStore { store, _ ->
        store.recordPlayed(song)
        store.recordPlayed(other)
        store.recordPlayed(song)
        assertEquals(listOf(song, other), store.snapshot().history)
        store.clearHistory()
        assertTrue(store.snapshot().history.isEmpty())
    }

    @Test fun invalidAndDuplicateNamesLeaveExistingPlaylistsUntouched() = withStore { store, _ ->
        val id = store.createPlaylist("Road trip")
        assertThrows(IllegalArgumentException::class.java) { store.createPlaylist(" ") }
        assertThrows(IllegalArgumentException::class.java) { store.createPlaylist("ROAD TRIP") }
        assertThrows(IllegalArgumentException::class.java) { store.renamePlaylist(id, " ") }
        assertEquals(listOf("Road trip"), store.snapshot().playlists.map { it.name })
    }
}
