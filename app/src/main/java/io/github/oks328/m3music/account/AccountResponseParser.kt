package io.github.oks328.m3music.account

import org.json.JSONObject
import org.json.JSONArray
import io.github.oks328.m3music.music.Track

internal object AccountResponseParser {
    fun discovery(json: JSONObject): List<DiscoveryShelf> {
        val root = json.opt("contents") ?: throw AccountResponseChanged()
        return (nodes(root, "musicCarouselShelfRenderer", true) +
            nodes(root, "musicImmersiveCarouselShelfRenderer", true) +
            nodes(root, "musicShelfRenderer", true)).mapNotNull { shelf ->
            val header = shelf.optJSONObject("header")
            val title = text(nodes(header, "title").firstOrNull() ?: shelf.optJSONObject("title"))
            val contents = shelf.opt("contents")
            val items = listOf("musicResponsiveListItemRenderer", "musicTwoRowItemRenderer", "musicMultiRowListItemRenderer")
                .flatMap { nodes(contents, it, true) }.mapNotNull(::item).distinctBy { it.key }
            if (title.isBlank() || items.isEmpty()) null else DiscoveryShelf(title, items)
        }
    }

    fun searchCollections(json: JSONObject, kind: AccountItemKind): List<AccountItem> {
        val root = json.opt("contents") ?: throw AccountResponseChanged()
        return (nodes(root, "musicResponsiveListItemRenderer", true) +
            nodes(root, "musicTwoRowItemRenderer", true) +
            nodes(root, "musicCardShelfRenderer", true))
            .mapNotNull(::item).filter { it.kind == kind }.distinctBy { it.id }
    }

    fun songCollection(json: JSONObject, trackId: String, kind: AccountItemKind): AccountItem? =
        songCollections(json, trackId, kind).firstOrNull()

    fun songCollections(json: JSONObject, trackId: String, kind: AccountItemKind): List<AccountItem> {
        val root = json.opt("contents") ?: return emptyList()
        val row = nodes(root, "musicResponsiveListItemRenderer", true)
            .firstOrNull { item(it)?.track?.id == trackId } ?: return emptyList()
        return listOf(row.opt("flexColumns"), row.opt("subtitle")).flatMap(::textRuns).mapNotNull { run ->
            val id = run.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                ?.optString("browseId").orEmpty()
            val title = run.optString("text").trim()
            val matches = when (kind) {
                AccountItemKind.ALBUM -> id.startsWith("MPRE")
                AccountItemKind.ARTIST -> id.startsWith("UC") || id.startsWith("MPLA")
                else -> false
            }
            if (matches && title.isNotBlank()) AccountItem(id, title, kind = kind) else null
        }.distinctBy { it.id }
    }

    private fun textRuns(value: Any?): List<JSONObject> = when (value) {
        is JSONObject -> value.optJSONArray("runs")?.let { runs ->
            (0 until runs.length()).mapNotNull { runs.optJSONObject(it) }
        } ?: value.keys().asSequence().flatMap { textRuns(value.opt(it)).asSequence() }.toList()
        is JSONArray -> (0 until value.length()).flatMap { textRuns(value.opt(it)) }
        else -> emptyList()
    }

    fun profile(json: JSONObject): AccountProfile {
        val header = nodes(json.opt("actions"), "activeAccountHeaderRenderer").firstOrNull() ?: throw AccountResponseChanged()
        val name = text(header.optJSONObject("accountName"))
        if (name.isBlank()) throw AccountResponseChanged()
        return AccountProfile(name, text(header.optJSONObject("channelHandle")))
    }

    fun page(json: JSONObject, artist: Boolean = false, playlist: Boolean = false): AccountPage {
        val root = json.opt("contents") ?: json.opt("continuationContents")
            ?: json.opt("onResponseReceivedActions") ?: json.opt("onResponseReceivedEndpoints") ?: throw AccountResponseChanged()
        val recognized = listOf("gridRenderer", "gridContinuation", "sectionListRenderer", "musicShelfRenderer",
            "musicPlaylistShelfRenderer", "musicShelfContinuation", "musicPlaylistShelfContinuation", "musicSectionListContinuation",
            "appendContinuationItemsAction", "reloadContinuationItemsCommand", "messageRenderer", "musicNotifierShelfRenderer")
            .any { nodes(root, it, true).isNotEmpty() }
        if (!recognized) throw AccountResponseChanged()
        val playlistShelf = if (playlist) {
            (nodes(root, "musicPlaylistShelfRenderer", true) + nodes(root, "musicPlaylistShelfContinuation", true))
                .firstOrNull()
        } else null
        val rowsRoot = playlistShelf ?: root
        val items = rows(rowsRoot)
        val continuation = continuation(rowsRoot)
        val editable = nodes(json.opt("header"), "musicEditablePlaylistDetailHeaderRenderer").isNotEmpty() ||
            nodes(root, "musicEditablePlaylistDetailHeaderRenderer").isNotEmpty() ||
            nodes(json.opt("header"), "playlistEditorEndpoint").isNotEmpty()
        val suggestionsToken = if (playlistShelf != null && editable) {
            nodes(root, "sectionListRenderer", true).firstNotNullOfOrNull { section ->
                if (nodes(section, "musicPlaylistShelfRenderer", true).none { it === playlistShelf }) null
                else continuation(section.opt("continuations"))
            }
        } else null
        val headers = listOf("musicResponsiveHeaderRenderer", "musicDetailHeaderRenderer", "musicVisualHeaderRenderer", "musicImmersiveHeaderRenderer")
        val headerRenderers = headers.flatMap { renderer ->
            nodes(json.opt("header"), renderer) + nodes(root, renderer)
        }
        val artwork = headerRenderers.firstNotNullOfOrNull {
            image(it.opt("thumbnail")) ?: image(it.opt("foregroundThumbnail")) ?: image(it.opt("backgroundThumbnail"))
        }
        val albumArtist = headerRenderers.firstNotNullOfOrNull { header ->
            text(header.optJSONObject("straplineTextOne")).trim().takeIf(String::isNotBlank)
                ?: linkedArtists(header.optJSONObject("subtitle"))
        }
        return AccountPage(items, continuation, editable, artwork, albumArtist,
            if (artist && json.has("contents")) discovery(json) else emptyList(), suggestionsToken)
    }

    fun suggestions(json: JSONObject): List<Track> {
        val root = json.optJSONObject("continuationContents") ?: return emptyList()
        val shelf = nodes(root, "musicShelfRenderer", true).firstOrNull()
            ?: nodes(root, "musicShelfContinuation", true).firstOrNull() ?: return emptyList()
        return rows(shelf).mapNotNull { it.track }.distinctBy { it.id }
    }

    private fun rows(root: Any?): List<AccountItem> =
        (nodes(root, "musicResponsiveListItemRenderer", true) + nodes(root, "musicTwoRowItemRenderer", true))
            .mapNotNull(::item).distinctBy { it.key }

    private fun continuation(root: Any?): String? =
        (nodes(root, "nextContinuationData", true) + nodes(root, "reloadContinuationData", true))
            .firstNotNullOfOrNull { it.optString("continuation").takeIf(String::isNotBlank) }
            ?: nodes(root, "continuationCommand", true).firstNotNullOfOrNull { it.optString("token").takeIf(String::isNotBlank) }

    private fun item(row: JSONObject): AccountItem? {
        val flex = row.optJSONArray("flexColumns")
        fun column(index: Int): String = text(flex?.optJSONObject(index)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text"))
        val title = text(row.optJSONObject("title")).ifBlank { column(0) }
        if (title.isBlank()) return null
        val subtitle = text(row.optJSONObject("subtitle")).ifBlank { column(1) }
        val playlistData = row.optJSONObject("playlistItemData")
        val video = playlistData?.optString("videoId")?.takeIf(String::isNotBlank)
            ?: nodes(row, "watchEndpoint", true).firstNotNullOfOrNull { it.optString("videoId").takeIf(String::isNotBlank) }
        val thumbnail = image(row)
        val collection = collection(row, title, subtitle, thumbnail)
        // Collection cards can include an overlay that plays a preview track.
        if (playlistData?.optString("videoId").isNullOrBlank() &&
            collection != null
        ) return collection
        if (video?.matches(Regex("[A-Za-z0-9_-]{11}")) == true) {
            val fixed = row.optJSONArray("fixedColumns")?.optJSONObject(0)
            val durationText = nodes(fixed, "text").firstOrNull()?.let(::text).orEmpty()
            val duration = durationText.split(':').takeIf { it.size in 2..3 && it.all { part -> part.toLongOrNull() != null } }
                ?.fold(0L) { total, part -> total * 60 + part.toLong() } ?: 0
            val artist = subtitle.split('•').map(String::trim)
                .dropWhile { it in setOf("Song", "Video") }.firstOrNull().orEmpty()
            val track = Track(video, title, artist, thumbnail, duration)
            val setId = playlistData?.optString("playlistSetVideoId")?.takeIf(String::isNotBlank)
                ?: playlistData?.optString("setVideoId")?.takeIf(String::isNotBlank)
            return AccountItem(video, title, subtitle, AccountItemKind.SONG, thumbnail, track, setVideoId = setId)
        }
        return collection
    }

    private fun collection(row: JSONObject, title: String, subtitle: String, thumbnail: String?): AccountItem? {
        val titleColumn = row.optJSONArray("flexColumns")?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
        val browse = row.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
            ?.takeIf(String::isNotBlank)
            ?: nodes(row.optJSONObject("title"), "browseEndpoint").firstNotNullOfOrNull { it.optString("browseId").takeIf(String::isNotBlank) }
            ?: nodes(titleColumn, "browseEndpoint").firstNotNullOfOrNull { it.optString("browseId").takeIf(String::isNotBlank) }
            ?: nodes(row, "watchPlaylistEndpoint", true).firstNotNullOfOrNull {
                it.optString("playlistId").takeIf(String::isNotBlank)?.let { id -> "VL${id.removePrefix("VL")}" }
            }
            ?: return null
        val kind = when {
            browse.startsWith("VL") -> AccountItemKind.PLAYLIST
            browse.startsWith("MPRE") -> AccountItemKind.ALBUM
            browse.startsWith("UC") || browse.startsWith("MPLA") -> AccountItemKind.ARTIST
            else -> return null
        }
        val editable = kind == AccountItemKind.PLAYLIST && (nodes(row.opt("menu"), "icon")
            .any { it.optString("iconType") == "EDIT" } || nodes(row.opt("menu"), "playlistEditorEndpoint").isNotEmpty())
        val albumArtist = if (kind == AccountItemKind.ALBUM) {
            linkedArtists(row.optJSONObject("subtitle"))
                ?: linkedArtists(row.optJSONArray("flexColumns")?.optJSONObject(1)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text"))
        } else null
        return AccountItem(browse, title, subtitle, kind, thumbnail, editable = editable, albumArtist = albumArtist)
    }

    private fun linkedArtists(value: JSONObject?): String? {
        val runs = value?.optJSONArray("runs") ?: return null
        return (0 until runs.length()).mapNotNull { index ->
            val run = runs.optJSONObject(index) ?: return@mapNotNull null
            val browseId = run.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                ?.optString("browseId").orEmpty()
            run.optString("text").trim().takeIf { it.isNotBlank() &&
                (browseId.startsWith("UC") || browseId.startsWith("MPLA")) }
        }.joinToString(", ").takeIf(String::isNotBlank)
    }

    private fun image(value: Any?, depth: Int = 0): String? {
        if (depth > 48) return null
        return when (value) {
            is JSONObject -> value.optJSONArray("thumbnails")?.let { thumbnails ->
                (0 until thumbnails.length()).mapNotNull { thumbnails.optJSONObject(it) }
                    .filter { it.optString("url").startsWith("https://") }.maxByOrNull { it.optInt("width") }?.optString("url")
            } ?: value.keys().asSequence().filter { it != "menu" }.firstNotNullOfOrNull { image(value.opt(it), depth + 1) }
            is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { image(value.opt(it), depth + 1) }
            else -> null
        }
    }

    private fun text(value: JSONObject?): String = value?.optString("simpleText")?.takeIf(String::isNotBlank)
        ?: value?.optJSONArray("runs")?.let { runs -> (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() } }.orEmpty()

    private fun nodes(value: Any?, key: String, skipMenus: Boolean = false, depth: Int = 0): List<JSONObject> {
        if (depth > 64) return emptyList()
        return when (value) {
            is JSONObject -> buildList {
                value.optJSONObject(key)?.let(::add)
                value.keys().asSequence().filter { it != key && (!skipMenus || it !in setOf("menu", "header", "trackingParams", "responseContext")) }
                    .forEach { addAll(nodes(value.opt(it), key, skipMenus, depth + 1)) }
            }
            is JSONArray -> (0 until value.length()).flatMap { nodes(value.opt(it), key, skipMenus, depth + 1) }
            else -> emptyList()
        }
    }
}
