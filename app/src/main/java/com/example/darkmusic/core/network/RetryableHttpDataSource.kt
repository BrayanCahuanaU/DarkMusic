package com.example.darkmusic.core.network

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import com.example.darkmusic.domain.repository.MusicRepository
import kotlinx.coroutines.runBlocking

@UnstableApi
class RetryableHttpDataSource(
    private val context: Context,
    private val repository: MusicRepository
) : HttpDataSource {

    private val delegateFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(
            "Mozilla/5.0 (Linux; Android 14; SM-A015F) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Mobile Safari/537.36"
        )
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(15000)
        .setDefaultRequestProperties(
            mapOf(
                "Referer" to "https://www.youtube.com/",
                "Origin" to "https://www.youtube.com"
            )
        )

    private val delegate =
        delegateFactory.createDataSource() as DefaultHttpDataSource

    private var currentVideoId: String? = null

    override fun open(dataSpec: DataSpec): Long {
        return try {
            delegate.open(dataSpec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {

            if (e.responseCode == 403) {

                val videoId = extractVideoId(dataSpec.uri.toString())

                if (videoId != null && videoId != currentVideoId) {

                    try {

                        val newUrl = runBlocking {
                            repository.getStreamUrl(videoId)
                        }

                        if (!newUrl.isNullOrBlank()) {

                            currentVideoId = videoId

                            val newSpec = dataSpec.buildUpon()
                                .setUri(Uri.parse(newUrl))
                                .build()

                            return delegate.open(newSpec)
                        }

                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }

            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return delegate.read(buffer, offset, length)
    }

    override fun getUri(): Uri? {
        return delegate.uri
    }

    override fun getResponseHeaders(): Map<String, List<String>> {
        return delegate.responseHeaders
    }

    override fun close() {
        delegate.close()
        currentVideoId = null
    }

    override fun addTransferListener(transferListener: TransferListener) {
        delegate.addTransferListener(transferListener)
    }



    override fun setRequestProperty(key: String, value: String) {
        delegate.setRequestProperty(key, value)
    }

    override fun clearRequestProperty(key: String) {
        delegate.clearRequestProperty(key)
    }

    override fun clearAllRequestProperties() {
        delegate.clearAllRequestProperties()
    }

    override fun getResponseCode(): Int {
        return try {
            // DefaultHttpDataSource exposes responseCode property
            val field = DefaultHttpDataSource::class.java.getDeclaredField("responseCode")
            field.isAccessible = true
            (field.get(delegate) as? Int) ?: -1
        } catch (e: Exception) {
            -1
        }
    }

    private fun extractVideoId(urlString: String): String? {

        Regex("[?&]v=([A-Za-z0-9_-]{10,12})")
            .find(urlString)
            ?.groupValues
            ?.get(1)
            ?.let { return it }

        Regex("youtu\\.be/([A-Za-z0-9_-]{10,12})")
            .find(urlString)
            ?.groupValues
            ?.get(1)
            ?.let { return it }

        if (
            urlString.length in 10..12 &&
            urlString.none { it == '/' || it == '?' || it == '=' }
        ) {
            return urlString
        }

        return null
    }

    class Factory(
        private val context: Context,
        private val repository: MusicRepository
    ) : HttpDataSource.Factory {

        override fun createDataSource(): HttpDataSource {
            return RetryableHttpDataSource(context, repository)
        }

        override fun setDefaultRequestProperties(p0: Map<String, String>): HttpDataSource.Factory {
            // Ignorar y devolver la fábrica; las propiedades se configuran en el delegado
            return this
        }
    }
}