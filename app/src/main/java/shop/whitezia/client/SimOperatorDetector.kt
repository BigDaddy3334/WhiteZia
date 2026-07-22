package shop.whitezia.client

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import java.util.Locale
import shop.whitezia.client.model.WhiteZiaOptions

internal data class SimOperatorCheckResult(
    val isMismatch: Boolean,
    val message: String,
)

internal data class SimOperatorDetectionResult(
    val operatorCode: String?,
    val rawValues: List<String>,
    val isTMobile: Boolean,
)

internal fun checkSelectedOperatorAgainstActiveSim(
    context: Context,
    selectedOperatorCode: String,
): SimOperatorCheckResult {
    val selectedLabel = operatorLabel(selectedOperatorCode)
    val detection = readActiveSimOperatorValues(context, preferNetworkOperator = true).getOrElse { error ->
        return SimOperatorCheckResult(
            isMismatch = false,
            message = "Не удалось проверить SIM: ${error.message ?: error::class.java.simpleName}",
        )
    }
    val rawValues = detection.rawValues
    if (rawValues.isEmpty()) {
        return SimOperatorCheckResult(
            isMismatch = false,
            message = "Не удалось определить активную SIM, продолжаю с выбранным оператором: $selectedLabel",
        )
    }

    val detectedOperator = detection.operatorCode
    if (detectedOperator == null && detection.isTMobile) {
        return SimOperatorCheckResult(false, "SIM T-Mobile: продолжаю с выбранным оператором: $selectedLabel")
    }
    if (detectedOperator == null) {
        return SimOperatorCheckResult(false, "Активная SIM: ${rawValues.joinToString()} — оператор не распознан")
    }

    val detectedLabel = operatorLabel(detectedOperator)
    if (detection.isTMobile) {
        return SimOperatorCheckResult(false, "SIM T-Mobile в сети $detectedLabel")
    }
    return if (detectedOperator == selectedOperatorCode) {
        SimOperatorCheckResult(false, "SIM проверена: $detectedLabel")
    } else {
        SimOperatorCheckResult(
            true,
            "Выбран $selectedLabel, но активная SIM: $detectedLabel (${rawValues.joinToString()})",
        )
    }
}

internal fun detectActiveSimOperator(
    context: Context,
    preferNetworkOperator: Boolean,
): SimOperatorDetectionResult {
    return readActiveSimOperatorValues(context, preferNetworkOperator).getOrElse {
        SimOperatorDetectionResult(operatorCode = null, rawValues = emptyList(), isTMobile = false)
    }
}

private fun readActiveSimOperatorValues(
    context: Context,
    preferNetworkOperator: Boolean,
): Result<SimOperatorDetectionResult> = runCatching {
    val telephonyManager = context.getSystemService(TelephonyManager::class.java)
        ?: return@runCatching SimOperatorDetectionResult(null, emptyList(), false)
    val defaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
    val dataTelephonyManager = if (defaultDataSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
        runCatching { telephonyManager.createForSubscriptionId(defaultDataSubId) }
            .getOrDefault(telephonyManager)
    } else {
        telephonyManager
    }
    val networkValues = listOf(
        dataTelephonyManager.networkOperatorName,
        dataTelephonyManager.networkOperator,
    ).normalizedOperatorValues()
    val simValues = listOf(
        dataTelephonyManager.simOperatorName,
        dataTelephonyManager.simOperator,
    ).normalizedOperatorValues()
    val mobileNetworkActive = isMobileNetworkAvailable(context)
    val rawValues = if (preferNetworkOperator || mobileNetworkActive) {
        networkValues + simValues
    } else {
        simValues + networkValues
    }.distinct()
    val detectedFromNetwork = detectOperatorCode(networkValues)
    val detectedFromSim = detectOperatorCode(simValues)
    SimOperatorDetectionResult(
        operatorCode = if (preferNetworkOperator || mobileNetworkActive) {
            detectedFromNetwork ?: detectedFromSim
        } else {
            detectedFromSim ?: detectedFromNetwork
        },
        rawValues = rawValues,
        isTMobile = rawValues.any { value ->
            val normalized = value.lowercase(Locale.US)
            TMobileOperatorMarkers.any { it in normalized }
        },
    )
}

private fun List<String>.normalizedOperatorValues(): List<String> =
    map(String::trim).filter(String::isNotEmpty).distinct()

private fun isMobileNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    return connectivityManager.allNetworks.any { network ->
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@any false
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

private fun detectOperatorCode(rawValues: List<String>): String? {
    val normalizedValues = rawValues.map { it.lowercase(Locale.US) }
    return when {
        normalizedValues.any { value -> MtsOperatorMarkers.any { it in value } } -> WhiteZiaOptions.OperatorMts
        normalizedValues.any { value -> BeelineOperatorMarkers.any { it in value } } -> WhiteZiaOptions.OperatorBeeline
        normalizedValues.any { value -> Tele2OperatorMarkers.any { it in value } } -> WhiteZiaOptions.OperatorTele2
        normalizedValues.any { value -> MegafonYotaOperatorMarkers.any { it in value } } -> WhiteZiaOptions.OperatorMegafonYota
        else -> null
    }
}

internal fun operatorLabel(operatorCode: String): String = when (operatorCode) {
    WhiteZiaOptions.OperatorMts -> "МТС"
    WhiteZiaOptions.OperatorBeeline -> "Билайн"
    WhiteZiaOptions.OperatorTele2 -> "Tele2"
    else -> "Мегафон/Йота"
}

private val TMobileOperatorMarkers = listOf("t-mobile", "tmobile")
private val MtsOperatorMarkers = listOf("mts", "мтс", "25001")
private val BeelineOperatorMarkers = listOf("beeline", "билайн", "vimpelcom", "вымпелком", "25099")
private val Tele2OperatorMarkers = listOf("tele2", "теле2", "t2", "25020")
private val MegafonYotaOperatorMarkers = listOf("megafon", "мегафон", "yota", "йота", "25002", "25011")
