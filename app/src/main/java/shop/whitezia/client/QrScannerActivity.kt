package shop.whitezia.client

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class QrScannerActivity : ComponentActivity() {
    private val scanCompleted = AtomicBoolean(false)
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            finishWithError("Для сканирования QR нужен доступ к камере")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        setContentView(
            FrameLayout(this).apply {
                addView(
                    previewView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
                addView(
                    ScannerReticleView(this@QrScannerActivity),
                    FrameLayout.LayoutParams(
                        248.dp,
                        248.dp,
                        Gravity.CENTER,
                    ),
                )
            },
        )

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                bindCamera(cameraProviderFuture)
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun bindCamera(cameraProviderFuture: ListenableFuture<ProcessCameraProvider>) {
        runCatching {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, QrCodeAnalyzer(::finishWithValue))
                }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }.onFailure {
            finishWithError("Не удалось запустить камеру")
        }
    }

    private fun finishWithValue(value: String) {
        if (!scanCompleted.compareAndSet(false, true)) return
        runOnUiThread {
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_QR_VALUE, value))
            finish()
        }
    }

    private fun finishWithError(message: String) {
        if (!scanCompleted.compareAndSet(false, true)) return
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(EXTRA_ERROR, message))
        finish()
    }

    companion object {
        const val EXTRA_QR_VALUE = "shop.whitezia.client.extra.QR_VALUE"
        const val EXTRA_ERROR = "shop.whitezia.client.extra.QR_ERROR"
    }
}

private class QrCodeAnalyzer(
    private val onQrCode: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.CHARACTER_SET to "UTF-8",
            ),
        )
    }

    override fun analyze(image: ImageProxy) {
        try {
            val source = PlanarYUVLuminanceSource(
                image.copyLumaPlane(),
                image.width,
                image.height,
                0,
                0,
                image.width,
                image.height,
                false,
            )
            val value = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text.trim()
            if (value.isNotEmpty()) {
                onQrCode(value)
            }
        } catch (_: Exception) {
            // Frames without a QR code are expected while the camera is scanning.
        } finally {
            reader.reset()
            image.close()
        }
    }
}

private fun ImageProxy.copyLumaPlane(): ByteArray {
    val plane = planes.first()
    val width = width
    val height = height
    val pixels = ByteArray(width * height)
    val buffer = plane.buffer.duplicate()
    val row = ByteArray(plane.rowStride)
    for (y in 0 until height) {
        buffer.position(y * plane.rowStride)
        buffer.get(row, 0, minOf(plane.rowStride, buffer.remaining()))
        for (x in 0 until width) {
            pixels[y * width + x] = row[x * plane.pixelStride]
        }
    }
    return pixels
}

private class ScannerReticleView(context: android.content.Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        val inset = paint.strokeWidth / 2f
        canvas.drawRoundRect(inset, inset, width - inset, height - inset, 20f, 20f, paint)
    }
}

private val Int.dp: Int
    get() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
