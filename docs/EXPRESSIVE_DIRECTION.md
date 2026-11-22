# Proposed expressive direction

This visual direction is informed by Google's M3 Expressive guidance. The main
Home, collection, and player treatments are now implemented; the generated
screens remain concept references with sample artwork and content.

## Feeling and hierarchy

Personal, rhythmic, tactile. The user's collection leads. The primary visual
attention belongs to music and playback, rather than slogans, account plumbing,
or equally weighted action buttons. Keep normal vertical song lists and labeled
navigation; Google explicitly documents poor usability from replacing a playlist
with an unstructured artwork collage.

## System

- Use Material semantic color roles. Concept palette: warm ivory surfaces, dark
  plum text, lime primary, muted violet secondary, peach tertiary. Dark mode uses
  deep plum surfaces and accessible lighter accents. These illustrate a palette;
  dynamic color is the default on Android 12+, as explicitly confirmed by the
  user. The profile dialog can switch to the optional app palette; the choice
  persists. System light/dark appearance remains automatic.
- Strong emphasized type for playlist/song titles and primary actions. Smaller,
  calm app bars and supporting metadata. Avoid oversized generic headings that
  displace music.
- Mix rounded rectangular artwork with a few contrasting Material shapes on
  prominent controls or liked-song artwork. Keep cover art recognizable. Do not
  apply unusual shapes to dense text containers.
- Group related song rows with tight internal spacing and clear outer
  containment. Secondary actions use overflow menus. Retain 48dp targets.
- Two expressive focal interactions: starting playback and opening Now playing.
  Spatial springs connect mini-player expansion and button response; effects
  springs handle color/opacity. Use Material motion tokens and respect disabled
  animations. Avoid perpetual decorative bounce and simulated audio visualizers.

## Screens

Home: compact identity bar, account collection immediately visible, an expressive
liked-songs entry, playlist artwork shelf, actual recent listening. Keep existing
Home/Search/Library/Downloads navigation and persistent compact player.

Collection: prominent cover and title, a dominant Play action with secondary
Shuffle, song count and honest loaded-page status. Songs form a readable vertical
list with overflow actions and clear current-song state.

Now playing: the strongest expressive screen. Large artwork, emphasized song
title, oversized morphing Play/Pause, tactile progress control, previous/next,
like and queue access. Wavy progress expresses playback state, not audio
amplitude. Queue access now displays metadata from the Media3 controller and
selects existing media items without rebuilding the queue. The player heart
explicitly saves local favorites; YouTube likes remain in song action menus.

## Everyday-use polish

Search and Downloads use tonal song groups with compact, named accessibility
actions. Local Library playlists use artwork-led rows and scrollable detail
headers so large text does not crowd out songs. Now playing has an explicit
collapse control and system Back returns from Queue to the player. Large online
artwork requests a higher resolution only for recognized Google thumbnail
formats, falling back to the supplied image; downloaded playback keeps original
cache-only artwork behavior.

## Next design pass

- Review the Google Sans Flex typography and identify refinements to heading hierarchy, width, and readability.
- Review the overall app feel, including animation timing, gestures, and transitions between screens and player states.
- Explore whether to offer a per-song theme derived from the current track's artwork.
- Design and test the tablet layout.
- Design and generate the M3 MUSIC app icon.

## Sources

- [Start building with Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)
- [Google's expressive design research](https://design.google/library/expressive-material-design-google-research)
- [Material motion physics](https://m3.material.io/styles/motion)
- [Androidify implementation examples](https://android-developers.googleblog.com/2025/05/androidify-building-delightful-ui-with-compose.html)

Generated concepts use illustrative music content. They do not claim that new
recommendation feeds, account actions, queue controls, or motion are already
implemented.
