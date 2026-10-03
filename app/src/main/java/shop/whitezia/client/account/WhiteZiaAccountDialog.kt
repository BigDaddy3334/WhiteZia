package shop.whitezia.client.account

import android.net.Uri
import android.os.SystemClock
import shop.whitezia.client.ui.WhiteZiaInk
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsControllerCompat
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.delay
import shop.whitezia.client.ui.WhiteZiaBackground
import shop.whitezia.client.ui.WhiteZiaBlue
import shop.whitezia.client.ui.WhiteZiaError
import shop.whitezia.client.ui.WhiteZiaPanel
import shop.whitezia.client.ui.WhiteZiaSuccess
import shop.whitezia.client.ui.WhiteZiaTextDim
import shop.whitezia.client.ui.WhiteZiaTextMuted
import shop.whitezia.client.ui.whiteZiaTextFieldColors

@Composable
fun WhiteZiaAccountDialog(
    state: AccountUiState,
    onDismiss: () -> Unit,
    onShowSignIn: () -> Unit,
    onShowRegister: () -> Unit,
    onShowRecovery: () -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onVerifyEmail: (String) -> Unit,
    onResendVerification: () -> Unit,
    onRequestPasswordReset: (String) -> Unit,
    onResetPassword: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onStartPayment: (String) -> Unit,
    onPaymentOpened: () -> Unit,
    onAttachCurrentDevice: () -> Unit,
    onDisableDevice: (String) -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(state.paymentUrl) {
        state.paymentUrl?.takeIf(String::isNotBlank)?.let { url ->
            runCatching {
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
            }
            onPaymentOpened()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        val darkIcons = WhiteZiaBackground.luminance() > 0.5f
        DisposableEffect(window, view, darkIcons) {
            if (window != null && !view.isInEditMode) {
                val controller = WindowInsetsControllerCompat(window, view)
                val previousStatusAppearance = controller.isAppearanceLightStatusBars
                val previousNavigationAppearance = controller.isAppearanceLightNavigationBars
                controller.isAppearanceLightStatusBars = darkIcons
                controller.isAppearanceLightNavigationBars = darkIcons
                onDispose {
                    controller.isAppearanceLightStatusBars = previousStatusAppearance
                    controller.isAppearanceLightNavigationBars = previousNavigationAppearance
                }
            } else {
                onDispose { }
            }
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = WhiteZiaBackground,
            contentColor = WhiteZiaInk,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    AccountTopBar(
                        title = if (state.stage == AccountStage.DASHBOARD) "Личный кабинет" else "Аккаунт WhiteZia",
                        onDismiss = onDismiss,
                    )
                    HorizontalDivider(color = WhiteZiaInk.copy(alpha = 0.08f))
                    when (state.stage) {
                        AccountStage.RESTORING -> AccountLoading()
                        AccountStage.DASHBOARD -> AccountDashboardContent(
                            state = state,
                            onRefresh = onRefresh,
                            onStartPayment = onStartPayment,
                            onAttachCurrentDevice = onAttachCurrentDevice,
                            onDisableDevice = onDisableDevice,
                            onLogout = onLogout,
                        )
                        else -> AccountAuthContent(
                            state = state,
                            onShowSignIn = onShowSignIn,
                            onShowRegister = onShowRegister,
                            onShowRecovery = onShowRecovery,
                            onLogin = onLogin,
                            onRegister = onRegister,
                            onVerifyEmail = onVerifyEmail,
                            onResendVerification = onResendVerification,
                            onRequestPasswordReset = onRequestPasswordReset,
                            onResetPassword = onResetPassword,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountTopBar(title: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = "Закрыть", tint = WhiteZiaTextMuted)
        }
    }
}

@Composable
private fun AccountLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = WhiteZiaBlue, strokeWidth = 2.dp)
    }
}

@Composable
private fun AccountAuthContent(
    state: AccountUiState,
    onShowSignIn: () -> Unit,
    onShowRegister: () -> Unit,
    onShowRecovery: () -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onVerifyEmail: (String) -> Unit,
    onResendVerification: () -> Unit,
    onRequestPasswordReset: (String) -> Unit,
    onResetPassword: (String, String) -> Unit,
) {
    var email by rememberSaveable(state.email) { mutableStateOf(state.email) }
    var password by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = when (state.stage) {
                    AccountStage.REGISTER -> "Создать аккаунт"
                    AccountStage.VERIFY_EMAIL -> "Подтвердите почту"
                    AccountStage.RECOVERY -> "Восстановить пароль"
                    AccountStage.RESET_PASSWORD -> "Новый пароль"
                    else -> "Войти"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (state.stage in setOf(AccountStage.SIGN_IN, AccountStage.REGISTER)) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WhiteZiaPanel, RoundedCornerShape(6.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AccountSegment(
                        modifier = Modifier.weight(1f),
                        text = "Вход",
                        selected = state.stage == AccountStage.SIGN_IN,
                        onClick = onShowSignIn,
                    )
                    AccountSegment(
                        modifier = Modifier.weight(1f),
                        text = "Регистрация",
                        selected = state.stage == AccountStage.REGISTER,
                        onClick = onShowRegister,
                    )
                }
            }
        }
        if (state.stage in setOf(AccountStage.REGISTER)) {
            item {
                AccountTextField(
                    value = displayName,
                    onValueChange = { displayName = it.take(80) },
                    label = "Имя",
                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                )
            }
        }
        if (state.stage in setOf(AccountStage.SIGN_IN, AccountStage.REGISTER, AccountStage.RECOVERY)) {
            item {
                AccountTextField(
                    value = email,
                    onValueChange = { email = it.trim().take(254) },
                    label = "Почта",
                    keyboardType = KeyboardType.Email,
                    leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                )
            }
        }
        if (state.stage in setOf(AccountStage.SIGN_IN, AccountStage.REGISTER, AccountStage.RESET_PASSWORD)) {
            item {
                AccountTextField(
                    value = password,
                    onValueChange = { password = it.take(128) },
                    label = if (state.stage == AccountStage.RESET_PASSWORD) "Новый пароль" else "Пароль",
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                )
            }
        }
        if (state.stage in setOf(AccountStage.VERIFY_EMAIL, AccountStage.RESET_PASSWORD)) {
            item {
                AccountTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    label = "Код из письма",
                    keyboardType = KeyboardType.NumberPassword,
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                )
            }
        }
        if (state.feedback.isNotBlank()) {
            item {
                Text(
                    text = state.feedback,
                    color = if (state.feedbackIsError) WhiteZiaError else WhiteZiaSuccess,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
                colors = ButtonDefaults.buttonColors(containerColor = WhiteZiaBlue),
                shape = RoundedCornerShape(6.dp),
                onClick = {
                    when (state.stage) {
                        AccountStage.REGISTER -> onRegister(email, password, displayName)
                        AccountStage.VERIFY_EMAIL -> onVerifyEmail(code)
                        AccountStage.RECOVERY -> onRequestPasswordReset(email)
                        AccountStage.RESET_PASSWORD -> onResetPassword(code, password)
                        else -> onLogin(email, password)
                    }
                },
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = WhiteZiaInk,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text(
                    when (state.stage) {
                        AccountStage.REGISTER -> "Зарегистрироваться"
                        AccountStage.VERIFY_EMAIL -> "Подтвердить"
                        AccountStage.RECOVERY -> "Получить код"
                        AccountStage.RESET_PASSWORD -> "Сохранить пароль"
                        else -> "Войти"
                    },
                )
            }
        }
        item {
            when (state.stage) {
                AccountStage.SIGN_IN -> TextButton(onClick = onShowRecovery) { Text("Забыли пароль?") }
                AccountStage.VERIFY_EMAIL -> Column {
                    TextButton(onClick = onShowSignIn) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                        Text("Назад")
                    }
                    TextButton(enabled = !state.busy, onClick = onResendVerification) {
                        Text("Отправить код снова")
                    }
                }
                AccountStage.RECOVERY,
                AccountStage.RESET_PASSWORD -> TextButton(onClick = onShowSignIn) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    Text("Назад ко входу")
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun AccountSegment(
    modifier: Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        modifier = modifier,
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) WhiteZiaInk.copy(alpha = 0.08f) else Color.Transparent,
            contentColor = if (selected) WhiteZiaInk else WhiteZiaTextMuted,
        ),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(text)
    }
}

@Composable
private fun AccountTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = leadingIcon,
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        colors = whiteZiaTextFieldColors(),
        shape = RoundedCornerShape(6.dp),
    )
}

@Composable
private fun AccountDashboardContent(
    state: AccountUiState,
    onRefresh: () -> Unit,
    onStartPayment: (String) -> Unit,
    onAttachCurrentDevice: () -> Unit,
    onDisableDevice: (String) -> Unit,
    onLogout: () -> Unit,
) {
    val dashboard = state.dashboard ?: return AccountLoading()
    var section by rememberSaveable(dashboard.account.id) { mutableStateOf(0) }
    val subscriptionScroll = rememberLazyListState()
    val devicesScroll = rememberLazyListState()
    val paymentsScroll = rememberLazyListState()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    dashboard.account.displayName.ifBlank { "Ваш аккаунт" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    dashboard.account.email,
                    color = WhiteZiaTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(enabled = !state.busy, onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Обновить", tint = WhiteZiaTextMuted)
            }
            IconButton(onClick = onLogout) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Выйти из аккаунта", tint = WhiteZiaTextMuted)
            }
        }
        SecondaryTabRow(
            selectedTabIndex = section,
            containerColor = WhiteZiaBackground,
            contentColor = WhiteZiaBlue,
        ) {
            listOf("Подписка", "Устройства", "Платежи").forEachIndexed { index, title ->
                Tab(
                    selected = section == index,
                    onClick = { section = index },
                    selectedContentColor = WhiteZiaBlue,
                    unselectedContentColor = WhiteZiaTextMuted,
                    text = { Text(title, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = when (section) {
                1 -> devicesScroll
                2 -> paymentsScroll
                else -> subscriptionScroll
            },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.feedback.isNotBlank() && state.enrollment?.failure != state.feedback) {
                item(key = "feedback") {
                    Text(
                        state.feedback,
                        color = if (state.feedbackIsError) WhiteZiaError else WhiteZiaTextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            state.enrollment?.let { progress ->
                if (progress.stage != DeviceEnrollmentStage.READY || section == 1) {
                    item(key = "enrollment") { EnrollmentProgress(progress, state.busy, onAttachCurrentDevice) }
                }
            }
            when (section) {
                0 -> {
                    item(key = "subscription-title") { AccountSectionTitle("Подписка") }
                    item(key = "subscription") { SubscriptionPanel(dashboard.subscription, dashboard.plans) }
                    item(key = "plans-title") { AccountSectionTitle("Тарифы") }
                    if (dashboard.plans.isEmpty()) {
                        item { EmptyAccountRow("Тарифы сейчас недоступны") }
                    }
                    items(dashboard.plans, key = { "plan-${it.id}" }) { plan ->
                        PlanRow(plan = plan, busy = state.busy, onClick = { onStartPayment(plan.id) })
                    }
                }
                1 -> {
                    item(key = "devices-title") { AccountSectionTitle("Мои устройства") }
                    item(key = "device-count") {
                        Text(
                            "${dashboard.subscription.deviceCount} из ${dashboard.subscription.deviceLimit} устройств",
                            color = WhiteZiaTextMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (dashboard.subscription.subscription != null) {
                        item(key = "current-device") {
                            CurrentDeviceControl(
                                currentDeviceId = state.currentDeviceId,
                                deviceLimitReached = dashboard.subscription.deviceCount >= dashboard.subscription.deviceLimit,
                                busy = state.busy || state.enrollment?.polling == true ||
                                    state.enrollment?.stage == DeviceEnrollmentStage.APPLYING,
                                onAttach = onAttachCurrentDevice,
                            )
                        }
                    }
                    if (dashboard.devices.isEmpty()) {
                        item { EmptyAccountRow("Устройство появится после активации подписки") }
                    } else {
                        items(dashboard.devices, key = { "device-${it.id}" }) { device ->
                            DeviceRow(
                                device = device,
                                isCurrent = device.id == state.currentDeviceId,
                                busy = state.busy,
                                onDisable = { onDisableDevice(device.id) },
                            )
                        }
                    }
                }
                2 -> {
                    item(key = "payments-title") { AccountSectionTitle("История платежей") }
                    if (dashboard.payments.isEmpty()) {
                        item { EmptyAccountRow("Платежей пока нет") }
                    } else {
                        items(dashboard.payments, key = { "payment-${it.id}" }) { payment ->
                            PaymentRow(payment, dashboard.plans)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnrollmentProgress(progress: DeviceEnrollmentProgress, busy: Boolean, onRetry: () -> Unit) {
    var now by remember(progress.startedAtMillis) { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(progress.startedAtMillis, progress.finishedAtMillis) {
        while (progress.finishedAtMillis == null) {
            now = SystemClock.elapsedRealtime()
            delay(1_000L)
        }
    }
    val activeStage = progress.failedAtStage ?: progress.stage
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Привязка этого устройства", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Прошло: ${progress.elapsedSeconds(now)} с",
            style = MaterialTheme.typography.bodySmall,
            color = WhiteZiaTextMuted,
        )
        if (progress.stage == DeviceEnrollmentStage.READY) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = WhiteZiaSuccess, modifier = Modifier.size(18.dp))
                Text("Профиль применён. Устройство готово", style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider(color = WhiteZiaInk.copy(alpha = 0.08f))
            return@Column
        }
        listOf(
            DeviceEnrollmentStage.PREPARING to "Подготовка",
            DeviceEnrollmentStage.BINDING to "Привязка к аккаунту",
            DeviceEnrollmentStage.PROVISIONING to "Подготовка протоколов",
            DeviceEnrollmentStage.APPLYING to "Применение профиля",
            DeviceEnrollmentStage.READY to "Готово",
        ).forEach { (stage, title) ->
            val complete = progress.stage == DeviceEnrollmentStage.READY || stage.ordinal < activeStage.ordinal
            val active = stage == activeStage
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                    when {
                        complete -> Icon(Icons.Rounded.CheckCircle, contentDescription = "Завершено", tint = WhiteZiaSuccess)
                        active && progress.stage == DeviceEnrollmentStage.FAILED ->
                            Icon(Icons.Rounded.Close, contentDescription = "Ошибка", tint = WhiteZiaError)
                        active && (progress.stage != DeviceEnrollmentStage.PROVISIONING || progress.polling) ->
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = WhiteZiaBlue, strokeWidth = 2.dp)
                        else -> Text("·", color = WhiteZiaTextDim)
                    }
                }
                Text(
                    title,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active || complete) WhiteZiaInk else WhiteZiaTextDim,
                )
            }
        }
        if (progress.stage == DeviceEnrollmentStage.PROVISIONING) {
            Text(
                if (progress.polling) "Ожидаем профиль сервера. Проверок выполнено: ${progress.pollAttempts}"
                else "Профиль ещё не готов. Автопроверка приостановлена",
                style = MaterialTheme.typography.bodySmall,
                color = WhiteZiaTextMuted,
            )
            if (progress.pollError.isNotBlank()) {
                Text(
                    if (progress.polling) "Связь с сервером временно недоступна. Продолжаем проверку"
                    else "Связь с сервером временно недоступна. Можно проверить готовность позже",
                    color = WhiteZiaTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (progress.stage == DeviceEnrollmentStage.APPLYING) {
            Text("Профиль получен. Ожидаем применения в приложении", style = MaterialTheme.typography.bodySmall, color = WhiteZiaTextMuted)
        }
        if (progress.failure.isNotBlank()) {
            Text(progress.failure, color = WhiteZiaError, style = MaterialTheme.typography.bodyMedium)
        }
        if (progress.canRetry) {
            TextButton(enabled = !busy, onClick = onRetry) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(if (progress.stage == DeviceEnrollmentStage.PROVISIONING) "Проверить готовность" else "Повторить")
            }
        }
        HorizontalDivider(color = WhiteZiaInk.copy(alpha = 0.08f))
    }
}

@Composable
private fun CurrentDeviceControl(
    currentDeviceId: String,
    deviceLimitReached: Boolean,
    busy: Boolean,
    onAttach: () -> Unit,
) {
    if (currentDeviceId.isNotBlank()) {
        Text("Это устройство привязано к аккаунту", color = WhiteZiaTextMuted)
        return
    }
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy && !deviceLimitReached,
        onClick = onAttach,
        colors = ButtonDefaults.buttonColors(containerColor = WhiteZiaBlue),
        shape = RoundedCornerShape(6.dp),
    ) {
        Icon(Icons.AutoMirrored.Rounded.AddToHomeScreen, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.size(8.dp))
        Text(if (deviceLimitReached) "Лимит устройств достигнут" else "Привязать это устройство")
    }
}

@Composable
private fun AccountSectionTitle(title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, color = WhiteZiaInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        HorizontalDivider(color = WhiteZiaInk.copy(alpha = 0.08f))
    }
}

@Composable
private fun SubscriptionPanel(status: AccountSubscriptionStatus, plans: List<AccountPlan>) {
    val subscription = status.subscription
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AccountValueRow("Статус", subscription?.status?.let(::statusLabel) ?: "Нет подписки")
        AccountValueRow("Тариф", subscription?.planId?.let(plans::titleFor) ?: "—")
        AccountValueRow(
            "Действует до",
            when {
                subscription == null -> "—"
                subscription.isForever -> "Без ограничения"
                else -> formatDate(subscription.expiresAt)
            },
        )
        AccountValueRow("Устройства", "${status.deviceCount} из ${status.deviceLimit}")
    }
}

@Composable
private fun AccountValueRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = WhiteZiaTextMuted)
        Text(value, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PlanRow(plan: AccountPlan, busy: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WhiteZiaPanel, RoundedCornerShape(6.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(plan.title, fontWeight = FontWeight.Medium)
            Text(
                planPriceText(plan),
                color = WhiteZiaTextMuted,
            )
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = WhiteZiaBlue),
            shape = RoundedCornerShape(5.dp),
        ) {
            Icon(
                if (plan.isTrial) Icons.Rounded.CheckCircle else Icons.Rounded.CreditCard,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(if (plan.isTrial) "Попробовать" else "Оплатить")
        }
    }
}

@Composable
private fun DeviceRow(device: AccountDevice, isCurrent: Boolean, busy: Boolean, onDisable: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Devices, contentDescription = null, tint = WhiteZiaTextMuted)
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(device.name, fontWeight = FontWeight.Medium)
            Text(
                buildString {
                    append(statusLabel(device.status))
                    if (isCurrent) append(" · Это устройство")
                },
                color = if (device.status == "active") WhiteZiaSuccess else WhiteZiaTextMuted,
            )
        }
        IconButton(enabled = !busy, onClick = onDisable) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Отключить устройство", tint = WhiteZiaTextMuted)
        }
    }
}

@Composable
private fun PaymentRow(payment: AccountPayment, plans: List<AccountPlan>) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(plans.titleFor(payment.planId), modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text(formatMoney(payment.amountMinor, payment.currency), modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        }
        Text("${formatDate(payment.createdAt)} · ${paymentStatusLabel(payment.status)}", color = WhiteZiaTextMuted)
        HorizontalDivider(color = WhiteZiaInk.copy(alpha = 0.08f))
    }
}

@Composable
private fun EmptyAccountRow(text: String) {
    Text(text, color = WhiteZiaTextMuted, modifier = Modifier.padding(vertical = 8.dp))
}

private fun statusLabel(value: String): String = when (value.lowercase(Locale.US)) {
    "active" -> "Активно"
    "pending" -> "Настраивается"
    "paid" -> "Оплачен"
    "confirmed", "succeeded", "completed" -> "Оплачен"
    "created" -> "Ожидает оплаты"
    "processing" -> "Обрабатывается"
    "refunded" -> "Возвращён"
    "expired" -> "Истекло"
    "disabled" -> "Отключено"
    "failed" -> "Ошибка"
    "canceled" -> "Отменён"
    else -> value.ifBlank { "—" }
}

private fun paymentStatusLabel(value: String): String = when (value.lowercase(Locale.US)) {
    "pending", "created" -> "Ожидает оплаты"
    else -> statusLabel(value)
}

private fun formatMoney(amountMinor: Long, currencyCode: String): String = runCatching {
    NumberFormat.getCurrencyInstance(RussianLocale).apply {
        currency = Currency.getInstance(currencyCode.ifBlank { "RUB" })
    }.format(amountMinor.toDouble() / 100.0)
}.getOrElse { "${amountMinor / 100} ₽" }

@Composable
private fun planPriceText(plan: AccountPlan) = buildAnnotatedString {
    append("${plan.durationDays} дн. · ")
    if (plan.isTrial) {
        append("Бесплатно")
        return@buildAnnotatedString
    }
    append(formatMoney(plan.priceMinor, plan.currency))
    if (plan.hasPromotionalPrice) {
        append("  ")
        withStyle(SpanStyle(color = WhiteZiaTextDim, textDecoration = TextDecoration.LineThrough)) {
            append(formatMoney(plan.originalPriceMinor, plan.currency))
        }
    }
}

private fun formatDate(value: String): String = runCatching {
    OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("d MMMM yyyy", RussianLocale))
}.getOrElse { "—" }

private val RussianLocale: Locale = Locale.forLanguageTag("ru-RU")
