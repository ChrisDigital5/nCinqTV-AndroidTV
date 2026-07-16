package app.ncinq.tv.player

import android.content.Context
import android.net.Uri
import app.ncinq.tv.data.Caption
import app.ncinq.tv.data.MediaProxy
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

internal data class ResolvedCaption(
    val caption: Caption,
    val uri: Uri,
)

internal class CaptionResolver(
    private val client: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson(),
) {
    suspend fun resolve(context: Context, caption: Caption): ResolvedCaption = withContext(Dispatchers.IO) {
        if (!isOpenSubtitlesDescriptor(caption.url)) {
            return@withContext ResolvedCaption(caption, Uri.parse(MediaProxy.captionUrl(caption)))
        }

        val directory = File(context.cacheDir, "subtitles").apply { mkdirs() }
        val destination = File(directory, "${sha256(caption.url)}.vtt")
        if (!destination.exists() || destination.length() == 0L) {
            destination.writeText(resolveOpenSubtitles(caption.url))
        }
        ResolvedCaption(caption, Uri.fromFile(destination))
    }

    private fun resolveOpenSubtitles(descriptorUrl: String): String {
        val searchRequest = Request.Builder()
            .url(descriptorUrl)
            .header("Accept", "application/json")
            .header("X-User-Agent", "trailers.to-UA")
            .header("User-Agent", "ncinqtv-android")
            .build()
        val results = client.newCall(searchRequest).execute().use { response ->
            if (!response.isSuccessful) error("Subtitle search returned ${response.code}")
            gson.fromJson(response.body.string(), Array<OpenSubtitlesResult>::class.java).toList()
        }
        val selected = results.firstOrNull {
            it.subDownloadLink != null && it.subFormat.equals("srt", true) && it.hearingImpaired != "1"
        } ?: results.firstOrNull {
            it.subDownloadLink != null && it.subFormat.equals("srt", true)
        } ?: error("No English subtitle was found")

        val downloadUrl = selected.subDownloadLink ?: error("Subtitle download URL is missing")
        require(isOpenSubtitlesDownload(downloadUrl)) { "Subtitle download is not allowed" }
        val downloadRequest = Request.Builder().url(downloadUrl).header("User-Agent", "ncinqtv-android").build()
        val bytes = client.newCall(downloadRequest).execute().use { response ->
            if (!response.isSuccessful) error("Subtitle download returned ${response.code}")
            response.body.bytes()
        }
        val srt = if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader().use { it.readText() }
        } else {
            bytes.toString(Charsets.UTF_8)
        }
        require(srt.contains("-->")) { "Subtitle download was not SRT" }
        return srtToVtt(srt)
    }

    private data class OpenSubtitlesResult(
        @com.google.gson.annotations.SerializedName("SubDownloadLink") val subDownloadLink: String? = null,
        @com.google.gson.annotations.SerializedName("SubFormat") val subFormat: String? = null,
        @com.google.gson.annotations.SerializedName("SubHearingImpaired") val hearingImpaired: String? = null,
    )
}

internal fun isOpenSubtitlesDescriptor(value: String): Boolean = runCatching {
    val url = value.toHttpUrlStrict()
    url.scheme == "https" && url.host == "rest.opensubtitles.org" && url.encodedPath.startsWith("/search/")
}.getOrDefault(false)

private fun isOpenSubtitlesDownload(value: String): Boolean = runCatching {
    val url = value.toHttpUrlStrict()
    url.scheme == "https" && (url.host == "opensubtitles.org" || url.host.endsWith(".opensubtitles.org"))
}.getOrDefault(false)

private fun String.toHttpUrlStrict() = toHttpUrl()

internal fun srtToVtt(value: String): String {
    val normalized = value.removePrefix("\uFEFF").replace("\r\n", "\n")
    val timestamps = Regex("(\\d{2}:\\d{2}:\\d{2}),(\\d{3})\\s+-->\\s+(\\d{2}:\\d{2}:\\d{2}),(\\d{3})")
    return "WEBVTT\n\n" + timestamps.replace(normalized, "$1.$2 --> $3.$4")
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }
