package shop.whitezia.client.vpn

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

internal fun Context.openVpnBackgroundPermission() {
    val packageUri = Uri.parse("package:$packageName")
    val allowed = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    val request = Intent(
        if (allowed) Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        else Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        packageUri,
    )
    runCatching { startActivity(request) }.getOrElse {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
    }
}
