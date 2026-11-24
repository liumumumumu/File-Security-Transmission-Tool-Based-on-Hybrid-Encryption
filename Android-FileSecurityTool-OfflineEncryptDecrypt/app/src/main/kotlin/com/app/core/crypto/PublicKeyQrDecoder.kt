package com.filesecuritytool.android.core.crypto

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.common.HybridBinarizer
import java.io.ByteArrayOutputStream

class PublicKeyQrDecoder(private val resolver: ContentResolver) {
    data class Result(val publicKeyPem: String, val fingerprint: String)

    fun decodePng(uri: Uri): Result {
        require(resolver.getType(uri)?.equals(PNG_MIME, ignoreCase = true) == true) {
            "A PNG image is required"
        }
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_ENCODED_BYTES) { "PNG image is too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw IllegalArgumentException("Unable to open PNG image")
        require(bytes.size >= PNG_SIGNATURE.size &&
            bytes.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)) {
            "The selected file is not a valid PNG image"
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..MAX_DIMENSION && bounds.outHeight in 1..MAX_DIMENSION) {
            "PNG dimensions are unsupported"
        }
        require(bounds.outWidth.toLong() * bounds.outHeight <= MAX_PIXELS) {
            "PNG image has too many pixels"
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("Unable to decode PNG image")
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val qrText = QRCodeReader().decode(
                BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))),
                mapOf(DecodeHintType.CHARACTER_SET to Charsets.UTF_8.name())
            ).text
            decodePayload(qrText)
        } finally {
            bitmap.recycle()
        }
    }

    fun decodePayload(qrText: String): Result {
        val pem = PublicKeyArtifactCodec.decode(qrText)
        return Result(pem, PublicKeyCodec.fingerprint(pem))
    }

    companion object {
        private const val PNG_MIME = "image/png"
        private const val MAX_ENCODED_BYTES = 12 * 1024 * 1024
        private const val MAX_DIMENSION = 4096
        private const val MAX_PIXELS = 16_000_000L
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
    }
}
