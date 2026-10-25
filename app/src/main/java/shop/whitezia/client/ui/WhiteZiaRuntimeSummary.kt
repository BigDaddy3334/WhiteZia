package shop.whitezia.client.ui


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import shop.whitezia.client.R
import shop.whitezia.client.model.ConnectionProgressState
import shop.whitezia.client.model.ConnectionStatus
import shop.whitezia.client.model.ConnectionVerificationState
import shop.whitezia.client.model.ConnectionVerificationStatus
import shop.whitezia.client.model.ResolverRuntimeState

private enum class ResolverRuntimeDialogType {
    ACTIVE,
    VALID,
}

@Composable
internal fun ResolverRuntimeSummary(
    resolverState: ResolverRuntimeState,
    progressState: ConnectionProgressState,
    connectionStatus: ConnectionStatus,
    modifier: Modifier = Modifier,
) {
    var selectedDialog by remember { mutableStateOf<ResolverRuntimeDialogType?>(null) }
    val activeResolverCount = resolverState.activeResolvers.size.takeIf { it > 0 }?.toString() ?: WhiteZiaL10n.resolverPending
    val backgroundMtuScanInProgress = connectionStatus == ConnectionStatus.CONNECTED &&
        progressState.phase.lowercase() == "mtu" &&
        progressState.total > 0 &&
        progressState.completed < progressState.total

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ResolverRuntimeValue(
                modifier = Modifier.weight(1f),
                label = WhiteZiaL10n.resolverActiveResolvers,
                value = activeResolverCount,
                onClick = { selectedDialog = ResolverRuntimeDialogType.ACTIVE },
            )
            ResolverRuntimeValue(
                modifier = Modifier.weight(1f),
                label = WhiteZiaL10n.resolverValidResolvers,
                value = resolverState.validResolvers.size.toString(),
                onClick = { selectedDialog = ResolverRuntimeDialogType.VALID },
            )
        }
        AnimatedVisibility(
            visible = backgroundMtuScanInProgress,
            enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(animationSpec = tween(120)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(WhiteZiaPalette.AccentSurface)
                    .border(1.5.dp, WhiteZiaPalette.Accent.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Enhanced progress indicator with percentage display
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(20.dp)
                ) {
                    if (progressState.total > 0 && progressState.completed > 0) {
                        // Determinate progress when total is known
                        CircularProgressIndicator(
                            progress = { progressState.completed.toFloat() / progressState.total.toFloat() },
                            modifier = Modifier.size(20.dp),
                            color = WhiteZiaPalette.Accent,
                            strokeWidth = 2.5.dp,
                        )
                        // Show percentage inside circle
                        Text(
                            text = "${(progressState.completed * 100 / progressState.total)}%",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 7.sp,
                                color = WhiteZiaPalette.Accent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    } else {
                        // Indeterminate progress when total is unknown
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = WhiteZiaPalette.Accent,
                            strokeWidth = 2.5.dp,
                        )
                    }
                }
                Text(
                    text = if (progressState.completed > 0) {
                        "${WhiteZiaL10n.backgroundScanningInProgress} ${progressState.completed}/${progressState.total}"
                    } else {
                        WhiteZiaL10n.backgroundScanningInProgress
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 11.sp,
                        color = WhiteZiaPalette.AccentText,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }

    selectedDialog?.let { dialog ->
        val title = when (dialog) {
            ResolverRuntimeDialogType.ACTIVE -> WhiteZiaL10n.resolverActiveResolvers
            ResolverRuntimeDialogType.VALID -> WhiteZiaL10n.resolverValidResolvers
        }
        val resolvers = when (dialog) {
            ResolverRuntimeDialogType.ACTIVE -> resolverState.activeResolvers
            ResolverRuntimeDialogType.VALID -> resolverState.validResolvers
        }
        ResolverRuntimeDialog(
            title = title,
            resolvers = resolvers,
            onDismiss = { selectedDialog = null },
        )
    }
}

@Composable
private fun ResolverRuntimeValue(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val statDetailDescription = stringResource(R.string.cd_stat_card_detail, label, value)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(WhiteZiaPalette.Surface)
            .border(1.5.dp, WhiteZiaPalette.Border, RoundedCornerShape(10.dp))
            .semantics {
                contentDescription = statDetailDescription
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 9.sp,
                color = WhiteZiaPalette.Muted,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            ),
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
        Text(
            text = value,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.sp,
                color = WhiteZiaPalette.Ink,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
internal fun ConnectionVerificationSummary(
    verification: ConnectionVerificationState,
    modifier: Modifier = Modifier,
) {
    val statusText = when (verification.status) {
        ConnectionVerificationStatus.Checking -> WhiteZiaL10n.verificationVerifying
        ConnectionVerificationStatus.Verified -> if (verification.coverage == shop.whitezia.client.model.ConnectionVerificationCoverage.SocksProxy) "Прокси работает" else WhiteZiaL10n.verificationVerified
        ConnectionVerificationStatus.Failed -> WhiteZiaL10n.verificationNeedsAttention
        else -> WhiteZiaL10n.verificationPending
    }
    val message = verificationDisplayMessage(verification)
    val color = when (verification.status) {
        ConnectionVerificationStatus.Verified -> WhiteZiaPalette.Success
        ConnectionVerificationStatus.Failed -> WhiteZiaPalette.WarningText
        ConnectionVerificationStatus.Checking -> WhiteZiaPalette.Accent
        else -> WhiteZiaPalette.Muted
    }
    val surface = when (verification.status) {
        ConnectionVerificationStatus.Verified -> WhiteZiaPalette.SuccessSurface
        ConnectionVerificationStatus.Failed -> WhiteZiaPalette.WarningSurface
        ConnectionVerificationStatus.Checking -> WhiteZiaPalette.AccentSurface
        else -> WhiteZiaPalette.Surface
    }
    val icon = when (verification.status) {
        ConnectionVerificationStatus.Verified -> Icons.Rounded.Check
        ConnectionVerificationStatus.Failed -> Icons.Rounded.WarningAmber
        ConnectionVerificationStatus.Checking -> Icons.Rounded.Tune
        else -> Icons.Rounded.Link
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(surface)
            .border(1.5.dp, color.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(
                when (verification.status) {
                    ConnectionVerificationStatus.Verified -> R.string.cd_verification_success
                    ConnectionVerificationStatus.Failed -> R.string.cd_verification_failed
                    ConnectionVerificationStatus.Checking -> R.string.cd_verification_pending
                    else -> R.string.cd_icon_link
                }
            ),
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = statusText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 10.sp,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
            Text(
                text = message,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = WhiteZiaPalette.Description,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

@Composable
private fun verificationDisplayMessage(verification: ConnectionVerificationState): String {
    val rawMessage = verification.message
    if (rawMessage.isBlank()) {
        return if (verification.status == ConnectionVerificationStatus.Idle) {
            WhiteZiaL10n.verificationNotRunYet
        } else {
            WhiteZiaL10n.verificationCheckingRoute
        }
    }
    return when (rawMessage) {
        "Connection verified: proxy tunnel can reach the internet" -> WhiteZiaL10n.verificationProxyReachable
        "Connection verified: VPN tunnel can reach the internet" -> "Прокси работает; TUN/FakeDNS отдельно не проверены"
        "Connection ready: proxy tunnel is active; outbound probe is still warming up" -> {
            WhiteZiaL10n.verificationProxyWarming
        }
        "Connection ready: VPN tunnel is active; outbound probe is still warming up" -> {
            WhiteZiaL10n.verificationVpnWarming
        }
        "Connection mode changed before verification finished" -> WhiteZiaL10n.verificationModeChanged
        "Connection verification failed: local SOCKS listener is not reachable" -> {
            WhiteZiaL10n.verificationSocksNotReachable
        }
        "Connection verification failed: VPN interface is not active" -> WhiteZiaL10n.verificationVpnInterfaceInactive
        else -> rawMessage
    }
}

@Composable
private fun ResolverRuntimeDialog(
    title: String,
    resolvers: List<String>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val resolverText = resolvers.joinToString("\n")
    val whiteZiaResolversLabel = WhiteZiaL10n.whiteZiaResolversLabel

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(WhiteZiaPalette.Surface)
                .border(1.5.dp, WhiteZiaPalette.Border, RoundedCornerShape(22.dp))
                .padding(18.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = WhiteZiaPalette.Ink,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            WhiteZiaTextField(
                label = WhiteZiaL10n.profileFieldResolvers,
                value = resolverText,
                onValueChange = {},
                placeholder = WhiteZiaL10n.noResolversPlaceholder,
                singleLine = false,
                minLines = 6,
                maxLines = 12,
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactActionButton(
                    modifier = Modifier.weight(1f),
                    label = WhiteZiaL10n.btnClose,
                    emphasized = false,
                    enabled = true,
                    onClick = onDismiss,
                )
                CompactActionButton(
                    modifier = Modifier.weight(1f),
                    label = WhiteZiaL10n.btnCopy,
                    emphasized = true,
                    enabled = resolverText.isNotBlank(),
                    onClick = {
                        copyTextToClipboard(
                            context = context,
                            label = whiteZiaResolversLabel,
                            text = resolverText,
                            sensitive = false,
                        )
                    },
                )
            }
        }
    }
}
