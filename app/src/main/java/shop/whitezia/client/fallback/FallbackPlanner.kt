package shop.whitezia.client.fallback

data class FallbackNetworkState(
    val activeWifi: Boolean,
    val mobileAvailable: Boolean,
)

enum class FallbackPlanAction {
    StartXray,
    StartDns,
    WaitForMobileForXray,
    WaitForWifiOffForDns,
    FailNoXray,
}

object FallbackPlanner {
    fun planManualXrayOnly(network: FallbackNetworkState): FallbackPlanAction {
        return if (network.canStartXray) {
            FallbackPlanAction.StartXray
        } else {
            FallbackPlanAction.WaitForMobileForXray
        }
    }

    fun planAfterAmneziaUnavailable(
        hasXray: Boolean,
        network: FallbackNetworkState,
    ): FallbackPlanAction {
        return if (hasXray) {
            planXrayFallback(hasXray = true, network = network, allowDnsFallback = true)
        } else {
            planDnsFallback(network)
        }
    }

    fun planXrayFallback(
        hasXray: Boolean,
        network: FallbackNetworkState,
        allowDnsFallback: Boolean,
    ): FallbackPlanAction {
        if (!hasXray) {
            return if (allowDnsFallback) {
                planDnsFallback(network)
            } else {
                FallbackPlanAction.FailNoXray
            }
        }
        return if (network.canStartXray) {
            FallbackPlanAction.StartXray
        } else {
            FallbackPlanAction.WaitForMobileForXray
        }
    }

    fun planDnsFallback(network: FallbackNetworkState): FallbackPlanAction {
        return if (network.activeWifi) {
            FallbackPlanAction.WaitForWifiOffForDns
        } else {
            FallbackPlanAction.StartDns
        }
    }
}

private val FallbackNetworkState.canStartXray: Boolean
    get() = !activeWifi && mobileAvailable
