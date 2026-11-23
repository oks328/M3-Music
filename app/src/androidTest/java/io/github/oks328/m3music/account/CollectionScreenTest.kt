package io.github.oks328.m3music.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.oks328.m3music.music.SearchState
import io.github.oks328.m3music.music.Track
import io.github.oks328.m3music.playback.PlaybackState
import io.github.oks328.m3music.downloads.DownloadsState
import io.github.oks328.m3music.ui.AccountActions
import io.github.oks328.m3music.ui.CollectionDownloadButton
import io.github.oks328.m3music.ui.CollectionPlaybackButtons
import io.github.oks328.m3music.ui.MusicScreen
import io.github.oks328.m3music.ui.theme.M3MusicTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CollectionScreenTest {
    @get:Rule val compose = createComposeRule()
    private val tracks = listOf(Track("abcdefghijk", "First song", "Artist", null, 180), Track("lmnopqrstuv", "Second song", "Artist", null, 120))
    private fun state(editable: Boolean = false, more: Boolean = false, empty: Boolean = false) = AccountState(
        initializing = false, profile = AccountProfile("Listener"),
        collection = AccountItem("VLtest", "My playlist", kind = AccountItemKind.PLAYLIST, editable = editable),
        detail = AccountPage(if (empty) emptyList() else tracks.map { AccountItem(it.id, it.title, kind = AccountItemKind.SONG, track = it, setVideoId = "set-${it.id}") },
            continuation = if (more) "next-page" else null))

    @Test fun playPreservesOrderAndShuffleIncludesEveryLoadedSong() {
        var queue = emptyList<Track>()
        var start = -1
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(), PlaybackState(connected = true), {}, {}, {}, {}, {}, account = state(),
                onPlayPlaylist = { songs, index -> queue = songs; start = index })
        } }
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("Play").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(tracks, queue); assertEquals(0, start) }
        compose.onNodeWithText("Shuffle").performClick()
        compose.runOnIdle { assertEquals(tracks.size, queue.size); assertEquals(tracks.toSet(), queue.toSet()); assertEquals(0, start) }
    }

    @Test fun shuffleRemainsAccessibleWhenItsLabelDoesNotFit() {
        var queue = emptyList<Track>()
        compose.setContent { M3MusicTheme {
            Box(Modifier.width(190.dp)) {
                CollectionPlaybackButtons(tracks, false, true, onPlay = { songs, _ -> queue = songs },
                    downloadAction = { CollectionDownloadButton("playlist", tracks, false, DownloadsState(), false) {} })
            }
        } }
        compose.onNodeWithText("Shuffle").assertDoesNotExist()
        compose.onNodeWithContentDescription("Shuffle").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Download playlist").assertIsDisplayed()
        compose.runOnIdle { assertEquals(tracks.toSet(), queue.toSet()) }
    }

    @Test fun partialCollectionLabelsPlaybackAndEmptyCollectionCannotPlay() {
        var current by androidx.compose.runtime.mutableStateOf(state(more = true))
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(), PlaybackState(connected = true), {}, {}, {}, {}, {}, account = current)
        } }
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("Play loaded").performScrollTo().assertIsEnabled()
        compose.runOnIdle { current = state(empty = true) }
        compose.onNodeWithText("Play").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Shuffle").assertIsNotEnabled()
    }

    @Test fun readOnlyCollectionHasNoRemoteEditingActions() {
        val album = state().let { it.copy(collection = it.collection?.copy(kind = AccountItemKind.ALBUM)) }
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(), PlaybackState(connected = true), {}, {}, {}, {}, {}, account = album)
        } }
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithContentDescription("Collection options").performClick()
        compose.onNodeWithText("Refresh collection").assertIsDisplayed()
        compose.onNodeWithText("Rename on YouTube").assertDoesNotExist()
        compose.onNodeWithText("Delete on YouTube").assertDoesNotExist()
    }

    @Test fun songRemovalUsesTheSpecificPlaylistEntryAndConfirmation() {
        var action: AccountAction? = null
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(), PlaybackState(connected = true), {}, {}, {}, {}, {}, account = state(editable = true),
                accountActions = AccountActions(perform = { action = it }))
        } }
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithContentDescription("More options for First song").performScrollTo().performClick()
        compose.onNodeWithText("Remove from YouTube playlist").performClick()
        compose.runOnIdle { assertNull(action) }
        compose.onNodeWithText("Remove on YouTube").performClick()
        compose.runOnIdle { assertEquals(AccountAction.RemoveSong("VLtest", tracks[0].id, "set-${tracks[0].id}"), action) }
    }
    @Test fun playlistDownloadQueuesSongsFromLaterPages() {
        val extra = Track("zyxwvutsrqp", "Later song", "Artist", null, 125)
        var queued = emptyList<Track>()
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(), PlaybackState(connected = true), {}, {}, {}, {}, {},
                account = state(more = true),
                loadCollectionTracks = { id, page, fromLibrary ->
                    assertEquals("VLtest", id)
                    assertEquals("next-page", page.continuation)
                    assertTrue(fromLibrary)
                    tracks + extra
                },
                onDownloadPlaylist = { playlist, songs ->
                    assertEquals("VLtest", playlist.id)
                    assertEquals((tracks + extra).map { it.id }, playlist.trackIds)
                    queued = songs
                })
        } }
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithContentDescription("Download playlist").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(tracks + extra, queued) }
    }
    @Test fun editablePlaylistShowsSuggestionsAndSearchAddsToTheOpenPlaylist() {
        var action: AccountAction? = null
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(), PlaybackState(connected = true), {}, {}, {}, {}, {},
                account = state(editable = true, empty = true),
                accountActions = AccountActions(suggestions = { _, _ -> listOf(tracks.first()) },
                    searchSongs = { listOf(tracks.last()) }, perform = { action = it }))
        } }
        compose.onNodeWithText("Library").performClick()
        val tracklist = compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
        tracklist.performScrollToNode(hasText("Suggestions"))
        compose.onNodeWithText("Suggestions").assertIsDisplayed()
        tracklist.performScrollToNode(hasText("Add a song"))
        compose.onNodeWithText("Add a song", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Search songs").performTextInput("Second")
        compose.onNodeWithContentDescription("Submit song search").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Add Second song to playlist").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Add Second song to playlist").performClick()
        compose.runOnIdle { assertEquals(AccountAction.AddSong("VLtest", tracks.last().id), action) }
    }
}
