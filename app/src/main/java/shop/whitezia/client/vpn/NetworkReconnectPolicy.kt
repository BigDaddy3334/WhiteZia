package shop.whitezia.client.vpn

/** Tracks physical network identity, not the VPN or just its transport label. */
internal class NetworkReconnectPolicy {
    private var lastNetworkId: String? = null
    private var networkLost = false
    private var lastValidation: Boolean? = null

    @Synchronized
    fun reset(networkId: String?, validated: Boolean? = null) {
        lastNetworkId = networkId
        networkLost = false
        lastValidation = validated
    }

    @Synchronized
    fun networkLost(networkId: String) {
        if (networkId == lastNetworkId) networkLost = true
    }

    @Synchronized
    fun capabilitiesChanged(networkId: String, usable: Boolean) {
        if (networkId != lastNetworkId) return
        if (lastValidation == true && !usable) networkLost = true
        lastValidation = usable
    }

    @Synchronized
    fun shouldReconnect(networkId: String?, usable: Boolean = true): Boolean {
        if (networkId == null || !usable) {
            networkLost = true
            return false
        }
        val changed = networkLost || networkId != lastNetworkId
        if (networkId != lastNetworkId) lastValidation = null
        lastNetworkId = networkId
        networkLost = false
        return changed
    }
}

internal class NetworkReconnectBackoff {
    private var nextDelayMillis = 3_000L

    @Synchronized
    fun reset() {
        nextDelayMillis = 3_000L
    }

    @Synchronized
    fun afterFailureMillis(): Long = nextDelayMillis.also {
        nextDelayMillis = (it * 2).coerceAtMost(30_000L)
    }
}
