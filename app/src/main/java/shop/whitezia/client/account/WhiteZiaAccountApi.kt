package shop.whitezia.client.account

import android.content.Context
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import shop.whitezia.client.BuildConfig
import shop.whitezia.client.controlplane.ControlPlaneTransport
import shop.whitezia.client.controlplane.isBootstrapRetryStatus
import shop.whitezia.client.controlplane.readUtf8Limited

internal class WhiteZiaAccountApi(
    context: Context,
    private val baseUrl: String = BuildConfig.ACCOUNT_API_BASE.trimEnd('/'),
    fallbackBaseUrl: String = BuildConfig.ACCOUNT_API_FALLBACK_BASE.trimEnd('/'),
    private val recoveryBaseUrl: String = BuildConfig.RECOVERY_API_BASE.trimEnd('/'),
) {
    private val accountBaseUrls = accountApiBaseCandidates(baseUrl, fallbackBaseUrl)
    private val controlPlaneTransport = ControlPlaneTransport(context)
    private val healthCheckUrl = URL(accountBaseUrls.first()).let { base ->
        URL(base.protocol, base.host, base.port, "/healthz").toString()
    }

    fun register(email: String, password: String, displayName: String) {
        request(
            path = "/auth/register",
            method = "POST",
            body = JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .put("display_name", displayName.trim()),
        )
    }

    fun verifyEmail(email: String, code: String): AccountSession = parseSession(
        request(
            path = "/auth/verify-email",
            method = "POST",
            body = JSONObject().put("email", email.trim()).put("code", code.trim()),
        ),
    )

    fun resendVerification(email: String) {
        request(
            path = "/auth/resend-verification",
            method = "POST",
            body = JSONObject().put("email", email.trim()),
        )
    }

    fun login(email: String, password: String): AccountSession = parseSession(
        request(
            path = "/auth/login",
            method = "POST",
            body = JSONObject().put("email", email.trim()).put("password", password),
            replayable = true,
        ),
    )

    fun refresh(refreshToken: String): AccountSession = parseSession(
        request(
            path = "/auth/refresh",
            method = "POST",
            body = JSONObject().put("refresh_token", refreshToken),
        ),
    )

    fun logout(refreshToken: String) {
        request(
            path = "/auth/logout",
            method = "POST",
            body = JSONObject().put("refresh_token", refreshToken),
        )
    }

    fun requestPasswordReset(email: String) {
        request(
            path = "/auth/password/forgot",
            method = "POST",
            body = JSONObject().put("email", email.trim()),
        )
    }

    fun resetPassword(email: String, code: String, password: String) {
        request(
            path = "/auth/password/reset",
            method = "POST",
            body = JSONObject()
                .put("email", email.trim())
                .put("code", code.trim())
                .put("password", password),
        )
    }

    fun account(accessToken: String): AccountProfile = parseAccount(
        JSONObject(request("/me/", accessToken = accessToken)),
    )

    fun subscription(accessToken: String): AccountSubscriptionStatus {
        val root = JSONObject(request("/me/subscription", accessToken = accessToken))
        val subscription = root.optJSONObject("subscription")?.let {
            AccountSubscription(
                id = it.optString("id"),
                planId = it.optString("plan_id"),
                status = it.optString("status"),
                expiresAt = it.optString("expires_at"),
                isForever = it.optBoolean("is_forever"),
            )
        }
        return AccountSubscriptionStatus(
            subscription = subscription,
            deviceLimit = root.optInt("device_limit", 3),
            deviceCount = root.optInt("device_count"),
            trialAvailable = root.optBoolean("trial_available"),
        )
    }

    fun devices(accessToken: String): List<AccountDevice> = JSONArray(
        request("/me/devices", accessToken = accessToken),
    ).mapObjects(::parseDevice)

    fun currentDevice(accessToken: String, installationId: String): AccountDevice? {
        val raw = try {
            request(
                path = "/me/devices/current",
                method = "POST",
                accessToken = accessToken,
                body = JSONObject().put("installation_id", installationId),
                replayable = true,
            )
        } catch (error: AccountApiException) {
            if (error.statusCode == 404) return null
            throw error
        }
        return parseDevice(JSONObject(raw))
    }

    fun linkCurrentDeviceIdentity(
        accessToken: String,
        installationId: String,
        aliasInstallationId: String,
    ): AccountDevice = parseDevice(
        JSONObject(
            request(
                path = "/me/devices/current/identity",
                method = "POST",
                accessToken = accessToken,
                body = JSONObject()
                    .put("installation_id", installationId)
                    .put("alias_installation_id", aliasInstallationId),
                replayable = true,
            ),
        ),
    )

    fun payments(accessToken: String): List<AccountPayment> = JSONArray(
        request("/me/payments", accessToken = accessToken),
    ).mapObjects { item ->
        AccountPayment(
            id = item.optString("id"),
            planId = item.optString("plan_id"),
            amountMinor = item.optLong("amount_minor"),
            currency = item.optString("currency", "RUB"),
            status = item.optString("status"),
            createdAt = item.optString("created_at"),
        )
    }

    fun plans(): List<AccountPlan> = JSONArray(request("/plans")).mapObjects { item ->
        AccountPlan(
            id = item.optString("id"),
            title = item.optString("title", item.optString("id")),
            durationDays = item.optInt("duration_days"),
            priceMinor = item.optLong("price_minor"),
            originalPriceMinor = item.optLong("original_price_minor"),
            currency = item.optString("currency", "RUB"),
        )
    }

    fun enrollDevice(
        accessToken: String,
        installationId: String,
        publicKey: String,
        name: String,
    ): AccountDevice {
        val item = JSONObject(
            request(
                path = "/me/devices/enroll",
                method = "POST",
                accessToken = accessToken,
                body = JSONObject()
                    .put("installation_id", installationId)
                    .put("public_key", publicKey)
                    .put("name", name)
                    .put("platform", "android"),
                replayable = true,
            ),
        )
        return parseDevice(item, name)
    }

    fun deviceBundleChallenge(accessToken: String, installationId: String): DeviceBundleChallenge {
        val item = JSONObject(
            request(
                path = "/me/devices/current/bundle/challenge",
                method = "POST",
                accessToken = accessToken,
                body = JSONObject().put("installation_id", installationId),
                replayable = true,
            ),
        )
        return DeviceBundleChallenge(
            id = item.getString("challenge_id"),
            challenge = item.getString("challenge"),
            expiresAt = item.optString("expires_at"),
        )
    }

    fun deviceBundle(
        accessToken: String,
        installationId: String,
        challengeId: String,
        signature: String,
    ): String = JSONObject(
        request(
            path = "/me/devices/current/bundle",
            method = "POST",
            accessToken = accessToken,
            body = JSONObject()
                .put("installation_id", installationId)
                .put("challenge_id", challengeId)
                .put("signature", signature),
        ),
    ).getString("bundle")

    fun disableDevice(accessToken: String, deviceId: String) {
        request("/me/devices/$deviceId", method = "DELETE", accessToken = accessToken)
    }

    fun createOrder(accessToken: String, planId: String): String = JSONObject(
        request(
            path = "/me/orders",
            method = "POST",
            accessToken = accessToken,
            body = JSONObject().put("plan_id", planId),
        ),
    ).getString("payment_url")

    fun redeemTrial(accessToken: String) {
        request(path = "/me/trial", method = "POST", accessToken = accessToken)
    }

    fun recoveryDeviceBundleChallenge(
        refreshToken: String,
        installationId: String,
    ): DeviceBundleChallenge? {
        val raw = try {
            requestUrl(
                rawUrl = recoveryBaseUrl + "/recovery/device-bundle/challenge",
                method = "POST",
                body = JSONObject()
                    .put("refresh_token", refreshToken)
                    .put("installation_id", installationId),
                replayable = true,
            )
        } catch (error: AccountApiException) {
            if (error.statusCode == 404) return null
            throw error
        }
        val item = JSONObject(raw)
        return DeviceBundleChallenge(
            id = item.getString("challenge_id"),
            challenge = item.getString("challenge"),
            expiresAt = item.optString("expires_at"),
        )
    }

    fun recoveryDeviceBundle(
        challengeId: String,
        signature: String,
    ): RecoveryDeviceBundle? {
        val raw = try {
            requestUrl(
                rawUrl = recoveryBaseUrl + "/recovery/device-bundle/proof",
                method = "POST",
                body = JSONObject()
                    .put("challenge_id", challengeId)
                    .put("signature", signature),
                replayable = false,
            )
        } catch (error: AccountApiException) {
            if (error.statusCode == 404) return null
            throw error
        }
        return parseRecoveryDeviceBundle(raw)
    }

    private fun request(
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        accessToken: String = "",
        replayable: Boolean = method == "GET" || method == "HEAD",
    ): String {
        val candidates = if (replayable) accountBaseUrls else listOf(accountBaseUrls.first())
        var lastError: Exception? = null
        candidates.forEachIndexed { index, candidateBaseUrl ->
            try {
                return requestUrl(
                    rawUrl = candidateBaseUrl + path,
                    method = method,
                    body = body,
                    accessToken = accessToken,
                    replayable = replayable,
                )
            } catch (error: Exception) {
                if (
                    !replayable ||
                    !shouldRetryAccountRequestThroughBootstrap(error) ||
                    index == candidates.lastIndex
                ) {
                    throw error
                }
                lastError = error
            }
        }
        throw checkNotNull(lastError)
    }

    private fun requestUrl(
        rawUrl: String,
        method: String,
        body: JSONObject? = null,
        accessToken: String = "",
        replayable: Boolean,
    ): String {
        val url = URL(rawUrl)
        require(url.protocol.equals("https", ignoreCase = true)) { "Account API must use HTTPS" }
        val requestBlock: (HttpURLConnection) -> String = { connection ->
            connection.apply {
                requestMethod = method
                connectTimeout = ConnectTimeoutMillis
                readTimeout = ReadTimeoutMillis
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "WhiteZia/${BuildConfig.VERSION_NAME}")
                if (accessToken.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $accessToken")
                }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            if (body != null) {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use { it.readUtf8Limited(MaxResponseBytes) }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(response).optString("error") }
                    .getOrNull()
                    .orEmpty()
                    .ifBlank { "Account API returned HTTP $code" }
                throw AccountApiException(code, message)
            }
            response
        }
        return if (replayable) {
            controlPlaneTransport.execute(
                rawUrl = url.toString(),
                shouldRetry = ::shouldRetryAccountRequestThroughBootstrap,
                request = requestBlock,
            )
        } else {
            controlPlaneTransport.executeNonReplayable(
                rawUrl = url.toString(),
                healthCheckUrl = healthCheckUrl,
                request = requestBlock,
            )
        }
    }

    private fun parseSession(raw: String): AccountSession {
        val root = JSONObject(raw)
        return AccountSession(
            accessToken = root.getString("access_token"),
            refreshToken = root.getString("refresh_token"),
            expiresInSeconds = root.optLong("expires_in", 900L),
            account = parseAccount(root.getJSONObject("account")),
        )
    }

    private fun parseAccount(item: JSONObject): AccountProfile = AccountProfile(
        id = item.optString("id"),
        email = item.optString("email"),
        displayName = item.optString("display_name"),
        telegramLinked = item.optBoolean("telegram_linked"),
    )

    private fun parseDevice(item: JSONObject, fallbackName: String = "Устройство") = AccountDevice(
        id = item.optString("id"),
        name = item.optString("name", fallbackName),
        status = item.optString("status"),
        platform = item.optString("platform", "android"),
        bundleReady = item.optBoolean("bundle_ready"),
        createdAt = item.optString("created_at"),
    )

    private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        buildList(length()) {
            repeat(length()) { index -> add(transform(getJSONObject(index))) }
        }

    private companion object {
        const val ConnectTimeoutMillis = 4_000
        const val ReadTimeoutMillis = 8_000
        const val MaxResponseBytes = 1024 * 1024
    }
}

internal fun parseRecoveryDeviceBundle(raw: String): RecoveryDeviceBundle {
    val root = JSONObject(raw)
    val deviceId = root.getString("device_id").trim()
    val bundle = root.getString("bundle").trim()
    require(deviceId.isNotBlank()) { "Recovery response has no device" }
    require(bundle.startsWith("stormbundle://")) { "Recovery response has invalid bundle" }
    return RecoveryDeviceBundle(
        deviceId = deviceId,
        bundle = bundle,
        updatedAt = root.optString("updated_at"),
    )
}

private fun shouldRetryAccountRequestThroughBootstrap(error: Throwable): Boolean =
    error is IOException || (error is AccountApiException && isBootstrapRetryStatus(error.statusCode))

internal fun shouldAttemptAccountRecovery(error: Throwable): Boolean =
    error is IOException ||
        (error is AccountApiException && isBootstrapRetryStatus(error.statusCode))

internal fun recoveryInstallationCandidates(storedId: String, stableId: String): List<String> =
    listOf(stableId.trim(), storedId.trim()).filter(String::isNotBlank).distinct()

internal fun accountApiBaseCandidates(primary: String, fallback: String): List<String> =
    listOf(primary, fallback)
        .map { it.trim().trimEnd('/') }
        .filter(String::isNotBlank)
        .distinct()
