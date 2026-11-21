# M3 Music

<!-- impeccable:product-schema 1 -->

## Platform

android

## Product Purpose

A focused Android music player using YouTube Music as its source and Google's
Material 3 Expressive design language. The user chose a new app rather than a
Square fork.

## Operating Context

Developed in Android Studio, tested on the user's connected Xiaomi Android phone.

## Capabilities and Constraints

The starting screen prioritizes the signed-in user's YouTube Music collection.
Home shows artwork-led playlists and a liked-songs shortcut; Search, local
Library, and Downloads are separate bottom-navigation destinations. Account
identity and disconnect are behind the profile control. Playback remains visible
as a compact player with an expandable Now playing sheet.

Song search and streaming are implemented and audible playback was confirmed by
the user. Song downloads and offline playback are implemented with a persistent
index and a cache-only playback source. Saved-song playback after an app restart
was verified on the user's phone with network access forbidden inside the player.
Optional Google web-session connection and YouTube Music account-library browsing
are implemented separately from the local library. Live sign-in, playlists, liked
songs, and followed artists were confirmed by the user; account restoration was
verified after a force-stop and fresh launch. The five account storage and screen
device tests passed. Saved albums and remote edits still need live verification.
The local Library supports favorites, recent history, and creating/editing
playlists. Playlist definitions and song order persist on the device. Playlist
playback uses the background media service, with lazy resolution for streamed
songs and local storage for downloads. These lists are not synchronized to Google.
Spotify and its Rust backend are outside scope. YouTube integration is unofficial
and can require updates when upstream changes. Retain dependency license notices.

## Brand Commitments

Material 3 Expressive is the explicit visual direction. Use native Android
components, dynamic color, light and dark themes, and Material motion.

## Open Decisions

Final branding, public distribution, and the full browsing-screen design remain
undecided. The initial integration screen is a working foundation.
