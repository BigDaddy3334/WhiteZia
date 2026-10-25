package shop.whitezia.client.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import shop.whitezia.client.detectActiveSimOperator
import shop.whitezia.client.resolver.VerifiedMobileResolverCatalog
import shop.whitezia.client.controlplane.readUtf8Limited

/** DNS and carrier identity from the same selected physical network, including while a VPN is up. */
internal data class AutomaticResolverNetwork(val dnsResolvers: List<String>, val cellularOperator: String? = null)

internal fun automaticResolverNetwork(context: Context): AutomaticResolverNetwork {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
        ?: return AutomaticResolverNetwork(emptyList())
    val network = connectivity.physicalInternetNetwork() ?: return AutomaticResolverNetwork(emptyList())
    val capabilities = connectivity.getNetworkCapabilities(network)
    val cellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    val wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    return AutomaticResolverNetwork(
        dnsResolvers = connectivity.getLinkProperties(network)?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty(),
        cellularOperator = mobileResolverCatalogOperator(cellular, wifi, registeredMobileNetwork(context),
            if (cellular && !wifi) detectActiveSimOperator(context, preferNetworkOperator = true).operatorCode else null),
    )
}

internal fun mobileResolverCatalogOperator(cellular: Boolean, wifi: Boolean, registeredMccMnc: String, detectedOperator: String?): String? =
    detectedOperator.takeIf {
        cellular && !wifi && registeredMccMnc.length in 5..6 && registeredMccMnc.all { digit -> digit in '0'..'9' } &&
            registeredMccMnc.startsWith("250")
    }

private fun registeredMobileNetwork(context: Context): String = runCatching {
    val telephony = context.getSystemService(TelephonyManager::class.java) ?: return@runCatching ""
    val subscription = SubscriptionManager.getDefaultDataSubscriptionId()
    val dataTelephony = if (subscription != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
        telephony.createForSubscriptionId(subscription)
    } else telephony
    dataTelephony.networkOperator.orEmpty()
}.getOrDefault("")

internal fun bundledMobileResolverCatalog(context: Context): VerifiedMobileResolverCatalog = runCatching {
    context.assets.open(VerifiedMobileResolverCatalog.AssetName).use {
        VerifiedMobileResolverCatalog.parse(it.readUtf8Limited(VerifiedMobileResolverCatalog.MaxBytes), System.currentTimeMillis())
    }
}.getOrDefault(VerifiedMobileResolverCatalog.Empty)
