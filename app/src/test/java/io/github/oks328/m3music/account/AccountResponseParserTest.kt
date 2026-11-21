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
}
