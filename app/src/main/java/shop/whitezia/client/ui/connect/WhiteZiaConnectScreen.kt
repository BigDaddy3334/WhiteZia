package shop.whitezia.client.ui.connect
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.delay
import shop.whitezia.client.model.ConnectionStatus
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.ui.WhiteZiaBackground
import shop.whitezia.client.ui.WhiteZiaBlue
import shop.whitezia.client.ui.WhiteZiaError
import shop.whitezia.client.ui.WhiteZiaPanel
import shop.whitezia.client.ui.WhiteZiaRed
import shop.whitezia.client.ui.WhiteZiaSetupOrange
import shop.whitezia.client.ui.WhiteZiaSuccess
import shop.whitezia.client.ui.WhiteZiaTextDim
import shop.whitezia.client.ui.WhiteZiaSmallTextStyle
import shop.whitezia.client.ui.WhiteZiaTextMuted
import shop.whitezia.client.ui.WhiteZiaInk
import shop.whitezia.client.ui.WhiteZiaPalette
@Composable
fun WhiteZiaConnectScreen(
    subscriptionLink: String,
    settings: WhiteZiaSettings,
    connectionStatus: ConnectionStatus,
    wifiEnabled: Boolean,
    errorMessage: String?,
    userStatus: String,
    isDisconnecting: Boolean,
    isSwitchingMode: Boolean,
    forceDnsTunnel: Boolean,
    xrayPreflightBlocked: Boolean,
    onConnectClick: () -> Unit,
    onXrayOnlyModeChange: (Boolean) -> Unit,
    onForceDnsTunnelChange: (Boolean) -> Unit,
    onAccountClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogClick: () -> Unit,
    onSplitTunnelClick: () -> Unit,
    backgroundVpnAllowed: Boolean = true,
    onBackgroundPermissionClick: () -> Unit = {},
) {
    val isRunning = connectionStatus != ConnectionStatus.DISCONNECTED
    val isPrimarySetup = userStatus == "производится первичная настройка"
    val isDnsPreparation = userStatus == "Подготовка DNS подключения"
    val isOptimizingConnection = userStatus == "Оптимизация подключения"
    val isProfileRefresh = userStatus == "Проверяю конфигурацию"
    val showProgress = isProfileRefresh || isPrimarySetup || connectionStatus == ConnectionStatus.CONNECTING ||
        userStatus == "Подключение" ||
        isOptimizingConnection ||
        userStatus == "Подключение через AmneziaWG" ||
        userStatus == "Подключение через Xray" ||
        userStatus == "Проверка AmneziaWG" ||
        userStatus == "Проверка Xray" ||
        userStatus == "Проверка подключения" ||
        userStatus == "Подготовка DNS подключения"
    val isConnectionFinalizing = connectionStatus == ConnectionStatus.CONNECTED && showProgress
    val isAutomaticConnectionFlow = isPrimarySetup ||
        isProfileRefresh ||
        isDnsPreparation ||
        isDisconnecting ||
        connectionStatus == ConnectionStatus.CONNECTING ||
        isConnectionFinalizing ||
        userStatus == "Подключение" ||
        userStatus == "Подключение через AmneziaWG" ||
        userStatus == "Подключение через Xray" ||
        userStatus == "Проверка AmneziaWG" ||
        userStatus == "Проверка Xray" ||
        userStatus == "Проверка подключения" ||
        userStatus == "Оптимизация подключения"
    val canForceStop = !isPrimarySetup &&
        !isDisconnecting &&
        (
            connectionStatus != ConnectionStatus.DISCONNECTED ||
                isProfileRefresh ||
                isDnsPreparation ||
                isOptimizingConnection
            )
    val canConnect = !isRunning &&
        subscriptionLink.trim().isNotEmpty() &&
        !isAutomaticConnectionFlow &&
        !xrayPreflightBlocked
    val canDisconnect = canForceStop
    val canChangeDnsMode = !isRunning && !isAutomaticConnectionFlow
    val manualMode = settings.manualMode
    val xrayOnlyEnabled = settings.transportMode == WhiteZiaOptions.TransportXray
    val minimalConnectionView = isAutomaticConnectionFlow && !isDisconnecting && !isSwitchingMode
    val statusText = when {
        isSwitchingMode -> "Смена режима"
        isDisconnecting -> "отключение"
        errorMessage != null -> {
            if (errorMessage in setOf("Выключите Wi-Fi", "Включите мобильный интернет", "Нет подключения к интернету")) {
                errorMessage.orEmpty()
            } else {
                "ошибка, попробуйте снова"
            }
        }
        minimalConnectionView -> "идет подключение, это может занять пару минут"
        connectionStatus == ConnectionStatus.CONNECTED -> "успешное подключение"
        else -> userStatus.ifBlank { "Готово к подключению" }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = WhiteZiaBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WhiteZiaLogo(modifier = Modifier.weight(1f))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(modifier = Modifier.size(40.dp), onClick = onAccountClick) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = "Личный кабинет",
                                tint = WhiteZiaTextMuted,
                            )
                        }
                        IconButton(modifier = Modifier.size(40.dp), onClick = onLogClick) {
                            Icon(
                                imageVector = Icons.Rounded.Article,
                                contentDescription = "Логи",
                                tint = WhiteZiaTextMuted,
                            )
                        }
                        IconButton(
                            modifier = Modifier.size(40.dp),
                            enabled = !isAutomaticConnectionFlow,
                            onClick = onSettingsClick,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Настройки",
                                tint = WhiteZiaTextMuted,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = statusText,
                    style = WhiteZiaStatusTextStyle(),
                    textAlign = TextAlign.Center,
                    color = when {
                        errorMessage != null -> WhiteZiaError
                        connectionStatus == ConnectionStatus.CONNECTED -> WhiteZiaSuccess
                        minimalConnectionView -> WhiteZiaBlue
                        isPrimarySetup -> WhiteZiaSetupOrange
                        showProgress -> WhiteZiaBlue
                        statusText == "Готово к подключению" -> WhiteZiaSuccess
                        else -> WhiteZiaTextMuted
                    },
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (manualMode && !minimalConnectionView) {
                    Spacer(modifier = Modifier.height(18.dp))
                    ForceDnsTunnelSwitch(
                        enabled = forceDnsTunnel,
                        interactiveEnabled = canChangeDnsMode,
                        wifiEnabled = wifiEnabled,
                        onToggle = { onForceDnsTunnelChange(!forceDnsTunnel) },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    XrayOnlySwitch(
                        enabled = xrayOnlyEnabled,
                        interactiveEnabled = canChangeDnsMode,
                        xrayAvailable = settings.xrayUri.isNotBlank(),
                        onToggle = { onXrayOnlyModeChange(!xrayOnlyEnabled) },
                    )
                }
                if (!isAutomaticConnectionFlow) {
                    if (!backgroundVpnAllowed) {
                        TextButton(onClick = onBackgroundPermissionClick) {
                            Icon(Icons.Rounded.BatteryAlert, contentDescription = null, tint = WhiteZiaSetupOrange)
                            Spacer(Modifier.width(8.dp))
                            Text("Фоновая работа ограничена", maxLines = 2, textAlign = TextAlign.Center)
                        }
                    }
                    TextButton(onClick = onSplitTunnelClick) {
                        Icon(Icons.Rounded.Apps, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Раздельное туннелирование", maxLines = 2, textAlign = TextAlign.Center)
                    }
                    if (settings.splitTunnelMode != WhiteZiaOptions.SplitTunnelModeOff) {
                        Text(
                            text = WhiteZiaOptions.splitTunnelModeLabel(settings.splitTunnelMode),
                            color = WhiteZiaTextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularConnectionButton(
                    connectionStatus = connectionStatus,
                    enabled = canConnect || canDisconnect,
                    isError = errorMessage != null,
                    isDisconnecting = isDisconnecting && !isSwitchingMode,
                    isFinalizing = isConnectionFinalizing,
                    isPrimarySetup = isPrimarySetup,
                    isOptimizing = isOptimizingConnection,
                    isPreparingConfig = isProfileRefresh || isSwitchingMode,
                    canForceStop = canForceStop,
                    onClick = onConnectClick,
                )
            }
        }
    }
}

@Composable
private fun XrayOnlySwitch(
    enabled: Boolean,
    interactiveEnabled: Boolean,
    xrayAvailable: Boolean,
    onToggle: () -> Unit,
) {
    val rowEnabled = interactiveEnabled && xrayAvailable
    val subtitle = when {
        !xrayAvailable -> "Xray ссылка не импортирована"
        enabled -> "AWG и DNS fallback отключены"
        else -> "Можно проверить только Xray"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WhiteZiaPanel, CircleShape)
            .border(
                width = 1.dp,
                color = if (enabled) WhiteZiaBlue.copy(alpha = 0.65f) else WhiteZiaPalette.Border,
                shape = CircleShape,
            )
            .clickable(enabled = rowEnabled, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "Только Xray",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp,
                ),
                color = WhiteZiaInk.copy(alpha = if (rowEnabled) 0.86f else 0.42f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.sp,
                ),
                color = if (xrayAvailable) WhiteZiaTextMuted else WhiteZiaSetupOrange,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = enabled,
            onCheckedChange = null,
            enabled = rowEnabled,
        )
    }
}

@Composable
private fun ForceDnsTunnelSwitch(
    enabled: Boolean,
    interactiveEnabled: Boolean,
    wifiEnabled: Boolean,
    onToggle: () -> Unit,
) {
    val subtitle = when {
        enabled && wifiEnabled -> "DNS канал. Выключите Wi-Fi перед подключением"
        enabled -> "DNS канал будет использоваться сразу"
        else -> "Авто: прямой Xray, затем CDN и DNS"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WhiteZiaPanel, CircleShape)
            .border(
                width = 1.dp,
                color = if (enabled) WhiteZiaBlue.copy(alpha = 0.55f) else WhiteZiaPalette.Border,
                shape = CircleShape,
            )
            .clickable(enabled = interactiveEnabled, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "Использовать DNS канал",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp,
                ),
                color = WhiteZiaInk.copy(alpha = if (interactiveEnabled) 0.86f else 0.42f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.sp,
                ),
                color = when {
                    !interactiveEnabled -> WhiteZiaTextDim
                    enabled && wifiEnabled -> WhiteZiaSetupOrange
                    else -> WhiteZiaTextMuted
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = enabled,
            onCheckedChange = null,
            enabled = interactiveEnabled,
        )
    }
}

@Composable
private fun WhiteZiaLogo(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "White",
            style = WhiteZiaLogoTextStyle(),
            color = WhiteZiaInk,
        )
        Text(
            text = "Zia",
            style = WhiteZiaLogoTextStyle(),
            color = WhiteZiaRed,
        )
    }
}

fun WhiteZiaLogoTextStyle(): TextStyle {
    return TextStyle(
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    )
}


private fun WhiteZiaStatusTextStyle(): TextStyle {
    return TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    )
}

private fun WhiteZiaTabTextStyle(): TextStyle {
    return TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    )
}

@Composable
private fun CircularConnectionButton(
    connectionStatus: ConnectionStatus,
    enabled: Boolean,
    isError: Boolean,
    isDisconnecting: Boolean,
    isFinalizing: Boolean,
    isPrimarySetup: Boolean,
    isOptimizing: Boolean,
    isPreparingConfig: Boolean,
    canForceStop: Boolean,
    onClick: () -> Unit,
) {
    val idleBlue = WhiteZiaBlue
    val connectedGreen = WhiteZiaSuccess
    val disconnectOrange = WhiteZiaSetupOrange
    val errorRed = WhiteZiaError
    val ink = WhiteZiaInk
    val ringColor = when {
        isError -> errorRed
        isPrimarySetup || isDisconnecting -> disconnectOrange
        connectionStatus == ConnectionStatus.CONNECTED && !isFinalizing -> connectedGreen
        else -> idleBlue
    }
    val innerButtonColor = when {
        isError -> WhiteZiaPalette.ErrorSurface
        connectionStatus == ConnectionStatus.CONNECTED && !isFinalizing -> WhiteZiaPalette.SuccessSurface
        else -> WhiteZiaPanel
    }
    val iconBubbleColor = when {
        isError -> errorRed.copy(alpha = 0.13f)
        isPrimarySetup || isDisconnecting -> disconnectOrange.copy(alpha = 0.16f)
        connectionStatus == ConnectionStatus.CONNECTED && !isFinalizing -> connectedGreen.copy(alpha = 0.13f)
        else -> idleBlue.copy(alpha = 0.20f)
    }
    val connectionMotionActive = isPreparingConfig ||
        isPrimarySetup ||
        isOptimizing ||
        isDisconnecting ||
        isFinalizing ||
        connectionStatus == ConnectionStatus.CONNECTING
    val spinnerTransition = rememberInfiniteTransition(label = "connectionOuterSpinner")
    val spinnerAngle by spinnerTransition.animateFloat(
        initialValue = -90f,
        targetValue = 270f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620, easing = LinearEasing),
        ),
        label = "connectionOuterSpinnerAngle",
    )
    var pulseProgress by remember { mutableFloatStateOf(0f) }
    val buttonText = when {
        isDisconnecting -> "ОТКЛЮЧЕНИЕ"
        isPreparingConfig -> ""
        isError -> "ОШИБКА"
        canForceStop && connectionStatus != ConnectionStatus.CONNECTED -> "ОТКЛЮЧИТЬ"
        isFinalizing -> ""
        connectionStatus == ConnectionStatus.CONNECTED -> "ПОДКЛЮЧЕНО"
        connectionStatus == ConnectionStatus.CONNECTING -> ""
        else -> "ПОДКЛЮЧИТЬСЯ"
    }
    val buttonIcon = when {
        isError -> Icons.Rounded.Close
        isPreparingConfig -> Icons.Rounded.Sync
        canForceStop && connectionStatus != ConnectionStatus.CONNECTED -> Icons.Rounded.Stop
        isDisconnecting || isFinalizing -> Icons.Rounded.Sync
        connectionStatus == ConnectionStatus.CONNECTED -> Icons.Rounded.Check
        else -> Icons.Rounded.PowerSettingsNew
    }
    LaunchedEffect(connectionStatus, isFinalizing) {
        if (connectionStatus != ConnectionStatus.CONNECTED || isFinalizing) {
            pulseProgress = 0f
            return@LaunchedEffect
        }
        while (true) {
            repeat(36) { step ->
                pulseProgress = (step + 1) / 36f
                delay(50)
            }
            pulseProgress = 0f
        }
    }
    Box(
        modifier = Modifier.size(188.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 4.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            if (connectionStatus == ConnectionStatus.CONNECTED && !isFinalizing) {
                drawCircle(
                    color = connectedGreen.copy(alpha = 0.35f * (1f - pulseProgress)),
                    radius = (size.minDimension / 2f - 12.dp.toPx()) * (1f + 0.18f * pulseProgress),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            drawArc(
                color = ink.copy(alpha = if (connectionMotionActive) 0.08f else 0.05f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            when {
                connectionMotionActive -> drawArc(
                    color = ringColor,
                    startAngle = spinnerAngle,
                    sweepAngle = 86f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                connectionStatus == ConnectionStatus.CONNECTED || isError -> drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                else -> Unit
            }
        }
        Box(
            modifier = Modifier
                .size(156.dp)
                .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
                .background(innerButtonColor, CircleShape)
                .border(
                    width = 1.dp,
                    color = WhiteZiaPalette.Border,
                    shape = CircleShape,
                )
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(iconBubbleColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        modifier = Modifier.size(22.dp),
                        imageVector = buttonIcon,
                        contentDescription = null,
                        tint = ringColor,
                    )
                }
                if (buttonText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 0.sp,
                        ),
                        color = if (enabled) ringColor else WhiteZiaTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
