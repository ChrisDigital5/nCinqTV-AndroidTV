package app.ncinq.tv

import app.ncinq.tv.data.MediaProxy
import app.ncinq.tv.data.StreamResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaProxyTest {
    @Test
    fun `signed stream URLs keep headers and proxy authorization`() {
        val url = MediaProxy.streamUrl(
            StreamResult(
                url = "https://rotating.example/video/master.m3u8",
                type = "hls",
                headers = mapOf("Referer" to "https://provider.example"),
                proxyToken = "signed-token",
            )
        )

        assertTrue(url.startsWith("https://tv.ncinq.app/api/cors-proxy?"))
        assertTrue(url.contains("signed-token"))
        assertTrue(url.contains("headers="))
    }

    @Test
    fun `unsigned streams do not invent authorization`() {
        val url = MediaProxy.streamUrl(StreamResult(url = "https://media.example/video.mp4", type = "mp4"))
        assertFalse(url.contains("token="))
    }

    @Test
    fun `srt captions ask the web proxy for vtt`() {
        val url = MediaProxy.captionUrl(app.ncinq.tv.data.Caption(url = "https://media.example/sub.srt", type = "srt"))
        assertTrue(url.contains("format=vtt"))
        assertEquals(1, "format=vtt".toRegex().findAll(url).count())
    }
}
