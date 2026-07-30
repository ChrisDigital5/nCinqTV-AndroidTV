package app.ncinq.tv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptionResolverTest {
    @Test
    fun `recognizes only the OpenSubtitles search descriptor`() {
        assertTrue(isOpenSubtitlesDescriptor("https://rest.opensubtitles.org/search/imdbid-123/sublanguageid-eng"))
        assertFalse(isOpenSubtitlesDescriptor("https://example.com/search/imdbid-123"))
        assertFalse(isOpenSubtitlesDescriptor("http://rest.opensubtitles.org/search/imdbid-123"))
    }

    @Test
    fun `converts SRT timestamps to WebVTT`() {
        assertEquals(
            "WEBVTT\n\n1\n00:00:01.250 --> 00:00:03.500\nHello\n",
            srtToVtt("1\r\n00:00:01,250 --> 00:00:03,500\r\nHello\r\n"),
        )
    }

    @Test
    fun `prefers synced retail releases over Spider-Verse telesync subtitles`() {
        val ranked = rankOpenSubtitles(
            listOf(
                OpenSubtitlesResult(
                    subtitleFileId = "hdts",
                    subDownloadLink = "https://dl.opensubtitles.org/hdts.gz",
                    subFormat = "srt",
                    releaseName = "1080p V3 HDTS Video X264",
                    downloads = "559319",
                    trusted = "1",
                    lastTimestamp = "02:12:57",
                ),
                OpenSubtitlesResult(
                    subtitleFileId = "web",
                    subDownloadLink = "https://dl.opensubtitles.org/web.gz",
                    subFormat = "srt",
                    releaseName = "1080p WEB-DL DDP5.1 Atmos H.264",
                    rating = "9.0",
                    downloads = "213656",
                    featured = "1",
                ),
                OpenSubtitlesResult(
                    subtitleFileId = "bluray",
                    subDownloadLink = "https://dl.opensubtitles.org/bluray.gz",
                    subFormat = "srt",
                    releaseName = "720p BluRay x264 AAC",
                    downloads = "58582",
                    lastTimestamp = "02:13:17",
                ),
            ),
            expectedRuntimeMinutes = 140,
        )

        assertEquals(listOf("web", "bluray"), ranked.take(2).map { it.subtitleFileId })
        assertFalse(ranked.any { it.subtitleFileId == "hdts" })
    }

    @Test
    fun `filters partial and commentary English tracks`() {
        val ranked = rankOpenSubtitles(
            listOf(
                OpenSubtitlesResult(
                    subtitleFileId = "forced",
                    subDownloadLink = "https://dl.opensubtitles.org/forced.gz",
                    subFormat = "srt",
                    foreignPartsOnly = "1",
                    releaseName = "WEB-DL forced",
                ),
                OpenSubtitlesResult(
                    subtitleFileId = "commentary",
                    subDownloadLink = "https://dl.opensubtitles.org/commentary.gz",
                    subFormat = "srt",
                    fileName = "movie.en.commentary.srt",
                ),
                OpenSubtitlesResult(
                    subtitleFileId = "full",
                    subDownloadLink = "https://dl.opensubtitles.org/full.gz",
                    subFormat = "srt",
                    releaseName = "WEB-DL",
                ),
            ),
        )

        assertEquals(listOf("full"), ranked.map { it.subtitleFileId })
    }
}
