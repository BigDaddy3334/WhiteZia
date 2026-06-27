package shop.whitezia.client.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class WhiteZiaVpnEventsTest {
    @Test
    fun eventsCarrySessionId() {
        val receivedEvents = mutableListOf<WhiteZiaVpnEvent>()
        val listener: (WhiteZiaVpnEvent) -> Unit = { event ->
            receivedEvents += event
        }

        WhiteZiaVpnEvents.addListener(listener)
        try {
            WhiteZiaVpnEvents.log("session-b", "log")
            WhiteZiaVpnEvents.ready("session-b", "ready")
            WhiteZiaVpnEvents.failed("session-b", "failed")
        } finally {
            WhiteZiaVpnEvents.removeListener(listener)
        }

        assertEquals(
            listOf(
                WhiteZiaVpnEvent.Log("session-b", "log"),
                WhiteZiaVpnEvent.Ready("session-b", "ready"),
                WhiteZiaVpnEvent.Failed("session-b", "failed"),
            ),
            receivedEvents,
        )
    }
}
