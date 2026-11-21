# Search and playback verification

## Everyday-use polish — November 21, 2026

All 45 JVM tests passed, including recognized-host artwork resizing and unchanged
signed/unrecognized image URLs. Debug and test APKs built successfully; lint
passed with warnings. The connected Xiaomi phone passed 24 checks: the 23
targeted interface tests and a real saved-song service integration test. The
integration test verified queue metadata, selection of a queue entry, playback
from local storage, and seeking; it restores the previous queue, position,
play-when-ready state, and volume afterward.

Light-mode layout at 130% text and a long song/artist name was tested through a
Compose density override, without changing the phone's display settings. Seek
semantics dispatched the expected position, explicit collapse returned to the
compact player, and a screenshot review exposed transport controls below the
fold. Transport controls now stay fixed; all nine MusicScreen checks passed
again after this change. The scrolling content is explicitly clipped below the
fixed player header. Actual remote artwork resolution remains source-dependent;
requests fall back to the supplied image, and offline artwork keeps cache-only
behavior.

The final clipping adjustment passed the large-text regression again. Search,
local Library, and Downloads were also visually inspected on the installed app
in the phone's current light/Monet appearance. No additional display-setting
changes were made.

## Expressive visual system and player — November 21, 2026

Debug app and instrumentation builds, all 43 JVM tests, and lint passed. The
installed build passed 22 targeted phone UI checks across Home/account,
collections, local Library, Search, Downloads, expanded-player seeking, and queue
selection callbacks. The new liked-song shortcut test verifies that playback
waits for loading and dispatches only once. An initial device pass caught a
duplicate player heading; the layout was corrected and the final suite passed.

Actual account collection and expanded Now playing layouts were visually
inspected on the connected Xiaomi phone in its existing dark/Monet appearance.
No display settings were changed during this pass. Light mode and 130% text were
not visually rechecked in this milestone. Native queue navigation is wired to
MediaController; queue selection dispatch is covered by the UI test, while a
real service queue jump was not newly exercised in this pass. Login, local
library, and downloads were preserved; no remote account edits were performed.

## Account collection details — November 21, 2026

The artwork-led account collection redesign passed nine targeted phone tests:
five existing account-screen checks and four collection checks. The new coverage
verifies ordered Play, shuffle preserving every loaded track, partial-page
labels, disabled empty playback, read-only album action restrictions, and
confirmed removal using the correct playlist entry identifier. Remote mutations
were represented by callbacks in synthetic test data; no real songs or playlists
were modified. Real Liked Music detail loaded 58 songs and artwork on the Xiaomi
phone. All 43 JVM tests and debug/test builds passed. Final lint passed with
warnings. The four collection tests passed again after tightening the detail
header. Visual inspection covered the real collection in dark mode. A subsequent
light/130% text capture was interrupted by the phone switching to another app,
so that collection check remains unverified. The unrelated capture was removed;
the phone's original dark mode and 100% text size were restored.

## Account-centered interface — November 21, 2026

Home is now the default destination, including for a restored account. Search is
explicit navigation. The connected Xiaomi phone passed all 16 targeted Compose
tests across MusicScreenTest, AccountScreenTest, and LibraryScreenTest. Coverage
includes initial account collection visibility, liked-songs dispatch, collection
opening, search, downloads, local playlist actions, disconnect confirmation, and
expanding the compact player to reach seeking controls. The first device pass
identified partially clipped sheet controls; the sheet now opens fully expanded
with a bounded scroll area, and the regression check passed.

Real account playlists loaded on Home after a fresh process launch. Visual checks
covered dark mode at normal text size and light mode at 130% text size. The
original dark mode and 100% text size were restored. No real account mutations
were made. Existing downloads, local library, and encrypted login were preserved.
All 43 JVM tests and the debug/test builds passed. Final lint completed
successfully; existing dependency and deprecated-API warnings remain.

The first milestone is anonymous YouTube Music song search and single-track
streaming. Downloads, account sync, browsing and persistent queues come later.

## Strategy

Gate 0 is off: search and playback have user-visible behavior. Gate 1 is on for
search state transitions and stream selection. Gate 2 is on for the extractor's
HTTP adapter and live YouTube integration. Gate 3 is on for the search and play
path. Gate 4 is off: there is no independently deployed API we own. Gate 5 is off:
there is no release pipeline yet. Gate 6 is off: a few representative partitions
cover the state machine; property-based generators add little value here.

```yaml
test_strategy:
  artifact: Android search and playback milestone
  rationale: Verify asynchronous search and the network boundary before relying on live playback.
  criticality: MEDIUM-HIGH
  selected_types:
    - rationale: Search must handle blank input, cancellation, errors, and retry.
      type: unit
      size: small
      framework: JUnit4 and kotlinx-coroutines-test
      dependencies: [in-memory catalog functions]
      gate: Gate 1
    - rationale: HTTP requests must preserve extractor headers, bodies, redirects, and status codes.
      type: integration
      size: medium
      framework: JUnit4
      dependencies: [OkHttp MockWebServer]
      gate: Gate 2
    - rationale: The primary screen must submit searches and make matching songs playable.
      type: component
      size: medium
      framework: Compose UI tests
      dependencies: [Android device, in-memory UI state]
      gate: Gate 3
    - rationale: Upstream search and stream extraction can change independently of our code.
      type: integration
      size: large
      framework: AndroidJUnit4
      dependencies: [Android device, live YouTube network]
      gate: Gate 2
  rejected_types:
    - reason: No owned API with independently deployed consumers.
      type: contract
    - reason: No deployment pipeline.
      type: smoke
    - reason: Finite state transitions and representative stream candidates give better value.
      type: property-based
  deliberately_skipped:
    - why: Network extraction is nondeterministic and may be region or IP restricted.
      what: Live network tests in the default unit suite; run explicitly on a device instead.
    - why: Visual styling is preliminary and will change during the M3 Expressive design phase.
      what: Screenshot golden tests.
```

## Test cases

- AC-1: Blank searches make no request; trimmed queries publish results; zero matches show an empty state.
- AC-2: A failed request ends loading and allows retry; a superseded request cannot overwrite newer results.
- AC-3: Only usable HTTPS progressive audio is selected; no candidates gives an actionable error.
- AC-4: HTTP POST bodies, repeated headers, redirects, and status codes survive the adapter.
- AC-5: Search submission and tapping a result invoke the correct actions; loading and errors are visible.
- AC-6: A live search yields song metadata; a selected result resolves to audio and reaches Media3 ready state.

## Commands

Use Android Studio's bundled JDK. Run `gradlew.bat :app:testDebugUnitTest
:app:assembleDebug :app:lintDebug` for local checks. Run
`gradlew.bat :app:connectedDebugAndroidTest` for deterministic device tests.

Live checks are opt-in: add
`-Pandroid.testInstrumentationRunnerArguments.runLiveYouTube=true` and
`-Pandroid.testInstrumentationRunnerArguments.class=io.github.oks328.m3music.music.YouTubeLiveTest`
to the connected test command. These checks can fail when YouTube changes or
blocks the current network. Never log signed stream URLs, cookies or credentials.

## Manual phone checks

Search for a song, select a result, verify sound, pause/resume, seek, then lock
the phone and use the media notification. Rotate the phone and confirm playback
does not restart. Search again while a track plays. Turn off the network and
confirm a failed search can be retried after reconnecting.

## Download milestone

Downloads use Media3 DownloadService, DownloadManager, a persistent download index,
and a non-evicting cache in app-private storage. Completed downloads are resolved
before any network lookup. Their `offline:` URI selects a cache-only data source
with no HTTP upstream. A missing cache span fails rather than silently streaming.

Additional checks cover metadata serialization (including Unicode, missing artwork,
and damaged metadata), local-first playback resolution, reading the cache after
reopening it, and failing closed on missing offline data. Screen tests cover
starting a download, opening saved songs, and changing the Wi-Fi-only preference.

The optional `LiveDownloadTest` downloads one real song and verifies that playback
advances with a network factory that throws if used. Run with `runLiveDownloads=true`.
After stopping the app process, run `savedSongPlaysAfterProcessRestart` with
`verifyPersistedDownload=true` to check persistence without another YouTube lookup.

When testing on the user's phone, install both APKs with `adb install -r` and run
instrumentation directly to preserve the app's downloaded songs. Gradle's connected
test task can uninstall its target afterward. Do not clear app data or uninstall
the app to refresh a development build.

For manual airplane-mode verification, download a song until it says Saved, open
Downloads, enable airplane mode and switch Wi-Fi off, then select the saved song.
Seek into its middle and confirm audible playback. Restore connectivity afterward.

Audio files and metadata are private to the app and are removed on uninstall.
Artwork can use Coil's existing disk cache; offline playback never fetches artwork.
Download failures can be retried with a fresh stream URL. Wi-Fi-only mode uses
Android's unmetered-network requirement; metered Wi-Fi waits too.

## Latest verification

### Account integration milestone — device checks and live sign-in passed

The user resumed verification on November 21, 2026. The account build was
previously installed with `adb install -r`, preserving existing app data. The
updated test package was installed without clearing data. All five account device
tests passed on the connected Xiaomi phone: two session-vault checks and three
Compose account-screen checks.

Before that request, local checks passed for signing, trusted HTTPS login hosts,
response fixtures, account verification before persistence, expired sessions,
retaining a session while offline, and ignoring late responses after disconnect.
MockWebServer checks passed for authenticated request context, continuation
payloads, redirect credential isolation, unauthorized/malformed responses, private
playlist creation, and playlist-removal identifiers. Build and lint passed.

`AccountSessionVaultTest` used synthetic sessions and unique test keys only.
The user confirmed live sign-in and browsing playlists, liked songs, and followed
artists. After a reported logout, the user confirmed the Account tab still showed
their profile. A force-stop and fresh launch also restored the signed-in Account
controls: Disconnect and category chips were present; Connect, expiry, and error
messages were absent. The app opens on Search, so that screen alone does not
indicate a logout. Live saved-album browsing and account mutations remain unverified.
The first live attempt exposed a blocked regional redirect to
`accounts.google.com.ph` after 2-step verification. That exact HTTPS host was
added to the login allowlist; a regression test covers it and rejects lookalike
hosts, user-info tricks, HTTP, and nonstandard ports. JVM tests and the debug
build passed, and the updated app was installed for a live retry.
Account write formats are locally tested; no real Google
playlist or like mutations were performed automatically. Never print session
cookies, signed account headers, or full authenticated response bodies in logs.

The saved session uses AES-GCM with an Android Keystore key and an atomic file in
noBackupFilesDir. Account pages are memory-only and are cleared on disconnect.
The login form is Google's actual web page; there is no JavaScript bridge or
credential-form inspection. Only page configuration is read on the exact Music
origin after the user taps Connect account. Temporary WebView data is cleared on
exit and excluded from backup.

### Local library milestone

- 20 JVM tests passed, including trimmed/blank/79/80/81-character playlist names.
- 21 deterministic device tests passed. New tests cover SQLite persistence after
  reopening, duplicate-song prevention, reordering, rename/delete safety, history
  deduplication, invalid-name rollback, library screen actions, lazy source
  resolution on seek, and automatic advancement through two real decoded WAV tracks.
- The database tests use uniquely named isolated databases and remove only those
  test databases. They do not touch the user's real library or downloads.
- Build and lint passed. Local definitions, favorites, and history use a separate
  `music_library.db`; downloaded audio remains in the Media3 store.
- The real-service verification also passed on the user's existing saved song:
  local playback advanced, Next selected the second queue entry, and the playback
  service recorded a single recent-history entry. No downloads were removed.
- The user independently confirmed audible airplane-mode playback in the prior
  milestone. The updated Library screen was opened for a physical-device layout check.

The optional `LibraryPlaybackIntegrationTest` (`verifySavedLibrary=true`) uses an
already downloaded song through the real app ViewModel and playback service,
silences the test player's volume temporarily, exercises Next, and checks that
playback records a deduplicated history entry. It does not download or remove songs.
Its temporary queue repeats the saved song to avoid requiring a second download;
user-created playlists themselves do not allow duplicate songs.

### Download milestone

On the connected Xiaomi 2311DRK48G (Android 16):

- Debug APK, test APK, and all 16 JVM tests passed. Lint passed with no errors
  after adding Media3's required API opt-ins.
- All six screen tests and both cache-only storage tests passed, along with the
  app-context test. The live streaming test also passed.
- The initial live-download test was interrupted during use of the phone and
  timed out. The user subsequently downloaded a song through the app.
- Force-stopped the app and ran the saved-download test in a fresh process.
  The saved song was found in the persistent index, resolved without a YouTube
  lookup, decoded from the local cache with a forbidden HTTP upstream, and its
  playback position advanced. This test passed.
- Tests did not clear app data or remove the user's saved song. Audible playback
  with the phone itself in airplane mode remains a manual confirmation.

### Streaming milestone

On the connected Xiaomi 2311DRK48G running Android 16:

- 11 JVM tests passed (including the generated starter test).
- 5 device tests passed, with no skips: app context, three search-screen tests,
  and live YouTube Music search plus real audio preparation to Media3 ready state.
- Debug build and lint passed; lint reported 0 errors and 32 warnings, primarily
  dependency update suggestions, version-catalog suggestions, and unused starter resources.
- Installed and launched the usable build; visually checked its initial dark-theme screen.

The live audio test buffers without playing sound. Audible playback, lock-screen
interactions, Bluetooth, long screen-off playback, light-theme visual review, and
larger font settings still need manual verification. No claim of full release
readiness is made by these milestone checks.
