package shop.whitezia.client.update

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import shop.whitezia.client.BuildConfig
import shop.whitezia.client.controlplane.ControlPlaneHttpStatusException
import shop.whitezia.client.controlplane.ControlPlaneTransport
import shop.whitezia.client.controlplane.readUtf8Limited

data class AppRelease(
    val applicationId: String,
    val channel: String,
    val versionCode: Int,
    val versionName: String,
    val minSupportedVersionCode: Int,
    val mandatory: Boolean,
    val apkUrl: String,
    val sha256: String,
    val certificateSha256: String,
    val sizeBytes: Long,
    val releaseNotes: List<String>,
) {
    val requiresUpdate: Boolean
        get() = mandatory || BuildConfig.VERSION_CODE < minSupportedVersionCode
}

internal data class AppUpdateTarget(
    val applicationId: String,
    val channel: String,
    val certificateSha256: String,
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class UpToDate(val versionName: String) : AppUpdateState
    data class Available(val release: AppRelease) : AppUpdateState
    data class Downloading(val release: AppRelease, val downloadedBytes: Long, val totalBytes: Long) : AppUpdateState
    data class ReadyToInstall(val release: AppRelease, val apk: File) : AppUpdateState
    data class Failed(val release: AppRelease?, val message: String) : AppUpdateState
}

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    var state: AppUpdateState by mutableStateOf<AppUpdateState>(AppUpdateState.Idle)
        private set

    private val preferences = application.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
    private val controlPlaneTransport = ControlPlaneTransport(application)
    private var downloadJob: Job? = null

    fun checkOnStartup() {
        if (state != AppUpdateState.Idle || !isUpdateConfigured()) return
        val now = System.currentTimeMillis()
        if (now - preferences.getLong(KeyLastCheck, 0L) < CheckIntervalMillis) return
        checkForUpdate(showResultFeedback = false)
    }

    fun checkForUpdate(showResultFeedback: Boolean = true) {
        if (state is AppUpdateState.Checking || state is AppUpdateState.Downloading) return
        if (!isUpdateConfigured()) {
            state = if (showResultFeedback) {
                AppUpdateState.Failed(null, "Обновления доступны только в основной версии приложения")
            } else {
                AppUpdateState.Idle
            }
            return
        }
        state = AppUpdateState.Checking
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { fetchRelease() } }
            result.onSuccess { release ->
                preferences.edit().putLong(KeyLastCheck, System.currentTimeMillis()).apply()
                val dismissedRecently =
                    preferences.getInt(KeyDismissedVersion, 0) == release.versionCode &&
                        System.currentTimeMillis() - preferences.getLong(KeyDismissedAt, 0L) < DismissIntervalMillis
                val shouldOfferUpdate = shouldOfferUpdate(
                    release = release,
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    dismissedRecently = dismissedRecently,
                    manualCheck = showResultFeedback,
                )
                state = if (shouldOfferUpdate) {
                    existingVerifiedAPK(release)?.let { AppUpdateState.ReadyToInstall(release, it) }
                        ?: AppUpdateState.Available(release)
                } else if (showResultFeedback) {
                    AppUpdateState.UpToDate(BuildConfig.VERSION_NAME)
                } else {
                    AppUpdateState.Idle
                }
            }.onFailure { error ->
                state = if (showResultFeedback) {
                    AppUpdateState.Failed(null, readableError(error))
                } else {
                    AppUpdateState.Idle
                }
            }
        }
    }

    fun download(release: AppRelease) {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            state = AppUpdateState.Downloading(release, 0L, release.sizeBytes)
            val result = withContext(Dispatchers.IO) { runCatching { downloadAndVerify(release) } }
            result.onSuccess { apk ->
                state = AppUpdateState.ReadyToInstall(release, apk)
            }.onFailure { error ->
                state = if (error is CancellationException) {
                    AppUpdateState.Available(release)
                } else {
                    AppUpdateState.Failed(release, readableError(error))
                }
            }
        }
    }

    fun cancelDownload() {
        val activeDownload = state as? AppUpdateState.Downloading
        downloadJob?.cancel()
        downloadJob = null
        if (activeDownload != null) {
            state = AppUpdateState.Available(activeDownload.release)
        }
    }

    fun retry() {
        when (val current = state) {
            is AppUpdateState.Failed -> current.release?.let(::download) ?: checkForUpdate()
            else -> Unit
        }
    }

    fun dismiss() {
        val release = when (val current = state) {
            is AppUpdateState.Available -> current.release
            is AppUpdateState.ReadyToInstall -> current.release
            is AppUpdateState.Failed -> current.release
            else -> null
        }
        if (release?.requiresUpdate == true) return
        if (release != null) {
            preferences.edit()
                .putInt(KeyDismissedVersion, release.versionCode)
                .putLong(KeyDismissedAt, System.currentTimeMillis())
                .apply()
        }
        state = AppUpdateState.Idle
    }

    fun installerOpened() {
        state = AppUpdateState.Idle
    }

    fun installerError(message: String) {
        val current = state as? AppUpdateState.ReadyToInstall
        state = AppUpdateState.Failed(current?.release, message)
    }

    private fun fetchRelease(): AppRelease {
        require(BuildConfig.UPDATE_METADATA_URL.isNotBlank()) { "URL обновлений не настроен" }
        return controlPlaneTransport.execute(BuildConfig.UPDATE_METADATA_URL) { response ->
            response.apply {
                connectTimeout = MetadataConnectTimeoutMillis
                readTimeout = MetadataReadTimeoutMillis
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "WhiteZia/${BuildConfig.VERSION_NAME}")
                setRequestProperty(UpdateApplicationIDHeader, BuildConfig.UPDATE_APPLICATION_ID)
                setRequestProperty(UpdateChannelHeader, BuildConfig.UPDATE_CHANNEL)
            }
            require(response.url.protocol.equals("https", ignoreCase = true)) {
                "Сервер обновлений перенаправил запрос на небезопасный адрес"
            }
            if (response.responseCode != HttpURLConnection.HTTP_OK) {
                throw ControlPlaneHttpStatusException(
                    response.responseCode,
                    "Сервер обновлений вернул HTTP ${response.responseCode}",
                )
            }
            val body = response.inputStream.use { it.readUtf8Limited(MaxMetadataResponseBytes) }
            parseAppRelease(JSONObject(body)).also {
                validateAppReleaseTarget(it, expectedReleaseTarget())
            }
        }
    }

    private suspend fun downloadAndVerify(release: AppRelease): File {
        val directory = File(getApplication<Application>().cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, "WhiteZia-${release.versionCode}.apk")
        val temporary = File(directory, "WhiteZia-${release.versionCode}.apk.part")
        temporary.delete()
        return controlPlaneTransport.executeSuspend(release.apkUrl) { response ->
            response.apply {
                connectTimeout = APKConnectTimeoutMillis
                readTimeout = APKReadTimeoutMillis
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.android.package-archive")
                setRequestProperty("User-Agent", "WhiteZia/${BuildConfig.VERSION_NAME}")
            }
            require(response.url.protocol.equals("https", ignoreCase = true)) {
                "Загрузка APK перенаправлена на небезопасный адрес"
            }
            if (response.responseCode != HttpURLConnection.HTTP_OK) {
                throw ControlPlaneHttpStatusException(
                    response.responseCode,
                    "Загрузка APK завершилась с HTTP ${response.responseCode}",
                )
            }
            val responseSize = response.contentLengthLong
            if (responseSize > 0 && responseSize != release.sizeBytes) error("Размер файла на сервере изменился")
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            var lastProgressAt = 0L
            try {
                response.inputStream.use { input ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            downloaded += count
                            require(downloaded <= MaxAPKSizeBytes) { "APK превышает допустимый размер" }
                            val now = System.currentTimeMillis()
                            if (now - lastProgressAt >= ProgressUpdateIntervalMillis) {
                                lastProgressAt = now
                                withContext(Dispatchers.Main) {
                                    state = AppUpdateState.Downloading(release, downloaded, release.sizeBytes)
                                }
                            }
                        }
                    }
                }
                require(downloaded == release.sizeBytes) { "APK загружен не полностью" }
                val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                require(actualHash.equals(release.sha256, ignoreCase = true)) {
                    "Проверка целостности APK не пройдена"
                }
                target.delete()
                require(temporary.renameTo(target)) { "Не удалось сохранить APK" }
                try {
                    verifyAPKArchive(target, release)
                } catch (error: Throwable) {
                    target.delete()
                    throw error
                }
                target
            } catch (error: Throwable) {
                temporary.delete()
                throw error
            }
        }
    }

    private fun existingVerifiedAPK(release: AppRelease): File? {
        val file = File(getApplication<Application>().cacheDir, "updates/WhiteZia-${release.versionCode}.apk")
        if (!file.isFile || file.length() != release.sizeBytes) return null
        if (!sha256(file).equals(release.sha256, ignoreCase = true)) return null
        return runCatching {
            verifyAPKArchive(file, release)
            file
        }.getOrElse {
            file.delete()
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyAPKArchive(file: File, release: AppRelease) {
        val packageManager = getApplication<Application>().packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val packageInfo = packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("APK не содержит корректный Android package")
        require(packageInfo.packageName == release.applicationId) {
            "APK предназначен для другого приложения"
        }
        val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
        require(archiveVersionCode == release.versionCode.toLong()) {
            "Версия APK не совпадает с метаданными"
        }
        require(packageInfo.versionName == release.versionName) {
            "Имя версии APK не совпадает с метаданными"
        }
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            packageInfo.signatures
        }
        require(signatures?.size == 1) { "APK содержит некорректную подпись" }
        val archiveCertificateSha256 = sha256(signatures.single().toByteArray())
        require(archiveCertificateSha256.equals(release.certificateSha256, ignoreCase = true)) {
            "Сертификат APK не совпадает с метаданными"
        }
        require(
            archiveCertificateSha256.equals(
                expectedReleaseTarget().certificateSha256,
                ignoreCase = true,
            ),
        ) {
            "APK подписан неизвестным сертификатом"
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    private fun expectedReleaseTarget(): AppUpdateTarget = AppUpdateTarget(
        applicationId = BuildConfig.UPDATE_APPLICATION_ID,
        channel = BuildConfig.UPDATE_CHANNEL,
        certificateSha256 = BuildConfig.UPDATE_CERTIFICATE_SHA256.lowercase(Locale.US),
    )

    private fun isUpdateConfigured(): Boolean {
        val target = expectedReleaseTarget()
        return BuildConfig.UPDATE_METADATA_URL.isNotBlank() &&
            target.applicationId.isNotBlank() &&
            target.channel.isNotBlank() &&
            target.certificateSha256.matches(AppUpdateSha256Regex)
    }

    private fun readableError(error: Throwable): String {
        return error.message?.takeIf(String::isNotBlank) ?: "Не удалось получить обновление"
    }

    companion object {
        private const val PreferencesName = "app-updates"
        private const val KeyLastCheck = "last_check"
        private const val KeyDismissedVersion = "dismissed_version"
        private const val KeyDismissedAt = "dismissed_at"
        private const val CheckIntervalMillis = 6L * 60L * 60L * 1_000L
        private const val DismissIntervalMillis = 24L * 60L * 60L * 1_000L
        private const val MetadataConnectTimeoutMillis = 4_000
        private const val MetadataReadTimeoutMillis = 6_000
        private const val MaxMetadataResponseBytes = 256 * 1024
        private const val APKConnectTimeoutMillis = 15_000
        private const val APKReadTimeoutMillis = 30_000
        private const val ProgressUpdateIntervalMillis = 250L
        private const val MaxAPKSizeBytes = AppUpdateMaxAPKSizeBytes
        private const val UpdateApplicationIDHeader = "X-WhiteZia-Application-Id"
        private const val UpdateChannelHeader = "X-WhiteZia-Update-Channel"
    }
}

internal fun parseAppRelease(json: JSONObject): AppRelease {
    val notes = json.optJSONArray("release_notes")
    val release = AppRelease(
        applicationId = json.getString("application_id").trim(),
        channel = json.getString("channel").trim().lowercase(Locale.US),
        versionCode = json.getInt("version_code"),
        versionName = json.getString("version_name").trim(),
        minSupportedVersionCode = json.optInt("min_supported_version_code", 0),
        mandatory = json.optBoolean("mandatory", false),
        apkUrl = json.getString("apk_url").trim(),
        sha256 = json.getString("sha256").trim().lowercase(Locale.US),
        certificateSha256 = json.getString("certificate_sha256").trim().lowercase(Locale.US),
        sizeBytes = json.getLong("size_bytes"),
        releaseNotes = if (notes == null) emptyList() else {
            List(notes.length()) { index -> notes.optString(index).trim() }.filter(String::isNotBlank)
        },
    )
    require(release.applicationId.matches(AppUpdateApplicationIdRegex)) { "Некорректный package ID" }
    require(release.channel.matches(AppUpdateChannelRegex)) { "Некорректный канал обновления" }
    require(release.versionCode > 0 && release.versionName.isNotBlank()) { "Некорректная версия обновления" }
    require(release.sha256.matches(AppUpdateSha256Regex)) { "Некорректная контрольная сумма" }
    require(release.certificateSha256.matches(AppUpdateSha256Regex)) { "Некорректный сертификат APK" }
    require(release.sizeBytes in 1..AppUpdateMaxAPKSizeBytes) { "Некорректный размер APK" }
    require(URI(release.apkUrl).scheme.equals("https", ignoreCase = true)) {
        "APK должен загружаться по HTTPS"
    }
    return release
}

internal fun validateAppReleaseTarget(release: AppRelease, target: AppUpdateTarget) {
    require(release.applicationId == target.applicationId) {
        "Обновление предназначено для другого приложения"
    }
    require(release.channel == target.channel) {
        "Обновление опубликовано в другом канале"
    }
    require(release.certificateSha256.equals(target.certificateSha256, ignoreCase = true)) {
        "Сертификат обновления не совпадает с ожидаемым"
    }
}

internal fun shouldOfferUpdate(
    release: AppRelease,
    currentVersionCode: Int,
    dismissedRecently: Boolean,
    manualCheck: Boolean,
): Boolean {
    return release.versionCode > currentVersionCode &&
        (!dismissedRecently || release.requiresUpdate || manualCheck)
}

private const val AppUpdateMaxAPKSizeBytes = 300L * 1024L * 1024L
private val AppUpdateSha256Regex = Regex("[0-9a-f]{64}")
private val AppUpdateApplicationIdRegex = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
private val AppUpdateChannelRegex = Regex("[a-z][a-z0-9_-]{0,31}")

object AppUpdateInstaller {
    fun canInstallPackages(context: Context): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    fun permissionIntent(context: Context): Intent {
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
    }

    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
