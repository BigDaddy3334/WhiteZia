package shop.whitezia.client.ui


import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import shop.whitezia.client.model.ConnectionProgressState
import shop.whitezia.client.model.ConnectionStatus

@Composable
internal fun ConnectButton(
    status: ConnectionStatus,
    progressState: ConnectionProgressState,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptic = rememberHapticFeedback()
    val ringColor by animateColorAsState(
        targetValue = when (status) {
            ConnectionStatus.DISCONNECTED -> if (enabled) WhiteZiaPalette.Accent else WhiteZiaPalette.Divider
            ConnectionStatus.CONNECTING -> WhiteZiaPalette.AccentPressed
            ConnectionStatus.CONNECTED -> WhiteZiaPalette.Success
        },
        animationSpec = tween(400),
        label = "connectRingColor",
    )
    val iconColor by animateColorAsState(
        targetValue = when (status) {
            ConnectionStatus.DISCONNECTED -> if (enabled) WhiteZiaPalette.Accent else WhiteZiaPalette.Disabled
            ConnectionStatus.CONNECTING -> WhiteZiaPalette.AccentPressed
            ConnectionStatus.CONNECTED -> WhiteZiaPalette.WarningText
        },
        animationSpec = tween(400),
        label = "connectIconColor",
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (status == ConnectionStatus.CONNECTED) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "connectButtonScale",
    )
    val infiniteTransition = rememberInfiniteTransition(label = "connectButtonMotion")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "connectSpinAngle",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "connectPulseAlpha",
    )
    val progressFraction by animateFloatAsState(
        targetValue = when (status) {
            ConnectionStatus.CONNECTING -> progressState.fraction.coerceIn(0.03f, 0.99f)
            ConnectionStatus.CONNECTED -> 1f
            ConnectionStatus.DISCONNECTED -> 0f
        },
        animationSpec = tween(300),
        label = "connectProgressFraction",
    )
    val accentColor = WhiteZiaPalette.Accent
    val borderColor = WhiteZiaPalette.Border
    val successColor = WhiteZiaPalette.Success
    val circleSize = 156.dp
    val outerRingSize = 198.dp
    val connectStr = WhiteZiaL10n.btnConnect
    val connectingStr = WhiteZiaL10n.btnConnecting
    val stopStr = WhiteZiaL10n.btnStop
    val label = when (status) {
        ConnectionStatus.DISCONNECTED -> connectStr
        ConnectionStatus.CONNECTING -> connectingStr
        ConnectionStatus.CONNECTED -> stopStr
    }
    val labelColor = when (status) {
        ConnectionStatus.CONNECTED -> WhiteZiaPalette.WarningText
        ConnectionStatus.DISCONNECTED -> if (enabled) WhiteZiaPalette.Accent else WhiteZiaPalette.Disabled
        else -> WhiteZiaPalette.AccentPressed
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(outerRingSize)
                .scale(buttonScale),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(outerRingSize)) {
                val strokeWidth = 3.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2f
                val color = if (status == ConnectionStatus.CONNECTING) {
                    accentColor.copy(alpha = pulseAlpha)
                } else {
                    ringColor.copy(alpha = if (status == ConnectionStatus.CONNECTED) 0.30f else 0.15f)
                }
                drawCircle(
                    color = color,
                    radius = radius,
                    style = Stroke(width = strokeWidth),
                )
            }

            Canvas(modifier = Modifier.size(circleSize + 14.dp)) {
                val strokeWidth = 5.dp.toPx()
                val arcSize = Size(
                    width = size.width - strokeWidth,
                    height = size.height - strokeWidth,
                )
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                when (status) {
                    ConnectionStatus.CONNECTING -> {
                        drawArc(
                            color = borderColor.copy(alpha = 0.65f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth),
                        )
                        drawArc(
                            color = accentColor,
                            startAngle = -90f,
                            sweepAngle = 360f * progressFraction,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round,
                            ),
                        )
                        rotate(spinAngle) {
                            drawArc(
                                color = accentColor.copy(alpha = 0.22f),
                                startAngle = 0f,
                                sweepAngle = 42f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Round,
                                ),
                            )
                        }
                    }
                    ConnectionStatus.CONNECTED -> {
                        drawArc(
                            color = successColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round,
                            ),
                        )
                    }
                    ConnectionStatus.DISCONNECTED -> Unit
                }
            }

            Box(
                modifier = Modifier
                    .size(circleSize)
                    .clip(CircleShape)
                    .background(if (enabled) WhiteZiaPalette.Surface else WhiteZiaPalette.SurfaceAlt)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            haptic.performMedium()
                            onClick()
                        },
                    )
                    .semantics(mergeDescendants = true) {},
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = if (status == ConnectionStatus.CONNECTED) {
                            Icons.Rounded.Stop
                        } else {
                            Icons.Rounded.PowerSettingsNew
                        },
                        contentDescription = when (status) {
                            ConnectionStatus.DISCONNECTED -> WhiteZiaL10n.cdConnectButtonDisconnected
                            ConnectionStatus.CONNECTING -> WhiteZiaL10n.cdConnectButtonConnecting
                            ConnectionStatus.CONNECTED -> WhiteZiaL10n.cdConnectButtonConnected
                        },
                        tint = iconColor,
                        modifier = Modifier.size(if (status == ConnectionStatus.CONNECTED) 30.dp else 34.dp),
                    )
                    Spacer(modifier = Modifier.height(WhiteZiaSpacing.iconSpacing))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = labelColor,
                        ),
                    )
                    if (status == ConnectionStatus.CONNECTING) {
                        Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
                        Text(
                            text = progressState.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                color = WhiteZiaPalette.Muted,
                            ),
                        )
                        Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
                        Text(
                            text = "${progressState.percent.coerceIn(0, 99)}%",
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = WhiteZiaPalette.Accent,
                            ),
                        )
                    }
                }
            }
        }
    }
}
