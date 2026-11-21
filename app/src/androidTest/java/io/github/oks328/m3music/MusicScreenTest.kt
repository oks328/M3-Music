package io.github.oks328.m3music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.oks328.m3music.music.SearchState
import io.github.oks328.m3music.music.Track
import io.github.oks328.m3music.playback.PlaybackState
import io.github.oks328.m3music.ui.MusicScreen
import io.github.oks328.m3music.ui.theme.M3MusicTheme
import io.github.oks328.m3music.downloads.DownloadsState
import io.github.oks328.m3music.downloads.DownloadEntry
import io.github.oks328.m3music.downloads.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

class MusicScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun largeTextLightPlayerSupportsSeekAndExplicitClose() {
        val track = Track("abcdefghijk", "A very long song title that wraps across multiple lines", "An artist with a long display name", null, 180)
        var sought = -1L
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                M3MusicTheme(darkTheme = false, dynamicColor = false) {
                    MusicScreen(SearchState(), PlaybackState(connected = true, track = track, durationMs = 180000),
                        {}, {}, {}, { sought = it }, {})
                }
            }
        }
        compose.onNodeWithContentDescription("Open now playing").performClick()
        compose.onAllNodesWithContentDescription("Play").onLast().assertIsDisplayed()
        compose.onNodeWithText("Home").assertDoesNotExist()
        compose.onNodeWithText("0:00").assertIsDisplayed()
        compose.onNodeWithText("3:00").assertIsDisplayed()
        compose.onNodeWithContentDescription("Song position").assertIsDisplayed()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(90000f) }
        compose.runOnIdle { assertEquals(90000L, sought) }
        compose.waitForIdle()
        if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("capturePolish") == "true") {
            val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "polish-player-large.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
        }
        compose.onNodeWithContentDescription("Close now playing").performClick()
        compose.onNodeWithContentDescription("Song position").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open now playing").assertIsDisplayed()
    }

    @Test fun expandedQueueSelectsTheRequestedEntry() {
        val first = Track("abcdefghijk", "First queued song", "Artist", null, 180)
        val second = Track("lmnopqrstuv", "Second queued song", "Artist", null, 120)
        var selected = -1
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(), PlaybackState(connected = true, track = first, durationMs = 180000,
                queue = listOf(first, second), queueSize = 2), {}, {}, {}, {}, {}, onQueueItem = { selected = it })
        } }
        compose.onNodeWithContentDescription("Open now playing").performClick()
        compose.onNodeWithText("Queue").performClick()
        compose.onNodeWithContentDescription("Play Second queued song by Artist").performClick()
        compose.runOnIdle { assertEquals(1, selected) }
        compose.onNodeWithText("Back to player").performClick()
        compose.onNodeWithContentDescription("Song position").assertIsDisplayed().assertIsDisplayed()
    }

    @Test fun compactPlayerExpandsToSeekControls() {
        val track = Track("abcdefghijk", "A playing song", "An artist", null, 180)
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(), PlaybackState(connected = true, track = track, durationMs = 180000), {}, {}, {}, {}, {})
        } }
        compose.onNodeWithContentDescription("Song position").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open now playing").performClick()
        compose.onNodeWithText("Now playing").assertIsDisplayed()
        compose.onNodeWithContentDescription("Song position").assertIsDisplayed().assertIsDisplayed()
    }

    @Test fun downloadActionSelectsTheCorrectSong() {
        val track = Track("abcdefghijk", "A song", "An artist", null, 180)
        var selected: Track? = null
        compose.setContent {
            M3MusicTheme {
                MusicScreen(SearchState(tracks = listOf(track), hasSearched = true), PlaybackState(connected = true),
                    {}, {}, {}, {}, {}, onDownload = { selected = it })
            }
        }
        compose.onNodeWithText("Search").performClick()
        compose.onNodeWithContentDescription("Download A song").performClick()
        compose.runOnIdle { assertEquals(track, selected) }
    }

    @Test fun downloadsRemainAvailableWithoutSearchResults() {
        val track = Track("abcdefghijk", "A saved song", "An artist", null, 180)
        var selected: Track? = null
        compose.setContent {
            M3MusicTheme {
                MusicScreen(SearchState(), PlaybackState(connected = true), {}, { selected = it }, {}, {}, {},
                    downloads = DownloadsState(items = listOf(DownloadEntry(track, DownloadStatus.COMPLETED, 100, 123456)), loading = false))
            }
        }
        compose.onNodeWithText("Downloads").performClick()
        compose.onNodeWithText("1 songs ready offline").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play A saved song by An artist").performClick()
        compose.runOnIdle { assertEquals(track, selected) }
    }

    @Test fun wifiOnlySettingCanBeChanged() {
        var setting: Boolean? = null
        compose.setContent {
            M3MusicTheme {
                MusicScreen(SearchState(), PlaybackState(connected = true), {}, {}, {}, {}, {},
                    onWifiOnlyChanged = { setting = it })
            }
        }
        compose.onNodeWithText("Downloads").performClick()
        compose.onNodeWithContentDescription("Download on Wi-Fi only").performClick()
        compose.runOnIdle { assertEquals(false, setting) }
    }

    @Test fun userCanSubmitSongSearch() {
        var submitted = ""
        compose.setContent {
            M3MusicTheme {
                MusicScreen(SearchState(), PlaybackState(connected = true),
                    { submitted = it }, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Search").performClick()
        compose.onNodeWithText("Songs or artists").performTextInput("Daft Punk")
        compose.onNodeWithContentDescription("Submit search").performClick()
        compose.runOnIdle { assertEquals("Daft Punk", submitted) }
    }

    @Test fun userCanSelectSearchResult() {
        val track = Track("abcdefghijk", "A song", "An artist", null, 180)
        var selected: Track? = null
        compose.setContent {
            M3MusicTheme {
                MusicScreen(SearchState(tracks = listOf(track), hasSearched = true),
                    PlaybackState(connected = true), {}, { selected = it }, {}, {}, {})
            }
        }
        compose.onNodeWithText("Search").performClick()
        compose.onNodeWithContentDescription("Play A song by An artist").performClick()
        compose.runOnIdle { assertEquals(track, selected) }
    }

    @Test fun searchFailureOffersRetry() {
        compose.setContent {
            M3MusicTheme {
                MusicScreen(SearchState(query = "artist", error = "Connection failed", hasSearched = true),
                    PlaybackState(connected = true), {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Search").performClick()
        compose.onNodeWithText("Connection failed").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertHasClickAction()
    }
}
