package com.filesecuritytool.android.ui.qr

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun PublicKeyQrScanner(
    modifier: Modifier = Modifier,
    onPayload: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val delivered = remember { AtomicBoolean(false) }
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { image ->
                        val payload = try {
                            decodeQr(image)
                        } finally {
                            image.close()
                        }
                        if (payload != null && delivered.compareAndSet(false, true)) {
                            post { onPayload(payload) }
                        }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }, ContextCompat.getMainExecutor(context))
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            executor.shutdownNow()
        }
    }
}

private fun decodeQr(image: ImageProxy): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val width = image.width
    val height = image.height
    val luminance = ByteArray(width * height)
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    for (row in 0 until height) {
        for (column in 0 until width) {
            luminance[row * width + column] = buffer.get(row * rowStride + column * pixelStride)
        }
    }
    val (rotated, rotatedWidth, rotatedHeight) = rotate(
        luminance, width, height, image.imageInfo.rotationDegrees
    )
    return runCatching {
        val source = PlanarYUVLuminanceSource(
            rotated, rotatedWidth, rotatedHeight,
            0, 0, rotatedWidth, rotatedHeight, false
        )
        QRCodeReader().decode(
            BinaryBitmap(HybridBinarizer(source)),
            mapOf(DecodeHintType.TRY_HARDER to true)
        ).text
    }.getOrNull()
}

private fun rotate(
    source: ByteArray,
    width: Int,
    height: Int,
    degrees: Int
): Triple<ByteArray, Int, Int> = when (degrees) {
    90 -> {
        val result = ByteArray(source.size)
        for (y in 0 until height) for (x in 0 until width) {
            result[x * height + (height - y - 1)] = source[y * width + x]
        }
        Triple(result, height, width)
    }
    180 -> Triple(source.reversedArray(), width, height)
    270 -> {
        val result = ByteArray(source.size)
        for (y in 0 until height) for (x in 0 until width) {
            result[(width - x - 1) * height + y] = source[y * width + x]
        }
        Triple(result, height, width)
    }
    else -> Triple(source, width, height)
}
