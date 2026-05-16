package com.example.darkmusic.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException

class NewPipeDownloader(
    private val client: OkHttpClient
) : Downloader() {

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
    }

    @Throws(IOException::class)
    override fun execute(
        request: org.schabi.newpipe.extractor.downloader.Request
    ): Response {
        val url = request.url()
        val method = request.httpMethod()
        val data = request.dataToSend()

        val builder = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)

        request.headers().forEach { (key, values) ->
            values.forEach { value ->
                builder.addHeader(key, value)
            }
        }

        when (method.uppercase()) {
            "POST" -> {
                // Especificamos el cuerpo correctamente si hay datos, si no, uno vacío.
                val body = data?.toRequestBody(null, 0, data.size) ?: "".toRequestBody(null)
                builder.post(body)
            }
            "GET" -> builder.get()
            else -> builder.method(method, null)
        }

        val okHttpRequest = builder.build()

        client.newCall(okHttpRequest).execute().use { response ->
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string(),
                response.request.url.toString()
            )
        }
    }
}
