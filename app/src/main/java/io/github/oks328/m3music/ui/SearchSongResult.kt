package io.github.oks328.m3music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import io.github.oks328.m3music.R
import io.github.oks328.m3music.downloads.DownloadEntry
import io.github.oks328.m3music.downloads.DownloadStatus
import io.github.oks328.m3music.downloads.active
import io.github.oks328.m3music.music.Track
import io.github.oks328.m3music.playback.PlaybackState

@Composable
internal fun SearchSongResult(
    track: Track,
    playback: PlaybackState,
    download: DownloadEntry?,
    loadingCollection: Boolean,
    onPlay: () -> Unit,
    onEnqueue: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onArtist: () -> Unit,
    onAlbum: () -> Unit,
    onDownload: () -> Unit,
) {
    var menu by remember(track.id) { mutableStateOf(false) }
    val saved = download?.status == DownloadStatus.COMPLETED
    val downloading = download?.active == true
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            SongRow(track, playback.track?.id == track.id, playback.connected, offline = saved,
                containerColor = Color.Transparent, compact = true, onClick = onPlay)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(painterResource(R.drawable.ic_more), "More options for ${track.title}")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Play") }, enabled = playback.connected,
                    onClick = { menu = false; onPlay() })
                DropdownMenuItem(text = { Text("Play next") }, enabled = playback.connected,
                    onClick = { menu = false; onPlayNext() })
                DropdownMenuItem(text = { Text("Add to queue") }, enabled = playback.connected,
                    onClick = { menu = false; onEnqueue() })
                DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { menu = false; onAddToPlaylist() })
                DropdownMenuItem(text = { Text("Go to artist") }, enabled = !loadingCollection,
                    onClick = { menu = false; onArtist() })
                DropdownMenuItem(text = { Text("Go to album") }, enabled = !loadingCollection,
                    onClick = { menu = false; onAlbum() })
                DropdownMenuItem(text = { Text(when {
                    saved -> "Downloaded"
                    downloading -> "Downloading"
                    else -> "Download"
                }) }, enabled = download == null || download.status == DownloadStatus.FAILED,
                    onClick = { menu = false; onDownload() })
            }
        }
    }
}
