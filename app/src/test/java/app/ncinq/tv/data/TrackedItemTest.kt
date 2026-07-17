package app.ncinq.tv.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackedItemTest {
    @Test
    fun `legacy planned items migrate to favorites`() {
        assertTrue(TrackedItem(1, MediaType.TV, "Show", status = TrackingStatus.PLANNED).isFavorite)
        assertFalse(TrackedItem(1, MediaType.TV, "Show", status = TrackingStatus.WATCHING).isFavorite)
        assertTrue(TrackedItem(1, MediaType.TV, "Show", favorite = true).isFavorite)
    }
}
