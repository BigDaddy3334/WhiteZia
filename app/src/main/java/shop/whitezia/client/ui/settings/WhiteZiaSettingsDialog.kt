package shop.whitezia.client.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale
import shop.whitezia.client.BuildConfig
import shop.whitezia.client.model.ResolverProfile
import shop.whitezia.client.model.ResolverTextValidation
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.syncSelectedConnectionProfileFields
import shop.whitezia.client.model.validateResolverText
import shop.whitezia.client.ui.WhiteZiaBackground
import shop.whitezia.client.ui.WhiteZiaBlue
import shop.whitezia.client.ui.WhiteZiaError
import shop.whitezia.client.ui.WhiteZiaSmallTextStyle
import shop.whitezia.client.ui.WhiteZiaTextDim
import shop.whitezia.client.ui.whiteZiaTextFieldColors
import shop.whitezia.client.ui.WhiteZiaTextMuted

@Composable
internal fun WhiteZiaSettingsDialog(
    settings: WhiteZiaSettings,
    subscriptionLink: String,
    accountManaged: Boolean,
    onDismiss: () -> Unit,
    onOpenSplitTunnelApps: (WhiteZiaSettings, String) -> Unit,
    onScanSubscription: () -> Unit,
    isCheckingForUpdates: Boolean,
    onCheckForUpdates: () -> Unit,
    onSave: (WhiteZiaSettings, String) -> Unit,
) {
    var selectedSectionIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var draftSubscription by rememberSaveable(subscriptionLink) {
        mutableStateOf(subscriptionLink)
    }
    var draftSettings by remember(settings) {
        mutableStateOf(settings)
    }
    val resolverValidation = remember(draftSettings.customResolverText) {
        validateResolverText(draftSettings.customResolverText)
    }
    val customResolversValid = !draftSettings.customResolversEnabled ||
        resolverValidation.normalizedResolvers.isNotEmpty() &&
        resolverValidation.invalidEntries.isEmpty()

    fun normalizedDraftSettings(): WhiteZiaSettings {
        val normalizedResolvers = resolverValidation.normalizedText
        return if (draftSettings.customResolversEnabled) {
            draftSettings.copy(
                customResolverText = normalizedResolvers,
                resolverText = normalizedResolvers,
            )
        } else {
            draftSettings
        }.syncSelectedConnectionProfileFields()
    }

    val sections = listOf(
        SettingsSection(
            "Подписка",
            if (accountManaged) "Управляется аккаунтом" else "Профиль и split tunnel",
            Icons.Rounded.Link,
        ),
        SettingsSection(
            "DNS resolver'ы",
            if (draftSettings.customResolversEnabled) "Кастомный список" else "Автоматический поиск",
            Icons.Rounded.Dns,
        ),
        SettingsSection(
            "StormDNS",
            if (draftSettings.customConnectionSettingsEnabled) "Кастомные параметры" else "Стандартные параметры",
            Icons.Rounded.Tune,
        ),
        SettingsSection(
            "Системные настройки",
            if (draftSettings.manualMode) "Ручной режим" else "Автоматический режим",
            Icons.Rounded.Settings,
        ),
    )
    val windowWidth = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    val isWideLayout = windowWidth >= SettingsTwoPaneMinWidthDp.dp
    val effectiveSectionIndex = selectedSectionIndex ?: if (isWideLayout) 0 else null

    BackHandler {
        if (!isWideLayout && selectedSectionIndex != null) {
            selectedSectionIndex = null
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = WhiteZiaBackground,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                SettingsTopBar(
                    title = effectiveSectionIndex
                        ?.takeIf { !isWideLayout }
                        ?.let { sections[it].title }
                        ?: "Настройки",
                    showBack = !isWideLayout && effectiveSectionIndex != null,
                    onBack = { selectedSectionIndex = null },
                    onClose = onDismiss,
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                if (isWideLayout) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        SettingsSectionList(
                            modifier = Modifier
                                .width(248.dp)
                                .fillMaxHeight(),
                            sections = sections,
                            selectedIndex = effectiveSectionIndex,
                            onSectionClick = { selectedSectionIndex = it },
                        )
                        Spacer(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(Color.White.copy(alpha = 0.08f)),
                        )
                        SettingsSectionContent(
                            modifier = Modifier.weight(1f),
                            sectionIndex = effectiveSectionIndex ?: 0,
                            subscriptionLink = draftSubscription,
                            settings = draftSettings,
                            accountManaged = accountManaged,
                            resolverValidation = resolverValidation,
                            customResolversValid = customResolversValid,
                            onSubscriptionChange = { draftSubscription = it },
                            onSettingsChange = { draftSettings = it },
                            onScanSubscription = onScanSubscription,
                            isCheckingForUpdates = isCheckingForUpdates,
                            onCheckForUpdates = onCheckForUpdates,
                            onOpenSplitTunnelApps = {
                                onOpenSplitTunnelApps(normalizedDraftSettings(), draftSubscription)
                            },
                        )
                    }
                } else if (effectiveSectionIndex == null) {
                    SettingsSectionList(
                        modifier = Modifier.weight(1f),
                        sections = sections,
                        selectedIndex = null,
                        onSectionClick = { selectedSectionIndex = it },
                    )
                } else {
                    SettingsSectionContent(
                        modifier = Modifier.weight(1f),
                        sectionIndex = effectiveSectionIndex,
                        subscriptionLink = draftSubscription,
                        settings = draftSettings,
                        accountManaged = accountManaged,
                        resolverValidation = resolverValidation,
                        customResolversValid = customResolversValid,
                        onSubscriptionChange = { draftSubscription = it },
                        onSettingsChange = { draftSettings = it },
                        onScanSubscription = onScanSubscription,
                        isCheckingForUpdates = isCheckingForUpdates,
                        onCheckForUpdates = onCheckForUpdates,
                        onOpenSplitTunnelApps = {
                            onOpenSplitTunnelApps(normalizedDraftSettings(), draftSubscription)
                        },
                    )
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    TextButton(
                        enabled = customResolversValid,
                        onClick = { onSave(normalizedDraftSettings(), draftSubscription) },
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}

private data class SettingsSection(
    val title: String,
    val summary: String,
    val icon: ImageVector,
)

@Composable
private fun SettingsTopBar(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад", tint = Color.White)
            }
        } else {
            Spacer(modifier = Modifier.width(48.dp))
        }
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = "Закрыть", tint = WhiteZiaTextMuted)
        }
    }
}

@Composable
private fun SettingsSectionList(
    modifier: Modifier,
    sections: List<SettingsSection>,
    selectedIndex: Int?,
    onSectionClick: (Int) -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        sections.forEachIndexed { index, section ->
            SettingsSectionRow(
                section = section,
                selected = selectedIndex == index,
                onClick = { onSectionClick(index) },
            )
        }
    }
}

@Composable
private fun SettingsSectionRow(
    section: SettingsSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) WhiteZiaBlue.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = section.icon,
            contentDescription = null,
            tint = if (selected) WhiteZiaBlue else WhiteZiaTextMuted,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = section.title,
                color = Color.White.copy(alpha = 0.88f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = section.summary,
                color = WhiteZiaTextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = WhiteZiaTextDim)
    }
}

@Composable
private fun SettingsSectionContent(
    modifier: Modifier,
    sectionIndex: Int,
    subscriptionLink: String,
    settings: WhiteZiaSettings,
    accountManaged: Boolean,
    resolverValidation: ResolverTextValidation,
    customResolversValid: Boolean,
    onSubscriptionChange: (String) -> Unit,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
    onScanSubscription: () -> Unit,
    isCheckingForUpdates: Boolean,
    onCheckForUpdates: () -> Unit,
    onOpenSplitTunnelApps: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (sectionIndex) {
            0 -> SubscriptionSettingsTab(
                subscriptionLink = subscriptionLink,
                settings = settings,
                accountManaged = accountManaged,
                onSubscriptionChange = onSubscriptionChange,
                onScanSubscription = onScanSubscription,
                onOpenSplitTunnelApps = {
                    if (customResolversValid) onOpenSplitTunnelApps()
                },
            )
            1 -> ResolverSettingsTab(settings, resolverValidation, onSettingsChange)
            2 -> StormDnsAdvancedSettingsTab(settings, onSettingsChange)
            else -> SystemSettingsTab(
                settings = settings,
                onSettingsChange = onSettingsChange,
                isCheckingForUpdates = isCheckingForUpdates,
                onCheckForUpdates = onCheckForUpdates,
            )
        }
    }
}

private const val SettingsTwoPaneMinWidthDp = 600

@Composable
private fun SubscriptionSettingsTab(
    subscriptionLink: String,
    settings: WhiteZiaSettings,
    accountManaged: Boolean,
    onSubscriptionChange: (String) -> Unit,
    onScanSubscription: () -> Unit,
    onOpenSplitTunnelApps: () -> Unit,
) {
    SettingsSectionTitle("Подписка")
    if (accountManaged) {
        Text(
            text = "Подписка управляется личным кабинетом",
            color = WhiteZiaTextMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = subscriptionLink,
            onValueChange = onSubscriptionChange,
            label = { Text("stormbundle:// или stormdns://") },
            singleLine = false,
            minLines = 4,
            colors = whiteZiaTextFieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
        )
        TextButton(onClick = onScanSubscription) {
            Text("Сканировать QR")
        }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
    SettingsSectionTitle("Split tunnel")
    Text(
        text = splitTunnelSummary(settings),
        color = WhiteZiaTextMuted,
        style = MaterialTheme.typography.bodySmall,
    )
    TextButton(onClick = onOpenSplitTunnelApps) {
        Icon(imageVector = Icons.Rounded.Apps, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Выбрать приложения")
    }
}

@Composable
private fun ResolverSettingsTab(
    settings: WhiteZiaSettings,
    validation: ResolverTextValidation,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    SettingsSwitchRow(
        title = "Кастомные resolver'ы",
        subtitle = "StormDNS будет работать через этот список вместо автопоиска",
        checked = settings.customResolversEnabled,
        onCheckedChange = {
            onSettingsChange(
                settings.copy(
                    customResolversEnabled = it,
                    customResolverText = settings.customResolverText.ifBlank { settings.resolverText },
                    selectedResolverProfileId = if (it) {
                        ResolverProfile.CustomId
                    } else {
                        settings.selectedResolverProfileId.takeIf { profileId ->
                            profileId != ResolverProfile.CustomId
                        }.orEmpty()
                    },
                ).syncSelectedConnectionProfileFields(),
            )
        },
    )
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        enabled = settings.customResolversEnabled,
        value = if (settings.customResolversEnabled) {
            settings.customResolverText
        } else {
            settings.customResolverText.ifBlank { settings.resolverText }
        },
        onValueChange = {
            onSettingsChange(settings.copy(customResolverText = it))
        },
        label = { Text("Resolver list") },
        placeholder = { Text("10.112.250.2\n77.88.8.8") },
        singleLine = false,
        minLines = 6,
        colors = whiteZiaTextFieldColors(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
    )
    val statusText = when {
        !settings.customResolversEnabled -> "Автопоиск resolver'ов включен"
        validation.invalidEntries.isNotEmpty() -> "Некорректные строки: ${validation.invalidEntries.joinToString()}"
        validation.normalizedResolvers.isEmpty() -> "Добавьте хотя бы один resolver"
        else -> "Resolver'ов: ${validation.normalizedResolvers.size}"
    }
    Text(
        text = statusText,
        color = if (settings.customResolversEnabled && !validation.isValid) {
            WhiteZiaError
        } else {
            WhiteZiaTextDim
        },
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun SystemSettingsTab(
    settings: WhiteZiaSettings,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
    isCheckingForUpdates: Boolean,
    onCheckForUpdates: () -> Unit,
) {
    SettingsSwitchRow(
        title = "Ручной режим",
        subtitle = "Показывать переключатели каналов на главном экране",
        checked = settings.manualMode,
        onCheckedChange = { enabled ->
            onSettingsChange(
                settings.copy(
                    manualMode = enabled,
                    forceDnsTunnel = if (enabled) settings.forceDnsTunnel else false,
                    transportMode = if (enabled) settings.transportMode else WhiteZiaOptions.TransportAuto,
                ),
            )
        },
    )
    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
    SettingsSectionTitle("Приложение")
    Text(
        text = "Версия ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        color = WhiteZiaTextMuted,
        style = MaterialTheme.typography.bodyMedium,
    )
    TextButton(
        enabled = !isCheckingForUpdates,
        onClick = onCheckForUpdates,
    ) {
        if (isCheckingForUpdates) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(Icons.Rounded.Sync, contentDescription = null)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (isCheckingForUpdates) "Проверяем обновления..." else "Проверить обновления")
    }
}

@Composable
private fun StormDnsAdvancedSettingsTab(
    settings: WhiteZiaSettings,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    val customSettingsEnabled = settings.customConnectionSettingsEnabled
    var expandedGroup by rememberSaveable { mutableStateOf("mtu") }

    fun updateString(update: WhiteZiaSettings.(String) -> WhiteZiaSettings): (String) -> Unit {
        return { value -> onSettingsChange(settings.update(value)) }
    }

    @Composable
    fun StormSettingsTextField(
        label: String,
        value: String,
        onValueChange: (String) -> Unit,
    ) {
        SettingsTextField(
            label = label,
            value = value,
            onValueChange = onValueChange,
            enabled = customSettingsEnabled,
        )
    }

    SettingsSwitchRow(
        title = "Кастомные настройки",
        subtitle = "Не перезаписывать MTU, duplication, workers и timeout при подключении",
        checked = settings.customConnectionSettingsEnabled,
        onCheckedChange = { onSettingsChange(settings.copy(customConnectionSettingsEnabled = it)) },
    )
    StormSettingsGroup("MTU", expandedGroup == "mtu", { expandedGroup = toggleGroup(expandedGroup, "mtu") }) {
        SettingsFieldGrid {
            StormSettingsTextField("Upload MTU: минимум", settings.minUploadMtu, updateString { copy(minUploadMtu = it) })
            StormSettingsTextField("Upload MTU: максимум", settings.maxUploadMtu, updateString { copy(maxUploadMtu = it) })
            StormSettingsTextField("Download MTU: минимум", settings.minDownloadMtu, updateString { copy(minDownloadMtu = it) })
            StormSettingsTextField("Download MTU: максимум", settings.maxDownloadMtu, updateString { copy(maxDownloadMtu = it) })
            StormSettingsTextField("Повторы проверки resolver'ов", settings.mtuTestRetriesResolvers, updateString { copy(mtuTestRetriesResolvers = it) })
            StormSettingsTextField("Timeout проверки resolver'ов", settings.mtuTestTimeoutResolvers, updateString { copy(mtuTestTimeoutResolvers = it) })
            StormSettingsTextField("Параллельные проверки resolver'ов", settings.mtuTestParallelismResolvers, updateString { copy(mtuTestParallelismResolvers = it) })
            StormSettingsTextField("Повторы проверки логов", settings.mtuTestRetriesLogs, updateString { copy(mtuTestRetriesLogs = it) })
            StormSettingsTextField("Timeout проверки логов", settings.mtuTestTimeoutLogs, updateString { copy(mtuTestTimeoutLogs = it) })
            StormSettingsTextField("Параллельные проверки логов", settings.mtuTestParallelismLogs, updateString { copy(mtuTestParallelismLogs = it) })
        }
    }

    StormSettingsGroup("Туннель", expandedGroup == "tunnel", { expandedGroup = toggleGroup(expandedGroup, "tunnel") }) {
        SettingsFieldGrid {
            StormSettingsTextField("UPLOAD_DUPLICATION", settings.uploadDuplication, updateString { copy(uploadDuplication = it) })
            StormSettingsTextField("DOWNLOAD_DUPLICATION", settings.downloadDuplication, updateString { copy(downloadDuplication = it) })
            StormSettingsTextField("UPLOAD_COMPRESSION", settings.uploadCompression.toString(), { onSettingsChange(settings.copy(uploadCompression = it.toIntOrNull() ?: settings.uploadCompression)) })
            StormSettingsTextField("DOWNLOAD_COMPRESSION", settings.downloadCompression.toString(), { onSettingsChange(settings.copy(downloadCompression = it.toIntOrNull() ?: settings.downloadCompression)) })
            StormSettingsTextField("BALANCING_STRATEGY", settings.balancingStrategy.toString(), { onSettingsChange(settings.copy(balancingStrategy = it.toIntOrNull() ?: settings.balancingStrategy)) })
            StormSettingsTextField("RX_TX_WORKERS", settings.rxTxWorkers, updateString { copy(rxTxWorkers = it) })
            StormSettingsTextField("TUNNEL_WORKERS", settings.tunnelProcessWorkers, updateString { copy(tunnelProcessWorkers = it) })
            StormSettingsTextField("PACKET_TIMEOUT", settings.tunnelPacketTimeoutSeconds, updateString { copy(tunnelPacketTimeoutSeconds = it) })
            StormSettingsTextField("IDLE_POLL_INTERVAL", settings.dispatcherIdlePollIntervalSeconds, updateString { copy(dispatcherIdlePollIntervalSeconds = it) })
        }
        SettingsSwitchRow(
            title = "BASE_ENCODE_DATA",
            subtitle = "Кодировать полезную нагрузку перед отправкой",
            checked = settings.baseEncodeData,
            enabled = customSettingsEnabled,
            onCheckedChange = { onSettingsChange(settings.copy(baseEncodeData = it)) },
        )
    }

    StormSettingsGroup("Очереди", expandedGroup == "queues", { expandedGroup = toggleGroup(expandedGroup, "queues") }) {
        SettingsFieldGrid {
            StormSettingsTextField("TX_CHANNEL_SIZE", settings.txChannelSize, updateString { copy(txChannelSize = it) })
            StormSettingsTextField("RX_CHANNEL_SIZE", settings.rxChannelSize, updateString { copy(rxChannelSize = it) })
            StormSettingsTextField("UDP_POOL_SIZE", settings.resolverUdpConnectionPoolSize, updateString { copy(resolverUdpConnectionPoolSize = it) })
            StormSettingsTextField("STREAM_QUEUE", settings.streamQueueInitialCapacity, updateString { copy(streamQueueInitialCapacity = it) })
            StormSettingsTextField("ORPHAN_QUEUE", settings.orphanQueueInitialCapacity, updateString { copy(orphanQueueInitialCapacity = it) })
            StormSettingsTextField("DNS_FRAGMENT_STORE", settings.dnsResponseFragmentStoreCapacity, updateString { copy(dnsResponseFragmentStoreCapacity = it) })
            StormSettingsTextField("MAX_ACTIVE_STREAMS", settings.maxActiveStreams, updateString { copy(maxActiveStreams = it) })
        }
    }

    StormSettingsGroup("Сессия", expandedGroup == "session", { expandedGroup = toggleGroup(expandedGroup, "session") }) {
        SettingsFieldGrid {
            StormSettingsTextField("HANDSHAKE_TIMEOUT", settings.localHandshakeTimeoutSeconds, updateString { copy(localHandshakeTimeoutSeconds = it) })
            StormSettingsTextField("SOCKS_UDP_TIMEOUT", settings.socksUdpAssociateReadTimeoutSeconds, updateString { copy(socksUdpAssociateReadTimeoutSeconds = it) })
            StormSettingsTextField("TERMINAL_RETENTION", settings.clientTerminalStreamRetentionSeconds, updateString { copy(clientTerminalStreamRetentionSeconds = it) })
            StormSettingsTextField("CANCELLED_RETENTION", settings.clientCancelledSetupRetentionSeconds, updateString { copy(clientCancelledSetupRetentionSeconds = it) })
            StormSettingsTextField("INIT_RETRY_BASE", settings.sessionInitRetryBaseSeconds, updateString { copy(sessionInitRetryBaseSeconds = it) })
            StormSettingsTextField("INIT_RETRY_STEP", settings.sessionInitRetryStepSeconds, updateString { copy(sessionInitRetryStepSeconds = it) })
            StormSettingsTextField("INIT_LINEAR_AFTER", settings.sessionInitRetryLinearAfter, updateString { copy(sessionInitRetryLinearAfter = it) })
            StormSettingsTextField("INIT_RETRY_MAX", settings.sessionInitRetryMaxSeconds, updateString { copy(sessionInitRetryMaxSeconds = it) })
            StormSettingsTextField("BUSY_RETRY_INTERVAL", settings.sessionInitBusyRetryIntervalSeconds, updateString { copy(sessionInitBusyRetryIntervalSeconds = it) })
            StormSettingsTextField("PING_WATCHDOG", settings.pingWatchdogSeconds, updateString { copy(pingWatchdogSeconds = it) })
        }
    }

    StormSettingsGroup("Локальные параметры", expandedGroup == "local", { expandedGroup = toggleGroup(expandedGroup, "local") }) {
        SettingsFieldGrid {
            StormSettingsTextField("LISTEN_IP", settings.listenIp, updateString { copy(listenIp = it) })
            StormSettingsTextField("LISTEN_PORT", settings.listenPort, updateString { copy(listenPort = it) })
            StormSettingsTextField("HTTP_PROXY_PORT", settings.httpProxyPort, updateString { copy(httpProxyPort = it) })
            StormSettingsTextField("LOCAL_DNS_PORT", settings.localDnsPort, updateString { copy(localDnsPort = it) })
            StormSettingsTextField("STARTUP_MODE", settings.startupMode, updateString { copy(startupMode = it) })
            StormSettingsTextField("LOG_LEVEL", settings.logLevel, updateString { copy(logLevel = it) })
        }
        SettingsSwitchRow("HTTP_PROXY_ENABLED", "", settings.httpProxyEnabled, customSettingsEnabled) {
            onSettingsChange(settings.copy(httpProxyEnabled = it))
        }
        SettingsSwitchRow("LOCAL_DNS_ENABLED", "", settings.localDnsEnabled, customSettingsEnabled) {
            onSettingsChange(settings.copy(localDnsEnabled = it))
        }
        SettingsSwitchRow("SOCKS5_AUTH", "", settings.socks5Authentication, customSettingsEnabled) {
            onSettingsChange(settings.copy(socks5Authentication = it))
        }
        SettingsSwitchRow("TRAFFIC_WARMUP", "", settings.trafficWarmupEnabled, customSettingsEnabled) {
            onSettingsChange(settings.copy(trafficWarmupEnabled = it))
        }
        StormSettingsTextField("TRAFFIC_WARMUP_PROBE_COUNT", settings.trafficWarmupProbeCount, updateString { copy(trafficWarmupProbeCount = it) })
        StormSettingsTextField("TRAFFIC_KEEPALIVE_INTERVAL", settings.trafficKeepaliveIntervalSeconds, updateString { copy(trafficKeepaliveIntervalSeconds = it) })
    }
}

private fun toggleGroup(current: String, requested: String): String =
    if (current == requested) "" else requested

@Composable
private fun StormSettingsGroup(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = title,
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Свернуть" else "Развернуть",
                tint = WhiteZiaTextMuted,
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text.uppercase(Locale.US),
        color = WhiteZiaTextDim,
        style = WhiteZiaSmallTextStyle(),
    )
}

@Composable
private fun SettingsFieldGrid(content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = { content() },
    )
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
) {
    var draftValue by rememberSaveable(label) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(value, focused) {
        if (!focused && draftValue != value) {
            draftValue = value
        }
    }

    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                focused = focusState.isFocused
                if (!focusState.isFocused && draftValue != value) {
                    draftValue = value
                }
            },
        enabled = enabled,
        value = draftValue,
        onValueChange = { nextValue ->
            draftValue = nextValue
            onValueChange(nextValue)
        },
        label = { Text(label) },
        singleLine = true,
        colors = whiteZiaTextFieldColors(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = if (enabled) 0.84f else 0.34f),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = if (enabled) WhiteZiaTextDim else Color.White.copy(alpha = 0.18f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            enabled = enabled,
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

internal fun splitTunnelSummary(settings: WhiteZiaSettings): String {
    return when (settings.splitTunnelMode) {
        WhiteZiaOptions.SplitTunnelModeInclude -> "Split tunnel: only ${settings.splitTunnelPackages.size} app(s)"
        WhiteZiaOptions.SplitTunnelModeExclude -> "Split tunnel: bypass ${settings.splitTunnelPackages.size} app(s)"
        else -> "Split tunnel: all apps"
    }
}
