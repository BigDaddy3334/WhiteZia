package shop.whitedns.client.scan

import android.content.Context
import shop.whitedns.client.model.WhiteDnsScanDefaults

class WhiteDnsScanSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun loadWorkerCount(): Int {
        return preferences
            .getInt(KeyWorkerCount, WhiteDnsScanDefaults.DefaultWorkerCount)
            .coerceAtLeast(1)
    }

    fun saveWorkerCount(workerCount: Int) {
        preferences.edit()
            .putInt(KeyWorkerCount, workerCount.coerceAtLeast(1))
            .apply()
    }

    fun loadConnectionProfileId(): String {
        return preferences.getString(KeyConnectionProfileId, null).orEmpty()
    }

    fun saveConnectionProfileId(profileId: String) {
        preferences.edit()
            .putString(KeyConnectionProfileId, profileId)
            .apply()
    }

    private companion object {
        const val PreferencesName = "white_dns_scan_settings"
        const val KeyWorkerCount = "worker_count"
        const val KeyConnectionProfileId = "connection_profile_id"
    }
}
