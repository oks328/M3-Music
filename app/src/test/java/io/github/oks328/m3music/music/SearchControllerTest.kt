package io.github.oks328.m3music.music

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SearchControllerTest {
    private val song = Track("abc123", "A song", "An artist", null, 180)

    @Test fun blankQueryDoesNotContactCatalog() = runTest {
        var calls = 0
        val controller = SearchController(backgroundScope) { calls++; listOf(song) }
        controller.search("  ")
        runCurrent()
        assertEquals(0, calls)
        assertFalse(controller.state.value.hasSearched)
    }

    @Test fun searchPublishesMatchingTracks() = runTest {
        var requested = ""
        val controller = SearchController(backgroundScope) { requested = it; listOf(song) }
        controller.search("  artist  ")
        assertTrue(controller.state.value.loading)
        runCurrent()
        assertEquals("artist", requested)
        assertEquals(listOf(song), controller.state.value.tracks)
        assertFalse(controller.state.value.loading)
        assertTrue(controller.state.value.hasSearched)
    }

    @Test fun emptyResultsRemainDistinctFromAnError() = runTest {
        val controller = SearchController(backgroundScope) { emptyList() }
        controller.search("unknown")
        runCurrent()
        assertTrue(controller.state.value.hasSearched)
        assertTrue(controller.state.value.tracks.isEmpty())
        assertNull(controller.state.value.error)
    }

    @Test fun networkFailureAllowsRetry() = runTest {
        var fail = true
        val controller = SearchController(backgroundScope) {
            if (fail) throw IOException("offline")
            listOf(song)
        }
        controller.search("artist")
        runCurrent()
        assertNotNull(controller.state.value.error)
        assertFalse(controller.state.value.loading)
        fail = false
        controller.search("artist")
        runCurrent()
        assertNull(controller.state.value.error)
        assertEquals(listOf(song), controller.state.value.tracks)
    }

    @Test fun newSearchCannotBeOverwrittenByOldResults() = runTest {
        val first = CompletableDeferred<List<Track>>()
        val controller = SearchController(backgroundScope) {
            if (it == "first") first.await() else listOf(song)
        }
        controller.search("first")
        runCurrent()
        controller.search("second")
        runCurrent()
        first.complete(emptyList())
        runCurrent()
        assertEquals("second", controller.state.value.query)
        assertEquals(listOf(song), controller.state.value.tracks)
    }
}
