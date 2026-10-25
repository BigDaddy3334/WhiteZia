package shop.whitezia.client.ui


import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ConnectionModeSegmentedControl(
    selectedMode: String,
    enabled: Boolean,
    onModeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHapticFeedback()

    val modeOptions: List<Pair<String, String>> = listOf(
        "proxy" to WhiteZiaL10n.connectionModeProxy,
        "vpn" to WhiteZiaL10n.connectionModeVpn,
    )
    Column(modifier = modifier) {
        FieldLabel(WhiteZiaL10n.fieldMode)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (enabled) WhiteZiaPalette.Surface else WhiteZiaPalette.SurfaceAlt)
                .border(1.5.dp, WhiteZiaPalette.ControlBorder, RoundedCornerShape(12.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            modeOptions.forEach { (modeValue, modeLabel) ->
                val selected = selectedMode == modeValue
                val background by animateColorAsState(
                    targetValue = if (selected) {
                        WhiteZiaPalette.Accent
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(180),
                    label = "connectionModeSegmentBackground",
                )
                val textColor by animateColorAsState(
                    targetValue = when {
                        !enabled -> WhiteZiaPalette.Disabled
                        selected -> WhiteZiaPalette.OnAccent
                        else -> WhiteZiaPalette.Muted
                    },
                    animationSpec = tween(180),
                    label = "connectionModeSegmentText",
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(background)
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = {
                                haptic.performLight()
                                onModeChange(modeValue)
                            },
                        )
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = 6.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = modeLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 10.sp,
                            color = textColor,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            letterSpacing = 0.4.sp,
                        ),
                    )
                }
            }
        }
    }
}
