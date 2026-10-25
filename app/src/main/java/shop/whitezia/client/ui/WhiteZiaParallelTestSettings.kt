package shop.whitezia.client.ui


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import shop.whitezia.client.R
import shop.whitezia.client.model.AdvancedSettingsProfile
import shop.whitezia.client.model.AutoTuneTrialResult
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.applyAdvancedProfile
import shop.whitezia.client.model.applyAutoTunePreset
import shop.whitezia.client.model.WhiteZiaAutoTunePresets
import shop.whitezia.client.model.WhiteZiaParallelTest
import shop.whitezia.client.model.syncSelectedConnectionProfileFields

internal fun parallelTestInitialSettingsForResult(
    result: AutoTuneTrialResult,
    settings: WhiteZiaSettings,
    advancedProfiles: List<AdvancedSettingsProfile>,
): WhiteZiaSettings? {
    WhiteZiaParallelTest.presetIdFromConfigId(result.configId)?.let { presetId ->
        val preset = WhiteZiaAutoTunePresets.all.firstOrNull { it.id == presetId } ?: return null
        return settings
            .applyAutoTunePreset(preset)
            .copy(autoTuneEnabled = false)
            .syncSelectedConnectionProfileFields()
    }
    WhiteZiaParallelTest.settingProfileIdFromConfigId(result.configId)?.let { profileId ->
        val profile = advancedProfiles.firstOrNull { it.id == profileId } ?: return null
        return settings
            .applyAdvancedProfile(profile)
            .copy(autoTuneEnabled = false)
            .syncSelectedConnectionProfileFields()
    }
    return null
}

@Composable
internal fun ParallelTestSelectionPanel(
    settings: WhiteZiaSettings,
    advancedProfiles: List<AdvancedSettingsProfile>,
    controlsEnabled: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()
    val collapseDescription = stringResource(R.string.cd_parallel_test_collapse)
    val expandDescription = stringResource(R.string.cd_parallel_test_expand)
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "parallelTestSelectionArrow",
    )
    val userProfiles = advancedProfiles.filter { it.id != AdvancedSettingsProfile.DefaultId }
    val selectedIds = WhiteZiaParallelTest.normalizeConfigIds(
        configIds = settings.parallelTestSelectedConfigIds,
        advancedProfiles = advancedProfiles,
        includeAggressive = settings.parallelTestAggressivePresetsEnabled,
    )
    val selectedSet = selectedIds.toSet()
    val stableWhiteZiaConfigIds = WhiteZiaParallelTest.stableConfigIds
    val aggressiveWhiteZiaConfigIds = WhiteZiaParallelTest.aggressiveConfigIds
    val selectedStableWhiteZiaCount = stableWhiteZiaConfigIds.count { it in selectedSet }
    val allStableWhiteZiaSelected = selectedStableWhiteZiaCount == stableWhiteZiaConfigIds.size
    val canAddStableWhiteZiaConfigs = allStableWhiteZiaSelected ||
        selectedIds.size - selectedStableWhiteZiaCount + stableWhiteZiaConfigIds.size <=
        WhiteZiaParallelTest.MaxSelectedConfigs
    val selectedAggressiveWhiteZiaCount = aggressiveWhiteZiaConfigIds.count { it in selectedSet }
    val allAggressiveWhiteZiaSelected = settings.parallelTestAggressivePresetsEnabled &&
        selectedAggressiveWhiteZiaCount == aggressiveWhiteZiaConfigIds.size
    val canAddAggressiveWhiteZiaConfigs = allAggressiveWhiteZiaSelected ||
        selectedIds.size - selectedAggressiveWhiteZiaCount + aggressiveWhiteZiaConfigIds.size <=
        WhiteZiaParallelTest.MaxSelectedConfigs

    fun updateSelectedConfigIds(
        nextIds: List<String>,
        includeAggressive: Boolean = settings.parallelTestAggressivePresetsEnabled,
    ) {
        val normalizedIds = WhiteZiaParallelTest.normalizeConfigIds(
            configIds = nextIds,
            advancedProfiles = advancedProfiles,
            defaultIfEmpty = false,
            includeAggressive = includeAggressive,
        )
        val keepsAggressive = includeAggressive && aggressiveWhiteZiaConfigIds.any { it in normalizedIds }
        onSettingsChange(
            settings.copy(
                parallelTestSelectedConfigIds = normalizedIds,
                parallelTestAggressivePresetsEnabled = keepsAggressive,
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = WhiteZiaSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(WhiteZiaSpacing.sm),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .semantics {
                    contentDescription = if (expanded) {
                        collapseDescription
                    } else {
                        expandDescription
                    }
                }
                .clickable {
                    haptic.performLight()
                    onExpandedChange(!expanded)
                }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${WhiteZiaL10n.connectSelectedCount} ${selectedIds.size}/${WhiteZiaParallelTest.MaxSelectedConfigs}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    color = WhiteZiaPalette.FieldLabel,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (expanded) WhiteZiaPalette.Accent else WhiteZiaPalette.SurfaceAlt)
                    .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (expanded) WhiteZiaL10n.parallelTestOpenLabel else WhiteZiaL10n.parallelTestClosedLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 9.sp,
                        color = if (expanded) WhiteZiaPalette.OnAccent else WhiteZiaPalette.Muted,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.8.sp,
                    ),
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) {
                        WhiteZiaL10n.parallelTestCollapseDescription
                    } else {
                        WhiteZiaL10n.parallelTestExpandDescription
                    },
                    tint = if (expanded) WhiteZiaPalette.OnAccent else WhiteZiaPalette.Muted,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer(rotationZ = arrowRotation),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(140)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(WhiteZiaSpacing.sm),
            ) {
                Text(
                    text = WhiteZiaL10n.parallelTestDescription,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = WhiteZiaPalette.Muted,
                        lineHeight = 16.sp,
                    ),
                )
                ParallelTestConfigRow(
                    label = WhiteZiaL10n.whiteZiaConfigsLabel,
                    detail = WhiteZiaL10n.whiteZiaConfigsDescription,
                    checked = allStableWhiteZiaSelected,
                    enabled = controlsEnabled && canAddStableWhiteZiaConfigs,
                    onToggle = {
                        haptic.performLight()
                        val withoutStableWhiteZiaConfigs = selectedIds.filterNot { it in stableWhiteZiaConfigIds }
                        if (allStableWhiteZiaSelected) {
                            if (withoutStableWhiteZiaConfigs.isNotEmpty()) {
                                updateSelectedConfigIds(withoutStableWhiteZiaConfigs)
                            }
                        } else if (canAddStableWhiteZiaConfigs) {
                            updateSelectedConfigIds(stableWhiteZiaConfigIds + withoutStableWhiteZiaConfigs)
                        }
                    },
                )
                ParallelTestConfigRow(
                    label = WhiteZiaL10n.whiteZiaAggressiveConfigsLabel,
                    detail = WhiteZiaL10n.whiteZiaAggressiveConfigsDescription,
                    checked = allAggressiveWhiteZiaSelected,
                    enabled = controlsEnabled && canAddAggressiveWhiteZiaConfigs,
                    onToggle = {
                        haptic.performLight()
                        val withoutAggressiveWhiteZiaConfigs = selectedIds.filterNot {
                            it in aggressiveWhiteZiaConfigIds
                        }
                        if (allAggressiveWhiteZiaSelected) {
                            updateSelectedConfigIds(
                                withoutAggressiveWhiteZiaConfigs,
                                includeAggressive = false,
                            )
                        } else if (canAddAggressiveWhiteZiaConfigs) {
                            updateSelectedConfigIds(
                                withoutAggressiveWhiteZiaConfigs + aggressiveWhiteZiaConfigIds,
                                includeAggressive = true,
                            )
                        }
                    },
                )
                if (userProfiles.isNotEmpty()) {
                    Text(
                        modifier = Modifier.padding(top = WhiteZiaSpacing.xs),
                        text = WhiteZiaL10n.parallelTestYourConfigs,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            color = WhiteZiaPalette.FieldLabel,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    userProfiles.forEach { profile ->
                        val configId = WhiteZiaParallelTest.settingConfigId(profile.id)
                        val checked = configId in selectedSet
                        val enabled = checked || selectedIds.size < WhiteZiaParallelTest.MaxSelectedConfigs
                        ParallelTestConfigRow(
                            label = profile.name.ifBlank { WhiteZiaL10n.genericSettingFallback },
                            detail = advancedProfileSummary(profile),
                            checked = checked,
                            enabled = controlsEnabled && enabled,
                            onToggle = {
                                haptic.performLight()
                                val nextIds = if (checked) {
                                    if (selectedIds.size == 1) {
                                        selectedIds
                                    } else {
                                        selectedIds.filterNot { it == configId }
                                    }
                                } else {
                                    selectedIds + configId
                                }
                                updateSelectedConfigIds(nextIds)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParallelTestConfigRow(
    label: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val contentAlpha = if (enabled) 1f else 0.46f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .padding(vertical = 7.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics {},
            colors = CheckboxDefaults.colors(
                checkedColor = WhiteZiaPalette.Accent,
                uncheckedColor = WhiteZiaPalette.ControlBorder,
                checkmarkColor = WhiteZiaPalette.OnAccent,
                disabledCheckedColor = WhiteZiaPalette.Accent.copy(alpha = 0.42f),
                disabledUncheckedColor = WhiteZiaPalette.ControlBorder.copy(alpha = 0.42f),
            ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.sp,
                    color = WhiteZiaPalette.Ink.copy(alpha = contentAlpha),
                    fontWeight = FontWeight.Medium,
                ),
            )
            Text(
                text = detail,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 10.sp,
                    color = WhiteZiaPalette.Muted.copy(alpha = contentAlpha),
                    lineHeight = 14.sp,
                ),
            )
        }
    }
}
