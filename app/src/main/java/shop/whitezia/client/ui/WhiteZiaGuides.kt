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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
internal fun SettingProfileGuideButton(
    onClick: () -> Unit,
) {
    val haptic = rememberHapticFeedback()
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(WhiteZiaPalette.AccentSurface)
            .border(1.5.dp, WhiteZiaPalette.Accent.copy(alpha = 0.26f), CircleShape)
            .clickable {
                haptic.performLight()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
            contentDescription = WhiteZiaL10n.cdSettingGuide,
            tint = WhiteZiaPalette.AccentText,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
internal fun SettingProfileGuideDialog(
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(WhiteZiaPalette.Surface)
                .border(1.5.dp, WhiteZiaPalette.Border, RoundedCornerShape(22.dp))
                .padding(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = WhiteZiaL10n.settingGuideTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        color = WhiteZiaPalette.Ink,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                    ),
                )
                ProfileIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = WhiteZiaL10n.btnClose,
                    emphasized = false,
                    enabled = true,
                    onClick = onDismiss,
                )
            }
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
            Text(
                text = WhiteZiaL10n.settingGuideIntro,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = WhiteZiaPalette.Description,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
            Text(
                text = WhiteZiaL10n.settingGuideSource,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 9.sp,
                    lineHeight = 13.sp,
                    color = WhiteZiaPalette.Pale,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                WhiteZiaL10n.settingGuideSections.forEachIndexed { index, section ->
                    if (index > 0) {
                        SectionDivider()
                    }
                    SettingsGuideSectionView(section = section)
                }
            }
        }
    }
}

@Composable
private fun SettingsGuideSectionView(
    section: SettingsGuideSection,
) {
    Text(
        text = section.title.uppercase(),
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 12.sp,
            color = WhiteZiaPalette.SectionTitle,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        ),
    )
    Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
    section.entries.forEachIndexed { index, entry ->
        if (index > 0) {
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
        }
        SettingsGuideEntryView(entry = entry)
    }
}

@Composable
private fun SettingsGuideEntryView(
    entry: SettingsGuideEntry,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(WhiteZiaPalette.SurfaceAlt)
            .border(1.5.dp, WhiteZiaPalette.Border, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = WhiteZiaPalette.Ink,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
        Text(
            text = entry.body,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = WhiteZiaPalette.Description,
            ),
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
        Text(
            text = "${WhiteZiaL10n.settingGuideEffectLabel}: ${entry.effect}",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = WhiteZiaPalette.AccentText,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}
