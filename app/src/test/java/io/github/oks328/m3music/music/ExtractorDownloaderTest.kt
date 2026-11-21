package io.github.oks328.m3music.music

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.downloader.Request
import java.util.concurrent.TimeUnit

class ExtractorDownloaderTest {
    @Test fun preservesPostBodyWithExtractorHeaders() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("response"))
            val result = ExtractorDownloader(OkHttpClient()).execute(
                Request.newBuilder().post(server.url("/search").toString(), "query".toByteArray())
                    .headers(mapOf("User-Agent" to listOf("extractor-agent"),
                        "Content-Type" to listOf("application/json"),
                        "X-Example" to listOf("one", "two")))
                    .build()
            )
            assertEquals(200, result.responseCode())
            assertEquals("response", result.responseBody())
            val sent = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            assertEquals("POST", sent.method)
            assertEquals("query", sent.body.readUtf8())
            assertEquals("extractor-agent", sent.getHeader("User-Agent"))
            assertEquals(listOf("one", "two"), sent.headers.values("X-Example"))
        }
    }

    @Test fun returnsRedirectDestination() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/final"))
            server.enqueue(MockResponse().setBody("ok"))
            val result = ExtractorDownloader(OkHttpClient()).execute(
                Request.newBuilder().get(server.url("/start").toString()).build()
            )
            assertEquals(server.url("/final").toString(), result.latestUrl())
            assertEquals("ok", result.responseBody())
        }
    }

    @Test fun preservesServerFailureStatus() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
            val result = ExtractorDownloader(OkHttpClient()).execute(
                Request.newBuilder().get(server.url("/").toString()).build()
            )
            assertEquals(503, result.responseCode())
            assertEquals("unavailable", result.responseBody())
        }
    }
}
