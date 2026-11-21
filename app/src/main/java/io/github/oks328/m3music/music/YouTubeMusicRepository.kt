package io.github.oks328.m3music.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit

/** Anonymous catalog only. Extractor objects never escape this boundary. */
object YouTubeMusicRepository {
    private val initialized by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .build()
        NewPipe.init(ExtractorDownloader(client), Localization("en", "PH"), ContentCountry("PH"))
        true
    }

    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        check(initialized)
        val extractor = ServiceList.YouTube.getSearchExtractor(
            query.trim(), listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), "",
        )
        extractor.fetchPage()
        extractor.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { item ->
            runCatching {
                Track(
                    id = ServiceList.YouTube.streamLHFactory.getId(item.url),
                    title = item.name,
                    artist = item.uploaderName.orEmpty(),
                    artworkUrl = item.thumbnails.maxByOrNull { it.height }?.url,
                    durationSeconds = item.duration,
                )
            }.getOrNull()
        }.distinctBy { it.id }
    }

    suspend fun resolve(trackId: String): AudioSource = withContext(Dispatchers.IO) {
        require(trackId.matches(Regex("[A-Za-z0-9_-]{11}"))) { "Invalid YouTube song ID" }
        check(initialized)
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$trackId")
        selectAudioSource(info.audioStreams.filter {
            it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
        }.map { AudioSource(it.content, it.format?.mimeType, it.averageBitrate) })
    }
}
