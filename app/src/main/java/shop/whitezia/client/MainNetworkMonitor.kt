package shop.whitezia.client

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class WifiStateSnapshot(
    val networkAvailable: Boolean,
    val radioEnabled: Boolean,
)

internal class MainNetworkMonitor(
    private val activity: ComponentActivity,
) : AutoCloseable {
    private val connectivityManager = activity.getSystemService(ConnectivityManager::class.java)
    private var wifiCallback: ConnectivityManager.NetworkCallback? = null
    private var wifiReceiver: BroadcastReceiver? = null
    private var baseTransportCallback: ConnectivityManager.NetworkCallback? = null

    fun observeWifiState(onChange: (WifiStateSnapshot) -> Unit) {
        var lastPublishedState: WifiStateSnapshot? = null
        fun publish(delayMillis: Long = 0L) {
            activity.lifecycleScope.launch {
                if (delayMillis > 0L) delay(delayMillis)
                val state = WifiStateSnapshot(
                    networkAvailable = isWifiNetworkAvailable(),
                    radioEnabled = isWifiRadioEnabled(),
                )
                if (lastPublishedState != state) {
                    lastPublishedState = state
                    onChange(state)
                }
            }
        }

        unregisterWifiObserver()
        publish()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publish(WifiStateSettleDelayMillis)
            override fun onLost(network: Network) = publish(WifiStateSettleDelayMillis)
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = publish()
        }
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build(),
            callback,
        )
        wifiCallback = callback

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = publish(WifiStateSettleDelayMillis)
        }
        val filter = IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)
        val registered = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                activity.registerReceiver(receiver, filter)
            }
        }.isSuccess
        if (registered) wifiReceiver = receiver
    }

    fun observeBaseNetworkTransport(onChange: (String) -> Unit) {
        var lastPublishedTransport: String? = null
        fun publish(delayMillis: Long = 0L) {
            activity.lifecycleScope.launch {
                if (delayMillis > 0L) delay(delayMillis)
                val transport = currentBaseNetworkTransport()
                if (lastPublishedTransport != transport) {
                    lastPublishedTransport = transport
                    onChange(transport)
                }
            }
        }

        unregisterBaseTransportObserver()
        publish()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publish(DefaultNetworkSettleDelayMillis)
            override fun onLost(network: Network) = publish(DefaultNetworkSettleDelayMillis)
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = publish()
        }
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            callback,
        )
        baseTransportCallback = callback
    }

    fun currentBaseNetworkTransport(): String = when {
        isWifiNetworkAvailable() -> NetworkTransportWifi
        isMobileNetworkAvailable() -> NetworkTransportMobile
        connectivityManager.allNetworks.any { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@any false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } -> NetworkTransportOther
        else -> NetworkTransportNone
    }

    fun isWifiNetworkAvailable(): Boolean = connectivityManager.allNetworks.any { network ->
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@any false
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun isWifiRadioEnabled(): Boolean {
        val wifiManager = activity.applicationContext.getSystemService(WifiManager::class.java)
        return wifiManager?.isWifiEnabled ?: isWifiNetworkAvailable()
    }

    fun isActiveWifiNetwork(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun isMobileNetworkAvailable(): Boolean = connectivityManager.allNetworks.any { network ->
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@any false
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun close() {
        unregisterWifiObserver()
        unregisterBaseTransportObserver()
    }

    private fun unregisterWifiObserver() {
        wifiCallback?.let { callback ->
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
        wifiCallback = null
        wifiReceiver?.let { receiver -> runCatching { activity.unregisterReceiver(receiver) } }
        wifiReceiver = null
    }

    private fun unregisterBaseTransportObserver() {
        baseTransportCallback?.let { callback ->
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
        baseTransportCallback = null
    }
}

internal const val NetworkTransportNone = "none"
internal const val NetworkTransportWifi = "wifi"
internal const val NetworkTransportMobile = "mobile"
internal const val NetworkTransportOther = "other"

private const val WifiStateSettleDelayMillis = 250L
private const val DefaultNetworkSettleDelayMillis = 600L
