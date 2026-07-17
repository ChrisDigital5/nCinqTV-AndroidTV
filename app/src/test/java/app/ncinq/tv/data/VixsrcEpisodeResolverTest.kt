package app.ncinq.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VixsrcEpisodeResolverTest {
    @Test
    fun `extracts signed VixSrc playback config`() {
        val config = extractVixsrcPlaybackConfig(
            """window.masterPlaylist = { token: 'abc', expires: '1999999999', url: 'https://vixsrc.to/list.m3u8?b=1&amp;x=2' }""",
        )
        assertNotNull(config)
        assertEquals("abc", config?.token)
        assertEquals("https://vixsrc.to/list.m3u8?b=1&x=2", config?.playlist)
    }

    @Test
    fun `sums media playlist runtime`() {
        val manifest = """#EXTM3U
#EXTINF:10.5,
one.ts
#EXTINF:19.5,
two.ts"""
        assertEquals(0.5, hlsDurationMinutes(manifest), 0.001)
    }

    @Test
    fun `only overrides the known mismatched live action episode`() {
        val request = PlaybackRequest(
            mediaId = 82452,
            mediaType = MediaType.TV,
            title = "Avatar: The Last Airbender",
            season = 2,
            episode = 1,
        )
        assertTrue(request.requiresVerifiedVixsrcSource())
        assertFalse(request.copy(episode = 2).requiresVerifiedVixsrcSource())
    }
}
