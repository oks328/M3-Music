package io.github.oks328.m3music.ui

import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.oks328.m3music.ui.theme.LocalMusicTypeStyles
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy
import androidx.compose.ui.platform.LocalContext
import io.github.oks328.m3music.downloads.DownloadsState
import io.github.oks328.m3music.downloads.DownloadEntry
import io.github.oks328.m3music.downloads.DownloadedAlbum
import io.github.oks328.m3music.downloads.DownloadStatus
import io.github.oks328.m3music.R
import io.github.oks328.m3music.music.SearchState
import io.github.oks328.m3music.music.Track
import io.github.oks328.m3music.playback.PlaybackState
import io.github.oks328.m3music.library.LibraryState
import io.github.oks328.m3music.library.LocalPlaylist
import io.github.oks328.m3music.account.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
fun MusicScreen(
    search: SearchState,
    playback: PlaybackState,
    onSearch: (String) -> Unit,
    onPlay: (Track) -> Unit,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    onRetryPlayback: () -> Unit,
    downloads: DownloadsState = DownloadsState(loading = false),
    onDownload: (Track) -> Unit = {},
    onRemoveDownload: (String) -> Unit = {},
    onWifiOnlyChanged: (Boolean) -> Unit = {},
    library: LibraryState = LibraryState(loading = false),
    onFavorite: (Track, Boolean) -> Unit = { _, _ -> },
    onCreatePlaylist: (String, Track?) -> Unit = { _, _ -> },
    onRenamePlaylist: (String, String) -> Unit = { _, _ -> },
    onDeletePlaylist: (String) -> Unit = {},
    onAddToPlaylist: (String, Track) -> Unit = { _, _ -> },
    onRemovePlaylistSong: (String, String) -> Unit = { _, _ -> },
    onMovePlaylistSong: (String, String, Int) -> Unit = { _, _, _ -> },
    onClearHistory: () -> Unit = {},
    onDismissLibraryError: () -> Unit = {},
    onPlayPlaylist: (List<Track>, Int) -> Unit = { _, _ -> },
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    account: AccountState = AccountState(initializing = false),
    accountActions: AccountActions = AccountActions(),
    onQueueItem: (Int) -> Unit = {},
    onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
    onEnqueue: (Track) -> Unit = {},
    onShuffle: () -> Unit = {},
    onRepeat: () -> Unit = {},
    searchHistory: List<String> = emptyList(),
    onClearSearchHistory: () -> Unit = {},
    loadDiscovery: suspend () -> List<DiscoveryShelf> = { emptyList() },
    loadDiscoveryCollection: suspend (String) -> AccountPage = { AccountPage() },
    findArtist: (suspend (String) -> AccountItem?)? = null,
    findTrackArtists: (suspend (Track) -> List<AccountItem>)? = null,
    findSongCollection: (suspend (Track, AccountItemKind) -> AccountItem?)? = null,
    onDownloadTracks: (List<Track>) -> Unit = { tracks -> tracks.forEach(onDownload) },
    onDownloadAlbum: (DownloadedAlbum, List<Track>) -> Unit = { _, tracks -> onDownloadTracks(tracks) },
    loadCollectionTracks: suspend (String, AccountPage, Boolean) -> List<Track> = { _, page, _ ->
        page.items.mapNotNull { it.track }
    },
) {
    var query by rememberSaveable { mutableStateOf(search.query) }
    var destination by rememberSaveable { mutableStateOf("Home") }
    var librarySection by rememberSaveable { mutableStateOf("YouTube Music") }
    val showDownloads = destination == "Library" && librarySection == "Downloads"
    val showAccountLibrary = destination == "Library" && librarySection == "YouTube Music"
    val showLibrary = destination == "Library"
    val showHome = destination == "Home"
    var searchFilter by rememberSaveable { mutableStateOf("All") }
    var searchCollectionId by rememberSaveable { mutableStateOf<String?>(null) }
    val searchCollection = search.collections.firstOrNull { it.id == searchCollectionId }
    val searchResultsScroll = androidx.compose.foundation.lazy.rememberLazyListState()
    var showAccount by rememberSaveable { mutableStateOf(false) }
    val tabState = rememberSaveableStateHolder()
    var expandedPlayer by rememberSaveable { mutableStateOf(false) }
    val playerMotion = remember { PlayerContainerMotion(expandedPlayer) }
    val playerScope = rememberCoroutineScope()
    val playerSpring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val settlePlayer: (Boolean, Float) -> Unit = { open, velocity ->
        expandedPlayer = open
        playerMotion.settle(playerScope, open, playerSpring, velocity)
    }
    val overlayPresent by remember { derivedStateOf { playerMotion.progress > 0f } }
    val showCompactFace by remember { derivedStateOf { playerMotion.progress < 0.45f } }
    val playerFullyOpen by remember { derivedStateOf {
        playerMotion.progress >= 0.999f && playerMotion.anchor != null
    } }
    BackHandler(!showHome && !showAccount && !expandedPlayer) {
        if (destination == "Library" && librarySection != "YouTube Music") librarySection = "YouTube Music"
        else destination = "Home"
    }
    var addTrack by remember { mutableStateOf<Track?>(null) }
    var youtubeAddTrack by remember { mutableStateOf<Track?>(null) }
    var creating by remember { mutableStateOf(false) }
    var createWithTrack by remember { mutableStateOf<Track?>(null) }
    var renaming by remember { mutableStateOf<LocalPlaylist?>(null) }
    var deleting by remember { mutableStateOf<LocalPlaylist?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var openedCatalog by remember { mutableStateOf<AccountItem?>(null) }
    var returnToPlayer by remember { mutableStateOf(false) }
    var lookingUpSongCollection by remember { mutableStateOf(false) }
    val openSongCollection: (Track, AccountItemKind) -> Unit = { track, kind ->
        if (!lookingUpSongCollection) {
            lookingUpSongCollection = true
            scope.launch {
                val label = if (kind == AccountItemKind.ARTIST) "artist" else "album"
                val error = try {
                    val collection = findSongCollection?.invoke(track, kind)
                    if (collection != null && destination == "Search") {
                        returnToPlayer = false
                        openedCatalog = collection
                        null
                    } else if (collection == null && destination == "Search") {
                        "Couldn't find the $label for ${track.title}."
                    } else null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    "Couldn't open the $label. Try again."
                } finally {
                    lookingUpSongCollection = false
                }
                if (error != null) snackbar.showSnackbar(error)
            }
        }
    }
    var lookingUpArtist by remember { mutableStateOf(false) }
    val playerSnackbar = remember { SnackbarHostState() }
    var artistChoices by remember(playback.track?.id) { mutableStateOf(emptyList<AccountItem>()) }
    val showPlayerArtist: (AccountItem) -> Unit = { artist ->
        artistChoices = emptyList()
        returnToPlayer = true
        openedCatalog = artist
        settlePlayer(false, 0f)
    }
    var preparingCollection by remember { mutableStateOf<String?>(null) }
    val downloadCollection: (AccountItem, AccountPage, Boolean) -> Unit = { item, page, fromLibrary ->
        if (preparingCollection == null) {
            preparingCollection = item.id
            scope.launch {
                val message = try {
                    val tracks = loadCollectionTracks(item.id, page, fromLibrary)
                    if (tracks.isEmpty()) "No downloadable songs in this collection."
                    else {
                        if (item.kind == AccountItemKind.ALBUM) {
                            onDownloadAlbum(DownloadedAlbum(item.id, item.title, page.albumArtist ?: item.albumArtist,
                                page.artworkUrl ?: item.artworkUrl ?: tracks.first().artworkUrl,
                                tracks.map { it.id }.distinct()), tracks)
                        } else onDownloadTracks(tracks)
                        "Download requested. Check Library > Downloads for progress."
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    "Couldn't load all songs. Try downloading the collection again."
                } finally {
                    preparingCollection = null
                }
                snackbar.showSnackbar(message)
            }
        }
    }
    val collectionDownloadButton: @Composable (AccountItem, AccountPage, Boolean) -> Unit = { item, page, fromLibrary ->
        val kind = if (item.kind == AccountItemKind.ALBUM) "album" else "playlist"
        CollectionDownloadButton(kind, page.items.mapNotNull { it.track }, page.continuation != null, downloads,
            preparingCollection != null, albumId = item.id.takeIf { item.kind == AccountItemKind.ALBUM }) {
            downloadCollection(item, page, fromLibrary)
        }
    }
    LaunchedEffect(library.error) {
        library.error?.let { snackbar.showSnackbar(it); onDismissLibraryError() }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        keyboard?.hide()
        searchFilter = "All"
        scope.launch { searchResultsScroll.scrollToItem(0) }
        onSearch(query)
    }
    Box(Modifier.fillMaxSize()) {
    Scaffold(modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!expandedPlayer || playback.track == null || !playerFullyOpen) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center) {
                    Column(Modifier.widthIn(max = 448.dp).fillMaxWidth().padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (playback.track != null) {
                            CompactPlayer(playback, onTogglePlayback, onNext = onNext,
                                onExpand = { settlePlayer(true, 0f) },
                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                    playerMotion.anchor = Rect(coordinates.positionInRoot(), coordinates.size.toSize())
                                }.graphicsLayer { alpha = if (playerMotion.progress > 0f) 0f else 1f }
                                    .playerSwipe(playerMotion, expanded = false, settle = settlePlayer))
                        }
                        MusicBottomNavigation(destination) { label ->
                            openedCatalog = null
                            destination = label
                        }
                    }
                }
            }
        }) { insets ->
        Box(Modifier.fillMaxSize().musicContentFade(destination), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxHeight().padding(
            start = insets.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
            top = insets.calculateTopPadding(),
            end = insets.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
        ).widthIn(max = 720.dp).fillMaxWidth()
            .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (showAccountLibrary && account.collection != null) when (account.collection.kind) {
                    io.github.oks328.m3music.account.AccountItemKind.ALBUM -> "Album"
                    io.github.oks328.m3music.account.AccountItemKind.ARTIST -> "Artist"
                    else -> "Playlist"
                } else if (showHome) stringResource(R.string.app_name) else destination,
                    style = if (showHome) LocalMusicTypeStyles.current.brandTitle else MaterialTheme.typography.titleLarge)
            }
                if (showAccountLibrary && openedCatalog == null && account.collection == null &&
                    account.profile != null && !account.expired
                ) {
                    IconButton(onClick = accountActions.refresh, enabled = !account.loading && !account.mutating) {
                        Icon(painterResource(R.drawable.ic_refresh), "Refresh account library")
                    }
                }
                FilledTonalIconButton(onClick = { showAccount = true }, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.ic_account), "Account")
                }
            }
            if (openedCatalog == null && showLibrary && (!showAccountLibrary || account.collection == null)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("YouTube Music", "Downloads", "On device").forEach { section ->
                        FilterChip(selected = librarySection == section, onClick = { librarySection = section },
                            label = { Text(section) })
                    }
                }
            }
            if (showDownloads && openedCatalog == null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Download on Wi-Fi only", modifier = Modifier.weight(1f))
                    Switch(checked = downloads.wifiOnly, onCheckedChange = onWifiOnlyChanged,
                        modifier = Modifier.semantics { contentDescription = "Download on Wi-Fi only" })
                }
            } else if (!showHome && !showLibrary && searchCollection == null && openedCatalog == null) OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Songs, albums, or artists") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                trailingIcon = {
                    Row {
                        if (query.isNotEmpty()) IconButton(onClick = { query = ""; onSearch("") }) {
                            Icon(painterResource(R.drawable.ic_clear), "Clear search text")
                        }
                        IconButton(onClick = { submit() }, enabled = query.isNotBlank()) {
                            Icon(painterResource(R.drawable.ic_search), "Submit search")
                        }
                    }
                },
            )
            if (openedCatalog == null && destination == "Search" && searchCollection == null && query.isNotBlank() &&
                search.hasSearched && search.error == null
            ) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Songs", "Albums", "Artists").forEach { filter ->
                        FilterChip(selected = searchFilter == filter, onClick = {
                            searchFilter = filter
                            scope.launch { searchResultsScroll.scrollToItem(0) }
                        }, label = { Text(filter) })
                    }
                }
            }
            tabState.SaveableStateProvider(if (showLibrary) librarySection else destination) {
            val artist = openedCatalog
            if (artist != null) {
                CatalogCollectionPanel(artist, loadDiscoveryCollection, playback, onPlayPlaylist,
                    insets.calculateBottomPadding(),
                    collectionDownload = { item, page -> collectionDownloadButton(item, page, false) },
                    onBack = {
                        openedCatalog = null
                        settlePlayer(returnToPlayer && playback.track != null, 0f)
                        returnToPlayer = false
                    }, modifier = Modifier.weight(1f).fillMaxWidth())
            } else if (showHome) {
                DiscoveryPanel(account, loadDiscovery, loadDiscoveryCollection, playback, onPlayPlaylist,
                    insets.calculateBottomPadding(),
                    collectionDownload = { item, page -> collectionDownloadButton(item, page, false) },
                    modifier = Modifier.weight(1f).fillMaxWidth())
            } else if (showAccountLibrary) {
                AccountPanel(account, accountActions, playbackConnected = playback.connected,
                    onPlaySongs = onPlayPlaylist, onDownload = onDownload,
                    collectionDownload = { item, page -> collectionDownloadButton(item, page, true) },
                    candidates = (search.tracks + library.favorites + downloads.items.map { it.track } + library.history).distinctBy { it.id },
                    playingTrackId = playback.track?.id,
                    bottomClearance = insets.calculateBottomPadding(),
                    modifier = Modifier.weight(1f).fillMaxWidth())
            } else if (showLibrary && !showDownloads) {
                LibraryPanel(library, playback, downloads, onPlay, onPlayPlaylist, onFavorite,
                    onAdd = { addTrack = it }, onCreate = { createWithTrack = null; creating = true },
                    onRename = { renaming = it }, onDelete = { deleting = it },
                    onRemoveSong = onRemovePlaylistSong, onMoveSong = onMovePlaylistSong,
                    onClearHistory = onClearHistory, onDownloadTracks = onDownloadTracks, bottomClearance = insets.calculateBottomPadding(), modifier = Modifier.weight(1f).fillMaxWidth())
            } else if (!showLibrary && searchCollection != null) {
                CatalogCollectionPanel(searchCollection, loadDiscoveryCollection, playback, onPlayPlaylist,
                    insets.calculateBottomPadding(),
                    collectionDownload = { item, page -> collectionDownloadButton(item, page, false) },
                    onBack = { searchCollectionId = null }, modifier = Modifier.weight(1f).fillMaxWidth())
            } else if (showDownloads) {
                DownloadsPanel(downloads, playback, onPlayPlaylist, onDownload, onRemoveDownload,
                    insets.calculateBottomPadding(),
                    Modifier.weight(1f).fillMaxWidth())
            } else LazyColumn(Modifier.weight(1f).fillMaxWidth()
                .consumeWindowInsets(PaddingValues(bottom = insets.calculateBottomPadding())),
                state = searchResultsScroll,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp + insets.calculateBottomPadding())) {
                when {
                    query.isBlank() || !search.hasSearched && !search.loading -> {
                        if (searchHistory.isNotEmpty()) {
                            item {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Recent searches", style = MaterialTheme.typography.titleMedium)
                                    TextButton(onClick = onClearSearchHistory) { Text("Clear history") }
                                }
                            }
                            items(searchHistory, key = { it }) { term ->
                                Surface(onClick = {
                                    query = term
                                    submit()
                                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Icon(painterResource(R.drawable.ic_search), null)
                                        Text(term)
                                    }
                                }
                            }
                        } else item {
                            Text("Search for songs, albums, and artists on YouTube Music.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    search.loading -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            LoadingIndicator()
                            Text("Searching YouTube Music…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    search.error != null -> item {
                        Text(search.error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { onSearch(search.query) }) { Text("Try again") }
                    }
                    else -> {
                        val songs = if (searchFilter in listOf("All", "Songs")) search.tracks else emptyList()
                        val groups = listOf("Albums" to AccountItemKind.ALBUM, "Artists" to AccountItemKind.ARTIST)
                            .filter { (label, _) -> searchFilter == "All" || searchFilter == label }
                            .map { (label, kind) -> label to search.collections.filter { it.kind == kind } }
                        search.partialError?.let { message -> item {
                            Text(message, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { onSearch(search.query) }) { Text("Try again") }
                        } }
                        if (songs.isEmpty() && groups.all { it.second.isEmpty() } && search.partialError == null) {
                            item { Text("No results found. Try a different search.") }
                        }
                        groups.filter { it.second.isNotEmpty() }.forEach { (label, results) ->
                            if (searchFilter == "All") item {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(label, style = MaterialTheme.typography.titleMedium)
                                    if (results.size > 3) TextButton(onClick = {
                                        searchFilter = label
                                        scope.launch { searchResultsScroll.scrollToItem(0) }
                                    }) { Text("See all") }
                                }
                            }
                            items(if (searchFilter == "All") results.take(3) else results, key = { "${it.kind}:${it.id}" }) { item ->
                                CatalogResultRow(item) { searchCollectionId = item.id }
                            }
                        }
                        if (songs.isNotEmpty() && searchFilter == "All") {
                            item { Text("Songs", style = MaterialTheme.typography.titleMedium) }
                        }
                        items(songs, key = { it.id }) { track ->
                            val entry = downloads.items.firstOrNull { it.track.id == track.id }
                            SearchSongResult(track, playback, entry, lookingUpSongCollection,
                                onPlay = { onPlay(track) },
                                onEnqueue = {
                                    onEnqueue(track)
                                    scope.launch { snackbar.showSnackbar("Added to queue") }
                                },
                                onAddToPlaylist = { youtubeAddTrack = track },
                                onArtist = { openSongCollection(track, AccountItemKind.ARTIST) },
                                onAlbum = { openSongCollection(track, AccountItemKind.ALBUM) },
                                onDownload = { onDownload(track) })
                        }
                    }
                }
                item(key = "search-ime-clearance") {
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.ime))
                }
            }
            }
            if (destination == "Search" && downloads.error != null) {
                Text(downloads.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (!playback.connected) {
                Text(playback.error ?: "Starting the player…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
    }
    if (showAccount) AccountSettingsDialog(account, accountActions, { showAccount = false })
    val currentTrack = playback.track
    if (currentTrack != null) {
        BackHandler(expandedPlayer) { settlePlayer(false, 0f) }
        if (expandedPlayer || overlayPresent) {
            val colors = MaterialTheme.colorScheme
            Box(Modifier.fillMaxSize().playerContainer(playerMotion, colors.secondaryContainer, colors.surfaceContainerLow)) {
                Box(Modifier.fillMaxSize().graphicsLayer {
                    val p = playerMotion.progress
                    alpha = ((p - 0.24f) / 0.62f).coerceIn(0f, 1f)
                    translationY = (playerMotion.anchor?.top ?: size.height) * (1f - p) * 0.58f
                }.statusBarsPadding()) {
                    CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
                        ExpressivePlayer(playback, library.favorites.any { it.id == currentTrack.id }, onTogglePlayback,
                            onSeek, onRetryPlayback, onPrevious, onNext, onQueueItem,
                            onMoveQueueItem = onMoveQueueItem,
                            onFavorite = { onFavorite(currentTrack, library.favorites.none { it.id == currentTrack.id }) },
                            onAddToPlaylist = { youtubeAddTrack = currentTrack },
                            onDownload = { onDownload(currentTrack) },
                            downloadEntry = downloads.items.firstOrNull { it.track.id == currentTrack.id },
                            onShuffle = onShuffle, onRepeat = onRepeat, onClose = { settlePlayer(false, 0f) },
                            swipeModifier = { enabled ->
                                Modifier.playerSwipe(playerMotion, expandedPlayer, enabled, settlePlayer)
                            },
                            onArtist = if (findTrackArtists == null && findArtist == null || lookingUpArtist) null else { name ->
                                lookingUpArtist = true
                                scope.launch {
                                    val artists = try {
                                        findTrackArtists?.invoke(currentTrack) ?: listOfNotNull(findArtist?.invoke(name))
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        emptyList()
                                    } finally {
                                        lookingUpArtist = false
                                    }
                                    if (expandedPlayer) when (artists.size) {
                                        0 -> playerSnackbar.showSnackbar("Couldn't open an artist page for $name.")
                                        1 -> showPlayerArtist(artists.single())
                                        else -> artistChoices = artists
                                    }
                                }
                            })
                    }
                    SnackbarHost(playerSnackbar, Modifier.align(Alignment.BottomCenter)
                        .navigationBarsPadding().padding(bottom = 64.dp))
                }
                val mini = playerMotion.anchor
                if (showCompactFace && mini != null) {
                    val width = with(LocalDensity.current) { mini.width.toDp() }
                    Box(Modifier.width(width).graphicsLayer {
                        val p = playerMotion.progress
                        translationX = mini.left * (1f - p)
                        translationY = mini.top * (1f - p)
                        alpha = (1f - p / 0.38f).coerceIn(0f, 1f)
                    }.clearAndSetSemantics {}) {
                        CompactPlayer(playback, onToggle = {}, onNext = {}, onExpand = {},
                            modifier = Modifier.playerSwipe(playerMotion, expandedPlayer, settle = settlePlayer),
                            inTransform = true)
                    }
                }
            }
        }
    }
    }
    if (expandedPlayer && artistChoices.isNotEmpty()) {
        AlertDialog(onDismissRequest = { artistChoices = emptyList() }, title = { Text("Go to artist") },
            text = {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(artistChoices, key = { it.id }) { artist ->
                        TextButton(onClick = { showPlayerArtist(artist) }, modifier = Modifier.fillMaxWidth()) {
                            Text(artist.title)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { artistChoices = emptyList() }) { Text("Cancel") } })
    }
    youtubeAddTrack?.let { track ->
        YouTubePlaylistPicker(track, accountActions, onDismiss = { youtubeAddTrack = null })
    }
    addTrack?.let { track ->
        AlertDialog(onDismissRequest = { addTrack = null }, title = { Text("Add to playlist") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium)
                    library.playlists.forEach { playlist ->
                        val contains = playlist.tracks.any { it.id == track.id }
                        TextButton(enabled = !contains, onClick = { onAddToPlaylist(playlist.id, track); addTrack = null }) {
                            Text(if (contains) "${playlist.name} · Already added" else playlist.name)
                        }
                    }
                    TextButton(onClick = { createWithTrack = track; addTrack = null; creating = true }) { Text("New playlist") }
                }
            }, confirmButton = { TextButton(onClick = { addTrack = null }) { Text("Done") } })
    }
    if (creating || renaming != null) {
        val editing = renaming
        PlaylistNameDialog(editing?.name.orEmpty(), editing != null,
            onDismiss = { creating = false; renaming = null; createWithTrack = null },
            onConfirm = { name ->
                if (editing == null) onCreatePlaylist(name, createWithTrack) else onRenamePlaylist(editing.id, name)
                creating = false; renaming = null; createWithTrack = null
            })
    }
    deleting?.let { playlist ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${playlist.name}?") },
            text = { Text("Only this playlist will be removed. Your favorites and downloaded songs will stay.") },
            confirmButton = { TextButton(onClick = { onDeletePlaylist(playlist.id); deleting = null }) { Text("Delete playlist") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep playlist") } })
    }
}

@Composable
internal fun SongRow(track: Track, selected: Boolean, enabled: Boolean, offline: Boolean = false, onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color? = null, compact: Boolean = false) {
    Surface(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "Play ${track.title} by ${track.artist}"
        },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else containerColor ?: MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(if (compact) 8.dp else 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Artwork(track.artworkUrl, Modifier.size(if (compact) 44.dp else 56.dp), offline)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (track.durationSeconds > 0) {
                Text(formatTime(track.durationSeconds * 1000), style = LocalMusicTypeStyles.current.playbackTime)
            }
        }
    }
}

@Composable
private fun PlaylistNameDialog(initialName: String, editing: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (editing) "Rename playlist" else "New playlist") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Playlist name") },
                singleLine = true, isError = name.trim().length > 80,
                supportingText = { Text("${name.trim().length}/80") })
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim()) },
            enabled = name.isNotBlank() && name.trim().length <= 80) { Text(if (editing) "Save" else "Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
internal fun Artwork(url: String?, modifier: Modifier, offline: Boolean = false, large: Boolean = false, cornerRadius: androidx.compose.ui.unit.Dp = 12.dp) {
    var useOriginal by remember(url, large, offline) { mutableStateOf(false) }
    val requested = if (large && !offline && !useOriginal) largeArtworkUrl(url) else url
    Surface(modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
        color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        if (url != null) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(requested)
            .networkCachePolicy(if (offline) CachePolicy.DISABLED else CachePolicy.ENABLED).build(), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            error = painterResource(R.drawable.ic_music_note), onError = { if (requested != url) useOriginal = true })
        else Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_music_note), null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun DownloadActions(entry: DownloadEntry, state: DownloadsState, onDownload: () -> Unit, onRemove: () -> Unit) {
    Column {
        if (entry.status == DownloadStatus.DOWNLOADING) {
            entry.percent?.let { percent -> LinearWavyProgressIndicator(progress = { (percent / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val label = when (entry.status) {
                DownloadStatus.COMPLETED -> "Saved · ${formatBytes(entry.bytes)}"
                DownloadStatus.PREPARING -> "Preparing download…"
                DownloadStatus.DOWNLOADING -> "Downloading ${entry.percent?.let { "$it%" }.orEmpty()} · ${formatBytes(entry.bytes)}"
                DownloadStatus.QUEUED -> if (state.waitingForNetwork)
                    if (state.wifiOnly) "Waiting for Wi-Fi" else "Waiting for connection" else "Queued"
                DownloadStatus.FAILED -> "Download failed. Check connection or storage."
                DownloadStatus.REMOVING -> "Removing…"
                DownloadStatus.PAUSED -> "Download paused"
            }
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = if (entry.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (entry.status == DownloadStatus.FAILED) TextButton(onClick = onDownload) { Text("Retry") }
            TextButton(onClick = onRemove, enabled = entry.status != DownloadStatus.REMOVING,
                modifier = Modifier.semantics { contentDescription = "Remove download of ${entry.track.title}" }) {
                Text(if (entry.status in listOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED)) "Remove" else "Cancel")
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = "%.1f MB".format(bytes / 1_000_000.0)

private fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
