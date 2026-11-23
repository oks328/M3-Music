package io.github.oks328.m3music

import android.os.Bundle
import android.content.Intent
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.oks328.m3music.ui.MusicScreen
import io.github.oks328.m3music.ui.SignInScreen
import io.github.oks328.m3music.ui.theme.M3MusicTheme
import io.github.oks328.m3music.downloads.OfflineDownloads
import io.github.oks328.m3music.account.YouTubeAccount
import io.github.oks328.m3music.account.YouTubeLoginActivity
import io.github.oks328.m3music.ui.AccountActions

class MainActivity : ComponentActivity() {
    private var activeModel: MusicViewModel? = null
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private fun requestDownloadNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onStart() {
        super.onStart()
        val account = YouTubeAccount.get(this).state.value
        if (account.profile != null && !account.expired) OfflineDownloads.get(this).resumePending()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val account = YouTubeAccount.get(this)
            val state = account.state.collectAsStateWithLifecycle().value
            val signIn = { startActivity(Intent(this, YouTubeLoginActivity::class.java)) }
            M3MusicTheme {
                if (state.initializing || state.profile == null || state.expired) {
                    LaunchedEffect(state.initializing, state.expired) {
                        if (!state.initializing) activeModel?.stopPlaying()
                    }
                    SignInScreen(state, signIn)
                    return@M3MusicTheme
                }
                val model: MusicViewModel = viewModel()
                SideEffect { activeModel = model }
                LaunchedEffect(state.profile) { OfflineDownloads.get(this@MainActivity).resumePending() }
                MusicScreen(
                    search = model.search.collectAsStateWithLifecycle().value,
                    playback = model.playback.collectAsStateWithLifecycle().value,
                    onSearch = model::search,
                    onRememberSearch = model::rememberSearch,
                    searchHistory = model.searchHistory.collectAsStateWithLifecycle().value,
                    onClearSearchHistory = model::clearSearchHistory,
                    loadDiscovery = account.controller::discovery,
                    online = model.online.collectAsStateWithLifecycle().value,
                    loadDiscoveryCollection = account.controller::discoveryCollection,
                    findTrackArtists = model::findTrackArtists,
                    findSongCollection = model::findSongCollection,
                    onPlay = model::play,
                    onTogglePlayback = model::togglePlayback,
                    onSeek = model::seek,
                    onRetryPlayback = model::retryPlayback,
                    downloads = model.downloads.collectAsStateWithLifecycle().value,
                    onDownload = { track ->
                        requestDownloadNotifications()
                        model.download(track)
                    },
                    loadCollectionTracks = account.controller::collectionTracks,
                    onDownloadAlbum = { album, tracks ->
                        requestDownloadNotifications()
                        model.downloadAlbum(album, tracks)
                    },
                    onDownloadPlaylist = { playlist, tracks ->
                        requestDownloadNotifications()
                        model.downloadPlaylist(playlist, tracks)
                    },
                    onRemoveDownload = model::removeDownload,
                    onWifiOnlyChanged = model::setWifiOnly,
                    library = model.library.collectAsStateWithLifecycle().value,
                    onFavorite = model::setFavorite,
                    onCreatePlaylist = model::createPlaylist,
                    onRenamePlaylist = model::renamePlaylist,
                    onDeletePlaylist = model::deletePlaylist,
                    onAddToPlaylist = model::addToPlaylist,
                    onRemovePlaylistSong = model::removeFromPlaylist,
                    onMovePlaylistSong = model::movePlaylistSong,
                    onClearHistory = model::clearHistory,
                    onDismissLibraryError = model::dismissLibraryError,
                    onPlayPlaylist = model::playPlaylist,
                    onPrevious = model::previous,
                    onNext = model::next,
                    onQueueItem = model::playQueueItem,
                    onMoveQueueItem = model::moveQueueItem,
                    onEnqueue = model::enqueue,
                    onShuffle = model::toggleShuffle,
                    onRepeat = model::cycleRepeat,
                    account = state,
                    accountActions = AccountActions(
                        signIn = signIn,
                        disconnect = {
                            model.stopPlaying()
                            account.disconnect()
                        },
                        category = account.controller::loadCategory,
                        open = account.controller::openCollection,
                        back = account.controller::back,
                        refresh = account.controller::refresh,
                        more = account.controller::loadMore,
                        perform = account.controller::perform,
                        editablePlaylists = account.controller::editablePlaylists,
                        savePlaylistTrack = account.controller::savePlaylistTrack,
                        suggestions = account.controller::playlistSuggestions,
                        searchSongs = io.github.oks328.m3music.music.YouTubeMusicRepository::search,
                    ),
                )
            }
        }
    }
}
