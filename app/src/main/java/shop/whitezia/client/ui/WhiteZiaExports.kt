package shop.whitezia.client.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.exportAllResolverProfilesText
import shop.whitezia.client.model.validateResolverText
import java.io.File
import androidx.core.content.FileProvider

internal fun shareProfileLink(
    context: Context,
    link: String,
    subject: String,
    chooserTitle: String,
) {
    shareTextExport(
        context = context,
        fileName = "whitezia-profile.txt",
        subject = subject,
        chooserTitle = chooserTitle,
        text = link,
    )
}

internal fun shareClientConfigToml(
    context: Context,
    toml: String,
    chooserTitle: String,
) {
    shareTextExport(
        context = context,
        fileName = "client_config.toml",
        subject = "client_config.toml",
        chooserTitle = chooserTitle,
        text = toml,
    )
}

internal fun shareAdvancedSettingsToml(
    context: Context,
    toml: String,
    chooserTitle: String,
) {
    shareTextExport(
        context = context,
        fileName = "advanced_settings.toml",
        subject = "advanced_settings.toml",
        chooserTitle = chooserTitle,
        text = toml,
    )
}

private fun shareTextExport(
    context: Context,
    fileName: String,
    subject: String,
    chooserTitle: String,
    text: String,
) {
    val exportFile = writeCacheExportFile(
        context = context,
        fileName = fileName,
        text = text,
    )
    shareCachedExportFile(
        context = context,
        exportFile = exportFile,
        subject = subject,
        chooserTitle = chooserTitle,
    )
}

internal fun shareCachedExportFile(
    context: Context,
    exportFile: File,
    subject: String,
    chooserTitle: String,
) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        exportFile,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

internal data class ResolverProfilesExportDialogState(
    val saving: Boolean = true,
    val resolverCount: Int = 0,
    val savedLocation: String = "",
    val shareFile: File? = null,
    val errorMessage: String? = null,
)

internal data class ResolverProfilesExportResult(
    val resolverCount: Int,
    val savedLocation: String,
    val shareFile: File,
)

internal data class DeviceExportFile(
    val fileName: String,
    val savedLocation: String,
)

internal fun writeResolverProfilesExport(
    context: Context,
    settings: WhiteZiaSettings,
): ResolverProfilesExportResult {
    val resolverText = settings.exportAllResolverProfilesText()
    val resolverCount = resolverText
        .lineSequence()
        .count { it.isNotBlank() }
    val savedFile = writeDeviceDownloadsExportFile(
        context = context,
        fileName = ResolverProfilesExportFileName,
        text = resolverText,
    )
    val shareFile = writeCacheExportFile(
        context = context,
        fileName = savedFile.fileName,
        text = resolverText,
    )
    return ResolverProfilesExportResult(
        resolverCount = resolverCount,
        savedLocation = savedFile.savedLocation,
        shareFile = shareFile,
    )
}

private fun writeCacheExportFile(
    context: Context,
    fileName: String,
    text: String,
): File {
    val exportDir = File(context.cacheDir, CacheExportDirectory).apply {
        mkdirs()
    }
    cleanOldCacheExports(exportDir)
    val safeName = safeExportFileName(fileName)
    val exportFile = File(exportDir, safeName)
    exportFile.writeText(text, Charsets.UTF_8)
    return exportFile
}

private fun writeDeviceDownloadsExportFile(
    context: Context,
    fileName: String,
    text: String,
): DeviceExportFile {
    val safeName = safeExportFileName(fileName)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safeName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Unable to create export file")
        runCatching {
            resolver.openOutputStream(uri)
                ?.bufferedWriter(Charsets.UTF_8)
                ?.use { writer -> writer.write(text) }
                ?: throw IllegalStateException("Unable to write export file")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }.getOrElse { error ->
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
        return DeviceExportFile(
            fileName = safeName,
            savedLocation = "${Environment.DIRECTORY_DOWNLOADS}/$safeName",
        )
    }

    val exportDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        ?: File(context.filesDir, Environment.DIRECTORY_DOWNLOADS)
    exportDir.mkdirs()
    val exportFile = File(exportDir, safeName)
    exportFile.writeText(text, Charsets.UTF_8)
    return DeviceExportFile(
        fileName = safeName,
        savedLocation = exportFile.absolutePath,
    )
}

private fun safeExportFileName(fileName: String): String {
    return fileName
        .replace(Regex("""[^A-Za-z0-9._-]"""), "_")
        .ifBlank { "whitezia-export.txt" }
}

private fun cleanOldCacheExports(exportDir: File) {
    val now = System.currentTimeMillis()
    exportDir.listFiles()?.forEach { file ->
        if (file.isFile && now - file.lastModified() > CacheExportMaxAgeMillis) {
            runCatching { file.delete() }
        }
    }
}

internal fun copyTextToClipboard(
    context: Context,
    label: String,
    text: String,
    sensitive: Boolean,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(label, text)
    if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = (clip.description.extras ?: android.os.PersistableBundle()).apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
}

internal fun readResolverTextFromUri(
    context: Context,
    uri: Uri,
    unableToOpenLabel: String,
    invalidResolverIpTemplate: String,
    noResolverEntriesLabel: String,
): Result<String> {
    return runCatching {
        val rawText = readTextFromUri(context, uri, unableToOpenLabel).getOrThrow()
        normalizeImportedResolverText(rawText, invalidResolverIpTemplate, noResolverEntriesLabel)
    }
}

internal fun readTextFromUri(
    context: Context,
    uri: Uri,
    errorMessage: String,
): Result<String> {
    return runCatching {
        context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { reader -> reader.readText() }
            ?: throw IllegalArgumentException(errorMessage)
    }
}

private fun normalizeImportedResolverText(
    rawText: String,
    invalidResolverIpTemplate: String,
    noResolverEntriesLabel: String,
): String {
    val validation = validateResolverText(rawText)
    if (validation.invalidEntries.isNotEmpty()) {
        throw IllegalArgumentException(invalidResolverIpTemplate.format(validation.invalidEntries.first()))
    }
    if (validation.normalizedText.isBlank()) {
        throw IllegalArgumentException(noResolverEntriesLabel)
    }
    return validation.normalizedText
}

internal fun resolverValidationMessage(
    name: String,
    resolverText: String,
    invalidEntries: List<String>,
    validResolverCount: Int,
    invalidResolverIpTemplate: String,
    enterValidResolverIpLabel: String,
    enterProfileNameToSaveLabel: String,
    validResolverSingularTemplate: String,
    validResolverPluralTemplate: String,
): String? {
    return when {
        resolverText.isBlank() -> null
        invalidEntries.isNotEmpty() -> invalidResolverIpTemplate.format(invalidEntries.first())
        validResolverCount == 0 -> enterValidResolverIpLabel
        name.isBlank() -> enterProfileNameToSaveLabel
        else -> {
            val template = if (validResolverCount == 1) validResolverSingularTemplate else validResolverPluralTemplate
            template.format(validResolverCount)
        }
    }
}

internal val ResolverImportMimeTypes = arrayOf(
    "text/*",
    "application/json",
    "application/octet-stream",
)

internal val SettingsImportMimeTypes = arrayOf(
    "text/*",
    "application/toml",
    "application/octet-stream",
)

internal const val QrBitmapSizePx = 768
internal const val WhiteZiaTelegramUrl = "https://t.me/whitezia"

internal data class DonationWallet(
    val label: String,
    val address: String,
)

internal val DonationWallets = listOf(
    DonationWallet(
        label = "USDT (TON / Jetton)",
        address = "UQCVUC-eZzxNkVVewFp9pz43JKd0XIc55KCdC5gbwxJKiqoL",
    ),
    DonationWallet(
        label = "USDT (TRC20 / TRON)",
        address = "TNvdayQydF8t8bNHMuBctxVdgiaWeNKhmR",
    ),
    DonationWallet(
        label = "USDT (ERC20 / Ethereum)",
        address = "0x87519c886F79d3935b9A45519f821519272D9967",
    ),
    DonationWallet(
        label = "USDT (SPL / Solana)",
        address = "7zKyVVnJRBEiw6vL6vnX1VKUTEkw5QvXu696QV5qLS94",
    ),
)

private const val CacheExportDirectory = "exports"
private const val CacheExportMaxAgeMillis = 60L * 60L * 1_000L
internal const val ResolverProfilesExportFileName = "client_resolvers.txt"
