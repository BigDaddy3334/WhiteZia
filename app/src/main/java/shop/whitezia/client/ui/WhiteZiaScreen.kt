package shop.whitezia.client.ui

import android.net.Uri

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.WhiteZiaUiState

@Composable
fun WhiteZiaScreen(
    uiState: WhiteZiaUiState,
    onBatteryOptimizationClick: () -> Unit,
    onNotificationPermissionClick: () -> Unit,
    onConnectClick: () -> Unit,
    onScanFileSelected: (Uri) -> Unit,
    onScanDefaultListSelected: () -> Unit,
    onScanStartClick: () -> Unit,
    onScanConnectionProfileChange: (String) -> Unit,
    onScanWorkerCountChange: (String) -> Unit,
    onScanStopClick: () -> Unit,
    onScanResumeClick: () -> Unit,
    onServerTestClick: (String?) -> Unit,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(WhiteZiaTab.CONNECT) }
    var profileCreateRequest by rememberSaveable { mutableStateOf<ProfileCreateRequest?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .whiteZiaPageBackground(),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                WhiteZiaTab.PROFILES -> ProfilesTabContent(
                    uiState = uiState,
                    createRequest = profileCreateRequest,
                    onCreateRequestConsumed = {
                        profileCreateRequest = null
                    },
                    onServerTestClick = onServerTestClick,
                    onSettingsChange = onSettingsChange,
                )
                WhiteZiaTab.CONNECT -> ConnectTabContent(
                    uiState = uiState,
                    onBatteryOptimizationClick = onBatteryOptimizationClick,
                    onNotificationPermissionClick = onNotificationPermissionClick,
                    onConnectClick = onConnectClick,
                    onAddConnectionClick = {
                        profileCreateRequest = ProfileCreateRequest.CONNECTION
                        selectedTab = WhiteZiaTab.PROFILES
                    },
                    onAddResolverProfileClick = {
                        profileCreateRequest = ProfileCreateRequest.RESOLVER
                        selectedTab = WhiteZiaTab.PROFILES
                    },
                    onSettingsChange = onSettingsChange,
                )
                WhiteZiaTab.SCAN -> ScanTabContent(
                    uiState = uiState,
                    onScanFileSelected = onScanFileSelected,
                    onScanDefaultListSelected = onScanDefaultListSelected,
                    onScanStartClick = onScanStartClick,
                    onScanConnectionProfileChange = onScanConnectionProfileChange,
                    onScanWorkerCountChange = onScanWorkerCountChange,
                    onScanStopClick = onScanStopClick,
                    onScanResumeClick = onScanResumeClick,
                    onSettingsChange = onSettingsChange,
                )
                WhiteZiaTab.LOGS -> LogsTabContent(
                    uiState = uiState,
                    onSettingsChange = onSettingsChange,
                )
            }
        }
        BottomNavigationBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
        )
    }
}

internal enum class WhiteZiaTab(
    val label: String,
    val icon: ImageVector,
) {
    PROFILES("Profiles", Icons.Filled.Apps),
    CONNECT("Connect", Icons.Rounded.PowerSettingsNew),
    SCAN("Scan", Icons.Rounded.Search),
    LOGS("Logs", Icons.Rounded.Link),
}

internal enum class ProfileCreateRequest {
    CONNECTION,
    RESOLVER,
}

@Composable
private fun BottomNavigationBar(
    selectedTab: WhiteZiaTab,
    onTabSelected: (WhiteZiaTab) -> Unit,
) {
    val haptic = rememberHapticFeedback()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WhiteZiaPalette.SurfaceAlt)
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, WhiteZiaPalette.Border)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WhiteZiaTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                val localizedLabel = tabLabel(tab)
                val background by animateColorAsState(
                    targetValue = if (selected) WhiteZiaPalette.AccentSurface else Color.Transparent,
                    animationSpec = tween(180),
                    label = "bottomNavBackground",
                )
                val color by animateColorAsState(
                    targetValue = if (selected) WhiteZiaPalette.AccentText else WhiteZiaPalette.Disabled,
                    animationSpec = tween(180),
                    label = "bottomNavColor",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(background)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = {
                                haptic.performLight()
                                onTabSelected(tab)
                            },
                        )
                        .semantics(mergeDescendants = true) {}
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = localizedLabel,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 9.sp,
                            color = color,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            letterSpacing = 0.5.sp,
                        ),
                    )
                }
            }
        }
    }
}
