package shop.whitezia.client.model

/** Explicit client choice, independent of saved server credentials and transport routing. */
object CottenDnsMode {
    const val Normal = "normal"
    const val Max = "cottenmax"

    fun normalize(raw: String): String = if (raw.trim().lowercase() == Max) Max else Normal

    /** Max's early local listener must also pass the same real content checks as automatic DNS. */
    fun requiresStrongDnsHealth(raw: String, customResolversEnabled: Boolean): Boolean =
        !customResolversEnabled || normalize(raw) == Max
}
