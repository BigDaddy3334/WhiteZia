package shop.whitezia.client.storm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import shop.whitezia.client.runtime.parseStormDnsConnectionProgressLine
import shop.whitezia.client.runtime.parseStormDnsResolverStateLine
import shop.whitezia.client.runtime.parseStormDnsTrafficStatsLine
import shop.whitezia.client.scan.StormDnsScanTelemetry
import shop.whitezia.client.scan.parseStormDnsScanLine

/** Fixtures follow Normal emitters at CottenDnsEngineContract.SourceRevision. */
class CottenDnsTelemetryCompatibilityTest {
    @Test fun normalProgressAndIpv6ResolverListsRemainReadable() {
        val progress = requireNotNull(parseStormDnsConnectionProgressLine(
            "WD_PROGRESS phase=mtu percent=45 completed=5 total=10 valid=3 rejected=2",
        ))
        assertEquals("mtu", progress.phase)
        assertEquals(45, progress.percent)
        assertEquals(5, progress.completed)
        val state = requireNotNull(parseStormDnsResolverStateLine(
            "WD_RESOLVERS active=1.1.1.1:53 standby=[2606:4700:4700::1111]:53 valid=1.1.1.1:53,[2606:4700:4700::1111]:53",
        ))
        assertEquals(listOf("1.1.1.1:53"), state.activeResolvers)
        assertEquals(listOf("[2606:4700:4700::1111]:53"), state.standbyResolvers)
        assertEquals(2, state.validResolvers.size)
    }

    @Test fun normalScanRetainsResultsAndIgnoresExtraPathEvents() {
        assertNull(parseStormDnsScanLine("WD_SCAN event=started total=2"))
        assertNull(parseStormDnsScanLine("WD_SCAN event=testing resolver=1.1.1.1:53 transport=udp"))
        assertEquals(StormDnsScanTelemetry.Valid("1.1.1.1:53"), parseStormDnsScanLine("WD_SCAN event=valid resolver=1.1.1.1:53"))
        assertNull(parseStormDnsScanLine("WD_SCAN event=path resolver=1.1.1.1:53 transport=auto usable=true"))
        assertEquals(StormDnsScanTelemetry.Rejected("8.8.8.8:53"), parseStormDnsScanLine("WD_SCAN event=rejected resolver=8.8.8.8:53"))
        assertEquals(StormDnsScanTelemetry.Complete(2, 1, 1), parseStormDnsScanLine("WD_SCAN event=complete total=2 valid=1 rejected=1"))
    }

    @Test fun extendedNormalTrafficStatsDoNotBreakExistingByteAccounting() {
        val stats = requireNotNull(parseStormDnsTrafficStatsLine(
            "📊 ↑ 2.00 KB/s (Total: 3.00 MB) | ↓ 4.00 KB/s (Total: 5.00 MB) | loss 1.2% | resolvers 3 | transport TCP/53 | queues 0/0/0 | drops rx=0 tx=0 | recoveries 1 | stream-fail dial=0 write=0",
        ))
        assertEquals(2048L, stats.uploadSpeedBytesPerSecond)
        assertEquals(4096L, stats.downloadSpeedBytesPerSecond)
        assertEquals(3L * 1024 * 1024, stats.uploadBytes)
        assertEquals(5L * 1024 * 1024, stats.downloadBytes)
    }
}
