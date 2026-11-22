package io.github.oks328.m3music.account

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.oks328.m3music.music.SearchState
import io.github.oks328.m3music.playback.PlaybackState
import io.github.oks328.m3music.ui.AccountActions
import io.github.oks328.m3music.ui.MusicScreen
import io.github.oks328.m3music.ui.theme.M3MusicTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class AccountScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun libraryPlaybackWaitsForLikedSongsAndStartsOnlyOnce() {
        val song = io.github.oks328.m3music.music.Track("abcdefghijk", "A liked song", "Artist", null, 180)
        var state by androidx.compose.runtime.mutableStateOf(AccountState(initializing = false, profile = AccountProfile("Listener")))
        var plays = 0
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(), PlaybackState(connected = true), {}, {}, {}, {}, {}, account = state,
                accountActions = AccountActions(category = { state = state.copy(category = it, loading = true, page = AccountPage()) }),
                onPlayPlaylist = { tracks, index -> assertEquals(listOf(song), tracks); assertEquals(0, index); plays++ })
        } }
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("Liked songs").performClick()
        compose.onNodeWithText("Play liked songs").performClick()
        compose.runOnIdle { assertEquals(0, plays); state = state.copy(loading = false,
            page = AccountPage(listOf(AccountItem(song.id, song.title, kind = AccountItemKind.SONG, track = song)))) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, plays); state = state.copy(message = "Refreshed") }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, plays) }
    }
    @Test fun signedOutAccountOffersAnExplicitConnectionAction() {
        var clicked = false
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(), PlaybackState(connected = true), {}, {}, {}, {}, {},
                accountActions = AccountActions(signIn = { clicked = true }))
        } }
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("Connect Google account").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(clicked) }
    }
    @Test fun expiredSessionDoesNotDisplayCachedPrivateItems() {
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(), PlaybackState(connected = true), {}, {}, {}, {}, {}, account = AccountState(
                initializing = false, profile = AccountProfile("Listener"), expired = true,
                page = AccountPage(listOf(AccountItem("VLprivate", "Private playlist", kind = AccountItemKind.PLAYLIST)))))
        } }
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("Reconnect Google account").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Private playlist").assertDoesNotExist()
    }
    @Test fun disconnectNeedsTheAccountConfirmation() {
        var disconnected = false
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(), PlaybackState(connected = true), {}, {}, {}, {}, {},
                account = AccountState(initializing = false, profile = AccountProfile("Listener")),
                accountActions = AccountActions(disconnect = { disconnected = true }))
        } }
        compose.onNodeWithContentDescription("Account").performClick()
        compose.onNodeWithText("Disconnect account").performClick()
        compose.runOnIdle { assertFalse(disconnected) }
        compose.onNodeWithText("Disconnect", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertTrue(disconnected) }
    }
    @Test fun signedInLibraryShowsAccountMusic() {
        var refreshed = false
        var opened: AccountItem? = null
        val playlist = AccountItem("VLtest", "My listening collection", kind = AccountItemKind.PLAYLIST)
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(), PlaybackState(connected = true), {}, {}, {}, {}, {},
                account = AccountState(initializing = false, profile = AccountProfile("Listener"), page = AccountPage(listOf(playlist))),
                accountActions = AccountActions(refresh = { refreshed = true }, open = { opened = it }))
        } }
        compose.onNodeWithText("Library").performClick()
        compose.onNode(hasText("Library") and hasClickAction()).assertIsSelected()
        compose.onNodeWithText("Songs, albums, or artists").assertDoesNotExist()
        compose.onNodeWithText("My listening collection").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(refreshed); assertEquals(playlist, opened) }
    }

    @Test fun libraryLikedSongsFilterLoadsTheAccountCategory() {
        var category: AccountCategory? = null
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(), PlaybackState(connected = true), {}, {}, {}, {}, {},
                account = AccountState(initializing = false, profile = AccountProfile("Listener")),
                accountActions = AccountActions(category = { category = it }))
        } }
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("Liked songs").performClick()
        compose.runOnIdle { assertEquals(AccountCategory.LIKED_SONGS, category) }
    }
    @Test fun newLibraryPlaylistUsesYouTubeMusic() {
        var action: AccountAction? = null
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(), PlaybackState(connected = true), {}, {}, {}, {}, {},
                account = AccountState(initializing = false, profile = AccountProfile("Listener")),
                accountActions = AccountActions(perform = { action = it }))
        } }
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("New playlist").performScrollTo().performClick()
        compose.onNodeWithText("YouTube playlist name").performTextInput("Road trip")
        compose.onNodeWithText("Create on YouTube").performClick()
        compose.runOnIdle { assertEquals(AccountAction.CreatePlaylist("Road trip"), action) }
    }
}
