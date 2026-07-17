package app.ncinq.tv.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

}
