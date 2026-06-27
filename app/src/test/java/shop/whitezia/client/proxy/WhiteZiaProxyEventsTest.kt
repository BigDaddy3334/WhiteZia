package shop.whitezia.client.proxy

import org.junit.Assert.assertEquals
import org.junit.Test

class WhiteZiaProxyEventsTest {
    @Test
    fun eventsCarrySessionId() {
        val receivedEvents = mutableListOf<WhiteZiaProxyEvent>()
        val listener: (WhiteZiaProxyEvent) -> Unit = { event ->
            receivedEvents += event
        }

        WhiteZiaProxyEvents.addListener(listener)
        try {
            WhiteZiaProxyEvents.log("session-a", "log")
            WhiteZiaProxyEvents.ready("session-a", "ready")
            WhiteZiaProxyEvents.failed("session-a", "failed")
        } finally {
            WhiteZiaProxyEvents.removeListener(listener)
        }

        assertEquals(
            listOf(
                WhiteZiaProxyEvent.Log("session-a", "log"),
                WhiteZiaProxyEvent.Ready("session-a", "ready"),
                WhiteZiaProxyEvent.Failed("session-a", "failed"),
            ),
            receivedEvents,
        )
    }
}
