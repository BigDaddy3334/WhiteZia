package shop.whitezia.client.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
internal fun ConnectionProfileExportDialog(
    title: String,
    fieldLabel: String,
    linkResult: Result<String>,
    onDismiss: () -> Unit,
    onShare: (String) -> Unit,
    placeholder: String = "stormdns://...",
    showQr: Boolean = true,
) {
    val context = LocalContext.current

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
            val link = linkResult.getOrNull()
            if (link != null) {
                if (showQr && !link.contains('\n')) {
                    ProfileQrPreview(link = link)
                    Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
                }
                WhiteZiaTextField(
                    label = fieldLabel,
                    value = link,
                    onValueChange = {},
                    placeholder = placeholder,
                    singleLine = false,
                    minLines = if (link.contains('\n')) 7 else 5,
                    maxLines = 12,
                    rawValue = true,
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
                        emphasized = false,
                        enabled = true,
                        onClick = {
                            copyTextToClipboard(
                                context = context,
                                label = fieldLabel,
                                text = link,
                                sensitive = true,
                            )
                        },
                    )
                    CompactActionButton(
                        modifier = Modifier.weight(1f),
                        label = WhiteZiaL10n.btnShare,
                        emphasized = true,
                        enabled = true,
                        onClick = {
                            onShare(link)
                        },
                    )
                }
            } else {
                Text(
                    text = linkResult.exceptionOrNull()?.message ?: WhiteZiaL10n.errorExportProfile,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 11.sp,
                        color = WhiteZiaPalette.Error,
                    ),
                )
                Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
                CompactActionButton(
                    modifier = Modifier.fillMaxWidth(),
                    label = WhiteZiaL10n.btnClose,
                    emphasized = true,
                    enabled = true,
                    onClick = onDismiss,
                )
            }
        }
    }
}
