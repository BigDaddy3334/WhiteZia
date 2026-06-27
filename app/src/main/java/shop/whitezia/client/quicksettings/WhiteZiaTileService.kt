package shop.whitezia.client.quicksettings

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.app.NotificationManagerCompat
import java.util.UUID
import shop.whitezia.client.MainActivity
import shop.whitezia.client.model.StormDnsServerProfile
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.WhiteZiaSettingsStore
import shop.whitezia.client.model.resolve
import shop.whitezia.client.model.runtimeConnectionSettings
import shop.whitezia.client.model.selectedConnectionProfile
import shop.whitezia.client.proxy.WhiteZiaProxyService
import shop.whitezia.client.runtime.WhiteZiaRuntimeState
import shop.whitezia.client.runtime.WhiteZiaRuntimeStateStore
import shop.whitezia.client.vpn.WhiteZiaVpnService

class WhiteZiaTileService : TileService() {

    private val settingsStore by lazy {
        WhiteZiaSettingsStore(applicationContext)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val activeState = activeRuntimeState()
        if (activeState != null) {
            WhiteZiaVpnService.stop(applicationContext)
            WhiteZiaProxyService.stop(applicationContext)
            updateTile(subtitle = "Stopping", state = Tile.STATE_INACTIVE)
            return
        }

        val settings = settingsStore.load().runtimeConnectionSettings()
        val resolvedSettings = settings.resolve()
        val serverProfile = selectServerProfile(settings)
        if (resolvedSettings.resolverEntries.isEmpty() || serverProfile == null) {
            updateTile(subtitle = "Needs setup", state = Tile.STATE_UNAVAILABLE)
            openApp()
            return
        }
        val sessionId = UUID.randomUUID().toString()

        when (resolvedSettings.connectionMode) {
            WhiteZiaRuntimeStateStore.ModeVpn -> {
                if (
                    VpnService.prepare(this) != null ||
                    !NotificationManagerCompat.from(this).areNotificationsEnabled()
                ) {
                    updateTile(subtitle = "Needs permission", state = Tile.STATE_UNAVAILABLE)
                    openApp()
                    return
                }
                WhiteZiaVpnService.start(
                    context = applicationContext,
                    sessionId = sessionId,
                    serverProfile = serverProfile,
                    settings = settings,
                )
                updateTile(subtitle = "Starting VPN", state = Tile.STATE_ACTIVE)
            }
            else -> {
                WhiteZiaProxyService.start(
                    context = applicationContext,
                    sessionId = sessionId,
                    serverProfile = serverProfile,
                    settings = settings,
                )
                updateTile(subtitle = "Starting proxy", state = Tile.STATE_ACTIVE)
            }
        }
    }

    private fun activeRuntimeState(): WhiteZiaRuntimeState? {
        return WhiteZiaRuntimeStateStore.readAll(applicationContext)
            .firstOrNull { state ->
                state.status == WhiteZiaRuntimeStateStore.StatusReady ||
                    state.status == WhiteZiaRuntimeStateStore.StatusStarting
            }
    }

    private fun updateTile(
        subtitle: String? = null,
        state: Int? = null,
    ) {
        val tile = qsTile ?: return
        val activeState = activeRuntimeState()
        val resolvedState = state ?: if (activeState == null) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        val resolvedSubtitle = subtitle ?: when (activeState?.mode) {
            WhiteZiaRuntimeStateStore.ModeVpn -> "VPN active"
            WhiteZiaRuntimeStateStore.ModeProxy -> "Proxy active"
            else -> "Disconnected"
        }
        tile.label = "WhiteZia"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = resolvedSubtitle
        }
        tile.state = resolvedState
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            startActivityAndCollapseLegacy(intent)
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun startActivityAndCollapseLegacy(intent: Intent) {
        startActivityAndCollapse(intent)
    }

    private fun selectServerProfile(settings: WhiteZiaSettings): StormDnsServerProfile? {
        val connectionProfile = settings.selectedConnectionProfile()
        val domain = connectionProfile.customServerDomain
            .trim()
            .trimEnd('.')
        val encryptionKey = connectionProfile.customServerEncryptionKey.trim()
        if (domain.isBlank() || encryptionKey.isBlank()) {
            return null
        }
        return StormDnsServerProfile(
            id = "custom",
            label = "Custom StormDNS Server",
            domain = domain,
            encryptionKey = encryptionKey,
            encryptionMethod = connectionProfile.customServerEncryptionMethod.coerceIn(0, 5),
        )
    }
}
