package shop.whitezia.client.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Main-process cleanup for notification Stop, revocation and terminal failure. */
class VpnBindingReleaseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Recheck persisted authority: a late cancellation must not unbind a new START.
        VpnRuntimeBinding.releaseWhenNotRequested(context)
    }
}
