package shop.whitezia.client.account

import java.util.UUID

/** Keys outlive a session: an uncertain debit must be retried, never recreated. */
internal class WalletRequestStore(
    private val read: (String) -> String?,
    private val write: (String, String) -> Boolean,
    private val remove: (String) -> Boolean,
) {
    fun existing(accountId: String, operation: String): String? = synchronized(processLock) {
        read(storageKey(accountId, operation))
    }

    fun key(accountId: String, operation: String): String = synchronized(processLock) {
        val name = storageKey(accountId, operation)
        read(name)?.let { return@synchronized it }
        val key = UUID.randomUUID().toString()
        check(write(name, key)) { "Не удалось сохранить запрос оплаты" }
        key
    }

    fun complete(accountId: String, operation: String) = synchronized(processLock) {
        check(remove(storageKey(accountId, operation))) {
            "Оплата выполнена. Обновите кабинет перед следующей покупкой"
        }
    }

    private fun storageKey(accountId: String, operation: String): String {
        require(accountId.isNotBlank())
        return "wallet_request_" + bundleFingerprint("$accountId:$operation")
    }

    private companion object {
        val processLock = Any()
    }
}
