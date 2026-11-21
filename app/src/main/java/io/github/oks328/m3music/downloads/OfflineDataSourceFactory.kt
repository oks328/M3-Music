@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package io.github.oks328.m3music.downloads

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource

/** Offline URIs have no upstream: a missing cache span must fail, never stream. */
internal class OfflineDataSourceFactory(cache: Cache, private val online: DataSource.Factory) : DataSource.Factory {
    private val offline = CacheDataSource.Factory().setCache(cache)
        .setCacheWriteDataSinkFactory(null).setUpstreamDataSourceFactory(null)

    override fun createDataSource(): DataSource = object : DataSource {
        private var delegate: DataSource? = null
        private val listeners = mutableListOf<TransferListener>()

        override fun addTransferListener(transferListener: TransferListener) {
            listeners.add(transferListener)
            delegate?.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val selected = if (dataSpec.uri.scheme == "offline") offline else online
            delegate = selected.createDataSource().also { source ->
                listeners.forEach(source::addTransferListener)
            }
            return requireNotNull(delegate).open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            requireNotNull(delegate).read(buffer, offset, length)

        override fun getUri(): Uri? = delegate?.uri
        override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders.orEmpty()
        override fun close() {
            try { delegate?.close() } finally { delegate = null }
        }
    }
}
