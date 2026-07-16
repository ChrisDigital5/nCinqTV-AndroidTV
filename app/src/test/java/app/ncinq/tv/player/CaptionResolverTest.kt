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
}
