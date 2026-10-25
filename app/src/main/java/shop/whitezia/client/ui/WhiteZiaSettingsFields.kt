package shop.whitezia.client.ui


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaSettings

internal const val MtuParallelismMin = 50
internal const val MtuParallelismMax = 1000
internal const val MtuParallelismStep = 50
internal const val MtuParallelismDefault = 100

@Composable
internal fun AdvancedSettingsFields(
    settings: WhiteZiaSettings,
    showProxySettings: Boolean,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    GroupLabel(WhiteZiaL10n.groupMtu)
    MtuSettingsGroup(
        settings = settings,
        onSettingsChange = onSettingsChange,
    )

    SectionDivider()
    GroupLabel(WhiteZiaL10n.groupRuntimeWorkers)
    RuntimeWorkersSettingsGroup(
        settings = settings,
        onSettingsChange = onSettingsChange,
    )

    SectionDivider()
    if (showProxySettings) {
        GroupLabel(WhiteZiaL10n.groupLocalProxy)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WhiteZiaTextField(
                modifier = Modifier.weight(1f),
                label = WhiteZiaL10n.settingListenIp,
                value = settings.listenIp,
                onValueChange = { onSettingsChange(settings.copy(listenIp = it)) },
                placeholder = "127.0.0.1",
            )
            WhiteZiaTextField(
                modifier = Modifier.weight(1f),
                label = WhiteZiaL10n.settingListenPort,
                value = settings.listenPort,
                onValueChange = { onSettingsChange(settings.copy(listenPort = it.filter(Char::isDigit))) },
                placeholder = "10886",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    capitalization = KeyboardCapitalization.None,
                ),
            )
        }

        ToggleRow(
            label = WhiteZiaL10n.settingHttpProxy,
            enabled = settings.httpProxyEnabled,
            onToggle = {
                onSettingsChange(settings.copy(httpProxyEnabled = !settings.httpProxyEnabled))
            },
        )
        AnimatedVisibility(
            visible = settings.httpProxyEnabled,
            enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(160)) + shrinkVertically(animationSpec = tween(160)),
        ) {
            WhiteZiaTextField(
                label = WhiteZiaL10n.settingHttpPort,
                value = settings.httpProxyPort,
                onValueChange = { onSettingsChange(settings.copy(httpProxyPort = it.filter(Char::isDigit))) },
                placeholder = "10887",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    capitalization = KeyboardCapitalization.None,
                ),
            )
        }

        ToggleRow(
            label = WhiteZiaL10n.settingSocks5Auth,
            enabled = settings.socks5Authentication,
            onToggle = {
                onSettingsChange(settings.copy(socks5Authentication = !settings.socks5Authentication))
            },
        )

        AnimatedVisibility(
            visible = settings.socks5Authentication,
            enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(160)) + shrinkVertically(animationSpec = tween(160)),
        ) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WhiteZiaTextField(
                        modifier = Modifier.weight(1f),
                        label = WhiteZiaL10n.settingUsername,
                        value = settings.socksUsername,
                        onValueChange = { onSettingsChange(settings.copy(socksUsername = it)) },
                        placeholder = "master_dns_vpn",
                    )
                    WhiteZiaTextField(
                        modifier = Modifier.weight(1f),
                        label = WhiteZiaL10n.settingPassword,
                        value = settings.socksPassword,
                        onValueChange = { onSettingsChange(settings.copy(socksPassword = it)) },
                        placeholder = "master_dns_vpn",
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            }
        }

        SectionDivider()
    }

    GroupLabel(WhiteZiaL10n.groupNetworkTuning)

    WhiteZiaDropdownField(
        label = WhiteZiaL10n.settingBalancingStrategy,
        value = settings.balancingStrategy,
        options = localizedBalancingStrategies(),
        onValueChange = { onSettingsChange(settings.copy(balancingStrategy = it)) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingUploadDup,
            value = settings.uploadDuplication,
            onValueChange = {
                onSettingsChange(settings.copy(uploadDuplication = it.filter(Char::isDigit)))
            },
            placeholder = "3",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingDownloadDup,
            value = settings.downloadDuplication,
            onValueChange = {
                onSettingsChange(settings.copy(downloadDuplication = it.filter(Char::isDigit)))
            },
            placeholder = "7",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaDropdownField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingUploadCompress,
            value = settings.uploadCompression,
            options = localizedCompressionTypes(),
            onValueChange = { onSettingsChange(settings.copy(uploadCompression = it)) },
        )
        WhiteZiaDropdownField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingDownloadCompress,
            value = settings.downloadCompression,
            options = localizedCompressionTypes(),
            onValueChange = { onSettingsChange(settings.copy(downloadCompression = it)) },
        )
    }
    ToggleRow(
        label = WhiteZiaL10n.settingBaseEncodeData,
        enabled = settings.baseEncodeData,
        onToggle = {
            onSettingsChange(settings.copy(baseEncodeData = !settings.baseEncodeData))
        },
    )

    SectionDivider()
    GroupLabel(WhiteZiaL10n.groupReliability)

    WhiteZiaTextField(
        label = WhiteZiaL10n.settingPingWatchdog,
        value = settings.pingWatchdogSeconds,
        onValueChange = {
            onSettingsChange(settings.copy(pingWatchdogSeconds = it.filter(Char::isDigit)))
        },
        placeholder = "300",
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            capitalization = KeyboardCapitalization.None,
        ),
    )
    ToggleRow(
        label = WhiteZiaL10n.settingTrafficWarmup,
        enabled = settings.trafficWarmupEnabled,
        onToggle = {
            onSettingsChange(settings.copy(trafficWarmupEnabled = !settings.trafficWarmupEnabled))
        },
    )
    AnimatedVisibility(
        visible = settings.trafficWarmupEnabled,
        enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(160)) + shrinkVertically(animationSpec = tween(160)),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WhiteZiaTextField(
                modifier = Modifier.weight(1f),
                label = WhiteZiaL10n.settingWarmupProbes,
                value = settings.trafficWarmupProbeCount,
                onValueChange = {
                    onSettingsChange(settings.copy(trafficWarmupProbeCount = it.filter(Char::isDigit)))
                },
                placeholder = "4",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    capitalization = KeyboardCapitalization.None,
                ),
            )
            WhiteZiaTextField(
                modifier = Modifier.weight(1f),
                label = WhiteZiaL10n.settingKeepalive,
                value = settings.trafficKeepaliveIntervalSeconds,
                onValueChange = {
                    onSettingsChange(
                        settings.copy(trafficKeepaliveIntervalSeconds = it.filter(Char::isDigit)),
                    )
                },
                placeholder = "5",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    capitalization = KeyboardCapitalization.None,
                ),
            )
        }
    }
    WhiteZiaDropdownField(
        label = WhiteZiaL10n.settingLogLevel,
        value = settings.logLevel,
        options = WhiteZiaOptions.logLevels,
        onValueChange = { onSettingsChange(settings.copy(logLevel = it)) },
    )
}

@Composable
private fun MtuSettingsGroup(
    settings: WhiteZiaSettings,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingMinUpload,
            value = settings.minUploadMtu,
            onValueChange = {
                onSettingsChange(settings.copy(minUploadMtu = it.filter(Char::isDigit)))
            },
            placeholder = "40",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingMinDownload,
            value = settings.minDownloadMtu,
            onValueChange = {
                onSettingsChange(settings.copy(minDownloadMtu = it.filter(Char::isDigit)))
            },
            placeholder = "300",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingMaxUpload,
            value = settings.maxUploadMtu,
            onValueChange = {
                onSettingsChange(settings.copy(maxUploadMtu = it.filter(Char::isDigit)))
            },
            placeholder = "140",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingMaxDownload,
            value = settings.maxDownloadMtu,
            onValueChange = {
                onSettingsChange(settings.copy(maxDownloadMtu = it.filter(Char::isDigit)))
            },
            placeholder = "3000",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingResolverRetries,
            value = settings.mtuTestRetriesResolvers,
            onValueChange = {
                onSettingsChange(settings.copy(mtuTestRetriesResolvers = it.filter(Char::isDigit)))
            },
            placeholder = "3",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingResolverTimeout,
            value = settings.mtuTestTimeoutResolvers,
            onValueChange = {
                onSettingsChange(settings.copy(mtuTestTimeoutResolvers = filterDecimalInput(it)))
            },
            placeholder = "2.0",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    MtuParallelismSlider(
        parallelism = settings.mtuTestParallelismResolvers.toMtuParallelismSliderValue(),
        enabled = true,
        onParallelismChange = { parallelism ->
            onSettingsChange(settings.copy(mtuTestParallelismResolvers = parallelism.toString()))
        },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingLogsRetries,
            value = settings.mtuTestRetriesLogs,
            onValueChange = {
                onSettingsChange(settings.copy(mtuTestRetriesLogs = it.filter(Char::isDigit)))
            },
            placeholder = "5",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingLogsTimeout,
            value = settings.mtuTestTimeoutLogs,
            onValueChange = {
                onSettingsChange(settings.copy(mtuTestTimeoutLogs = filterDecimalInput(it)))
            },
            placeholder = "2.0",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    WhiteZiaTextField(
        label = WhiteZiaL10n.settingLogsParallel,
        value = settings.mtuTestParallelismLogs,
        onValueChange = {
            onSettingsChange(settings.copy(mtuTestParallelismLogs = it.filter(Char::isDigit)))
        },
        placeholder = "32",
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            capitalization = KeyboardCapitalization.None,
        ),
    )
}

@Composable
private fun RuntimeWorkersSettingsGroup(
    settings: WhiteZiaSettings,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingRxTxWorkers,
            value = settings.rxTxWorkers,
            onValueChange = {
                onSettingsChange(settings.copy(rxTxWorkers = it.filter(Char::isDigit)))
            },
            placeholder = "4",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingProcessWorkers,
            value = settings.tunnelProcessWorkers,
            onValueChange = {
                onSettingsChange(settings.copy(tunnelProcessWorkers = it.filter(Char::isDigit)))
            },
            placeholder = "4",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingTunnelPacketTimeout,
            value = settings.tunnelPacketTimeoutSeconds,
            onValueChange = {
                onSettingsChange(settings.copy(tunnelPacketTimeoutSeconds = filterDecimalInput(it)))
            },
            placeholder = "10.0",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingIdlePoll,
            value = settings.dispatcherIdlePollIntervalSeconds,
            onValueChange = {
                onSettingsChange(settings.copy(dispatcherIdlePollIntervalSeconds = filterDecimalInput(it)))
            },
            placeholder = "0.020",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingTxChannel,
            value = settings.txChannelSize,
            onValueChange = {
                onSettingsChange(settings.copy(txChannelSize = it.filter(Char::isDigit)))
            },
            placeholder = "2048",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingRxChannel,
            value = settings.rxChannelSize,
            onValueChange = {
                onSettingsChange(settings.copy(rxChannelSize = it.filter(Char::isDigit)))
            },
            placeholder = "2048",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingUdpPool,
            value = settings.resolverUdpConnectionPoolSize,
            onValueChange = {
                onSettingsChange(settings.copy(resolverUdpConnectionPoolSize = it.filter(Char::isDigit)))
            },
            placeholder = "64",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingStreamQueue,
            value = settings.streamQueueInitialCapacity,
            onValueChange = {
                onSettingsChange(settings.copy(streamQueueInitialCapacity = it.filter(Char::isDigit)))
            },
            placeholder = "128",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingOrphanQueue,
            value = settings.orphanQueueInitialCapacity,
            onValueChange = {
                onSettingsChange(settings.copy(orphanQueueInitialCapacity = it.filter(Char::isDigit)))
            },
            placeholder = "32",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingDnsFragments,
            value = settings.dnsResponseFragmentStoreCapacity,
            onValueChange = {
                onSettingsChange(settings.copy(dnsResponseFragmentStoreCapacity = it.filter(Char::isDigit)))
            },
            placeholder = "256",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingSocksUdpTimeout,
            value = settings.socksUdpAssociateReadTimeoutSeconds,
            onValueChange = {
                onSettingsChange(settings.copy(socksUdpAssociateReadTimeoutSeconds = filterDecimalInput(it)))
            },
            placeholder = "30.0",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingTerminalRetain,
            value = settings.clientTerminalStreamRetentionSeconds,
            onValueChange = {
                onSettingsChange(settings.copy(clientTerminalStreamRetentionSeconds = filterDecimalInput(it)))
            },
            placeholder = "45.0",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingCancelledRetain,
            value = settings.clientCancelledSetupRetentionSeconds,
            onValueChange = {
                onSettingsChange(settings.copy(clientCancelledSetupRetentionSeconds = filterDecimalInput(it)))
            },
            placeholder = "120.0",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingRetryBase,
            value = settings.sessionInitRetryBaseSeconds,
            onValueChange = {
                onSettingsChange(settings.copy(sessionInitRetryBaseSeconds = filterDecimalInput(it)))
            },
            placeholder = "1.0",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingRetryStep,
            value = settings.sessionInitRetryStepSeconds,
            onValueChange = {
                onSettingsChange(settings.copy(sessionInitRetryStepSeconds = filterDecimalInput(it)))
            },
            placeholder = "1.0",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingRetryLinear,
            value = settings.sessionInitRetryLinearAfter,
            onValueChange = {
                onSettingsChange(settings.copy(sessionInitRetryLinearAfter = it.filter(Char::isDigit)))
            },
            placeholder = "5",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingRetryMax,
            value = settings.sessionInitRetryMaxSeconds,
            onValueChange = {
                onSettingsChange(settings.copy(sessionInitRetryMaxSeconds = filterDecimalInput(it)))
            },
            placeholder = "60.0",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingBusyRetry,
            value = settings.sessionInitBusyRetryIntervalSeconds,
            onValueChange = {
                onSettingsChange(settings.copy(sessionInitBusyRetryIntervalSeconds = filterDecimalInput(it)))
            },
            placeholder = "60.0",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
}
