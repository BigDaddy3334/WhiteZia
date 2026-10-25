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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import shop.whitezia.client.model.WhiteZiaSettings

@Composable
internal fun ConnectionProfileImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Result<WhiteZiaSettings>,
) {
    var profileLinks by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    val canImport = profileLinks.trim().isNotEmpty()
    val errorImportProfileLabel = WhiteZiaL10n.errorImportProfile
    val qrScanner = rememberQrProfileImportLauncher(
        onDecoded = { decodedLink ->
            profileLinks = decodedLink
            importError = null
            onImport(decodedLink)
                .onFailure { error ->
                    importError = error.message ?: errorImportProfileLabel
                }
        },
        onError = { message ->
            importError = message
        },
    )

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
                text = WhiteZiaL10n.profileDialogImportConnection,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = WhiteZiaPalette.Ink,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            WhiteZiaTextField(
                label = WhiteZiaL10n.profileFieldProfileLinks,
                value = profileLinks,
                onValueChange = {
                    profileLinks = it
                    importError = null
                },
                placeholder = "stormdns://...\nstormdns://...",
                singleLine = false,
                minLines = 5,
                maxLines = 9,
            )
            CompactActionButton(
                modifier = Modifier.fillMaxWidth(),
                label = WhiteZiaL10n.profileBtnScanQr,
                emphasized = false,
                enabled = true,
                onClick = qrScanner,
            )
            importError?.let { message ->
                Spacer(modifier = Modifier.height(WhiteZiaSpacing.iconSpacing))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 10.sp,
                        color = WhiteZiaPalette.Error,
                    ),
                )
            }
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactActionButton(
                    modifier = Modifier.weight(1f),
                    label = WhiteZiaL10n.btnCancel,
                    emphasized = false,
                    enabled = true,
                    onClick = onDismiss,
                )
                CompactActionButton(
                    modifier = Modifier.weight(1f),
                    label = WhiteZiaL10n.btnImport,
                    emphasized = true,
                    enabled = canImport,
                    onClick = {
                        onImport(profileLinks)
                            .onFailure { error ->
                                importError = error.message ?: errorImportProfileLabel
                            }
                    },
                )
            }
        }
    }
}
