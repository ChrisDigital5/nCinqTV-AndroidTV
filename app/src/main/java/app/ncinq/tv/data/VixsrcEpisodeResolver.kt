package app.ncinq.tv.data

import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

private const val VIXSRC_BASE_URL = "https://vixsrc.to"
private const val VIXSRC_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; TV) AppleWebKit/537.36 Chrome/148.0.0.0 Safari/537.36"

internal data class VixsrcPlaybackConfig(val token: String, val expires: String, val playlist: String)

internal fun PlaybackRequest.requiresVerifiedVixsrcSource(): Boolean =
    mediaType == MediaType.TV && mediaId == 82452 && season == 2 && episode == 1

internal fun extractVixsrcPlaybackConfig(html: String): VixsrcPlaybackConfig? {
    val marker = html.indexOf("window.masterPlaylist")
    val source = if (marker >= 0) html.substring(marker, minOf(html.length, marker + 2_000)) else html
    val token = Regex("""[\"']?token[\"']?\s*:\s*[\"']([^\"']+)[\"']""").find(source)?.groupValues?.get(1)
    val expires = Regex("""[\"']?expires[\"']?\s*:\s*[\"']([^\"']+)[\"']""").find(source)?.groupValues?.get(1)
    val playlist = Regex("""\burl\s*:\s*[\"']([^\"']+)[\"']""").find(source)?.groupValues?.get(1)?.replace("&amp;", "&")
    return if (token != null && expires != null && playlist != null) {
        VixsrcPlaybackConfig(token, expires, playlist)
    } else null
}

internal fun hlsDurationMinutes(manifest: String): Double =
    Regex("""(?m)^#EXTINF:([0-9.]+)""").findAll(manifest)
        .sumOf { it.groupValues[1].toDoubleOrNull() ?: 0.0 } / 60.0

class VixsrcEpisodeResolver(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    fun resolve(request: PlaybackRequest): StreamResult? {
        if (!request.requiresVerifiedVixsrcSource()) return null
        val season = request.season ?: return null
        val episode = request.episode ?: return null
        val contentUrl = "$VIXSRC_BASE_URL/tv/${request.mediaId}/$season/$episode"
        val apiUrl = "$VIXSRC_BASE_URL/api/tv/${request.mediaId}/$season/$episode"

        return runCatching {
            val apiBody = fetchText(apiUrl, contentUrl, "application/json, text/plain, */*") ?: return null
            val embedSource = JsonParser.parseString(apiBody).asJsonObject.get("src")?.asString ?: return null
            val embedUrl = URI(apiUrl).resolve(embedSource).toString()
            val embedBody = fetchText(embedUrl, apiUrl, "text/html,application/xhtml+xml,*/*") ?: return null
            val config = extractVixsrcPlaybackConfig(embedBody) ?: return null
            if ((config.expires.toLongOrNull() ?: 0L) * 1_000L < System.currentTimeMillis() + 60_000L) return null

            val masterUrl = config.playlist.toHttpUrl().newBuilder()
                .setQueryParameter("token", config.token)
                .setQueryParameter("expires", config.expires)
                .setQueryParameter("h", "1")
                .build()
                .toString()
            val master = fetchText(masterUrl, apiUrl, "application/vnd.apple.mpegurl, application/x-mpegURL, */*")
                ?: return null
            if (!master.trimStart().startsWith("#EXTM3U")) return null

            val childPath = master.lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("#") }
            val mediaManifest = childPath?.let { path ->
                fetchText(URI(masterUrl).resolve(path).toString(), apiUrl, "application/vnd.apple.mpegurl, */*")
            } ?: master
            val actualMinutes = hlsDurationMinutes(mediaManifest)
            val expectedMinutes = request.expectedRuntimeMinutes
            if (expectedMinutes != null && actualMinutes > 0 && actualMinutes < expectedMinutes * 0.65) return null

            val heights = Regex("""RESOLUTION=\d+x(\d+)""").findAll(master)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .toList()
            StreamResult(
                url = masterUrl,
                type = "hls",
                quality = heights.maxOrNull()?.let { "${it}p" },
                provider = "vixsrc",
                headers = mapOf("Referer" to apiUrl, "Origin" to VIXSRC_BASE_URL),
            )
        }.getOrNull()
    }

    private fun fetchText(url: String, referer: String, accept: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", VIXSRC_USER_AGENT)
            .header("Accept", accept)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", referer)
            .build()
        return client.newCall(request).execute().use { response ->
            response.body?.string().takeIf { response.isSuccessful && !it.isNullOrBlank() }
        }
    }
}
