package io.github.oks328.m3music

import androidx.compose.ui.test.*
import androidx.compose.ui.text.rememberTextMeasurer
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
        compose.onNodeWithContentDescription("Back to player").performClick()
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
                MusicScreen(SearchState(query = "A song", tracks = listOf(track), hasSearched = true), PlaybackState(connected = true),
                    {}, {}, {}, {}, {}, onDownload = { selected = it })
            }
        }
        compose.onNodeWithText("Search").performClick()
        compose.onNodeWithContentDescription("More options for A song").performClick()
        compose.onNodeWithText("Download").performClick()
        compose.runOnIdle { assertEquals(track, selected) }
    }

    @Test fun downloadsRemainAvailableWithoutSearchResults() {
        val track = Track("abcdefghijk", "A saved song", "An artist", null, 180)
        var selected: Track? = null
        compose.setContent {
            M3MusicTheme {
                MusicScreen(SearchState(), PlaybackState(connected = true), {}, {}, {}, {}, {},
                    onPlayPlaylist = { tracks, index -> selected = tracks[index] },
                    downloads = DownloadsState(items = listOf(DownloadEntry(track, DownloadStatus.COMPLETED, 100, 123456)), loading = false))
            }
        }
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("Downloads").performClick()
        compose.onNodeWithText("1 song ready offline").assertIsDisplayed()
        compose.onNodeWithText("Downloaded Songs").performClick()
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
        compose.onNodeWithText("Library").performClick()
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
        compose.onNodeWithText("Songs, albums, or artists").performTextInput("Daft Punk")
        compose.onNodeWithContentDescription("Submit search").performClick()
        compose.runOnIdle { assertEquals("Daft Punk", submitted) }
    }

    @Test fun userCanSelectSearchResult() {
        val track = Track("abcdefghijk", "A song", "An artist", null, 180)
        var selected: Track? = null
        compose.setContent {
            M3MusicTheme {
                MusicScreen(SearchState(query = "A song", tracks = listOf(track), hasSearched = true),
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
    @Test fun recentSearchCanBeRunFromTheEmptySearchTab() {
        var submitted = ""
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(), PlaybackState(connected = true), { submitted = it }, {}, {}, {}, {},
                searchHistory = listOf("Daft Punk"))
        } }
        compose.onNodeWithText("Search").performClick()
        compose.onNodeWithText("Recent searches").assertIsDisplayed()
        compose.onNodeWithText("Daft Punk").performClick()
        compose.runOnIdle { assertEquals("Daft Punk", submitted) }
    }
    @Test fun swipeDownMinimizesTheExpandedPlayer() {
        val track = Track("abcdefghijk", "A song", "An artist", null, 180)
        compose.setContent {
            M3MusicTheme {
                MusicScreen(SearchState(), PlaybackState(connected = true, track = track, durationMs = 180000),
                    {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription("Open now playing").performClick()
        compose.onNodeWithText("Queue").performClick()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithContentDescription("Song position").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithContentDescription("Open now playing").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open now playing").performClick()
        compose.onRoot().performTouchInput {
            swipe(start = center.copy(y = height * 0.22f), end = center.copy(y = height * 0.52f))
        }
        compose.onNodeWithContentDescription("Song position").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open now playing").assertIsDisplayed()
    }
    @Test fun searchFiltersOpenArtistAndAlbumPages() {
        val album = io.github.oks328.m3music.account.AccountItem("MPREtest", "Test album",
            kind = io.github.oks328.m3music.account.AccountItemKind.ALBUM)
        val artist = io.github.oks328.m3music.account.AccountItem("UCtest", "Test artist",
            kind = io.github.oks328.m3music.account.AccountItemKind.ARTIST)
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(query = "test", hasSearched = true, collections = listOf(album, artist)),
                PlaybackState(connected = true), {}, {}, {}, {}, {},
                loadDiscoveryCollection = { id ->
                    io.github.oks328.m3music.account.AccountPage(if (id == artist.id) listOf(album) else emptyList())
                })
        } }
        compose.onNodeWithText("Search").performClick()
        compose.onNode(hasText("Artists") and hasClickAction()).performClick()
        compose.onNodeWithText("Test album").assertDoesNotExist()
        compose.onNodeWithText("Test artist").performClick()
        compose.onNodeWithText("Test album").performClick()
        compose.onNodeWithText("No songs or releases available.").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("Test artist").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNode(hasText("Albums") and hasClickAction()).performClick()
        compose.onNodeWithText("Test artist").assertDoesNotExist()
        compose.onNodeWithText("Test album").performClick()
        compose.onNodeWithText("No songs or releases available.").assertIsDisplayed()
    }
    @Test fun nowPlayingArtistOpensMusicPageAndBackRestoresPlayer() {
        val song = Track("abcdefghijk", "A song", "Test artist", null, 180)
        val artist = io.github.oks328.m3music.account.AccountItem("UCtest", song.artist,
            kind = io.github.oks328.m3music.account.AccountItemKind.ARTIST)
        val item = io.github.oks328.m3music.account.AccountItem(song.id, song.title,
            kind = io.github.oks328.m3music.account.AccountItemKind.SONG, track = song)
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(), PlaybackState(connected = true, track = song, durationMs = 180000),
                {}, {}, {}, {}, {}, findArtist = { name -> artist.takeIf { name == artist.title } },
                loadDiscoveryCollection = { id ->
                    assertEquals(artist.id, id)
                    io.github.oks328.m3music.account.AccountPage(items = listOf(item))
                })
        } }
        compose.onNodeWithContentDescription("Open now playing").performClick()
        compose.onNode(hasText("Test artist") and hasClickAction()).performClick()
        compose.onNodeWithText("Top songs").assertIsDisplayed()
        compose.onNodeWithText("Concerts").assertDoesNotExist()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithContentDescription("Song position").assertIsDisplayed()
    }
    @Test fun downloadedAlbumOpensItsOwnOfflineQueue() {
        val first = Track("abcdefghijk", "First album song", "Artist", null, 180)
        val second = Track("lmnopqrstuv", "Second album song", "Artist", null, 180)
        val standalone = Track("zyxwvutsrqp", "Standalone song", "Artist", null, 180)
        val album = io.github.oks328.m3music.downloads.DownloadedAlbum(
            "MPREtest", "Saved album", "Artist", null, listOf(first.id, second.id)
        )
        var queue = emptyList<Track>()
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(), PlaybackState(connected = true), {}, {}, {}, {}, {},
                downloads = DownloadsState(
                    items = listOf(second, standalone, first).map { DownloadEntry(it, DownloadStatus.COMPLETED) },
                    albums = listOf(album), loading = false),
                onPlayPlaylist = { songs, _ -> queue = songs })
        } }
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("Downloads").performClick()
        compose.onNodeWithText("Saved album").performClick()
        compose.onNodeWithText("Standalone song").assertDoesNotExist()
        compose.onNodeWithText("Remove download").assertDoesNotExist()
        compose.onNodeWithContentDescription("Favorite First album song").assertDoesNotExist()
        compose.onNodeWithContentDescription("Options for First album song").performScrollTo().performClick()
        compose.onNodeWithText("Remove download").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("Play", useUnmergedTree = true).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(first, second), queue) }
    }
    @Test fun streamedSongCanBeAddedToAYouTubePlaylistFromNowPlaying() {
        val track = Track("abcdefghijk", "Streamed song", "Artist", null, 180)
        val playlist = io.github.oks328.m3music.account.AccountItem("VLtest", "Road trip",
            kind = io.github.oks328.m3music.account.AccountItemKind.PLAYLIST, editable = true)
        var added: Track? = null
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(), PlaybackState(connected = true, track = track, durationMs = 180000),
                {}, {}, {}, {}, {}, accountActions = io.github.oks328.m3music.ui.AccountActions(
                    editablePlaylists = { io.github.oks328.m3music.account.AccountPage(listOf(playlist)) },
                    savePlaylistTrack = { target, song ->
                        assertEquals(playlist.id, target.id)
                        added = song
                        true
                    }))
        } }
        compose.onNodeWithContentDescription("Open now playing").performClick()
        compose.onNodeWithContentDescription("Save to local favorites").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add to playlist").performClick()
        compose.onNodeWithText("Road trip").performClick()
        compose.onNodeWithText("Added to Road trip").assertIsDisplayed()
        compose.runOnIdle { assertEquals(track, added) }
    }
    @Test fun searchSongMenuHasSixActionsAndOpensTheLinkedAlbum() {
        val track = Track("abcdefghijk", "A song", "Artist", null, 180)
        val album = io.github.oks328.m3music.account.AccountItem("MPREtest", "Linked album",
            kind = io.github.oks328.m3music.account.AccountItemKind.ALBUM)
        var enqueued: Track? = null
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(query = "A song", tracks = listOf(track), hasSearched = true),
                PlaybackState(connected = true), {}, {}, {}, {}, {}, onEnqueue = { enqueued = it },
                findSongCollection = { selected, kind ->
                    assertEquals(track, selected)
                    album.takeIf { kind == album.kind }
                })
        } }
        compose.onNodeWithText("Search").performClick()
        compose.onNodeWithContentDescription("More options for A song").performClick()
        listOf("Play", "Add to queue", "Add to playlist", "Go to artist", "Go to album", "Download")
            .forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("Add to queue").performClick()
        compose.runOnIdle { assertEquals(track, enqueued) }
        compose.onNodeWithContentDescription("More options for A song").performClick()
        compose.onNodeWithText("Go to album").performClick()
        compose.onNodeWithText("Linked album").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithContentDescription("More options for A song").assertIsDisplayed()
    }

    @Test fun queueRowsCanMoveUpOrDown() {
        val first = Track("abcdefghijk", "First song", "Artist", null, 180)
        val second = Track("lmnopqrstuv", "Second song", "Artist", null, 180)
        var moved: Pair<Int, Int>? = null
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(), PlaybackState(connected = true, track = first, queue = listOf(first, second),
                queueSize = 2), {}, {}, {}, {}, {}, onMoveQueueItem = { from, to -> moved = from to to })
        } }
        compose.onNodeWithContentDescription("Open now playing").performClick()
        compose.onNodeWithText("Queue").performClick()
        compose.onNodeWithContentDescription("Reorder Second song").performClick()
        compose.onNodeWithText("Move down").assertIsNotEnabled()
        compose.onNodeWithText("Move up").performClick()
        compose.runOnIdle { assertEquals(1 to 0, moved) }
    }
    @Test fun collaborationCreditOffersIndividualArtistPages() {
        val track = Track("abcdefghijk", "A duet", "First Artist & Second Artist", null, 180)
        val artists = listOf("First Artist", "Second Artist").mapIndexed { index, name ->
            io.github.oks328.m3music.account.AccountItem("UC$index", name,
                kind = io.github.oks328.m3music.account.AccountItemKind.ARTIST)
        }
        compose.setContent { M3MusicTheme {
            MusicScreen(SearchState(), PlaybackState(connected = true, track = track, durationMs = 180000),
                {}, {}, {}, {}, {}, findTrackArtists = { artists })
        } }
        compose.onNodeWithContentDescription("Open now playing").performClick()
        compose.onNodeWithText(track.artist).performClick()
        compose.onNodeWithText("Go to artist").assertIsDisplayed()
        compose.onNodeWithText("Second Artist").performClick()
        compose.onNodeWithText("Second Artist").assertIsDisplayed()
        compose.onNodeWithText("Go to artist").assertDoesNotExist()
    }
    @Test fun wordmarkUsesTheCondensedFontAxisAtTheSameTextSize() {
        var widths: Pair<Int, Int>? = null
        compose.setContent { M3MusicTheme {
            val measurer = rememberTextMeasurer()
            val heading = io.github.oks328.m3music.ui.theme.LocalMusicTypeStyles.current.brandTitle
            val body = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                .copy(fontSize = heading.fontSize, letterSpacing = heading.letterSpacing)
            widths = measurer.measure("M3 MUSIC", heading).size.width to measurer.measure("M3 MUSIC", body).size.width
        } }
        compose.runOnIdle {
            val (condensed, regular) = requireNotNull(widths)
            org.junit.Assert.assertTrue("Condensed width $condensed vs regular width $regular", condensed < regular * 0.8)
        }
    }
}
