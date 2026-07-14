package shop.whitezia.client.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun AppUpdateDialog(
    state: AppUpdateState,
    onDownload: (AppRelease) -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: (AppUpdateState.ReadyToInstall) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        AppUpdateState.Idle,
        AppUpdateState.Checking -> Unit
        is AppUpdateState.UpToDate -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Обновления не требуются") },
            text = { Text("Установлена актуальная версия ${state.versionName}.") },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Готово") }
            },
        )
        is AppUpdateState.Available -> ReleaseDialog(
            title = "Доступно обновление ${state.release.versionName}",
            release = state.release,
            confirmLabel = "Скачать",
            onConfirm = { onDownload(state.release) },
            onDismiss = onDismiss,
        )
        is AppUpdateState.Downloading -> {
            val progress = if (state.totalBytes > 0L) {
                (state.downloadedBytes.toFloat() / state.totalBytes.toFloat()).coerceIn(0f, 1f)
            } else 0f
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Загрузка обновления") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${formatMegabytes(state.downloadedBytes)} / ${formatMegabytes(state.totalBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = onCancelDownload) { Text("Отмена") }
                },
            )
        }
        is AppUpdateState.ReadyToInstall -> ReleaseDialog(
            title = "Обновление загружено",
            release = state.release,
            confirmLabel = "Установить",
            onConfirm = { onInstall(state) },
            onDismiss = onDismiss,
        )
        is AppUpdateState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Не удалось обновить приложение") },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = onRetry) { Text("Повторить") }
            },
            dismissButton = {
                if (state.release?.requiresUpdate != true) {
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                }
            },
        )
    }
}

@Composable
private fun ReleaseDialog(
    title: String,
    release: AppRelease,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!release.requiresUpdate) onDismiss() },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (release.releaseNotes.isEmpty()) {
                    Text("Доступна новая версия WhiteZia.")
                } else {
                    release.releaseNotes.forEach { note ->
                        Text("• $note", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    "Размер: ${formatMegabytes(release.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            if (!release.requiresUpdate) {
                TextButton(onClick = onDismiss) { Text("Позже") }
            }
        },
    )
}

private fun formatMegabytes(bytes: Long): String {
    return String.format(Locale.US, "%.1f МБ", bytes.toDouble() / (1024.0 * 1024.0))
}
