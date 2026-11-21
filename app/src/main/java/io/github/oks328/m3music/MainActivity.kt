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
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.oks328.m3music.ui.MusicScreen
import io.github.oks328.m3music.ui.theme.M3MusicTheme
import io.github.oks328.m3music.downloads.OfflineDownloads
import io.github.oks328.m3music.account.YouTubeAccount
import io.github.oks328.m3music.account.YouTubeLoginActivity
import io.github.oks328.m3music.ui.AccountActions

class MainActivity : ComponentActivity() {
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onStart() {
        super.onStart()
        OfflineDownloads.get(this).resumePending()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: MusicViewModel = viewModel()
            val account = YouTubeAccount.get(this)
            M3MusicTheme {
                MusicScreen(
                    search = model.search.collectAsStateWithLifecycle().value,
                    playback = model.playback.collectAsStateWithLifecycle().value,
                    onSearch = model::search,
                    onPlay = model::play,
                    onTogglePlayback = model::togglePlayback,
                    onSeek = model::seek,
                    onRetryPlayback = model::retryPlayback,
                    downloads = model.downloads.collectAsStateWithLifecycle().value,
                    onDownload = { track ->
                        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        model.download(track)
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
                    account = account.state.collectAsStateWithLifecycle().value,
                    accountActions = AccountActions(
                        signIn = { startActivity(Intent(this, YouTubeLoginActivity::class.java)) },
                        disconnect = account::disconnect,
                        category = account.controller::loadCategory,
                        open = account.controller::openCollection,
                        back = account.controller::back,
                        refresh = account.controller::refresh,
                        more = account.controller::loadMore,
                        perform = account.controller::perform,
                    ),
                )
            }
        }
    }
}
