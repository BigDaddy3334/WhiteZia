package shop.whitezia.client.quicksettings

import java.util.UUID

/** Issued by the non-exported trampoline and consumed once in the same process. */
internal object TileConnectionRequests {
    const val ExtraRequest = "shop.whitezia.client.TILE_REQUEST"
    private var pending: String? = null

    @Synchronized
    fun issue(): String = UUID.randomUUID().toString().also { pending = it }

    @Synchronized
    fun consume(request: String?): Boolean {
        if (request == null || request != pending) return false
        pending = null
        return true
    }
}
