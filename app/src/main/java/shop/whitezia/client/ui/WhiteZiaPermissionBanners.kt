package shop.whitezia.client.ui


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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import shop.whitezia.client.R

@Composable
internal fun NotificationPermissionBanner(onClick: () -> Unit) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()
    val enableNotificationDescription = stringResource(R.string.cd_enable_vpn_notification)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WhiteZiaPalette.WarningSurface)
            .border(1.5.dp, WhiteZiaPalette.Warning.copy(alpha = 0.26f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(
            text = WhiteZiaL10n.bannerNotificationBlockedTitle,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 10.sp,
                color = WhiteZiaPalette.WarningText,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
            ),
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.iconSpacing))
        Text(
            text = WhiteZiaL10n.bannerNotificationBlockedBody,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = WhiteZiaPalette.WarningText,
            ),
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.inputSpacing))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(WhiteZiaPalette.Surface)
                .border(1.5.dp, WhiteZiaPalette.Warning.copy(alpha = 0.32f), RoundedCornerShape(10.dp))
                .semantics { contentDescription = enableNotificationDescription }
                .clickable {
                    haptic.performMedium()
                    onClick()
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = WhiteZiaL10n.bannerEnableNotification,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 9.sp,
                    color = WhiteZiaPalette.WarningText,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                ),
            )
        }
    }
}

@Composable
internal fun BatteryOptimizationBanner(
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()
    val allowBackgroundDescription = stringResource(R.string.cd_allow_background_vpn)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WhiteZiaPalette.WarningSurface)
            .border(1.5.dp, WhiteZiaPalette.Warning.copy(alpha = 0.26f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = WhiteZiaL10n.bannerBatteryTitle,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 10.sp,
                    color = WhiteZiaPalette.WarningText,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                ),
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable {
                        haptic.performLight()
                        onDismiss()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = WhiteZiaL10n.cdDismissBatteryWarning,
                    tint = WhiteZiaPalette.WarningText,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.iconSpacing))
        Text(
            text = WhiteZiaL10n.bannerBatteryBody,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = WhiteZiaPalette.WarningText,
            ),
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.inputSpacing))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(WhiteZiaPalette.Surface)
                .border(1.5.dp, WhiteZiaPalette.Warning.copy(alpha = 0.32f), RoundedCornerShape(10.dp))
                .semantics { contentDescription = allowBackgroundDescription }
                .clickable {
                    haptic.performMedium()
                    onClick()
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = WhiteZiaL10n.bannerAllowBackground,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 9.sp,
                    color = WhiteZiaPalette.WarningText,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                ),
            )
        }
    }
}

@Composable
internal fun FullVpnPerformanceWarning(onDismiss: () -> Unit) {
    val haptic = rememberHapticFeedback()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WhiteZiaPalette.WarningSurface)
            .border(1.5.dp, WhiteZiaPalette.Warning.copy(alpha = 0.24f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Rounded.WarningAmber,
            contentDescription = stringResource(R.string.cd_icon_warning),
            tint = WhiteZiaPalette.WarningText,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = WhiteZiaL10n.bannerFullVpnWarningTitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 9.sp,
                    color = WhiteZiaPalette.WarningText,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
            Text(
                text = WhiteZiaL10n.bannerFullVpnWarningBody,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = WhiteZiaPalette.WarningText,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable {
                    haptic.performLight()
                    onDismiss()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = WhiteZiaL10n.cdDismissVpnWarning,
                tint = WhiteZiaPalette.WarningText,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
