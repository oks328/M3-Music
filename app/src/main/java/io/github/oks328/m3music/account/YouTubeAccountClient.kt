package io.github.oks328.m3music.account

import io.github.oks328.m3music.library.normalizePlaylistName
import io.github.oks328.m3music.music.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class YouTubeAccountClient(
    private val baseUrl: HttpUrl = "https://music.youtube.com/youtubei/v1/".toHttpUrl(),
    client: OkHttpClient = OkHttpClient(),
) : AccountGateway {
    // Never redirect credentials to another origin, or retry a write after an uncertain result.
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS).build()

    suspend fun searchCollections(query: String): List<AccountItem> = coroutineScope {
        val albums = async { searchCollections(query, "IY", AccountItemKind.ALBUM) }
        val artists = async { searchCollections(query, "Ig", AccountItemKind.ARTIST) }
        albums.await() + artists.await()
    }

    suspend fun findArtist(name: String): AccountItem? =
        searchCollections(name, "Ig", AccountItemKind.ARTIST)
            .firstOrNull { it.title.trim().equals(name.trim(), ignoreCase = true) }

    suspend fun findSongCollection(track: Track, kind: AccountItemKind): AccountItem? {
        val linked = AccountResponseParser.songCollection(searchTrack(track), track.id, kind)
        return linked ?: if (kind == AccountItemKind.ARTIST && track.artist.isNotBlank()) findArtist(track.artist) else null
    }

    suspend fun findTrackArtists(track: Track): List<AccountItem> {
        val linked = AccountResponseParser.songCollections(searchTrack(track), track.id, AccountItemKind.ARTIST)
        if (linked.isNotEmpty()) return linked
        val credit = track.artist.trim()
        if (credit.isBlank()) return emptyList()
        findArtist(credit)?.let { return listOf(it) }
        val names = credit.split(Regex(" +[&] +|, +| +(?:feat[.]?|ft[.]?|featuring) +", RegexOption.IGNORE_CASE))
            .map(String::trim).filter(String::isNotBlank).distinct()
        return if (names.size > 1) coroutineScope {
            names.take(4).map { name -> async { findArtist(name) } }.mapNotNull { it.await() }.distinctBy { it.id }
        } else emptyList()
    }

    private suspend fun searchTrack(track: Track): JSONObject {
        val query = listOf(track.title, track.artist).filter(String::isNotBlank).joinToString(" ")
        val body = JSONObject().put("query", query).put("params", "EgWKAQIIAWoMEA4QChADEAQQCRAF")
        return post(null, "search", body)
    }

    private suspend fun searchCollections(query: String, filter: String, kind: AccountItemKind): List<AccountItem> {
        val body = JSONObject().put("query", query).put("params", "EgWKAQ${filter}AWoMEA4QChADEAQQCRAF")
        return AccountResponseParser.searchCollections(post(null, "search", body), kind)
    }

    override suspend fun discovery(session: AccountSession?): List<DiscoveryShelf> =
        AccountResponseParser.discovery(post(session, "browse", JSONObject().put("browseId", "FEmusic_home")))

    override suspend fun discoveryCollection(session: AccountSession?, id: String, continuation: String?): AccountPage =
        browsePage(session, id, continuation, artist = id.startsWith("UC") || id.startsWith("MPLA"))

    override suspend fun profile(session: AccountSession): AccountProfile =
        AccountResponseParser.profile(post(session, "account/account_menu", JSONObject()))

    override suspend fun browse(session: AccountSession, browseId: String, continuation: String?): AccountPage =
        browsePage(session, browseId, continuation)

    private suspend fun browsePage(
        session: AccountSession?, browseId: String, continuation: String?, artist: Boolean = false,
    ): AccountPage {
        val body = JSONObject()
        if (continuation != null) body.put("continuation", continuation) else body.put("browseId", browseId)
        return AccountResponseParser.page(post(session, "browse", body), artist, playlist = browseId.startsWith("VL"))
    }

    override suspend fun playlistSuggestions(session: AccountSession, id: String, token: String): List<Track> =
        AccountResponseParser.suggestions(post(session, "browse", JSONObject().put("browseId", id), token))

    override suspend fun perform(session: AccountSession, action: AccountAction) {
        fun playlistId(id: String): String = id.removePrefix("VL").also {
            require(it.matches(Regex("[A-Za-z0-9_-]{2,200}")) && it != "LM") { "This playlist cannot be edited here." }
        }
        fun videoId(id: String): String = id.also { require(it.matches(Regex("[A-Za-z0-9_-]{11}"))) }
        val body = JSONObject()
        val path = when (action) {
            is AccountAction.LikeSong -> {
                body.put("target", JSONObject().put("videoId", videoId(action.videoId)))
                if (action.like) "like/like" else "like/removelike"
            }
            is AccountAction.CreatePlaylist -> {
                body.put("title", normalizePlaylistName(action.name)).put("privacyStatus", "PRIVATE")
                "playlist/create"
            }
            is AccountAction.DeletePlaylist -> { body.put("playlistId", playlistId(action.id)); "playlist/delete" }
            is AccountAction.RenamePlaylist -> {
                body.put("playlistId", playlistId(action.id)).put("actions", JSONArray().put(JSONObject()
                    .put("action", "ACTION_SET_PLAYLIST_NAME").put("playlistName", normalizePlaylistName(action.name))))
                "browse/edit_playlist"
            }
            is AccountAction.AddSong -> {
                body.put("playlistId", playlistId(action.playlistId)).put("actions", JSONArray().put(JSONObject()
                    .put("action", "ACTION_ADD_VIDEO").put("addedVideoId", videoId(action.videoId))))
                "browse/edit_playlist"
            }
            is AccountAction.RemoveSong -> {
                require(action.setVideoId.isNotBlank()) { "This song cannot be removed from this page." }
                body.put("playlistId", playlistId(action.playlistId)).put("actions", JSONArray().put(JSONObject()
                    .put("action", "ACTION_REMOVE_VIDEO").put("removedVideoId", videoId(action.videoId)).put("setVideoId", action.setVideoId)))
                "browse/edit_playlist"
            }
        }
        val response = post(session, path, body)
        if (response.optString("status").let { it.isNotBlank() && it != "STATUS_SUCCEEDED" }) {
            throw IOException("YouTube Music did not confirm this change. Refresh before trying again.")
        }
        if (action is AccountAction.CreatePlaylist && response.optString("playlistId").isBlank()) throw AccountResponseChanged()
    }

    private suspend fun post(
        session: AccountSession?, endpoint: String, body: JSONObject, browseContinuation: String? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val clientVersion = (session ?: AccountSession("")).clientVersion
        val client = JSONObject().put("clientName", "WEB_REMIX").put("clientVersion", clientVersion)
            .put("hl", "en").put("gl", "PH")
        if (session != null && session.visitorData.isNotBlank()) client.put("visitorData", session.visitorData)
        val user = JSONObject()
        if (session != null && session.dataSyncId.isNotBlank()) user.put("onBehalfOfUser", session.dataSyncId.substringBefore("||"))
        body.put("context", JSONObject().put("client", client).put("user", user))
        val url = baseUrl.newBuilder().addPathSegments(endpoint).addQueryParameter("prettyPrint", "false")
        if (browseContinuation != null) {
            url.addQueryParameter("ctoken", browseContinuation).addQueryParameter("continuation", browseContinuation)
        }
        val request = Request.Builder().url(url.build())
            .header("Origin", SignedAccountRequest.ORIGIN).header("X-Origin", SignedAccountRequest.ORIGIN)
            .header("Referer", "${SignedAccountRequest.ORIGIN}/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")
            .header("X-YouTube-Client-Name", "67").header("X-YouTube-Client-Version", clientVersion)
            .header("X-Goog-Api-Format-Version", "1")
        if (session != null) {
            request.header("X-Goog-AuthUser", session.authUser).header("Cookie", session.cookie)
                .header("Authorization", SignedAccountRequest.authorization(session, System.currentTimeMillis() / 1000))
            if (session.visitorData.isNotBlank()) request.header("X-Goog-Visitor-Id", session.visitorData)
            if (session.pageId.isNotBlank()) request.header("X-Goog-PageId", session.pageId)
        }
        http.newCall(request.post(body.toString().toRequestBody("application/json".toMediaType())).build()).execute().use { response ->
            if (response.code == 401 || response.code == 403 && endpoint == "account/account_menu") throw AccountSessionExpired()
            if (!response.isSuccessful) throw IOException("YouTube Music couldn't complete this request (${response.code}).")
            val source = response.body?.source() ?: throw AccountResponseChanged()
            if (source.request(4L * 1024 * 1024 + 1)) throw AccountResponseChanged()
            val raw = source.readUtf8()
            val json = try { JSONObject(if (raw.startsWith(")]}'")) raw.substringAfter('\n') else raw) }
                catch (_: Exception) { throw AccountResponseChanged() }
            if (session != null && json.optJSONObject("responseContext")?.optJSONObject("mainAppWebResponseContext")?.optBoolean("loggedOut") == true) throw AccountSessionExpired()
            json.optJSONObject("error")?.let {
                if (it.optInt("code") in listOf(401, 403)) throw AccountSessionExpired()
                throw AccountResponseChanged()
            }
            json
        }
    }
}
