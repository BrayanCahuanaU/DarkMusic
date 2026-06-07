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
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
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

    // ── getStreamUrl con NewPipeExtractor primero, luego Piped → InnerTube → yt-dlp → Public fallback ───────────
    override suspend fun getStreamUrl(videoId: String): String? =
        withContext(Dispatchers.IO) {

            try {
                val cleanId = extractCleanVideoId(videoId)
                val url = normalizeToYouTubeUrl(cleanId)

                val linkHandler = youtube.streamLHFactory.fromUrl(url)
                val extractor = youtube.getStreamExtractor(linkHandler)

                extractor.fetchPage()

                // DEBUG TEMPORAL
                val audioCount = extractor.audioStreams.size
                val videoCount = extractor.videoStreams.size
                val videoOnlyCount = extractor.videoOnlyStreams.size
                Log.d("MusicRepository", "Streams: audio=$audioCount video=$videoCount videoOnly=$videoOnlyCount hlsUrl=${extractor.hlsUrl}")
                if (audioCount > 0) {
                    val first = extractor.audioStreams.first()
                    Log.d("MusicRepository", "Audio[0]: url=${first.url?.take(80)} bitrate=${first.bitrate} avgBitrate=${first.averageBitrate}")
                }
                if (videoCount > 0) {
                    val first = extractor.videoStreams.first()
                    Log.d("MusicRepository", "Video[0]: url=${first.url?.take(80)} bitrate=${first.bitrate}")
                }
                // FIN DEBUG

                val bestAudio = extractor.audioStreams
                    .mapNotNull { stream -> stream.url?.let { Pair(stream, it) } }
                    .maxByOrNull { (stream, _) -> try { stream.averageBitrate } catch (_: Exception) { stream.bitrate } }

                bestAudio?.let { (_, streamUrl) ->
                    Log.d("MusicRepository", "✓ Audio stream")
                    return@withContext streamUrl
                }

                // Fallback: video muxed (contiene audio)
                val bestVideo = extractor.videoStreams
                    .mapNotNull { stream -> stream.url?.let { Pair(stream, it) } }
                    .maxByOrNull { (stream, _) -> stream.bitrate }

                bestVideo?.let { (_, streamUrl) ->
                    Log.d("MusicRepository", "✓ Video stream (muxed)")
                    return@withContext streamUrl
                }

                extractor.hlsUrl?.takeIf { it.isNotBlank() }?.let { hls ->
                    Log.d("MusicRepository", "✓ HLS")
                    return@withContext hls
                }

            } catch (e: Exception) {
                Log.e("MusicRepository", "Error obteniendo stream", e)
                null
            }
        }

    // ── InnerTube ──────────────────────────────────────────────────────────
    private fun getStreamUrlViaInnerTube(videoId: String): String? {
        return tryInnerTubeClient(
            videoId,
            clientName = "IOS",
            clientVersion = "19.29.1",
            clientNameInt = "5",
            userAgent = "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)"
        ) ?: tryInnerTubeClient(
            videoId,
            clientName = "ANDROID",
            clientVersion = "19.30.37",
            clientNameInt = "3",
            userAgent = "com.google.android.youtube/19.30.37 (Linux; U; Android 11) gzip"
        ) ?: tryInnerTubeClient(
            videoId,
            clientName = "WEB",
            clientVersion = "2.20260601.00.00",
            clientNameInt = "1",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
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
            val body = buildString {
                append("{\"context\":{\"client\":{")
                append("\"clientName\":\"$clientName\",")
                append("\"clientVersion\":\"$clientVersion\",")
                append("\"hl\":\"es\",\"gl\":\"US\"")
                if (clientName == "ANDROID") {
                    append(",\"androidSdkVersion\":30")
                    append(",\"osName\":\"Android\"")
                    append(",\"osVersion\":\"11\"")
                }
                if (clientName == "IOS") {
                    append(",\"deviceModel\":\"iPhone16,2\"")
                    append(",\"osName\":\"iPhone\"")
                    append(",\"osVersion\":\"17.5.1.21F90\"")
                    append(",\"userInterfaceTheme\":\"USER_INTERFACE_THEME_DARK\"")
                }
                append("}},\"videoId\":\"$videoId\",\"params\":\"CgIQBg==\"}")
            }

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
            val linkHandler = youtube.streamLHFactory.fromUrl(url)
            val extractor = youtube.getStreamExtractor(linkHandler)
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