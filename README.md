# M3 Music

A focused Android music player built with Material 3 Expressive and an unofficial
YouTube Music integration. This project is independent of Google and YouTube.

## Current milestone

- Anonymous YouTube Music song search, with artwork and artist metadata.
- Single-song streaming, play/pause, seeking, and retry.
- Media3 background service with audio focus, headphone-disconnect handling,
  media notification, and lock-screen controls.
- Dynamic color on Android 12+, system light/dark themes, expressive Material theme.
- Phone/Monet colors remain the default. The profile dialog offers a persistent
  wallpaper-color toggle; turning it off uses the ivory/plum, lime, violet, and
  peach app palette.
- Account-centered Home with artwork-led collections, liked-song access, recent
  listening, and Material 3 Expressive bottom navigation.
- A compact persistent player that opens an expanded Now playing sheet.
- Now playing keeps transport controls visible with long titles, supports explicit
  collapse, and returns from Queue with system Back. Large online cover images
  request higher resolution when supported, with original-image fallback.
- Expressive title weights, Material shape-morphing playback control, a wavy
  accessible seek track, and a live service queue with selectable entries.
- Account playlist and album details with cover art, Play/Shuffle, metadata, and
  per-song action menus. Partial collections clearly identify loaded-song playback.
- Background song downloads with persistent progress, retry, cancel, and removal.
- A Downloads view, an unmetered-network preference, and cache-only offline playback.
- A local Library with favorites, recent listening history, and persistent playlists.
- Search and Downloads share compact song actions; local playlists have artwork,
  scrollable headers, and Play/Shuffle controls for larger-text layouts.
- Playlist creation, renaming, deletion, song removal and reordering, plus sequential playback and Previous/Next controls.
- Optional Google web sign-in, a Keystore-protected session, disconnect, and a separate YouTube Music account library.

Search currently displays the first page of song results. The account integration
supports live sign-in and browsing playlists, liked songs, and followed artists,
confirmed on the user's phone. All five account device tests passed, and the
signed-in account was restored after a force-stop and fresh launch. Saved albums
and remote account edits still need live verification.
The first account-centered interface is implemented; further visual refinement
and richer discovery remain planned.
Upstream changes or network restrictions
can break search or stream extraction independently of this app.

## Build and run

Open this directory in Android Studio and let Gradle sync. Use the bundled JDK
and install the SDK requested by the project. Android 8.0 (API 26) is the minimum.

Select the `app` configuration, choose an emulator or USB-debugging phone, and
press Run. Home opens your account collection. Use Search to find a song, tap a
result, and use the compact player above navigation. Tap it to open artwork,
seeking, and queue controls.

To save music, tap Download beneath a search result, then open Downloads. Wait
until it says Saved before disconnecting. Completed songs play from app-private
storage, with no network fallback. Remove deletes the local copy. Wi-Fi-only
downloads wait for an unmetered connection; turning this setting off allows mobile
data. Downloaded audio is removed when the app is uninstalled.

Use Favorite or Add to playlist beneath a song in search or Downloads. Open Library
to create a playlist, view favorites, or see the last 200 distinct songs that
actually started playing. Inside a playlist, More offers song removal and Move
up/down; Play playlist starts the stored order. Playlist edits do not delete
downloaded audio. A playlist can contain both streamed and downloaded songs; only
downloaded songs work offline. Favorites and playlists are local, with no Google
account synchronization yet. Playlist definitions persist across restarts; the
current playback queue is kept by the running service and is not restored after
process termination.

## Optional YouTube Music account

On Home, tap Connect Google account (also available behind the profile icon).
Complete Google's own web form and
tap Connect account after returning to music.youtube.com. The app verifies the
account before saving the session. Google may restrict embedded sign-in; a live
connection is not guaranteed until checked on the device.

Home has separate sections for YouTube playlists, liked songs, saved
albums, and followed artists, with explicit Load more pagination. Supported write
actions are labeled On YouTube: create a private playlist, rename or delete an
editable playlist, add/remove songs, and like/unlike a song. These actions do not
merge local playlists with YouTube playlists. Only loaded songs enter playback;
load additional pages first for a longer account queue.

This uses an unofficial web session, not Google OAuth. The saved session is
encrypted with an Android Keystore key in no-backup app storage. Temporary WebView
cookies and web storage are cleared when the sign-in activity closes; WebView
data is excluded from backup. Disconnect clears the saved session and account
pages while preserving local favorites, playlists, and downloads.

Playback still uses the existing public-catalog integration. Private uploads,
restricted tracks, and account-specific playback entitlements are not supported
by this account-library milestone.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

See [verification instructions](docs/TESTING.md) for device and live-network tests.
Local SDK paths, Gradle caches, and build outputs are excluded from Git.

## Architecture

`MusicScreen` renders state and sends actions to `MusicViewModel`. `SearchController`
handles cancellation and search state. `YouTubeMusicRepository` owns NewPipeExtractor
and runs blocking extraction on an IO dispatcher. Stream URLs are resolved for each
selection and kept in memory. `PlaybackService` owns ExoPlayer and MediaSession;
the activity connects through MediaController and does not own the audio engine.
`LocalLibrary` serializes SQLite transactions and publishes committed snapshots.
PlaybackService records history when audio starts, including while the UI is in
the background. Playlist entries resolve through TrackDataSourceFactory when
loaded, keeping expiring stream URLs out of the persistent library.

## License and acknowledgments

GPL-3.0-or-later; see [LICENSE](LICENSE) and [third-party notices](THIRD_PARTY_NOTICES.md).
Square informed the integration architecture; this milestone does not copy its
application code or include its Spotify backend.
