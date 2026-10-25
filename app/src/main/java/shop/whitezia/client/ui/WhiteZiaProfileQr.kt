package shop.whitezia.client.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import shop.whitezia.client.QrScannerActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

@Composable
internal fun ProfileQrPreview(link: String) {
    val qrBitmap = remember(link) {
        runCatching { buildQrBitmap(link, QrBitmapSizePx) }.getOrNull()
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = WhiteZiaL10n.cdProfileQrCode,
                modifier = Modifier
                    .size(210.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.5.dp, WhiteZiaPalette.Border, RoundedCornerShape(12.dp))
                    .padding(10.dp),
            )
        } else {
            Text(
                text = WhiteZiaL10n.profileQrUnavailable,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 10.sp,
                    color = WhiteZiaPalette.Error,
                ),
            )
        }
    }
}

@Composable
internal fun rememberQrProfileImportLauncher(
    onDecoded: (String) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val cancelledMessage = WhiteZiaL10n.qrScanCancelled
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val decoded = result.data?.getStringExtra(QrScannerActivity.EXTRA_QR_VALUE)?.trim().orEmpty()
        if (result.resultCode == Activity.RESULT_OK && decoded.isNotEmpty()) {
            onDecoded(decoded)
        } else {
            onError(result.data?.getStringExtra(QrScannerActivity.EXTRA_ERROR) ?: cancelledMessage)
        }
    }
    return remember(launcher, context, cancelledMessage, onDecoded, onError) {
        {
            runCatching {
                launcher.launch(Intent(context, QrScannerActivity::class.java))
            }.onFailure {
                onError(cancelledMessage)
            }
        }
    }
}

private fun buildQrBitmap(
    value: String,
    sizePx: Int,
): Bitmap {
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 2,
    )
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val pixels = IntArray(matrix.width * matrix.height)
    for (y in 0 until matrix.height) {
        val rowOffset = y * matrix.width
        for (x in 0 until matrix.width) {
            pixels[rowOffset + x] = if (matrix[x, y]) {
                android.graphics.Color.BLACK
            } else {
                android.graphics.Color.WHITE
            }
        }
    }
    return Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
    }
}
