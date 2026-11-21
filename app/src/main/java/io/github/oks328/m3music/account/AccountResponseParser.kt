package io.github.oks328.m3music.account

import org.json.JSONObject
import org.json.JSONArray
import io.github.oks328.m3music.music.Track

internal object AccountResponseParser {
    fun profile(json: JSONObject): AccountProfile {
        val header = nodes(json.opt("actions"), "activeAccountHeaderRenderer").firstOrNull() ?: throw AccountResponseChanged()
        val name = text(header.optJSONObject("accountName"))
        if (name.isBlank()) throw AccountResponseChanged()
        return AccountProfile(name, text(header.optJSONObject("channelHandle")))
    }

    fun page(json: JSONObject): AccountPage {
        val root = json.opt("contents") ?: json.opt("continuationContents")
            ?: json.opt("onResponseReceivedActions") ?: json.opt("onResponseReceivedEndpoints") ?: throw AccountResponseChanged()
        val recognized = listOf("gridRenderer", "gridContinuation", "sectionListRenderer", "musicShelfRenderer",
            "musicPlaylistShelfRenderer", "musicShelfContinuation", "musicPlaylistShelfContinuation", "musicSectionListContinuation",
            "appendContinuationItemsAction", "reloadContinuationItemsCommand", "messageRenderer", "musicNotifierShelfRenderer")
            .any { nodes(root, it, true).isNotEmpty() }
        if (!recognized) throw AccountResponseChanged()
        val rows = nodes(root, "musicResponsiveListItemRenderer", true) + nodes(root, "musicTwoRowItemRenderer", true)
        val items = rows.mapNotNull(::item).distinctBy { it.key }
        val continuation = (nodes(root, "nextContinuationData", true) + nodes(root, "reloadContinuationData", true))
            .firstNotNullOfOrNull { it.optString("continuation").takeIf(String::isNotBlank) }
            ?: nodes(root, "continuationCommand", true).firstNotNullOfOrNull { it.optString("token").takeIf(String::isNotBlank) }
        val editable = nodes(json.opt("header"), "musicEditablePlaylistDetailHeaderRenderer").isNotEmpty() ||
            nodes(json.opt("header"), "playlistEditorEndpoint").isNotEmpty()
        return AccountPage(items, continuation, editable)
    }

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
        val artwork = nodes(row, "thumbnails", true).lastOrNull()?.optString("url") // Arrays handled below instead.
        val thumbnail = image(row) ?: artwork?.takeIf { it.startsWith("https://") }
        if (video?.matches(Regex("[A-Za-z0-9_-]{11}")) == true) {
            val fixed = row.optJSONArray("fixedColumns")?.optJSONObject(0)
            val durationText = nodes(fixed, "text").firstOrNull()?.let(::text).orEmpty()
            val duration = durationText.split(':').takeIf { it.size in 2..3 && it.all { part -> part.toLongOrNull() != null } }
                ?.fold(0L) { total, part -> total * 60 + part.toLong() } ?: 0
            val track = Track(video, title, subtitle.substringBefore(" • "), thumbnail, duration)
            val setId = playlistData?.optString("playlistSetVideoId")?.takeIf(String::isNotBlank)
                ?: playlistData?.optString("setVideoId")?.takeIf(String::isNotBlank)
            return AccountItem(video, title, subtitle, AccountItemKind.SONG, thumbnail, track, setVideoId = setId)
        }
        val browse = row.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
            ?.takeIf(String::isNotBlank)
            ?: nodes(row.optJSONObject("title"), "browseEndpoint").firstNotNullOfOrNull { it.optString("browseId").takeIf(String::isNotBlank) }
            ?: nodes(flex?.optJSONObject(0), "browseEndpoint").firstNotNullOfOrNull { it.optString("browseId").takeIf(String::isNotBlank) }
            ?: return null
        val kind = when {
            browse.startsWith("VL") -> AccountItemKind.PLAYLIST
            browse.startsWith("MPRE") -> AccountItemKind.ALBUM
            browse.startsWith("UC") || browse.startsWith("MPLA") -> AccountItemKind.ARTIST
            else -> return null
        }
        val editable = kind == AccountItemKind.PLAYLIST && (nodes(row.opt("menu"), "icon")
            .any { it.optString("iconType") == "EDIT" } || nodes(row.opt("menu"), "playlistEditorEndpoint").isNotEmpty())
        return AccountItem(browse, title, subtitle, kind, thumbnail, editable = editable)
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
