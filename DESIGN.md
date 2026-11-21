# Android visual foundation

The user's pinned direction is Google Material 3 Expressive. This initial screen
operates within that system; it does not introduce an alternative visual identity.

- Use MaterialExpressiveTheme with expressive motion and semantic color roles.
- Follow system light/dark appearance and use the phone's dynamic color on
  Android 12+ by default. The profile dialog offers an optional app palette;
  ivory/plum, lime, violet, and peach are its fallback color relationships.
- Use Material typography roles, native buttons and text fields, 48dp minimum targets.
- Album artwork leads song rows; song names and artists remain readable.
- Search and playback each have explicit idle, loading, error, and recovery states.
- Keep playback controls in a tonal surface beneath the search results.
- Respect system and keyboard insets; keep results scrollable at larger font sizes.
- Library inherits the same visual system, with Playlists, Favorites, and History.
  Song actions are explicit, and deleting a playlist or clearing history requires
  a focused confirmation. Playlist detail respects system Back and provides
  keyboard-accessible Move up/down actions rather than requiring drag gestures.

The app now starts on an account-centered Home. Its expressive tonal feature
opens liked songs; playlist and artist collections use artwork-led cards, with
recent listening drawn from actual device history. Search is a separate
destination. Home, Search, Library, and Downloads use the Material 3 Expressive
short navigation bar. Account identity and disconnect live behind the profile
control. A compact persistent player expands into a Now playing sheet.
Account collection details lead with centered cover art, full title and metadata,
paired Play/Shuffle controls, and song rows with overflow menus. Editing appears
only for editable account playlists. Keep partial-page playback explicit, and
return to the top when switching collections or categories.
Use semantic colors and Material type roles throughout; preserve the user's
Google session, downloads, and local library across interface updates.

The expressive visual pass uses heavier Material title roles, a compact identity
bar, a horizontal playlist shelf, grouped tonal song rows, and a compact player.
Now playing uses Material shape morphing and spatial spring tokens for Play/Pause,
a native accessible Slider with a wavy active track, real service queue selection,
downloads, and explicitly local favorites. Motion follows Compose's system
animation scaling; the progress wave is a playback-state treatment, not an audio
visualizer. The user explicitly confirmed Monet/system colors as the default.
