package shop.whitezia.client.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StormDnsConnectionProgressTest {
    @Test
    fun parseStormDnsConnectionProgressLineParsesMtuProgress() {
        val state = parseStormDnsConnectionProgressLine(
            "2026 WD_PROGRESS phase=mtu percent=45 completed=27 total=54 valid=20 rejected=7",
        )

        requireNotNull(state)
        assertEquals("mtu", state.phase)
        assertEquals(45, state.percent)
        assertEquals(27, state.completed)
        assertEquals(54, state.total)
        assertEquals(20, state.valid)
        assertEquals(7, state.rejected)
        assertEquals("Scanning 27/54", state.label)
    }

    @Test
    fun parseStormDnsConnectionProgressLineInfersMtuPercentWhenMissing() {
        val state = parseStormDnsConnectionProgressLine(
            "WD_PROGRESS phase=mtu completed=27 total=54 valid=20 rejected=7",
        )

        requireNotNull(state)
        assertEquals(45, state.percent)
    }

    @Test
    fun parseStormDnsConnectionProgressLineIgnoresOtherLines() {
        assertNull(parseStormDnsConnectionProgressLine("not progress"))
    }
}
