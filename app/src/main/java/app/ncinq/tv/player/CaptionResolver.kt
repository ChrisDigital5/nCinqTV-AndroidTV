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
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlin.math.ln

internal data class ResolvedCaption(
    val caption: Caption,
    val uri: Uri,
    val trackId: String,
    val label: String,
)

internal class CaptionResolver(
    private val client: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson(),
) {
    suspend fun resolve(
        context: Context,
        caption: Caption,
        expectedRuntimeMinutes: Int? = null,
    ): List<ResolvedCaption> = withContext(Dispatchers.IO) {
        if (!isOpenSubtitlesDescriptor(caption.url)) {
            val trackId = caption.id.ifBlank { sha256(caption.url) }
            return@withContext listOf(
                ResolvedCaption(
                    caption = caption,
                    uri = Uri.parse(MediaProxy.captionUrl(caption)),
                    trackId = trackId,
                    label = caption.language,
                ),
            )
        }

        resolveOpenSubtitles(context, caption, expectedRuntimeMinutes)
    }

    private fun resolveOpenSubtitles(
        context: Context,
        caption: Caption,
        expectedRuntimeMinutes: Int?,
    ): List<ResolvedCaption> {
        val descriptorUrl = caption.url
        val directory = File(context.cacheDir, "subtitles").apply { mkdirs() }
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
        val candidates = rankOpenSubtitles(results, expectedRuntimeMinutes)
        val resolved = mutableListOf<ResolvedCaption>()
        for (candidate in candidates.take(MAX_SUBTITLE_DOWNLOAD_ATTEMPTS)) {
            val downloaded = runCatching {
                downloadCandidate(directory, descriptorUrl, caption, candidate)
            }.getOrNull() ?: continue
            resolved += downloaded
            if (resolved.size == MAX_ENGLISH_SUBTITLE_OPTIONS) break
        }
        if (resolved.isEmpty()) error("No English subtitle was found")
        return resolved
    }

    private fun downloadCandidate(
        directory: File,
        descriptorUrl: String,
        caption: Caption,
        candidate: OpenSubtitlesResult,
    ): ResolvedCaption {
        val downloadUrl = candidate.subDownloadLink ?: error("Subtitle download URL is missing")
        require(isOpenSubtitlesDownload(downloadUrl)) { "Subtitle download is not allowed" }
        val trackId = candidate.subtitleFileId?.takeIf { it.isNotBlank() }
            ?: sha256(downloadUrl)
        val destination = File(directory, "${sha256("$descriptorUrl:$trackId")}.vtt")
        if (!destination.exists() || destination.length() == 0L) {
            val downloadRequest = Request.Builder().url(downloadUrl).header("User-Agent", "ncinqtv-android").build()
            val bytes = client.newCall(downloadRequest).execute().use { response ->
                if (!response.isSuccessful) error("Subtitle download returned ${response.code}")
                response.body.bytes()
            }
            val uncompressed = if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
                GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            } else {
                bytes
            }
            val charset = runCatching {
                candidate.encoding?.takeIf { it.isNotBlank() }?.let(Charset::forName)
            }.getOrNull() ?: Charsets.UTF_8
            val srt = uncompressed.toString(charset)
            require(srt.contains("-->")) { "Subtitle download was not SRT" }
            destination.writeText(srtToVtt(srt))
        }
        return ResolvedCaption(
            caption = caption,
            uri = Uri.fromFile(destination),
            trackId = trackId,
            label = candidate.displayLabel(),
        )
    }

    private companion object {
        const val MAX_ENGLISH_SUBTITLE_OPTIONS = 2
        const val MAX_SUBTITLE_DOWNLOAD_ATTEMPTS = 5
    }
}

internal data class OpenSubtitlesResult(
    @com.google.gson.annotations.SerializedName("IDSubtitleFile") val subtitleFileId: String? = null,
    @com.google.gson.annotations.SerializedName("SubDownloadLink") val subDownloadLink: String? = null,
    @com.google.gson.annotations.SerializedName("SubFormat") val subFormat: String? = null,
    @com.google.gson.annotations.SerializedName("SubHearingImpaired") val hearingImpaired: String? = null,
    @com.google.gson.annotations.SerializedName("SubForeignPartsOnly") val foreignPartsOnly: String? = null,
    @com.google.gson.annotations.SerializedName("SubBad") val bad: String? = null,
    @com.google.gson.annotations.SerializedName("SubFeatured") val featured: String? = null,
    @com.google.gson.annotations.SerializedName("SubFromTrusted") val trusted: String? = null,
    @com.google.gson.annotations.SerializedName("SubRating") val rating: String? = null,
    @com.google.gson.annotations.SerializedName("SubDownloadsCnt") val downloads: String? = null,
    @com.google.gson.annotations.SerializedName("SubLastTS") val lastTimestamp: String? = null,
    @com.google.gson.annotations.SerializedName("SubFileName") val fileName: String? = null,
    @com.google.gson.annotations.SerializedName("MovieReleaseName") val releaseName: String? = null,
    @com.google.gson.annotations.SerializedName("SubAuthorComment") val authorComment: String? = null,
    @com.google.gson.annotations.SerializedName("SubEncoding") val encoding: String? = null,
)

internal fun rankOpenSubtitles(
    results: List<OpenSubtitlesResult>,
    expectedRuntimeMinutes: Int? = null,
): List<OpenSubtitlesResult> {
    val eligible = results
        .filter { result ->
            result.subDownloadLink != null &&
                result.subFormat.equals("srt", true) &&
                result.bad != "1" &&
                result.foreignPartsOnly != "1" &&
                !result.searchableText().containsAny("commentary only", ".commentary.", "director commentary")
        }
        .distinctBy { it.subtitleFileId ?: it.subDownloadLink }
        .sortedByDescending { it.syncScore(expectedRuntimeMinutes) }

    val releaseMatched = if (eligible.any { it.releaseKind() != "Telesync" }) {
        eligible.filter { it.releaseKind() != "Telesync" }
    } else {
        eligible
    }
    // Prefer alternatives mastered for different releases before returning a near-duplicate.
    val distinctReleases = releaseMatched.distinctBy { it.releaseKind() }
    return (distinctReleases + releaseMatched).distinctBy { it.subtitleFileId ?: it.subDownloadLink }
}

private fun OpenSubtitlesResult.syncScore(expectedRuntimeMinutes: Int?): Double {
    val text = searchableText()
    var score = 0.0
    score += rating?.toDoubleOrNull()?.times(3.0) ?: 0.0
    score += downloads?.toDoubleOrNull()?.coerceAtLeast(0.0)?.let { ln(it + 1.0) * 2.0 } ?: 0.0
    if (featured == "1") score += 20.0
    if (trusted == "1") score += 10.0
    if (hearingImpaired != "1") score += 8.0
    if (text.containsAny("web-dl", "webdl", "webrip")) score += 45.0
    if (text.containsAny("blu-ray", "bluray", "bdrip", "brrip")) score += 42.0
    if (text.containsAny("hdtv")) score += 25.0
    if (text.containsAny("hdts", "telesync", "telecine", "camrip", " hdcam", ".cam.")) score -= 120.0

    val expectedSeconds = expectedRuntimeMinutes?.takeIf { it > 0 }?.times(60)
    val lastCueSeconds = lastTimestamp?.toTimestampSeconds()
    if (expectedSeconds != null && lastCueSeconds != null) {
        val minutesEarly = (expectedSeconds - lastCueSeconds) / 60.0
        when {
            minutesEarly < -5.0 -> score -= 20.0
            minutesEarly <= 15.0 -> score += 8.0
            minutesEarly > 30.0 -> score -= 40.0
        }
    }
    return score
}

private fun OpenSubtitlesResult.displayLabel(): String {
    val accessibility = if (hearingImpaired == "1") " SDH" else ""
    return "English · ${releaseKind()}$accessibility"
}

private fun OpenSubtitlesResult.releaseKind(): String {
    val text = searchableText()
    return when {
        text.containsAny("web-dl", "webdl", "webrip") -> "Web"
        text.containsAny("blu-ray", "bluray", "bdrip", "brrip") -> "Blu-ray"
        text.containsAny("hdtv") -> "HDTV"
        text.containsAny("dvd", "dvdrip") -> "DVD"
        text.containsAny("hdts", "telesync", "telecine", "camrip", " hdcam", ".cam.") -> "Telesync"
        else -> "Alternate"
    }
}

private fun OpenSubtitlesResult.searchableText(): String =
    listOf(fileName, releaseName, authorComment).joinToString(" ").lowercase()

private fun String.containsAny(vararg needles: String): Boolean = needles.any(::contains)

private fun String.toTimestampSeconds(): Int? {
    val parts = split(':')
    if (parts.size != 3) return null
    val hours = parts[0].toIntOrNull() ?: return null
    val minutes = parts[1].toIntOrNull() ?: return null
    val seconds = parts[2].toDoubleOrNull() ?: return null
    return hours * 3_600 + minutes * 60 + seconds.toInt()
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
