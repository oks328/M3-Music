package io.github.oks328.m3music.library

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.oks328.m3music.music.SearchState
import io.github.oks328.m3music.music.Track
import io.github.oks328.m3music.playback.PlaybackState
import io.github.oks328.m3music.ui.MusicScreen
import io.github.oks328.m3music.ui.theme.M3MusicTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryScreenTest {
    @get:Rule val compose = createComposeRule()
    private val track = Track("abcdefghijk", "A song", "An artist", null, 180)

    @Test fun aLocalPlaylistCanBeCreatedOnDevice() {
        var name = ""
        compose.setContent {
            M3MusicTheme {
                MusicScreen(SearchState(), PlaybackState(connected = true), {}, {}, {}, {}, {},
                    library = LibraryState(loading = false), onCreatePlaylist = { value, _ -> name = value })
            }
        }
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("On device").performClick()
        compose.onNodeWithText("New playlist").performClick()
        compose.onNodeWithText("Playlist name").performTextInput("Road trip")
        compose.onNodeWithText("Create").performClick()
        compose.runOnIdle { assertEquals("Road trip", name) }
    }

    @Test fun historySongCanBeFavoritedAndAddedToLocalPlaylist() {
        var favorite: Boolean? = null
        var added: Track? = null
        compose.setContent {
            M3MusicTheme {
                MusicScreen(SearchState(), PlaybackState(connected = true), {}, {}, {}, {}, {},
                    library = LibraryState(playlists = listOf(LocalPlaylist("list", "Road trip")), history = listOf(track), loading = false),
                    onFavorite = { _, value -> favorite = value }, onAddToPlaylist = { _, value -> added = value })
            }
        }
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("On device").performClick()
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithContentDescription("Favorite A song").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, favorite) }
        compose.onNodeWithContentDescription("Add A song to playlist").performScrollTo().performClick()
        compose.onNodeWithText("Road trip").performClick()
        compose.runOnIdle { assertEquals(track, added) }
    }

    @Test fun playlistPlaybackUsesTheStoredOrder() {
        var selected: List<Track>? = null
        compose.setContent {
            M3MusicTheme {
                MusicScreen(SearchState(), PlaybackState(connected = true), {}, {}, {}, {}, {},
                    library = LibraryState(playlists = listOf(LocalPlaylist("list", "Road trip", listOf(track))), loading = false),
                    onPlayPlaylist = { tracks, _ -> selected = tracks })
            }
        }
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("On device").performClick()
        compose.onNodeWithText("Road trip").performClick()
        compose.onNodeWithText("Search").performClick()
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("Play playlist").performClick()
        compose.runOnIdle { assertEquals(listOf(track), selected) }
    }

    @Test fun deletingAPlaylistRequiresTheSpecificConfirmation() {
        var deleted: String? = null
        compose.setContent {
            M3MusicTheme {
                MusicScreen(SearchState(), PlaybackState(connected = true), {}, {}, {}, {}, {},
                    library = LibraryState(playlists = listOf(LocalPlaylist("list", "Road trip")), loading = false),
                    onDeletePlaylist = { deleted = it })
            }
        }
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("On device").performClick()
        compose.onNodeWithText("Road trip").performClick()
        compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertNull(deleted) }
        compose.onNodeWithText("Delete playlist").performClick()
        compose.runOnIdle { assertEquals("list", deleted) }
    }
}
