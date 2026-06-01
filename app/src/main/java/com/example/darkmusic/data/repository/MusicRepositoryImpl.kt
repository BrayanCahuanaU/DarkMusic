package com.example.darkmusic.data.repository

import android.util.Log
import com.example.darkmusic.core.network.MusicDownloader
import com.example.darkmusic.data.local.dao.SongDao
import com.example.darkmusic.data.mapper.toDomain
import com.example.darkmusic.data.mapper.toEntity
import com.example.darkmusic.domain.model.Song
import com.example.darkmusic.domain.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit

class MusicRepositoryImpl @Inject constructor(
    private val songDao: SongDao,
    private val musicDownloader: MusicDownloader
) : MusicRepository {

    private val youtube = ServiceList.YouTube

    private val innerTubeClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val pipedInstances = listOf(
        "https://pipedapi.leptons.xyz",
        "https://pipedapi.adminforge.de", 
        "https://api.piped.yt",
        "https://pipedapi.drgns.space",
        "https://pipedapi.owo.si",
        "https://piped-api.privacy.com.de",
        "https://pipedapi.darkness.services",
        "https://pipedapi-libre.kavin.rocks"
    )

    // ── getStreamUrl con fallbacks: NewPipe → InnerTube → Piped ───────────
    override suspend fun getStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val url = normalizeToYouTubeUrl(videoId)
        val cleanId = extractCleanVideoId(videoId)

        // Intento 1: NewPipe
        val newPipeResult = runCatching {
            val extractor = youtube.getStreamExtractor(url)
            extractor.fetchPage()
            val info = StreamInfo.getInfo(extractor)

            Log.d("MusicRepository", "NewPipe → audio=${info.audioStreams.size} hls=${info.hlsUrl} dash=${info.dashMpdUrl}")

            when {
                info.audioStreams.isNotEmpty() ->
                    info.audioStreams
                        .maxByOrNull { it.averageBitrate.takeIf { b -> b > 0 } ?: it.bitrate }
                        ?.content
                info.hlsUrl?.isNotEmpty() == true -> info.hlsUrl
                info.dashMpdUrl?.isNotEmpty() == true -> info.dashMpdUrl
                else -> null
            }
        }.getOrElse {
            Log.w("MusicRepository", "NewPipe falló: ${it::class.simpleName}: ${it.message}")
            null
        }

        if (newPipeResult != null) {
            Log.d("MusicRepository", "✓ NewPipe obtuvo stream")
            return@withContext newPipeResult
        }

        // Intento 2: InnerTube
        Log.d("MusicRepository", "NewPipe sin streams, probando InnerTube para $cleanId")
        val innerTubeResult = getStreamUrlViaInnerTube(cleanId)
        if (innerTubeResult != null) {
            return@withContext innerTubeResult
        }

        // Intento 3: Piped API (resuelve restricciones de región)
        Log.d("MusicRepository", "InnerTube falló, probando Piped para $cleanId")
        getStreamUrlViaPiped(cleanId)
    }

    // ── InnerTube ──────────────────────────────────────────────────────────
    private fun getStreamUrlViaInnerTube(videoId: String): String? {
        return tryInnerTubeClient(
            videoId,
            clientName = "ANDROID_TESTSUITE",
            clientVersion = "1.9",
            clientNameInt = "30",
            userAgent = "com.google.android.youtube/17.36.4 (Linux; U; Android 12) gzip"
        ) ?: tryInnerTubeClient(
            videoId,
            clientName = "WEB",
            clientVersion = "2.20240726.00.00",
            clientNameInt = "1",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        ) ?: tryInnerTubeClient(
            videoId,
            clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            clientVersion = "2.0",
            clientNameInt = "85",
            userAgent = "Mozilla/5.0 (SMART-TV; LINUX; Tizen 6.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/6.0 TV Safari/538.1"
        )
    }

    private fun tryInnerTubeClient(
        videoId: String,
        clientName: String,
        clientVersion: String,
        clientNameInt: String,
        userAgent: String
    ): String? {
        return try {
            val body = "{" +
                    "\"context\":{" +
                    "\"client\":{" +
                    "\"clientName\":\"$clientName\"," +
                    "\"clientVersion\":\"$clientVersion\"," +
                    "\"hl\":\"es\"," +
                    "\"gl\":\"PE\"" +
                    "}" +
                    "}," +
                    "\"videoId\":\"$videoId\"" +
                    "}"

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", userAgent)
                .header("X-YouTube-Client-Name", clientNameInt)
                .header("X-YouTube-Client-Version", clientVersion)
                .header("Content-Type", "application/json")
                .header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/")
                .build()

            val response = innerTubeClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null
            Log.d("MusicRepository", "InnerTube [$clientName] HTTP=${response.code} body=${responseBody.take(300)}")
            val json = JSONObject(responseBody)

            val status = json.optJSONObject("playabilityStatus")?.optString("status")
            val reason = json.optJSONObject("playabilityStatus")?.optString("reason")
            Log.d("MusicRepository", "InnerTube [$clientName] status=$status reason=$reason")

            if (status != "OK") return null

            val adaptiveFormats = json
                .optJSONObject("streamingData")
                ?.optJSONArray("adaptiveFormats")
                ?: return null

            var bestUrl: String? = null
            var bestBitrate = 0

            for (i in 0 until adaptiveFormats.length()) {
                val fmt = adaptiveFormats.getJSONObject(i)
                if (!fmt.optString("mimeType").startsWith("audio/")) continue
                val bitrate = fmt.optInt("averageBitrate", fmt.optInt("bitrate", 0))
                val streamUrl = fmt.optString("url", "")
                if (streamUrl.isNotEmpty() && bitrate > bestBitrate) {
                    bestUrl = streamUrl
                    bestBitrate = bitrate
                }
            }

            bestUrl?.also { Log.d("MusicRepository", "✓ [$clientName] stream bitrate=$bestBitrate") }
                ?: json.optJSONObject("streamingData")
                    ?.optString("hlsManifestUrl")
                    ?.takeIf { it.isNotEmpty() }

        } catch (e: Exception) {
            Log.e("MusicRepository", "InnerTube [$clientName] falló: ${e.message}")
            null
        }
    }

    // ── Piped API ──────────────────────────────────────────────────────────
    private fun getStreamUrlViaPiped(videoId: String): String? {
        for (instance in pipedInstances) {
            try {
                val request = Request.Builder()
                    .url("$instance/streams/$videoId")
                    .header("User-Agent", "DarkMusic/1.0")
                    .build()

                val response = innerTubeClient.newCall(request).execute()
                val body = response.body?.string() ?: continue
                Log.d("MusicRepository", "Piped [$instance] HTTP=${response.code} body=${body.take(200)}")

                if (!response.isSuccessful) continue

                val json = JSONObject(body)
                val audioStreams = json.optJSONArray("audioStreams") ?: continue

                var bestUrl: String? = null
                var bestBitrate = 0

                for (i in 0 until audioStreams.length()) {
                    val stream = audioStreams.getJSONObject(i)
                    val bitrate = stream.optInt("bitrate", 0)
                    val url = stream.optString("url", "")
                    if (url.isNotEmpty() && bitrate > bestBitrate) {
                        bestUrl = url
                        bestBitrate = bitrate
                    }
                }

                if (bestUrl != null) {
                    Log.d("MusicRepository", "✓ Piped [$instance] stream bitrate=$bestBitrate")
                    return bestUrl
                }

            } catch (e: Exception) {
                Log.w("MusicRepository", "Piped [$instance] falló: ${e.message}")
            }
        }
        return null
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private fun normalizeToYouTubeUrl(input: String): String = when {
        input.startsWith("http") -> input
        input.startsWith("/watch") -> "https://www.youtube.com$input"
        else -> "https://www.youtube.com/watch?v=$input"
    }

    private fun extractCleanVideoId(input: String): String {
        Regex("[?&]v=([A-Za-z0-9_-]{10,12})").find(input)?.groupValues?.get(1)?.let { return it }
        Regex("youtu\\.be/([A-Za-z0-9_-]{10,12})").find(input)?.groupValues?.get(1)?.let { return it }
        if (input.length in 10..12 && input.none { it == '/' || it == '?' || it == '=' }) return input
        return input
    }

    // ── Resto de métodos ───────────────────────────────────────────────────
    override fun getAllSongs() = songDao.getAllSongs().map { it.map { e -> e.toDomain() } }
    override fun getFavoriteSongs() = songDao.getFavoriteSongs().map { it.map { e -> e.toDomain() } }
    override suspend fun insertSong(song: Song) = songDao.insertSong(song.toEntity())
    override suspend fun deleteSong(songId: String) = songDao.deleteSong(songId)
    override suspend fun getSongById(songId: String) = songDao.getSongById(songId)?.toDomain()
    override suspend fun getTrendingSongs() = searchSongs("Top music hits world 2024")
    override suspend fun getSongsByGenre(genre: String) = searchSongs("$genre music hits")

    override suspend fun searchSongs(query: String) = withContext(Dispatchers.IO) {
        try {
            val extractor = youtube.getSearchExtractor(query)
            extractor.fetchPage()
            mapInfoItems(extractor.initialPage.items)
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error buscando '$query'", e)
            emptyList()
        }
    }

    override suspend fun downloadSong(song: Song) = withContext(Dispatchers.IO) {
        try {
            val streamUrl = getStreamUrl(song.id) ?: return@withContext false
            val localPath = musicDownloader.downloadSong(song, streamUrl)
            if (localPath != null) {
                val fullSong = getFullSongInfo(song.id) ?: song
                insertSong(fullSong.copy(isDownloaded = true, localPath = localPath))
                true
            } else false
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error descargando", e)
            false
        }
    }

    override suspend fun getFullSongInfo(songId: String) = withContext(Dispatchers.IO) {
        try {
            val url = normalizeToYouTubeUrl(songId)
            val extractor = youtube.getStreamExtractor(url)
            extractor.fetchPage()
            val info = StreamInfo.getInfo(extractor)
            Song(
                id = songId,
                title = info.name,
                artist = info.uploaderName ?: "Desconocido",
                album = null,
                genre = info.category ?: "Música",
                durationMs = info.duration * 1000L,
                coverUrl = info.thumbnails.maxByOrNull { it.width * it.height }?.url,
                mediaUrl = url
            )
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error obteniendo info de $songId", e)
            null
        }
    }

    private fun mapInfoItems(items: List<InfoItem>) =
        items.filterIsInstance<StreamInfoItem>().map { item ->
            Song(
                id = item.url,
                title = item.name,
                artist = item.uploaderName ?: "Desconocido",
                album = null,
                genre = null,
                durationMs = item.duration * 1000L,
                coverUrl = item.thumbnails.maxByOrNull { it.width * it.height }?.url,
                mediaUrl = item.url
            )
        }
}