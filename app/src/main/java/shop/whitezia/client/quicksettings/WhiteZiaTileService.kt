package shop.whitezia.client.quicksettings

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import shop.whitezia.client.MainActivity
import shop.whitezia.client.proxy.WhiteZiaProxyService
import shop.whitezia.client.runtime.WhiteZiaRuntimeState
import shop.whitezia.client.runtime.WhiteZiaRuntimeStateStore
import shop.whitezia.client.vpn.WhiteZiaVpnService

class WhiteZiaTileService : TileService() {

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
            updateTile(subtitle = "Отключение", state = Tile.STATE_INACTIVE)
            return
        }

        // Use the same profile refresh, permissions and fallback chain as the Connect button.
        openApp()
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
            WhiteZiaRuntimeStateStore.ModeVpn -> "VPN активен"
            WhiteZiaRuntimeStateStore.ModeProxy -> "Прокси активен"
            else -> "отключено"
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
            action = MainActivity.ActionConnectFromTile
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

}
