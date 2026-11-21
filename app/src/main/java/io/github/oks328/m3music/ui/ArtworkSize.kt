package io.github.oks328.m3music.ui

import java.net.URI

/** Only known public Music image resize parameters; never rewrite signed or unfamiliar URLs. */
internal fun largeArtworkUrl(url: String?): String? {
    if (url == null) return null
    return runCatching {
        val uri = URI(url)
        if (uri.scheme != "https" || uri.userInfo != null || uri.port != -1 || uri.rawQuery != null ||
            uri.host !in setOf("lh3.googleusercontent.com", "yt3.googleusercontent.com")) return url
        val size = Regex("=w(\\d+)-h(\\d+)(?=-|$)").find(url) ?: return url
        val width = size.groupValues[1].toIntOrNull() ?: return url
        val height = size.groupValues[2].toIntOrNull() ?: return url
        if (width >= 720 && height >= 720) url else url.replaceRange(size.range, "=w720-h720")
    }.getOrDefault(url)
}
