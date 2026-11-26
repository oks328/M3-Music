package io.github.oks328.m3music.account

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class YouTubeAccountClientTest {
    private val session = AccountSession("SAPISID=synthetic-cookie", "visitor", "sync||ignored", "channel", "1")
    private val profileJson = """{"actions":[{"openPopupAction":{"popup":{"multiPageMenuRenderer":{"header":{"activeAccountHeaderRenderer":{"accountName":{"runs":[{"text":"Listener"}]}}}}}}}]}"""

    @Test fun authenticatedRequestKeepsAccountContextTogether() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(profileJson))
            val profile = runBlocking { YouTubeAccountClient(server.url("/youtubei/v1/")).profile(session) }
            assertEquals("Listener", profile.name)
            val sent = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            assertEquals("/youtubei/v1/account/account_menu?prettyPrint=false", sent.path)
            assertEquals(session.cookie, sent.getHeader("Cookie"))
            assertTrue(sent.getHeader("Authorization").orEmpty().startsWith("SAPISIDHASH "))
            assertEquals("https://music.youtube.com", sent.getHeader("Origin"))
            assertEquals("channel", sent.getHeader("X-Goog-PageId"))
            assertEquals("1", sent.getHeader("X-Goog-AuthUser"))
            val context = JSONObject(sent.body.readUtf8()).getJSONObject("context")
            assertEquals("sync", context.getJSONObject("user").getString("onBehalfOfUser"))
            assertEquals("WEB_REMIX", context.getJSONObject("client").getString("clientName"))
        }
    }

    @Test fun continuationRequestsKeepTheContinuationToken() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"continuationContents":{"gridContinuation":{"items":[]}}}"""))
            runBlocking { YouTubeAccountClient(server.url("/")).browse(session, "library", "next-token") }
            val body = JSONObject(requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)).body.readUtf8())
            assertEquals("next-token", body.getString("continuation"))
            assertFalse(body.has("browseId"))
        }
    }

    @Test fun redirectsNeverForwardAccountCredentials() {
        MockWebServer().use { source -> MockWebServer().use { other ->
            source.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/collect")))
            assertThrows(IOException::class.java) { runBlocking { YouTubeAccountClient(source.url("/")).profile(session) } }
            assertEquals(0, other.requestCount)
        } }
    }

    @Test fun anUnauthorizedResponseIsAnExpiredSession() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401))
            assertThrows(AccountSessionExpired::class.java) { runBlocking { YouTubeAccountClient(server.url("/")).profile(session) } }
        }
    }

    @Test fun malformedPagesAreNotTreatedAsAnEmptyAccount() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<html>Sign in</html>"))
            assertThrows(AccountResponseChanged::class.java) { runBlocking { YouTubeAccountClient(server.url("/")).profile(session) } }
        }
    }

    @Test fun newAccountPlaylistsArePrivate() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"playlistId":"PLcreated"}"""))
            runBlocking { YouTubeAccountClient(server.url("/")).perform(session, AccountAction.CreatePlaylist("Road trip", listOf("abcdefghijk", "lmnopqrstuv"))) }
            val sent = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            val body = JSONObject(sent.body.readUtf8())
            assertEquals("PRIVATE", body.getString("privacyStatus"))
            assertEquals("Road trip", body.getString("title"))
            assertEquals("abcdefghijk", body.getJSONArray("videoIds").getString(0))
            assertEquals("lmnopqrstuv", body.getJSONArray("videoIds").getString(1))
            assertTrue(sent.path.orEmpty().startsWith("/playlist/create"))
        }
    }

    @Test fun removingASongUsesItsPlaylistEntryIdentifier() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"status":"STATUS_SUCCEEDED"}"""))
            runBlocking { YouTubeAccountClient(server.url("/")).perform(session, AccountAction.RemoveSong("VLPLtest", "abcdefghijk", "entry-id")) }
            val body = JSONObject(requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)).body.readUtf8())
            val action = body.getJSONArray("actions").getJSONObject(0)
            assertEquals("PLtest", body.getString("playlistId"))
            assertEquals("ACTION_REMOVE_VIDEO", action.getString("action"))
            assertEquals("entry-id", action.getString("setVideoId"))
            assertEquals("abcdefghijk", action.getString("removedVideoId"))
        }
    }
}
