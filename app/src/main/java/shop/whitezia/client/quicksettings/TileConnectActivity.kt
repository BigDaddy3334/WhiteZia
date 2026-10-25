package shop.whitezia.client.quicksettings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import shop.whitezia.client.MainActivity

/** Internal entrypoint: the exported launcher never accepts a connect command. */
class TileConnectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ActionConnectFromTile
            putExtra(TileConnectionRequests.ExtraRequest, TileConnectionRequests.issue())
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }
}
