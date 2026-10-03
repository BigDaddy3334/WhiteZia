package shop.whitezia.client.account

import java.io.IOException

internal const val DeviceProfilePendingMessage = "device profile is not ready"
internal const val DeviceProfilePollWindowMillis = 10 * 60 * 1_000L

internal class AccountDeviceNotBoundException : IllegalStateException("Устройство не привязано. Привяжите его в личном кабинете")

internal fun readExistingDeviceProfile(
    findDevice: () -> AccountDevice?,
    fetchBundle: () -> String,
): AccountDeviceSync? {
    val device = findDevice() ?: return null
    val bundle = if (device.status !in setOf("disabled", "failed") &&
        (device.bundleReady || device.status == "active")) {
        try {
            fetchBundle()
        } catch (error: AccountApiException) {
            if (error.statusCode in setOf(401, 404) && findDevice() == null) {
                throw AccountDeviceNotBoundException()
            }
            if (isDeviceProfilePending(error)) null else throw error
        }
    } else null
    return AccountDeviceSync(device, bundle)
}

internal fun isDeviceProfilePending(error: Throwable): Boolean =
    error is AccountApiException && error.statusCode == 409 && error.message == DeviceProfilePendingMessage

internal fun shouldRetryDeviceEnrollment(error: Throwable): Boolean =
    error is IOException || isDeviceProfilePending(error) ||
        (error is AccountApiException && (error.statusCode in setOf(408, 429) || error.statusCode in 500..599))
