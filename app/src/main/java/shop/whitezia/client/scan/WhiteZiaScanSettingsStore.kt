package shop.whitezia.client.scan

import android.content.Context
import shop.whitezia.client.model.WhiteZiaScanDefaults

class WhiteZiaScanSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun loadWorkerCount(): Int {
        return preferences
            .getInt(KeyWorkerCount, WhiteZiaScanDefaults.DefaultWorkerCount)
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
        const val PreferencesName = "whitezia_scan_settings"
        const val KeyWorkerCount = "worker_count"
        const val KeyConnectionProfileId = "connection_profile_id"
    }
}
