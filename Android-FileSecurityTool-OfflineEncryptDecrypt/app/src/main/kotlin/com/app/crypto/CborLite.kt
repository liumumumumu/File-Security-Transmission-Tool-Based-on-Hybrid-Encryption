package com.filesecuritytool.android.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Port of CborLite.java — canonical CBOR (RFC 7049 subset) encoder/decoder.
 * Canonical encoding sorts map keys by their CBOR-encoded byte representation.
 * This deterministic ordering is critical for FST2/FST-TEXT1 interop.
 */
object CborLite {

    fun encodeCanonical(map: Map<String, Any?>): ByteArray {
        val out = ByteArrayOutputStream()
        writeMap(out, map)
        return out.toByteArray()
    }

    fun decodeMap(bytes: ByteArray): Map<String, Any?> {
        val value = Reader(bytes).read()
        require(value is Map<*, *>) { "CBOR root must be a map" }
        val result = LinkedHashMap<String, Any?>()
        for ((k, v) in value) {
            result[k.toString()] = v
        }
        return result
    }

    // ── Write dispatch ─────────────────────────────────────────

    private fun writeValue(out: ByteArrayOutputStream, value: Any?) {
        when (value) {
            null -> out.write(0xf6)
            is String -> writeString(out, value)
            is ByteArray -> {
                writeTypeAndLength(out, 2, value.size.toLong())
                out.write(value)
            }
            is Int -> writeInteger(out, value.toLong())
            is Long -> writeInteger(out, value)
            is Boolean -> out.write(if (value) 0xf5 else 0xf4)
            is List<*> -> {
                writeTypeAndLength(out, 4, value.size.toLong())
                for (item in value) writeValue(out, item)
            }
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                writeMap(out, value as Map<String, Any?>)
            }
            else -> throw IllegalArgumentException("Unsupported CBOR value: ${value::class.java.name}")
        }
    }

    private fun writeMap(out: ByteArrayOutputStream, map: Map<String, Any?>) {
        val entries = map.entries.sortedWith { a, b ->
            compareBytes(encodedString(a.key), encodedString(b.key))
        }
        writeTypeAndLength(out, 5, entries.size.toLong())
        for ((key, value) in entries) {
            writeString(out, key)
            writeValue(out, value)
        }
    }

    private fun encodedString(value: String): ByteArray {
        val out = ByteArrayOutputStream()
        writeString(out, value)
        return out.toByteArray()
    }

    private fun compareBytes(left: ByteArray, right: ByteArray): Int {
        val min = minOf(left.size, right.size)
        for (i in 0 until min) {
            val diff = (left[i].toInt() and 0xff) - (right[i].toInt() and 0xff)
            if (diff != 0) return diff
        }
        return left.size - right.size
    }

    private fun writeString(out: ByteArrayOutputStream, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        writeTypeAndLength(out, 3, bytes.size.toLong())
        out.write(bytes)
    }

    private fun writeInteger(out: ByteArrayOutputStream, value: Long) {
        if (value >= 0) {
            writeTypeAndLength(out, 0, value)
        } else {
            writeTypeAndLength(out, 1, -1 - value)
        }
    }

    private fun writeTypeAndLength(out: ByteArrayOutputStream, majorType: Int, length: Long) {
        val prefix = majorType shl 5
        when {
            length < 24 -> out.write(prefix or length.toInt())
            length <= 0xff -> {
                out.write(prefix or 24)
                out.write(length.toInt())
            }
            length <= 0xffff -> {
                out.write(prefix or 25)
                out.write((length shr 8).toInt())
                out.write(length.toInt())
            }
            length <= 0xffffffffL -> {
                out.write(prefix or 26)
                for (shift in 24 downTo 0 step 8) {
                    out.write((length shr shift).toInt())
                }
            }
            else -> {
                out.write(prefix or 27)
                for (shift in 56 downTo 0 step 8) {
                    out.write((length shr shift).toInt())
                }
            }
        }
    }

    // ── Reader ─────────────────────────────────────────────────

    private class Reader(private val bytes: ByteArray) {
        private val input = ByteArrayInputStream(bytes)

        fun read(): Any? {
            val initial = readByte()
            val major = initial shr 5
            val additional = initial and 0x1f
            return when (major) {
                0 -> readLength(additional)
                1 -> -1 - readLength(additional)
                2 -> readBytes(readLength(additional).toInt())
                3 -> String(readBytes(readLength(additional).toInt()), StandardCharsets.UTF_8)
                4 -> readArray(readLength(additional).toInt())
                5 -> readMap(readLength(additional).toInt())
                7 -> readSimple(additional)
                else -> throw IllegalArgumentException("Unsupported CBOR major type: $major")
            }
        }

        private fun readSimple(additional: Int): Any? = when (additional) {
            20 -> false
            21 -> true
            22 -> null
            else -> throw IllegalArgumentException("Unsupported CBOR simple value: $additional")
        }

        private fun readArray(size: Int): List<Any?> {
            return (0 until size).map { read() }
        }

        private fun readMap(size: Int): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            repeat(size) {
                val key = read()
                require(key is String) { "CBOR map keys must be strings" }
                map[key] = read()
            }
            return map
        }

        private fun readBytes(length: Int): ByteArray {
            val buf = ByteArray(length)
            val read = input.read(buf, 0, length)
            require(read == length) { "Unexpected CBOR EOF" }
            return buf
        }

        private fun readLength(additional: Int): Long = when {
            additional < 24 -> additional.toLong()
            additional == 24 -> readByte().toLong() and 0xff
            additional == 25 -> ((readByte().toLong() and 0xff) shl 8) or (readByte().toLong() and 0xff)
            additional == 26 -> {
                var value = 0L
                repeat(4) { value = (value shl 8) or (readByte().toLong() and 0xff) }
                value
            }
            additional == 27 -> {
                var value = 0L
                repeat(8) { value = (value shl 8) or (readByte().toLong() and 0xff) }
                value
            }
            else -> throw IllegalArgumentException("Indefinite CBOR length not supported")
        }

        private fun readByte(): Int {
            val value = input.read()
            require(value >= 0) { "Unexpected CBOR EOF" }
            return value
        }
    }
}
