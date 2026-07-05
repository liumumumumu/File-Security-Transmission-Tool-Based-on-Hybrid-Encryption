package com.filesecuritytool.android.core.crypto

import android.graphics.Bitmap
import android.graphics.Color
import com.filesecuritytool.android.core.files.DownloadsOutputStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class PublicKeyQrExporter(private val outputs: DownloadsOutputStore) {
    fun export(publicKeyPem: String): DownloadsOutputStore.PendingOutput {
        val fingerprint = PublicKeyCodec.fingerprint(publicKeyPem)
        val payload = PublicKeyArtifactCodec.encode(publicKeyPem)
        val output = outputs.create(
            "fst-public-key-${fingerprint.takeLast(8)}.png",
            "image/png"
        )
        return try {
            val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 1024, 1024)
            val pixels = IntArray(1024 * 1024)
            for (y in 0 until 1024) for (x in 0 until 1024) {
                pixels[y * 1024 + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
            Bitmap.createBitmap(pixels, 1024, 1024, Bitmap.Config.ARGB_8888).useBitmap { bitmap ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output.stream))
            }
            outputs.complete(output)
            output
        } catch (failure: Throwable) {
            outputs.discard(output)
            throw failure
        }
    }

    private inline fun <T> Bitmap.useBitmap(block: (Bitmap) -> T): T =
        try { block(this) } finally { recycle() }
}
