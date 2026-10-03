package shop.whitezia.client.vpn

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

internal fun ConnectivityManager.physicalInternetNetwork(): Network? {
    val physical = allNetworks.filter { network ->
        val caps = getNetworkCapabilities(network) ?: return@filter false
        !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    return activeNetwork?.takeIf { it in physical }
        ?: physical.firstOrNull { network ->
            getNetworkCapabilities(network)?.let { caps ->
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } == true
        }
        ?: physical.firstOrNull { network ->
            getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        }
        ?: physical.firstOrNull()
}
