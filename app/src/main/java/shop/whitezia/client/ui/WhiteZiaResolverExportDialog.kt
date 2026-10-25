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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import shop.whitezia.client.model.WhiteZiaSettings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ResolverProfilesExportDialog(
    settings: WhiteZiaSettings,
    onDismiss: () -> Unit,
    onShare: (File) -> Unit,
) {
    val context = LocalContext.current
    val errorExportProfileLabel = WhiteZiaL10n.errorExportProfile
    var exportState by remember(settings) { mutableStateOf(ResolverProfilesExportDialogState()) }

    LaunchedEffect(settings) {
        exportState = ResolverProfilesExportDialogState()
        val result = withContext(Dispatchers.IO) {
            runCatching {
                writeResolverProfilesExport(
                    context = context.applicationContext,
                    settings = settings,
                )
            }
        }
        exportState = result.fold(
            onSuccess = { exportResult ->
                ResolverProfilesExportDialogState(
                    saving = false,
                    resolverCount = exportResult.resolverCount,
                    savedLocation = exportResult.savedLocation,
                    shareFile = exportResult.shareFile,
                )
            },
            onFailure = { error ->
                ResolverProfilesExportDialogState(
                    saving = false,
                    errorMessage = error.message ?: errorExportProfileLabel,
                )
            },
        )
    }

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
                text = WhiteZiaL10n.profileDialogExportAllResolvers,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = WhiteZiaPalette.Ink,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            when {
                exportState.saving -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = WhiteZiaPalette.Accent,
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = WhiteZiaL10n.profileExportSavingFile,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 11.sp,
                                color = WhiteZiaPalette.Muted,
                            ),
                        )
                    }
                    Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
                    CompactActionButton(
                        modifier = Modifier.fillMaxWidth(),
                        label = WhiteZiaL10n.btnClose,
                        emphasized = false,
                        enabled = true,
                        onClick = onDismiss,
                    )
                }
                exportState.errorMessage != null -> {
                    Text(
                        text = exportState.errorMessage ?: WhiteZiaL10n.errorExportProfile,
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
                else -> {
                    Text(
                        text = WhiteZiaL10n.profileExportResolverTotalTemplate.format(
                            WhiteZiaL10n.resolverProfileSummary(exportState.resolverCount),
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.sp,
                            color = WhiteZiaPalette.Ink,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
                    Text(
                        text = WhiteZiaL10n.profileExportSavedToTemplate.format(exportState.savedLocation),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 10.sp,
                            color = WhiteZiaPalette.Muted,
                        ),
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
                            label = WhiteZiaL10n.btnShare,
                            emphasized = true,
                            enabled = exportState.shareFile != null,
                            onClick = {
                                exportState.shareFile?.let(onShare)
                            },
                        )
                    }
                }
            }
        }
    }
}
