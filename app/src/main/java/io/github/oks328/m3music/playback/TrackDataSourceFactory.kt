@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package io.github.oks328.m3music.playback

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import io.github.oks328.m3music.music.AudioSource
import java.io.IOException

/** Resolves queue entries when their audio is loaded, rather than resolving an entire playlist up front. */
internal class TrackDataSourceFactory(
    private val delegateFactory: DataSource.Factory,
    private val resolve: (String) -> AudioSource,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = object : DataSource {
        private val delegate = delegateFactory.createDataSource()
        private var resolvedId: String? = null
        private var resolvedSource: AudioSource? = null
        override fun addTransferListener(transferListener: TransferListener) = delegate.addTransferListener(transferListener)
        override fun open(dataSpec: DataSpec): Long {
            if (dataSpec.uri.scheme != "track") return delegate.open(dataSpec)
            val id = requireNotNull(dataSpec.uri.lastPathSegment)
            try {
                val source = if (resolvedId == id) requireNotNull(resolvedSource) else resolve(id).also {
                    resolvedSource = it; resolvedId = id
                }
                return delegate.open(dataSpec.buildUpon().setUri(Uri.parse(source.url)).setKey(id).build())
            } catch (failure: Exception) {
                resolvedId = null; resolvedSource = null
                throw IOException("Couldn't load this song. Retry playback or check your connection.", failure)
            }
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = delegate.read(buffer, offset, length)
        override fun getUri(): Uri? = delegate.uri
        override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders
        override fun close() = delegate.close()
    }
}
