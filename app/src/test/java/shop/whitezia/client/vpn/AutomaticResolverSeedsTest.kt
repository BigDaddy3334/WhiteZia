package shop.whitezia.client.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticResolverSeedsTest {
    @Test fun selectedPhysicalCellularNetworkMayUseCatalogButWifiAndForeignRoamingNeverUseItsSimCatalog() {
        assertEquals("megafon_yota", mobileResolverCatalogOperator(true, false, "25011", "megafon_yota"))
        assertEquals("mts", mobileResolverCatalogOperator(true, false, "250001", "mts"))
        assertEquals(null, mobileResolverCatalogOperator(false, true, "25011", "megafon_yota"))
        assertEquals(null, mobileResolverCatalogOperator(true, true, "25011", "megafon_yota"))
        assertEquals(null, mobileResolverCatalogOperator(false, false, "25011", "megafon_yota"))
        for (network in listOf("", "250", "2501", "2501111", "310260", "26201", "2501x")) {
            assertEquals(network, null, mobileResolverCatalogOperator(true, false, network, "megafon_yota"))
        }
        assertEquals(null, mobileResolverCatalogOperator(true, false, "25011", null))
    }

    @Test fun verifiedOperatorPoolFollowsPhysicalAndPrecedesExistingPublicFallbackWithoutMixing() {
        val seeds = AutomaticResolverSeeds(listOf("10.20.0.53"), "")
        assertEquals(listOf(listOf("10.20.0.53"), listOf("176.59.127.146"), Fallback),
            seeds.pools(Fallback, listOf("176.59.127.146")))
    }

    @Test fun operatorCatalogCannotPromoteNetworkScopedAddressesOrDuplicatePhysicalAndFallbackPools() {
        val seeds = AutomaticResolverSeeds(listOf("176.59.127.146", "10.20.0.53"), "")
        assertEquals(listOf(listOf("176.59.127.146", "10.20.0.53"), listOf("176.59.127.147"), Fallback),
            seeds.pools(listOf("176.59.127.146", "176.59.127.147"),
                listOf("176.59.127.146", "176.59.127.147", "10.99.0.53", "100.64.1.1")))
    }

    @Test fun operatorPoolIsBoundedAndHonorsDisabledManualResolverExclusions() {
        val seeds = AutomaticResolverSeeds(emptyList(), "176.59.127.146")
        assertEquals(listOf("176.59.127.147", "176.59.127.148", "176.59.127.149", "176.59.127.150"),
            seeds.operatorPool((146..160).map { "176.59.127.$it" }))
    }

    @Test fun currentPhysicalDnsIsTriedBeforeSeparatePublicFallbackDespiteStalePrivateSeeds() {
        val physical = listOf("10.20.0.53", "10.20.0.54")
        val seeds = AutomaticResolverSeeds(physical, "")
        assertEquals(listOf(physical, Fallback), seeds.pools(listOf("10.99.0.53", "192.168.1.1", "100.64.1.1")))
    }

    @Test fun currentPhysicalPublicDnsRemainsPhysicalEvenWhenPreviouslyTypedAsDisabledCustom() {
        val seeds = AutomaticResolverSeeds(listOf("8.8.8.8"), "8.8.8.8")
        assertEquals(listOf(listOf("8.8.8.8"), Fallback), seeds.pools(listOf("8.8.8.8")))
        assertTrue(seeds.eligibleAutomatic("8.8.8.8:53"))
        assertFalse(seeds.eligiblePublic("8.8.8.8:53"))
    }

    @Test fun disabledCustomGlobalEndpointIsExcludedFromPublicPoolUnlessItIsCurrentPhysicalDns() {
        val seeds = AutomaticResolverSeeds(listOf("10.20.0.53"), "9.9.9.9")
        assertFalse(seeds.eligibleAutomatic("9.9.9.9"))
        assertFalse(seeds.eligiblePublic("9.9.9.9"))
        assertEquals(listOf("1.1.1.1", "77.88.8.8", "77.88.8.1"), seeds.publicPool(listOf("9.9.9.9", "1.1.1.1")))
    }

    @Test fun staleNetworkScopedIpv4AndIpv6CannotSurviveFromSettingsOrCache() {
        val seeds = AutomaticResolverSeeds(emptyList(), "")
        listOf("10.0.0.53", "172.16.0.53", "172.31.255.254", "192.168.1.53",
            "100.64.0.53", "100.127.255.254", "169.254.1.53", "fc00::53", "fdff::53", "fe80::53")
            .forEach {
                assertFalse("stale network-scoped endpoint must be filtered: $it", seeds.eligibleAutomatic(it))
                assertFalse("public fallback must never include network-scoped endpoint: $it", seeds.eligiblePublic(it))
            }
        assertEquals(listOf(Fallback), seeds.pools(listOf("10.0.0.53", "100.64.0.53", "fd00::53")))
    }

    @Test fun currentNetworkScopedAddressIsAllowedOnlyWithItsExactEndpointAndNeverPublic() {
        val seeds = AutomaticResolverSeeds(listOf("10.20.0.53:53", "[fd00::53]:5353"), "")
        assertTrue(seeds.eligibleAutomatic("10.20.0.53"))
        assertFalse(seeds.eligibleAutomatic("10.20.0.53:5353"))
        assertTrue(seeds.eligibleAutomatic("[fd00:0:0:0:0:0:0:53]:5353"))
        assertFalse(seeds.eligibleAutomatic("fd00::53"))
        assertFalse(seeds.eligiblePublic("10.20.0.53"))
        assertFalse(seeds.eligiblePublic("[fd00::53]:5353"))
        assertEquals(Fallback, seeds.publicPool(listOf("10.20.0.53", "[fd00::53]:5353")))
    }

    @Test fun physicalDefaultPortsAndPreferredDuplicatesAreCanonicalizedWithoutMixing() {
        val seeds = AutomaticResolverSeeds(listOf("10.20.0.53:53", "10.20.0.53"), "")
        assertEquals(listOf("10.20.0.53"), seeds.physical)
        assertEquals(listOf(listOf("10.20.0.53"), listOf("1.1.1.1", "77.88.8.8", "77.88.8.1")),
            seeds.pools(listOf("10.20.0.53:53", "1.1.1.1:53", "1.1.1.1")))
    }

    @Test fun theTwoIndependentPoolsStayBoundedWithoutDisplacingPhysicalDns() {
        val physical = listOf("10.20.0.51", "10.20.0.52", "10.20.0.53", "10.20.0.54", "10.20.0.55")
        val seeds = AutomaticResolverSeeds(physical, "")
        val pools = seeds.pools(listOf("1.1.1.1", "8.8.8.8", "9.9.9.9", "208.67.222.222", "1.0.0.1"))
        assertEquals(2, pools.size)
        assertEquals(physical.take(4), pools[0])
        assertEquals(listOf("1.1.1.1", "8.8.8.8", "9.9.9.9", "208.67.222.222", "77.88.8.8", "77.88.8.1"), pools[1])
        assertTrue(pools.all { it.size <= 6 })
        assertTrue(pools[0].none { it in pools[1] })
    }

    @Test fun yandexPreferenceCannotReplaceOrMixWithCurrentPhysicalDns() {
        val physical = listOf("10.20.0.53", "10.20.0.54")
        assertEquals(listOf(physical, Fallback), AutomaticResolverSeeds(physical, "").pools(Fallback))
    }

    @Test fun noPhysicalAndOnlyYandexPreferredRetainsOneCompleteExistingFallbackPool() {
        val seeds = AutomaticResolverSeeds(emptyList(), Fallback.joinToString("\n"))
        assertEquals(Fallback, seeds.publicPool(Fallback))
        assertEquals(listOf(Fallback), seeds.pools(Fallback))
    }

    @Test fun unusablePhysicalTargetsDoNotEnterEitherAutomaticPool() {
        val seeds = AutomaticResolverSeeds(listOf("invalid", "0.0.0.0", "127.0.0.1", "224.0.0.1", "::", "::1", "ff02::1"), "")
        assertTrue(seeds.physical.isEmpty())
        assertEquals(listOf(Fallback), seeds.pools(emptyList()))
    }

    @Test fun globallyRoutablePreferredAddressesRemainEligibleOutsidePhysicalPool() {
        val seeds = AutomaticResolverSeeds(emptyList(), "")
        listOf("1.1.1.1", "8.8.8.8", "176.59.127.146", "2606:4700:4700::1111")
            .forEach {
                assertTrue("global endpoint should remain eligible: $it", seeds.eligibleAutomatic(it))
                assertTrue("global endpoint should remain a public candidate: $it", seeds.eligiblePublic(it))
            }
    }

    @Test fun automaticSeedsRejectCidrExpansionWhileExplicitCustomRemainsSeparate() {
        val seeds = AutomaticResolverSeeds(emptyList(), "")
        assertFalse(seeds.eligibleAutomatic("8.8.0.0/16"))
        assertFalse(seeds.eligiblePublic("8.8.0.0/16"))
        assertEquals(listOf(Fallback), seeds.pools(listOf("8.8.0.0/16")))
    }

    @Test fun knownYandexPhysicalDnsCannotReappearInTheIndependentPublicPool() {
        val physical = listOf("77.88.8.8", "77.88.8.1")
        val seeds = AutomaticResolverSeeds(physical, "")
        val pools = seeds.pools(Fallback + physical)
        assertEquals(2, pools.size)
        assertEquals(physical, pools[0])
        assertEquals(listOf("77.88.8.2", "77.88.8.3", "77.88.8.7", "77.88.8.88"), pools[1])
        assertTrue(pools[0].none { it in pools[1] })
    }

    @Test fun onePlanUsesOnePhysicalSnapshotRatherThanAMutableFutureNetwork() {
        val input = mutableListOf("10.20.0.53", "10.20.0.54")
        val seeds = AutomaticResolverSeeds(input, "")
        input.clear()
        input += "10.99.0.53"
        assertEquals(listOf(listOf("10.20.0.53", "10.20.0.54"), Fallback), seeds.pools(input))
        assertFalse(seeds.eligibleAutomatic("10.99.0.53"))
    }

    private companion object {
        val Fallback = listOf("77.88.8.8", "77.88.8.1", "77.88.8.2", "77.88.8.3", "77.88.8.7", "77.88.8.88")
    }
}
