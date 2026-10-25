package shop.whitezia.client.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import shop.whitezia.client.model.ResolverProfile
import shop.whitezia.client.model.WhiteZiaScanDefaults
import shop.whitezia.client.model.WhiteZiaScanState
import shop.whitezia.client.model.WhiteZiaScanStatus
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.WhiteZiaSettingsStore
import shop.whitezia.client.model.WhiteZiaUiState
import shop.whitezia.client.model.normalizedConnectionProfiles
import shop.whitezia.client.model.normalizedResolverProfiles
import shop.whitezia.client.model.selectedConnectionProfile
import shop.whitezia.client.model.syncSelectedConnectionProfileFields
import shop.whitezia.client.runtime.RuntimeLaunchRequestStore
import shop.whitezia.client.scan.WhiteZiaScanLaunchRequest
import shop.whitezia.client.scan.WhiteZiaScanRequestStore
import shop.whitezia.client.scan.WhiteZiaScanService
import shop.whitezia.client.scan.WhiteZiaScanSettingsStore
import shop.whitezia.client.scan.WhiteZiaScanStateStore
import shop.whitezia.client.scan.WhiteZiaScannerResultStore

import shop.whitezia.client.model.selectServerProfile

internal class ScanUiController(
    private val appContext: Context,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val settingsStore: WhiteZiaSettingsStore,
    private val scanSettingsStore: WhiteZiaScanSettingsStore,
    private val readState: () -> WhiteZiaUiState,
    private val writeState: (WhiteZiaUiState) -> Unit,
) {
    private var uiState: WhiteZiaUiState
        get() = readState()
        set(value) = writeState(value)
    private var scanLaunchJob: Job? = null
    private var scanStateRefreshJob: Job? = null
    private var lastScannerResultProfileText = ""

    fun close() {
        scanLaunchJob?.cancel()
        scanStateRefreshJob?.cancel()
    }

    fun updateScanWorkerCount(rawValue: String) {
        val filtered = rawValue.filter(Char::isDigit).take(MaxScanWorkerDigits)
        val workerCount = filtered.toIntOrNull()
        if (workerCount != null && workerCount > 0) {
            scanSettingsStore.saveWorkerCount(workerCount)
        }
        uiState = uiState.copy(scanWorkerCount = filtered)
    }

    fun updateScanConnectionProfile(profileId: String) {
        if (uiState.scanState.isRunning) {
            return
        }
        val scanConnectionProfileId = resolveScanConnectionProfileId(
            settings = uiState.settings,
            requestedProfileId = profileId,
        )
        scanSettingsStore.saveConnectionProfileId(scanConnectionProfileId)
        uiState = uiState.copy(scanConnectionProfileId = scanConnectionProfileId)
    }

    fun beginScanFromFile(uri: Uri) {
        beginScanFromSource(sourceName = "Resolver file") { sessionId ->
            importScanResolverFile(uri, sessionId)
        }
    }

    fun beginScanFromDefaultResolvers() {
        beginScanFromSource(sourceName = "Default resolver list") { sessionId ->
            importDefaultScanResolverFile(sessionId)
        }
    }

    private fun beginScanFromSource(
        sourceName: String,
        importSource: (String) -> ImportedScanResolverFile,
    ) {
        if (uiState.scanState.isRunning) {
            return
        }
        scanLaunchJob?.cancel()
        scanLaunchJob = scope.launch {
            val sessionId = UUID.randomUUID().toString()
            try {
                val baseSettings = uiState.settings.syncSelectedConnectionProfileFields()
                val scanConnectionProfileId = resolveScanConnectionProfileId(
                    settings = baseSettings,
                    requestedProfileId = uiState.scanConnectionProfileId,
                )
                if (scanConnectionProfileId != uiState.scanConnectionProfileId) {
                    scanSettingsStore.saveConnectionProfileId(scanConnectionProfileId)
                    uiState = uiState.copy(scanConnectionProfileId = scanConnectionProfileId)
                }
                val workerCount = uiState.scanWorkerCount.toIntOrNull()
                    ?.coerceAtLeast(1)
                    ?: WhiteZiaScanDefaults.DefaultWorkerCount
                val importingState = WhiteZiaScanState(
                    sessionId = sessionId,
                    status = WhiteZiaScanStatus.Starting,
                    sourceName = sourceName,
                    workerCount = workerCount,
                    updatedAtMillis = System.currentTimeMillis(),
                    message = "Importing $sourceName",
                )
                withContext(Dispatchers.IO) {
                    WhiteZiaScanStateStore.write(appContext, importingState)
                }
                uiState = uiState.copy(scanState = importingState)

                val imported = withContext(Dispatchers.IO) {
                    importSource(sessionId)
                }

                withContext(Dispatchers.IO) {
                    WhiteZiaScanRequestStore.save(
                        context = appContext,
                        request = WhiteZiaScanLaunchRequest(
                            id = sessionId,
                            sourceName = imported.sourceName,
                            resolverFilePath = imported.file.absolutePath,
                            workerCount = workerCount.coerceAtLeast(1),
                            initialValidResolvers = emptyList(),
                            initialCompletedResolvers = 0,
                            totalResolvers = imported.pendingResolverCount,
                        ),
                    )
                }

                val readyStatus = if (imported.pendingResolverCount > 0) {
                    WhiteZiaScanStatus.Ready
                } else {
                    WhiteZiaScanStatus.Completed
                }
                val readyState = WhiteZiaScanState(
                    sessionId = sessionId,
                    status = readyStatus,
                    sourceName = imported.sourceName,
                    totalResolvers = imported.displayResolverCount,
                    completedResolvers = if (imported.pendingResolverCount > 0) 0 else imported.displayResolverCount,
                    validResolvers = if (imported.pendingResolverCount > 0) 0 else imported.alreadyValidResolverCount,
                    rejectedResolvers = 0,
                    workerCount = workerCount.coerceAtMost(imported.pendingResolverCount.coerceAtLeast(1)),
                    updatedAtMillis = System.currentTimeMillis(),
                    message = buildPreparedScanMessage(imported),
                    validResolverEntries = emptyList(),
                    rejectedResolverEntries = emptyList(),
                )
                withContext(Dispatchers.IO) {
                    WhiteZiaScanStateStore.write(appContext, readyState)
                }
                uiState = uiState.copy(scanState = readyState)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                setScanFailure(sessionId, "Resolver import failed: ${error.message ?: error::class.java.simpleName}")
            }
        }
    }

    fun startPreparedScan() {
        if (uiState.scanState.isRunning) {
            return
        }
        scanLaunchJob?.cancel()
        scanLaunchJob = scope.launch {
            val previousState = uiState.scanState
            val sessionId = previousState.sessionId
            if (
                sessionId.isBlank() ||
                previousState.status != WhiteZiaScanStatus.Ready ||
                previousState.completedResolvers >= previousState.totalResolvers
            ) {
                return@launch
            }
            val scanRequest = withContext(Dispatchers.IO) {
                WhiteZiaScanRequestStore.load(appContext, sessionId)
            } ?: run {
                setScanFailure(sessionId, "Scan start failed: resolver file is missing")
                return@launch
            }
            val baseSettings = uiState.settings.syncSelectedConnectionProfileFields()
            val scanConnectionProfileId = resolveScanConnectionProfileId(
                settings = baseSettings,
                requestedProfileId = uiState.scanConnectionProfileId,
            )
            if (scanConnectionProfileId != uiState.scanConnectionProfileId) {
                scanSettingsStore.saveConnectionProfileId(scanConnectionProfileId)
                uiState = uiState.copy(scanConnectionProfileId = scanConnectionProfileId)
            }
            val settings = baseSettings.copy(
                selectedConnectionProfileId = scanConnectionProfileId,
            ).syncSelectedConnectionProfileFields()
            val serverProfile = selectServerProfile(settings)
            if (serverProfile == null) {
                val profileName = settings.selectedConnectionProfile().name.ifBlank { "selected scan profile" }
                setScanFailure(sessionId, "$profileName requires a CottenDNS domain and encryption key")
                return@launch
            }
            val workerCount = uiState.scanWorkerCount.toIntOrNull()
                ?.coerceAtLeast(1)
                ?: scanRequest.workerCount.coerceAtLeast(1)
            val resolverFileExists = withContext(Dispatchers.IO) {
                File(scanRequest.resolverFilePath).isFile
            }
            if (!resolverFileExists) {
                setScanFailure(sessionId, "Scan start failed: resolver file is missing")
                return@launch
            }
            val pendingResolverCount = previousState.totalResolvers
                .takeIf { it > 0 }
                ?: scanRequest.totalResolvers
            if (pendingResolverCount == 0) {
                val completedState = previousState.copy(
                    status = WhiteZiaScanStatus.Completed,
                    updatedAtMillis = System.currentTimeMillis(),
                    message = "No new resolvers to scan; Scanner result is up to date",
                )
                WhiteZiaScanStateStore.write(appContext, completedState)
                uiState = uiState.copy(scanState = completedState)
                return@launch
            }
            val nowMillis = System.currentTimeMillis()
            val startingState = previousState.copy(
                status = WhiteZiaScanStatus.Starting,
                workerCount = workerCount.coerceAtMost(pendingResolverCount.coerceAtLeast(1)),
                startedAtMillis = nowMillis,
                updatedAtMillis = nowMillis,
                message = "Starting scan",
            )
            withContext(Dispatchers.IO) {
                RuntimeLaunchRequestStore.save(
                    context = appContext,
                    requestId = sessionId,
                    serverProfile = serverProfile,
                    settings = settings,
                )
                WhiteZiaScanRequestStore.save(
                    context = appContext,
                    request = scanRequest.copy(
                        workerCount = workerCount,
                        initialValidResolvers = previousState.validResolverEntries,
                        initialRejectedResolvers = previousState.rejectedResolverEntries,
                        initialCompletedResolvers = previousState.completedResolvers,
                        totalResolvers = previousState.totalResolvers,
                    ),
                )
            }
            WhiteZiaScanStateStore.write(appContext, startingState)
            uiState = uiState.copy(scanState = startingState)

            withContext(Dispatchers.IO) {
                WhiteZiaScanService.startPrepared(appContext, sessionId)
            }
        }
    }

    fun stopScan() {
        scanLaunchJob?.cancel()
        if (uiState.scanState.isRunning) {
            val stoppedState = uiState.scanState.copy(
                status = WhiteZiaScanStatus.Stopped,
                updatedAtMillis = System.currentTimeMillis(),
                message = "Scan stopped",
            )
            WhiteZiaScanStateStore.write(appContext, stoppedState)
            uiState = uiState.copy(scanState = stoppedState)
        }
        scope.launch(Dispatchers.IO) {
            WhiteZiaScanService.stop(appContext)
        }
    }

    fun refreshScanState() {
        scanStateRefreshJob?.cancel()
        scanStateRefreshJob = scope.launch {
            val refreshResult = withContext(Dispatchers.IO) {
                val persistedState = WhiteZiaScanStateStore.read(appContext)
                val scanState = persistedState.recoverIfStale(
                    nowMillis = System.currentTimeMillis(),
                    staleAfterMillis = StaleScanStateTimeoutMillis,
                )
                if (scanState != persistedState) {
                    WhiteZiaScanStateStore.write(appContext, scanState)
                }
                ScanStateRefreshResult(
                    scanState = scanState,
                    resolverText = WhiteZiaScannerResultStore.readValidResolvers(appContext).joinToString("\n"),
                )
            }
            uiState = uiState.copy(
                settings = syncScannerResultProfile(uiState.settings, refreshResult.scanState, refreshResult.resolverText) ?: uiState.settings,
                scanState = refreshResult.scanState,
            )
        }
    }

    fun resumeScan() {
        if (uiState.scanState.isRunning) {
            return
        }
        scanLaunchJob?.cancel()
        scanLaunchJob = scope.launch {
            val previousState = uiState.scanState
            val previousSessionId = previousState.sessionId
            if (previousSessionId.isBlank()) {
                return@launch
            }
            val runtimeRequest = withContext(Dispatchers.IO) {
                RuntimeLaunchRequestStore.load(appContext, previousSessionId)
            } ?: run {
                setScanFailure(previousSessionId, "Scan resume failed: launch settings are missing")
                return@launch
            }
            val serverProfile = runtimeRequest.serverProfile ?: run {
                setScanFailure(previousSessionId, "Scan resume failed: CottenDNS profile is missing")
                return@launch
            }
            val scanRequest = withContext(Dispatchers.IO) {
                WhiteZiaScanRequestStore.load(appContext, previousSessionId)
            } ?: run {
                setScanFailure(previousSessionId, "Scan resume failed: resolver file is missing")
                return@launch
            }
            val validEntries = WhiteZiaScannerResultStore.normalizeResolverEntries(previousState.validResolverEntries)
            val rejectedEntries = WhiteZiaScannerResultStore.normalizeResolverEntries(previousState.rejectedResolverEntries)
                .filterNot(validEntries::contains)
            val processed = (validEntries + rejectedEntries).toSet()
            val sessionId = UUID.randomUUID().toString()
            val resolverFile = File(
                File(File(appContext.noBackupFilesDir, "stormdns/scan"), sessionId).apply { mkdirs() },
                "resume.resolvers",
            )
            val remainingResolverCount = withContext(Dispatchers.IO) {
                runCatching {
                    val excludedResolvers = processed + WhiteZiaScannerResultStore.readValidResolverSet(appContext)
                    File(scanRequest.resolverFilePath).bufferedReader(Charsets.UTF_8).useLines { lines ->
                        WhiteZiaScannerResultStore.writePendingScanResolverFile(
                            lines = lines,
                            outputFile = resolverFile,
                            excludedResolvers = excludedResolvers,
                        )
                    }.pendingResolverCount
                }
            }.getOrElse { error ->
                setScanFailure(previousSessionId, "Scan resume failed: ${error.message ?: error::class.java.simpleName}")
                return@launch
            }
            if (remainingResolverCount == 0) {
                val updatedState = previousState.copy(
                    updatedAtMillis = System.currentTimeMillis(),
                    message = "No remaining resolvers to resume",
                )
                WhiteZiaScanStateStore.write(appContext, updatedState)
                uiState = uiState.copy(scanState = updatedState)
                return@launch
            }

            val workerCount = uiState.scanWorkerCount.toIntOrNull()
                ?.coerceAtLeast(1)
                ?: scanRequest.workerCount.coerceAtLeast(1)
            val startingState = WhiteZiaScanState(
                sessionId = sessionId,
                status = WhiteZiaScanStatus.Starting,
                sourceName = previousState.sourceName.ifBlank { scanRequest.sourceName },
                totalResolvers = remainingResolverCount + processed.size,
                completedResolvers = processed.size,
                validResolvers = validEntries.size,
                rejectedResolvers = rejectedEntries.size,
                workerCount = workerCount.coerceAtMost(remainingResolverCount.coerceAtLeast(1)),
                startedAtMillis = System.currentTimeMillis(),
                updatedAtMillis = System.currentTimeMillis(),
                message = "Resuming scan",
                validResolverEntries = validEntries,
                rejectedResolverEntries = rejectedEntries,
            )
            WhiteZiaScanStateStore.write(appContext, startingState)
            uiState = uiState.copy(scanState = startingState)

            withContext(Dispatchers.IO) {
                WhiteZiaScanService.start(
                    context = appContext,
                    sessionId = sessionId,
                    serverProfile = serverProfile,
                    settings = runtimeRequest.settings,
                    sourceName = startingState.sourceName,
                    resolverFile = resolverFile,
                    workerCount = workerCount,
                    initialValidResolvers = validEntries,
                    initialRejectedResolvers = rejectedEntries,
                )
            }
        }
    }

    private fun setScanFailure(sessionId: String, message: String) {
        val failedState = WhiteZiaScanState(
            sessionId = sessionId,
            status = WhiteZiaScanStatus.Failed,
            updatedAtMillis = System.currentTimeMillis(),
            message = message,
        )
        WhiteZiaScanStateStore.write(appContext, failedState)
        uiState = uiState.copy(scanState = failedState)
    }

    private fun importScanResolverFile(uri: Uri, sessionId: String): ImportedScanResolverFile {
        val scanDir = File(File(appContext.noBackupFilesDir, "stormdns/scan"), sessionId).apply {
            mkdirs()
        }
        val resolverFile = File(scanDir, "input.resolvers")
        val summary = appContext.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.useLines { lines ->
                WhiteZiaScannerResultStore.writePendingScanResolverFile(
                    lines = lines,
                    outputFile = resolverFile,
                )
            }
            ?: throw IllegalArgumentException("Unable to open resolver file")
        if (summary.totalResolverCount == 0) {
            throw IllegalArgumentException("No valid resolver entries found in file")
        }
        return ImportedScanResolverFile(
            file = resolverFile,
            sourceName = displayNameForUri(uri),
            pendingResolverCount = summary.pendingResolverCount,
            totalResolverCount = summary.totalResolverCount,
            alreadyValidResolverCount = summary.alreadyValidResolverCount,
            invalidEntryCount = summary.invalidEntryCount,
        )
    }

    private fun importDefaultScanResolverFile(sessionId: String): ImportedScanResolverFile {
        val scanDir = File(File(appContext.noBackupFilesDir, "stormdns/scan"), sessionId).apply {
            mkdirs()
        }
        val resolverFile = File(scanDir, "default.resolvers")
        val summary = appContext.assets.open(DefaultScanResolverAssetName)
            .bufferedReader(Charsets.UTF_8)
            .useLines { lines ->
                WhiteZiaScannerResultStore.writePendingScanResolverFile(
                    lines = lines,
                    outputFile = resolverFile,
                )
            }
        if (summary.totalResolverCount == 0) {
            throw IllegalArgumentException("No valid resolver entries found in default list")
        }
        return ImportedScanResolverFile(
            file = resolverFile,
            sourceName = "Default resolver list",
            pendingResolverCount = summary.pendingResolverCount,
            totalResolverCount = summary.totalResolverCount,
            alreadyValidResolverCount = summary.alreadyValidResolverCount,
            invalidEntryCount = summary.invalidEntryCount,
        )
    }

    private fun buildPreparedScanMessage(imported: ImportedScanResolverFile): String {
        val parts = mutableListOf<String>()
        parts += "Imported ${imported.totalResolverCount} resolver${if (imported.totalResolverCount == 1) "" else "s"}"
        if (imported.pendingResolverCount > 0) {
            parts += "Ready to scan ${imported.pendingResolverCount} entr${if (imported.pendingResolverCount == 1) "y" else "ies"}"
        } else {
            parts += "No new resolvers to scan"
        }
        if (imported.alreadyValidResolverCount > 0) {
            parts += "skipped ${imported.alreadyValidResolverCount} already in Scanner result"
        }
        if (imported.invalidEntryCount > 0) {
            parts += "ignored ${imported.invalidEntryCount} invalid entries"
        }
        return parts.joinToString(separator = "\n")
    }

    private fun syncScannerResultProfile(
        currentSettings: WhiteZiaSettings,
        scanState: WhiteZiaScanState,
        scannerResultText: String,
    ): WhiteZiaSettings? {
        if (scanState.isRunning) {
            return null
        }
        if (scannerResultText.isBlank() || scannerResultText == lastScannerResultProfileText) {
            return null
        }

        val normalizedSettings = currentSettings.syncSelectedConnectionProfileFields()
        val resolverProfiles = normalizedSettings.normalizedResolverProfiles()
        val existingIndex = resolverProfiles.indexOfFirst { it.name == ScannerResultProfileName }
        if (existingIndex >= 0 && resolverProfiles[existingIndex].resolverText == scannerResultText) {
            lastScannerResultProfileText = scannerResultText
            return null
        }

        val scannerResultProfile = if (existingIndex >= 0) {
            resolverProfiles[existingIndex].copy(resolverText = scannerResultText)
        } else {
            ResolverProfile(
                id = ResolverProfile.newId(),
                name = ScannerResultProfileName,
                resolverText = scannerResultText,
            )
        }
        val updatedProfiles = if (existingIndex >= 0) {
            resolverProfiles.toMutableList().also { profiles ->
                profiles[existingIndex] = scannerResultProfile
            }
        } else {
            resolverProfiles + scannerResultProfile
        }
        val updatedSettings = normalizedSettings.copy(
            resolverProfiles = updatedProfiles,
        ).syncSelectedConnectionProfileFields()
        settingsStore.save(updatedSettings)
        lastScannerResultProfileText = scannerResultText
        return updatedSettings
    }

    private fun displayNameForUri(uri: Uri): String {
        return runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            cursor.getString(index)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
        }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)
            ?: "Resolver file"
    }

    private data class ImportedScanResolverFile(
        val file: File,
        val sourceName: String,
        val pendingResolverCount: Int,
        val totalResolverCount: Int,
        val alreadyValidResolverCount: Int,
        val invalidEntryCount: Int,
    ) {
        val displayResolverCount: Int
            get() = if (pendingResolverCount > 0) pendingResolverCount else totalResolverCount
    }

    private data class ScanStateRefreshResult(
        val scanState: WhiteZiaScanState,
        val resolverText: String,
    )

    private companion object {
        const val MaxScanWorkerDigits = 3
        const val StaleScanStateTimeoutMillis = 15_000L
        const val ScannerResultProfileName = "Scanner result"
        const val DefaultScanResolverAssetName = "default_resolvers.txt"
    }
}

internal fun resolveScanConnectionProfileId(
    settings: WhiteZiaSettings,
    requestedProfileId: String,
): String {
    val normalizedSettings = settings.syncSelectedConnectionProfileFields()
    val profiles = normalizedSettings.normalizedConnectionProfiles()
    return profiles.firstOrNull { it.id == requestedProfileId }?.id
        ?: normalizedSettings.selectedConnectionProfile().id
}

