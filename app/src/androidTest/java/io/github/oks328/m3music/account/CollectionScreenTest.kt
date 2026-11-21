package io.github.oks328.m3music.account

import androidx.compose.ui.test.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.oks328.m3music.music.SearchState
import io.github.oks328.m3music.music.Track
import io.github.oks328.m3music.playback.PlaybackState
import io.github.oks328.m3music.ui.AccountActions
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
        compose.onNodeWithText("Play").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(tracks, queue); assertEquals(0, start) }
        compose.onNodeWithText("Shuffle").performClick()
        compose.runOnIdle { assertEquals(tracks.size, queue.size); assertEquals(tracks.toSet(), queue.toSet()); assertEquals(0, start) }
    }

    @Test fun partialCollectionLabelsPlaybackAndEmptyCollectionCannotPlay() {
        var current by androidx.compose.runtime.mutableStateOf(state(more = true))
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(), PlaybackState(connected = true), {}, {}, {}, {}, {}, account = current)
        } }
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
        compose.onNodeWithContentDescription("More options for First song").performScrollTo().performClick()
        compose.onNodeWithText("Remove from YouTube playlist").performClick()
        compose.runOnIdle { assertNull(action) }
        compose.onNodeWithText("Remove on YouTube").performClick()
        compose.runOnIdle { assertEquals(AccountAction.RemoveSong("VLtest", tracks[0].id, "set-${tracks[0].id}"), action) }
    }
}
