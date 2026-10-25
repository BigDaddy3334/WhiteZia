package shop.whitezia.client.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ProtocolRow(
    protocol: String,
    showDivider: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = WhiteZiaL10n.infoLabelProtocol,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.sp,
                color = WhiteZiaPalette.Muted,
            ),
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(WhiteZiaPalette.AccentSurface)
                .border(1.5.dp, WhiteZiaPalette.Accent.copy(alpha = 0.15f), RoundedCornerShape(5.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        ) {
            Text(
                text = protocol,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = WhiteZiaPalette.AccentText,
                ),
            )
        }
    }
    if (showDivider) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(WhiteZiaPalette.Divider),
        )
    }
}

@Composable
internal fun InfoCard(
    title: String,
    compact: Boolean = false,
    titleAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(if (compact) 12.dp else 14.dp)
    val borderWidth = if (compact) 2.dp else 2.5.dp
    val contentPadding = if (compact) 14.dp else 18.dp
    val titleBottomSpacing = if (compact) 9.dp else 14.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(WhiteZiaPalette.Surface)
            .border(borderWidth, WhiteZiaPalette.Border, shape)
            .padding(contentPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WhiteZiaSpacing.sm),
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = if (compact) 12.sp else 13.sp,
                    color = WhiteZiaPalette.SectionTitle,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                ),
            )
            titleAction?.invoke()
        }
        Spacer(modifier = Modifier.height(titleBottomSpacing))
        content()
    }
}

@Composable
internal fun InfoRow(
    label: String,
    value: String,
    showDivider: Boolean = true,
    multilineValue: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        horizontalArrangement = if (multilineValue) Arrangement.spacedBy(12.dp) else Arrangement.SpaceBetween,
        verticalAlignment = if (multilineValue) Alignment.Top else Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = if (multilineValue) Modifier.width(72.dp) else Modifier,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.sp,
                color = WhiteZiaPalette.Muted,
            ),
        )
        Text(
            text = value,
            modifier = if (multilineValue) Modifier.weight(1f) else Modifier,
            maxLines = if (multilineValue) Int.MAX_VALUE else 1,
            overflow = if (multilineValue) TextOverflow.Clip else TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = WhiteZiaPalette.Ink,
            ),
        )
    }
    if (showDivider) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(WhiteZiaPalette.Divider),
        )
    }
}
