package shop.whitezia.client.account

import android.annotation.SuppressLint

import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.MessageDigest
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class SecureAccountStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun saveRefreshToken(token: String) {
        if (token.isBlank()) {
            clearRefreshToken()
            return
        }
        val cipher = Cipher.getInstance(AES_TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateEncryptionKey())
        }
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString(KeyRefreshToken, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KeyRefreshTokenIv, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    @Synchronized
    fun refreshToken(): String? {
        val encryptedRaw = preferences.getString(KeyRefreshToken, null) ?: return null
        val ivRaw = preferences.getString(KeyRefreshTokenIv, null) ?: return null
        return runCatching {
            val encrypted = Base64.decode(encryptedRaw, Base64.NO_WRAP)
            val iv = Base64.decode(ivRaw, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(AES_TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, getOrCreateEncryptionKey(), GCMParameterSpec(128, iv))
            }
            cipher.doFinal(encrypted).toString(Charsets.UTF_8).takeIf(String::isNotBlank)
        }.getOrElse {
            clearRefreshToken()
            null
        }
    }

    fun hasRefreshToken(): Boolean = refreshToken() != null

    @Synchronized
    fun clearRefreshToken() {
        preferences.edit()
            .remove(KeyRefreshToken)
            .remove(KeyRefreshTokenIv)
            .apply()
    }

    fun hasManagedProfile(): Boolean = preferences.getBoolean(KeyManagedProfile, false)

    fun shouldApplyManagedProfile(bundle: String): Boolean {
        if (bundle.isBlank()) return false
        return preferences.getString(KeyManagedProfileFingerprint, null) != bundleFingerprint(bundle)
    }

    fun markManagedProfileInstalled(bundle: String) {
        if (bundle.isBlank()) return
        preferences.edit()
            .putBoolean(KeyManagedProfile, true)
            .putString(KeyManagedProfileFingerprint, bundleFingerprint(bundle))
            .apply()
    }

    fun clearManagedProfile() {
        preferences.edit()
            .remove(KeyManagedProfile)
            .remove(KeyManagedProfileFingerprint)
            .apply()
    }

    @Synchronized
    fun installationId(): String {
        preferences.getString(KeyInstallationId, null)?.takeIf(String::isNotBlank)?.let { return it }
        val generatedId = derivedStableInstallationId() ?: UUID.randomUUID().toString()
        return generatedId.also {
            preferences.edit().putString(KeyInstallationId, it).apply()
        }
    }

    fun stableInstallationId(): String = derivedStableInstallationId() ?: installationId()

    @Synchronized
    fun promoteStableInstallationId() {
        val stableId = derivedStableInstallationId() ?: return
        preferences.edit().putString(KeyInstallationId, stableId).apply()
    }

    @Synchronized
    fun devicePublicKey(): String {
        val keyStore = keyStore()
        val certificate = keyStore.getCertificate(DeviceKeyAlias) ?: run {
            val generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                AndroidKeyStore,
            )
            generator.initialize(
                KeyGenParameterSpec.Builder(
                    DeviceKeyAlias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build(),
            )
            generator.generateKeyPair()
            keyStore().getCertificate(DeviceKeyAlias)
        }
        return Base64.encodeToString(certificate.publicKey.encoded, Base64.NO_WRAP)
    }

    private fun getOrCreateEncryptionKey(): SecretKey {
        val keyStore = keyStore()
        (keyStore.getKey(EncryptionKeyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore).run {
            init(
                KeyGenParameterSpec.Builder(
                    EncryptionKeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }

    @SuppressLint("HardwareIds")
    private fun derivedStableInstallationId(): String? {
        // ANDROID_ID is app-signing-key and user scoped on Android 8+, which is
        // exactly the reinstall-stable identity required for device recovery.
        val androidId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        return deriveStableInstallationId(androidId)
    }

    private companion object {
        const val PreferencesName = "whitezia-account"
        const val KeyRefreshToken = "refresh_token"
        const val KeyRefreshTokenIv = "refresh_token_iv"
        const val KeyInstallationId = "installation_id"
        const val KeyManagedProfile = "managed_profile_installed"
        const val KeyManagedProfileFingerprint = "managed_profile_fingerprint"
        const val EncryptionKeyAlias = "whitezia-account-session-v1"
        const val DeviceKeyAlias = "whitezia-account-device-v1"
        const val AndroidKeyStore = "AndroidKeyStore"
        const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

internal fun bundleFingerprint(bundle: String): String = MessageDigest.getInstance("SHA-256")
    .digest(bundle.toByteArray(Charsets.UTF_8))
    .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

internal fun deriveStableInstallationId(androidId: String?): String? {
    val normalized = androidId?.trim()?.lowercase().orEmpty()
    if (normalized.isBlank() || normalized == KnownBrokenAndroidId) {
        return null
    }
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("whitezia-device-v2:$normalized".toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return "android-v2-$digest"
}

private const val KnownBrokenAndroidId = "9774d56d682e549c"
