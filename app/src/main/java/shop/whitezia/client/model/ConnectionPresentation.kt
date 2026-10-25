package shop.whitezia.client.model

import shop.whitezia.client.diagnostics.DiagnosticPrivacy

/** User-facing facts, separate from runtime ownership and transport selection. */
internal fun connectionStatusMessage(status: ConnectionStatus, progress: ConnectionProgressState): String = when {
    status == ConnectionStatus.CONNECTED -> "Подключение работает"
    status == ConnectionStatus.DISCONNECTED -> "Готово к подключению"
    progress.phase == "checking" -> "Проверяю интернет через прокси"
    progress.phase == "reconnecting" -> "Восстанавливаю подключение"
    progress.phase in setOf("waiting-network", "waiting-action") -> progress.detail.takeIf(String::isNotBlank)
        ?.let(::connectionActionMessage) ?: "Жду восстановления сети"
    progress.phase == "stopping" -> "Останавливаю предыдущий маршрут"
    progress.phase == "preparing" -> "Проверяю доступность маршрутов"
    else -> "Подключаю VPN"
}

internal fun connectionActionMessage(message: String): String = when (message) {
    "VPN permission is required" -> "Разрешите VPN в системном окне Android"
    "VPN permission was revoked by Android" -> "VPN отключён в настройках Android. Нажмите «Подключиться» для повторного запуска"
    "VPN tunnel did not stop" -> "Предыдущий VPN ещё останавливается. Повторите подключение"
    "Connection could not start" -> "Не удалось запустить подключение. Откройте журнал диагностики"
    else -> DiagnosticPrivacy.redact(message).lineSequence().firstOrNull().orEmpty().take(240)
}

internal fun runtimeProgressState(phase: String, message: String = ""): ConnectionProgressState = ConnectionProgressState(
    phase = phase.takeIf { it in setOf("preparing", "starting", "checking", "stopping", "reconnecting", "waiting-network", "waiting-action") }
        ?: "starting",
    percent = 0,
    detail = DiagnosticPrivacy.redact(message).take(240),
)

internal fun proxyVerification(message: String, checkedAtMillis: Long): ConnectionVerificationState =
    ConnectionVerificationState(
        status = ConnectionVerificationStatus.Verified,
        message = message,
        checkedAtMillis = checkedAtMillis,
        coverage = ConnectionVerificationCoverage.SocksProxy,
    )
