package shop.whitezia.client.ui.connect

import android.app.Activity
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
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
@Composable
fun WhiteZiaConnectScreen(
    subscriptionLink: String,
    settings: WhiteZiaSettings,
    operatorDisplayLabel: String,
    connectionStatus: ConnectionStatus,
    wifiEnabled: Boolean,
    errorMessage: String?,
    userStatus: String,
    isDisconnecting: Boolean,
    forceDnsTunnel: Boolean,
    xrayPreflightBlocked: Boolean,
    onConnectClick: () -> Unit,
    onXrayOnlyModeChange: (Boolean) -> Unit,
    onForceDnsTunnelChange: (Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onLogClick: () -> Unit,
) {
    val isRunning = connectionStatus != ConnectionStatus.DISCONNECTED
    val isPrimarySetup = userStatus == "производится первичная настройка"
    val isDnsPreparation = userStatus == "Подготовка DNS подключения"
    val isOptimizingConnection = userStatus == "Оптимизация подключения"
    val showProgress = isPrimarySetup || connectionStatus == ConnectionStatus.CONNECTING ||
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
    val minimalConnectionView = isAutomaticConnectionFlow && !isDisconnecting
    val buttonProgress = when {
        isDisconnecting -> 0.35f
        errorMessage != null -> 1f
        isConnectionFinalizing -> 0.88f
        connectionStatus == ConnectionStatus.CONNECTED -> 1f
        connectionStatus == ConnectionStatus.CONNECTING -> 0.62f
        isPrimarySetup -> 0.18f
        else -> 0.08f
    }
    val statusText = when {
        isDisconnecting -> "отключение"
        errorMessage != null -> {
            if (errorMessage == "Выключите Wi-Fi" || errorMessage == "Включите мобильный интернет") {
                errorMessage.orEmpty()
            } else {
                "ошибка, попробуйте снова"
            }
        }
        minimalConnectionView -> "идет подключение, это может занять пару минут"
        connectionStatus == ConnectionStatus.CONNECTED -> "успешное подключение"
        else -> userStatus.ifBlank { "Готово к подключению" }
    }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        window.statusBarColor = WhiteZiaBackground.toArgb()
        window.navigationBarColor = WhiteZiaBackground.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
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
                Box(modifier = Modifier.fillMaxWidth()) {
                    WhiteZiaLogo(
                        modifier = Modifier.align(Alignment.Center),
                    )
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onLogClick) {
                            Icon(
                                imageVector = Icons.Rounded.Article,
                                contentDescription = "Логи",
                                tint = WhiteZiaTextMuted,
                            )
                        }
                        IconButton(
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
                if (!minimalConnectionView) {
                    Text(
                        text = "Оператор SIM: $operatorDisplayLabel".uppercase(Locale.US),
                        style = WhiteZiaSmallTextStyle(),
                        color = WhiteZiaSetupOrange,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }
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
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularConnectionButton(
                    connectionStatus = connectionStatus,
                    progress = buttonProgress,
                    enabled = canConnect || canDisconnect,
                    isError = errorMessage != null,
                    isDisconnecting = isDisconnecting,
                    isFinalizing = isConnectionFinalizing,
                    isPrimarySetup = isPrimarySetup,
                    isOptimizing = isOptimizingConnection,
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
                color = if (enabled) WhiteZiaBlue.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.08f),
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
                    letterSpacing = 1.0.sp,
                ),
                color = Color.White.copy(alpha = if (rowEnabled) 0.86f else 0.42f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.4.sp,
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
        else -> "Авто: сначала AmneziaWG, затем DNS fallback"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WhiteZiaPanel, CircleShape)
            .border(
                width = 1.dp,
                color = if (enabled) WhiteZiaBlue.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
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
                    letterSpacing = 1.0.sp,
                ),
                color = Color.White.copy(alpha = if (interactiveEnabled) 0.86f else 0.42f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.4.sp,
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
            color = Color.White.copy(alpha = 0.92f),
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
        letterSpacing = 1.8.sp,
    )
}


private fun WhiteZiaStatusTextStyle(): TextStyle {
    return TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 2.4.sp,
    )
}

private fun WhiteZiaTabTextStyle(): TextStyle {
    return TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.1.sp,
    )
}

@Composable
private fun CircularConnectionButton(
    connectionStatus: ConnectionStatus,
    progress: Float,
    enabled: Boolean,
    isError: Boolean,
    isDisconnecting: Boolean,
    isFinalizing: Boolean,
    isPrimarySetup: Boolean,
    isOptimizing: Boolean,
    canForceStop: Boolean,
    onClick: () -> Unit,
) {
    val idleBlue = Color(0xFF5B6AF0)
    val connectedGreen = Color(0xFF00C9A7)
    val disconnectOrange = Color(0xFFFFA726)
    val errorRed = Color(0xFFFF4D4D)
    val ringColor = when {
        isError -> errorRed
        isPrimarySetup -> disconnectOrange
        isDisconnecting -> disconnectOrange
        isFinalizing -> idleBlue
        connectionStatus == ConnectionStatus.CONNECTED -> connectedGreen
        else -> idleBlue
    }
    val innerButtonColor = when {
        isError -> Color(0xFF1E1414)
        isFinalizing -> Color(0xFF16161F)
        connectionStatus == ConnectionStatus.CONNECTED -> Color(0xFF141E1C)
        else -> Color(0xFF16161F)
    }
    val iconBubbleColor = when {
        isError -> errorRed.copy(alpha = 0.13f)
        isPrimarySetup -> disconnectOrange.copy(alpha = 0.16f)
        isDisconnecting -> disconnectOrange.copy(alpha = 0.16f)
        isFinalizing -> idleBlue.copy(alpha = 0.20f)
        connectionStatus == ConnectionStatus.CONNECTED -> connectedGreen.copy(alpha = 0.13f)
        else -> idleBlue.copy(alpha = 0.20f)
    }
    var displayedProgress by remember { mutableStateOf(progress.coerceIn(0f, 1f)) }
    var pulseProgress by remember { mutableStateOf(0f) }
    var activeArcStart by remember { mutableStateOf(-90f) }
    var activePulseProgress by remember { mutableStateOf(0f) }
    val buttonText = when {
        isDisconnecting -> "ОТКЛЮЧЕНИЕ"
        isError -> "ОШИБКА"
        canForceStop && connectionStatus != ConnectionStatus.CONNECTED -> "ОТКЛЮЧИТЬ"
        isFinalizing -> ""
        connectionStatus == ConnectionStatus.CONNECTED -> "ПОДКЛЮЧЕНО"
        connectionStatus == ConnectionStatus.CONNECTING -> ""
        else -> "ПОДКЛЮЧИТЬСЯ"
    }
    val buttonIcon = when {
        isError -> Icons.Rounded.Close
        canForceStop && connectionStatus != ConnectionStatus.CONNECTED -> Icons.Rounded.Stop
        isDisconnecting || isFinalizing -> Icons.Rounded.Sync
        connectionStatus == ConnectionStatus.CONNECTED -> Icons.Rounded.Check
        else -> Icons.Rounded.PowerSettingsNew
    }
    LaunchedEffect(connectionStatus, isError, isDisconnecting, isFinalizing, isPrimarySetup, isOptimizing, progress) {
        when {
            isPrimarySetup || isOptimizing -> {
                displayedProgress = 0f
            }
            isError -> {
                val start = displayedProgress
                repeat(28) { step ->
                    displayedProgress = start * (1f - (step + 1) / 28f)
                    delay(50)
                }
            }
            connectionStatus == ConnectionStatus.CONNECTING || isFinalizing -> {
                displayedProgress = 0f
                repeat(20) { step ->
                    displayedProgress = ((step + 1) / 20f).coerceAtMost(0.96f)
                    delay(18)
                }
            }
            connectionStatus == ConnectionStatus.CONNECTED -> {
                displayedProgress = 1f
            }
            isDisconnecting -> {
                displayedProgress = 0.35f
            }
            else -> {
                displayedProgress = 0f
            }
        }
    }
    LaunchedEffect(isPrimarySetup, isOptimizing) {
        if (!isPrimarySetup && !isOptimizing) {
            activeArcStart = -90f
            activePulseProgress = 0f
            return@LaunchedEffect
        }
        while (true) {
            repeat(72) { step ->
                activeArcStart = -90f + step * 5f
                activePulseProgress = (step + 1) / 72f
                delay(14)
            }
            activePulseProgress = 0f
        }
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
        modifier = Modifier.size(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 4.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            if (isOptimizing) {
                drawCircle(
                    color = ringColor.copy(alpha = 0.18f * (1f - activePulseProgress)),
                    radius = (size.minDimension / 2f - 12.dp.toPx()) * (1f + 0.12f * activePulseProgress),
                    style = Stroke(width = 2.dp.toPx()),
                )
            } else if (connectionStatus == ConnectionStatus.CONNECTED && !isFinalizing) {
                drawCircle(
                    color = connectedGreen.copy(alpha = 0.35f * (1f - pulseProgress)),
                    radius = (size.minDimension / 2f - 12.dp.toPx()) * (1f + 0.18f * pulseProgress),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            drawArc(
                color = Color.White.copy(alpha = 0.05f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            if (isPrimarySetup || isOptimizing) {
                drawArc(
                    color = ringColor,
                    startAngle = activeArcStart,
                    sweepAngle = if (isOptimizing) 116f else 82f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            } else {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * displayedProgress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(156.dp)
                .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
                .background(innerButtonColor, CircleShape)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.04f),
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
                        .background(
                            if (isOptimizing) {
                                iconBubbleColor.copy(alpha = 0.16f + 0.10f * activePulseProgress)
                            } else {
                                iconBubbleColor
                            },
                            CircleShape,
                        ),
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
                            letterSpacing = 2.5.sp,
                        ),
                        color = if (enabled) ringColor else Color.White.copy(alpha = 0.33f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
