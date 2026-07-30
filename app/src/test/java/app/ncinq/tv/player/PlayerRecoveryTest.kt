package app.ncinq.tv.player

import androidx.media3.common.PlaybackException
import app.ncinq.tv.data.MediaType
import app.ncinq.tv.data.PlaybackRequest
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRecoveryTest {
    @Test
    fun `transient network and server failures are recoverable`() {
        assertTrue(isRecoverableSourceErrorCode(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertTrue(isRecoverableSourceErrorCode(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 503))
    }

    @Test
    fun `decoder failures are not retried as network failures`() {
        assertFalse(isRecoverableSourceErrorCode(PlaybackException.ERROR_CODE_DECODING_FAILED))
    }

    @Test
    fun `progress and time formatting stay bounded`() {
        assertEquals(0f, playbackFraction(10_000, 0), 0f)
        assertEquals(1f, playbackFraction(120_000, 60_000), 0f)
        assertEquals("05:32", formatTime(332_000))
        assertEquals("1:38:51", formatTime(5_931_000))
    }

    @Test
    fun `runtime validation catches a short mismatched episode`() {
        assertTrue(hasRuntimeMismatch(68, 24L * 60_000L))
        assertFalse(hasRuntimeMismatch(68, 67L * 60_000L))
        assertFalse(hasRuntimeMismatch(null, 24L * 60_000L))
    }

    @Test
    fun `resolver HTTP failures offer explicit alternate server recovery`() {
        val actions = recoveryActionsFor(PlaybackFailureKind.RESOLVER)

        assertTrue(PlaybackRecoveryAction.ALTERNATE_SERVER in actions)
        assertTrue(PlaybackRecoveryAction.RETRY in actions)
        assertEquals(
            "Direct server returned HTTP 500. Try again or use alternate server.",
            resolverFailureMessage(500, "HTTP 500"),
        )
    }

    @Test
    fun `transient resolver failures retry direct instead of selecting embeds`() {
        assertTrue(IllegalStateException("Direct stream resolution timed out").isRecoverableResolverFailure(null))
        assertTrue(IllegalStateException("temporary").isRecoverableResolverFailure(502))
        assertFalse(IllegalArgumentException("No source exists").isRecoverableResolverFailure(null))
    }

    @Test
    fun `alternate web player state is decoded for native TV controls`() {
        assertEquals(
            AlternatePlaybackState(
                positionMs = 12_500L,
                durationMs = 7_091_200L,
                isPlaying = true,
                ready = true,
                ended = false,
                captionsAvailable = true,
                captionsEnabled = false,
                playable = true,
                failed = false,
            ),
            parseAlternatePlaybackState("\"12500|7091200|1|4|0|1|0|1|0\""),
        )
        assertEquals(
            AlternatePlaybackState(
                positionMs = 0L,
                durationMs = 0L,
                isPlaying = false,
                ready = false,
                ended = false,
                captionsAvailable = false,
                captionsEnabled = false,
                playable = false,
                failed = false,
            ),
            parseAlternatePlaybackState("\"0|0|0|0|0|0|0|0|0\""),
        )
        assertTrue(parseAlternatePlaybackState("\"0|0|0|0|0|0|0|0|1\"")?.failed == true)
        assertNull(parseAlternatePlaybackState("null"))
        assertNull(parseAlternatePlaybackState("\"not-a-player-state\""))
    }

    @Test
    fun `alternate playback has five independent auto failover servers`() {
        val sources = PlaybackRequest(
            mediaId = 390043,
            mediaType = MediaType.MOVIE,
            title = "The Hitman's Bodyguard",
        ).alternateStreamSources()

        assertEquals(5, sources.size)
        assertEquals("vidsrc", sources.first().id)
        assertTrue(sources.first().url.contains("/movie/390043"))
        assertEquals(
            sources.size,
            sources.map { URI(it.url).host }.distinct().size,
        )
    }

    @Test
    fun `alternate TV server URLs preserve season and episode`() {
        val sources = PlaybackRequest(
            mediaId = 1399,
            mediaType = MediaType.TV,
            title = "Example",
            season = 4,
            episode = 7,
        ).alternateStreamSources()

        assertTrue(sources.all { it.url.contains("/1399/4/7") })
    }
}
