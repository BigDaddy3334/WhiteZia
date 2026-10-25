package shop.whitezia.client.storm

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import shop.whitezia.client.model.CottenDnsMode

/** Max opens SOCKS before authentication. A listening port alone cannot establish readiness. */
internal class CottenDnsStartupState(mode: String) {
    private val max = CottenDnsMode.normalize(mode) == CottenDnsMode.Max
    private val ready = AtomicBoolean(!max)
    private val failure = AtomicReference<String?>(null)

    fun isProtocolReady(): Boolean = ready.get() && failure.get() == null
    fun failureOrNull(): String? = failure.get()

    @Synchronized fun observe(line: String) {
        if (!max || ready.get() || failure.get() != null) return
        val marker = line.indexOf("WD_PROGRESS")
        if (marker >= 0) {
            val fields = Field.findAll(line.substring(marker + "WD_PROGRESS".length))
                .associate { it.groupValues[1] to it.groupValues[2] }
            if (fields["phase"] == "connected" && fields["mode"] == "cottenmax" && fields["percent"] == "100") {
                ready.set(true)
                return
            }
        }
        // Keep native error details (which may include endpoints) out of the startup exception.
        if (line.contains("CottenMax authentication failed:")) {
            failure.compareAndSet(null, "CottenMax authentication failed; check server Max support, session quota and the selected profile")
        } else if (line.contains("CottenMax authenticated, but tunnel transfer verification failed;")) {
            failure.compareAndSet(null, "CottenMax transfer verification failed")
        }
    }

    private companion object { val Field = Regex("""(\w+)=([^\s]+)""") }
}
