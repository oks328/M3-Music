# M3 MUSIC

M3 MUSIC is a focused Android music player with a Material 3 Expressive interface
and an unofficial YouTube Music integration. It is independent of Google and
YouTube.

## Version 1.0 (testing)

This is the first QA version. It requires Android 8.0 (API 26) or later and a
Google account. Sign-in opens Google's web page in Android WebView. Search,
streaming, and downloading require an internet connection. Previously downloaded
songs can play offline with a saved session.

- **Home** shows YouTube Music recommendations and featured collections.
- **Search** finds songs, albums, and artists and keeps recent searches.
- **Library** brings together YouTube Music playlists, liked songs, saved albums,
  and followed artists. Downloads and on-device playlists have their own sections.
  You can create YouTube Music playlists and add tracks from Search or Now Playing.
- **Downloads** supports songs, albums, and playlists. Completed downloads play
  from app-private storage and are removed when the app is uninstalled.
- **Now Playing** expands from the mini-player with a swipe or tap. It includes
  seeking, shuffle, repeat, and a reorderable queue. Media3 handles background
  playback, audio focus, and lock-screen controls.
- **Appearance** uses Google Sans Flex, an adaptive launcher icon, and Material 3
  Expressive motion. Wallpaper colors are available on Android 12 and later.
  Older devices use the built-in light and dark palettes.

### Known issues

- Google may interrupt embedded sign-in with a device verification prompt. This
  build permits the Google verification host reported by QA. The complete
  2-Step Verification flow still needs a retry on the affected device.
- Library's saved Albums filter can show an unsupported-page error. YouTube Music
  changes can also affect account access, search, or streaming.
- Background playback and offline downloads have been tested on a Xiaomi phone.
  Low-end performance and broader Android device compatibility still need testing.

## Build and install

Open this repository in Android Studio, install the SDK requested by the project,
and let Gradle sync. Run the `app` configuration on an emulator or a connected
Android phone. On Windows, the Android Studio bundled JDK can also build the QA
APK and run the local checks.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The installable QA build is at `app/build/outputs/apk/debug/app-debug.apk`. This
is a debug-signed APK. See the [verification instructions](docs/TESTING.md) for
device and live-network checks.

## Implementation

Jetpack Compose renders app state from `MusicViewModel`. NewPipeExtractor
resolves public-catalog streams, while Media3's playback service owns the player
and queue. The account library uses an unofficial web session stored with Android
Keystore protection. Disconnecting removes the saved session and account pages.
Local playlists, history, and downloads stay on the device. Private uploads and
account-specific playback entitlements are outside this testing build.

## References and acknowledgments

- [Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)
  guided the shapes, typography, and expressive transitions.
- [Google Sans Flex](https://fonts.google.com/specimen/Google+Sans+Flex) is the
  variable typeface used throughout the app.
- [Square](https://github.com/Lelonio/Square) informed the approach to YouTube Music
  integration and offline listening.
- [PixelPlayer](https://github.com/PixelPlayerHQ/PixelPlayer) was a design reference
  for the animated seek bar and Now Playing controls.
- [YouTube Music](https://music.youtube.com/) informed the organization of Home,
  Search, Library, and collection details.

M3 MUSIC is licensed under GPL-3.0-or-later. See [LICENSE](LICENSE) and
[third-party notices](THIRD_PARTY_NOTICES.md).
