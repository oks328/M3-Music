package io.github.oks328.m3music.account

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AccountResponseParserTest {
    @Test fun readsTheVerifiedAccountName() {
        val json = JSONObject("""{"actions":[{"openPopupAction":{"popup":{"multiPageMenuRenderer":{"header":{"activeAccountHeaderRenderer":{"accountName":{"runs":[{"text":"Music listener"}]},"channelHandle":{"runs":[{"text":"@listener"}]}}}}}}}]}""")
        assertEquals(AccountProfile("Music listener", "@listener"), AccountResponseParser.profile(json))
    }
    @Test fun playlistWithoutArtworkIsStillVisible() {
        val json = JSONObject("""{"contents":{"gridRenderer":{"items":[{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"My empty playlist"}]},"navigationEndpoint":{"browseEndpoint":{"browseId":"VLPLexample"}},"menu":{"menuRenderer":{"items":[{"menuNavigationItemRenderer":{"icon":{"iconType":"EDIT"}}}]}}}}],"continuations":[{"nextContinuationData":{"continuation":"next-page"}}]}}}""")
        val page = AccountResponseParser.page(json)
        assertEquals("next-page", page.continuation)
        assertEquals("VLPLexample", page.items.single().id)
        assertEquals("My empty playlist", page.items.single().title)
        assertNull(page.items.single().artworkUrl)
        assertTrue(page.items.single().editable)
    }
    @Test fun playlistRowsRetainSongAndRemovalIdentifiers() {
        val json = JSONObject("""{"continuationContents":{"musicPlaylistShelfContinuation":{"contents":[{"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"abcdefghijk","playlistSetVideoId":"set-one"},"flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"A song"}]}}},{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"An artist"}]}}}],"fixedColumns":[{"musicResponsiveListItemFixedColumnRenderer":{"text":{"runs":[{"text":"3:21"}]}}}]}},{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"next"}}}}]}}}""")
        val page = AccountResponseParser.page(json)
        val item = page.items.single()
        assertEquals("set-one", item.setVideoId)
        assertEquals("A song", item.track?.title)
        assertEquals("An artist", item.track?.artist)
        assertEquals(201L, item.track?.durationSeconds)
        assertEquals("next", page.continuation)
    }
    @Test fun aRecognizedEmptyShelfIsNotAnError() {
        assertTrue(AccountResponseParser.page(JSONObject("""{"contents":{"gridRenderer":{"items":[]}}}""")).items.isEmpty())
    }
    @Test fun unknownResponsesAreNotPresentedAsAnEmptyLibrary() {
        assertThrows(AccountResponseChanged::class.java) { AccountResponseParser.page(JSONObject("""{"unexpected":"layout"}""")) }
        assertThrows(AccountResponseChanged::class.java) { AccountResponseParser.profile(JSONObject("{}")) }
    }
    @Test fun discoveryAlbumWithPlayOverlayOpensTheAlbum() {
        val json = JSONObject("""
            {"contents":{"musicCarouselShelfRenderer":{
                "header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"simpleText":"Albums for you"}}},
                "contents":[
                    {"musicTwoRowItemRenderer":{
                        "title":{"runs":[{"text":"An album"}]},
                        "navigationEndpoint":{"browseEndpoint":{"browseId":"MPREalbum"}},
                        "thumbnailOverlay":{"musicItemThumbnailOverlayRenderer":{"content":{
                            "musicPlayButtonRenderer":{"playNavigationEndpoint":{"watchEndpoint":{"videoId":"abcdefghijk"}}}
                        }}}
                    }},
                    {"musicTwoRowItemRenderer":{
                        "title":{"simpleText":"A song"},
                        "subtitle":{"simpleText":"Song \u2022 A singer"},
                        "navigationEndpoint":{"watchEndpoint":{"videoId":"lmnopqrstuv"}}
                    }}
                ]
            }}}
        """.trimIndent())
        val (album, song) = AccountResponseParser.discovery(json).single().items
        assertEquals(AccountItemKind.ALBUM, album.kind)
        assertEquals("MPREalbum", album.id)
        assertNull(album.track)
        assertEquals(AccountItemKind.SONG, song.kind)
        assertEquals("lmnopqrstuv", song.track?.id)
        assertEquals("A singer", song.track?.artist)
    }
    @Test fun albumHeaderFillsMissingCoverAndArtist() {
        val page = AccountResponseParser.page(JSONObject("""
            {"contents":{"sectionListRenderer":{"contents":[
                {"musicResponsiveHeaderRenderer":{"straplineTextOne":{"simpleText":"Album Artist"},"thumbnail":{"musicThumbnailRenderer":{"thumbnail":{
                    "thumbnails":[{"url":"https://example.org/cover.jpg","width":500}]
                }}}}},
                {"musicShelfRenderer":{"contents":[{"musicResponsiveListItemRenderer":{
                    "title":{"simpleText":"A song"},
                    "playlistItemData":{"videoId":"abcdefghijk"}
                }}]}}
            ]}}}
        """.trimIndent()))
        val album = AccountItem("MPREalbum", "An album", kind = AccountItemKind.ALBUM)
        assertEquals("https://example.org/cover.jpg", page.artworkUrl)
        assertNull(page.items.single().track?.artworkUrl)
        val track = requireNotNull(page.withAlbumMetadata(album).items.single().track)
        assertEquals(page.artworkUrl, track.artworkUrl)
        assertEquals("Album Artist", track.artist)
        assertEquals("Guest Artist", page.fillAlbumCredits(track.copy(artist = "Guest Artist")).artist)
    }
    @Test fun editablePlaylistKeepsSuggestionContinuationSeparateFromSongs() {
        val page = AccountResponseParser.page(JSONObject("""
            {"contents":{
                "header":{"musicEditablePlaylistDetailHeaderRenderer":{}},
                "sectionListRenderer":{
                    "contents":[{"musicPlaylistShelfRenderer":{"contents":[],
                        "continuations":[{"nextContinuationData":{"continuation":"more-songs"}}]
                    }}],
                    "continuations":[{"nextContinuationData":{"continuation":"suggested-songs"}}]
                }
            }}
        """.trimIndent()), playlist = true)
        assertTrue(page.editable)
        assertTrue(page.items.isEmpty())
        assertEquals("more-songs", page.continuation)
        assertEquals("suggested-songs", page.suggestionsToken)
    }
    @Test fun songAlbumLookupRequiresTheExactVideoId() {
        val json = JSONObject("""
            {"contents":{"musicShelfRenderer":{"contents":[
                {"musicResponsiveListItemRenderer":{"title":{"simpleText":"A song"},
                 "playlistItemData":{"videoId":"abcdefghijk"},"flexColumns":[
                   {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
                     {"text":"The album","navigationEndpoint":{"browseEndpoint":{"browseId":"MPREtest"}}}
                   ]}}}
                 ]}}
            ]}}}
        """.trimIndent())
        assertEquals("MPREtest", AccountResponseParser.songCollection(json, "abcdefghijk", AccountItemKind.ALBUM)?.id)
        assertNull(AccountResponseParser.songCollection(json, "lmnopqrstuv", AccountItemKind.ALBUM))
    }
    @Test fun collaborationTracksExposeEachLinkedArtist() {
        val json = JSONObject("""
            {"contents":{"musicShelfRenderer":{"contents":[
                {"musicResponsiveListItemRenderer":{"title":{"simpleText":"A duet"},
                 "playlistItemData":{"videoId":"abcdefghijk"},"flexColumns":[
                   {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
                     {"text":"First Artist","navigationEndpoint":{"browseEndpoint":{"browseId":"UCfirst"}}},
                     {"text":" & "},
                     {"text":"Second Artist","navigationEndpoint":{"browseEndpoint":{"browseId":"UCsecond"}}}
                   ]}}}
                 ]}}
            ]}}}
        """.trimIndent())
        val credits = AccountResponseParser.songCollections(json, "abcdefghijk", AccountItemKind.ARTIST)
        assertEquals(listOf("First Artist", "Second Artist"), credits.map { it.title })
    }
}
