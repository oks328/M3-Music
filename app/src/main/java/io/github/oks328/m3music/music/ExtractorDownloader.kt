package io.github.oks328.m3music.music

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

class ExtractorDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder().url(request.url())
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        val body = request.dataToSend()?.toRequestBody()
            ?: if (request.httpMethod() in listOf("POST", "PUT", "PATCH")) ByteArray(0).toRequestBody() else null
        builder.method(request.httpMethod(), body)
        return client.newCall(builder.build()).execute().use { response ->
            Response(response.code, response.message, response.headers.toMultimap(),
                response.body?.string(), response.request.url.toString())
        }
    }
}
