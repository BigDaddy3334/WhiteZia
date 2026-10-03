package shop.whitezia.client.ui.connection

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import shop.whitezia.client.account.WhiteZiaAccountViewModel
import shop.whitezia.client.detectActiveSimOperator
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.ui.WhiteZiaViewModel
import shop.whitezia.client.vpn.physicalInternetNetwork

internal class WhiteZiaConnectionUiActions(private val viewModel: WhiteZiaViewModel) : ConnectionUiActions {
    override val settings get() = viewModel.uiState.settings
    override val connectionStatus get() = viewModel.uiState.connectionStatus
    override fun prepareSubscription(link: String, transportMode: String, connectionMode: String) =
        viewModel.prepareSubscriptionConnection(link, settings.operatorCode, transportMode, connectionMode)
    override fun importSubscription(link: String) = viewModel.updateSubscriptionLink(link)
    override fun resetConnectionLog() = viewModel.resetConnectionLog("New connection request")
    override fun log(message: String) = viewModel.appendConnectionLog(message)
    override fun applyCachedResolvers() = viewModel.applyCachedResolversForOperator(settings.operatorCode)
    override suspend fun discoverResolvers() = viewModel.discoverAndApplyDnsResolvers(::log)
    override fun beginConnection() = viewModel.beginConnection()
    override fun disconnect() = viewModel.disconnect()
    override suspend fun awaitRuntimeStop() = viewModel.awaitRuntimeStopCompletion()
    override fun updateSettings(settings: WhiteZiaSettings) = viewModel.updateSettings(settings)
    override fun clearSubscription() = viewModel.clearSubscriptionProfile()
    override fun refreshOperator() {
        val context = viewModel.getApplication<Application>().applicationContext
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.physicalInternetNetwork()
        val wifi = network?.let(connectivity::getNetworkCapabilities)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val operator = detectActiveSimOperator(context, preferNetworkOperator = !wifi).operatorCode ?: return
        if (operator != settings.operatorCode) viewModel.updateOperatorCode(operator)
    }
}

internal class WhiteZiaConnectionAccount(private val viewModel: WhiteZiaAccountViewModel) : ConnectionAccount {
    override suspend fun refreshProfile() = viewModel.refreshManagedProfileBeforeConnection()
    override fun profileApplied(bundle: String) = viewModel.profileBundleApplied(bundle)
    override fun profileRejected(message: String) = viewModel.profileBundleRejected(message)
}
