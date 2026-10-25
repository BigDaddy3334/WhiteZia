package shop.whitezia.client.quicksettings

import org.junit.Assert.*
import org.junit.Test

class TileConnectionRequestsTest {
    @Test fun rejectsExternalAndReplayedRequests() {
        val issued = TileConnectionRequests.issue()
        assertFalse(TileConnectionRequests.consume(null))
        assertFalse(TileConnectionRequests.consume("external-request"))
        assertTrue(TileConnectionRequests.consume(issued))
        assertFalse(TileConnectionRequests.consume(issued))
    }

    @Test fun supersededRequestCannotConsumeNewestConnection() {
        val previous = TileConnectionRequests.issue()
        val latest = TileConnectionRequests.issue()
        assertFalse(TileConnectionRequests.consume(previous))
        assertTrue(TileConnectionRequests.consume(latest))
    }
}
