package com.filesecuritytool.android.crypto

import org.junit.Assert.*
import org.junit.Test

class CborLiteTest {

    @Test
    fun `round-trip simple string values`() {
        val input = mapOf("hello" to "world", "number" to 42)
        val encoded = CborLite.encodeCanonical(input)
        val decoded = CborLite.decodeMap(encoded)

        assertEquals("world", decoded["hello"])
        assertEquals(42L, decoded["number"]) // CBOR integers decode as Long
    }

    @Test
    fun `round-trip all supported types`() {
        val input = mapOf(
            "string" to "hello",
            "int" to 123,
            "long" to 1234567890123L,
            "negative" to -5,
            "bool_true" to true,
            "bool_false" to false,
            "null_value" to null,
            "bytes" to byteArrayOf(0x01, 0x02, 0x03),
            "nested_map" to mapOf("a" to 1, "b" to 2),
            "list" to listOf(1, 2, 3, "four")
        )
        val encoded = CborLite.encodeCanonical(input)
        val decoded = CborLite.decodeMap(encoded)

        assertEquals("hello", decoded["string"])
        assertEquals(123L, decoded["int"])
        assertEquals(1234567890123L, decoded["long"])
        assertEquals(-5L, decoded["negative"])
        assertEquals(true, decoded["bool_true"])
        assertEquals(false, decoded["bool_false"])
        assertEquals(null, decoded["null_value"])
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), decoded["bytes"] as ByteArray)

        @Suppress("UNCHECKED_CAST")
        val nestedMap = decoded["nested_map"] as Map<String, Any?>
        assertEquals(1L, nestedMap["a"])
        assertEquals(2L, nestedMap["b"])

        @Suppress("UNCHECKED_CAST")
        val list = decoded["list"] as List<*>
        assertEquals(4, list.size)
        assertEquals(1L, list[0])
        assertEquals("four", list[3])
    }

    @Test
    fun `canonical encoding produces deterministic output`() {
        // Keys in different insertion order should produce identical output
        val map1 = LinkedHashMap<String, Any?>().apply {
            put("c", 3)
            put("a", 1)
            put("b", 2)
        }
        val map2 = LinkedHashMap<String, Any?>().apply {
            put("a", 1)
            put("b", 2)
            put("c", 3)
        }

        val encoded1 = CborLite.encodeCanonical(map1)
        val encoded2 = CborLite.encodeCanonical(map2)

        assertArrayEquals("Canonical encoding must be deterministic", encoded1, encoded2)
    }

    @Test
    fun `canonical encoding sorts by CBOR-encoded key bytes`() {
        // Key "b" (CBOR: 0x61 0x62) sorts before key "aa" (CBOR: 0x62 0x61 0x61)
        // because 0x61 < 0x62 at the first differing byte
        val map = LinkedHashMap<String, Any?>().apply {
            put("aa", 1)
            put("b", 2)
        }
        val encoded = CborLite.encodeCanonical(map)
        val decoded = CborLite.decodeMap(encoded)

        // Keys should be in canonical order: "b" before "aa"
        val keys = decoded.keys.toList()
        assertEquals("b", keys[0])
        assertEquals("aa", keys[1])
    }

    @Test
    fun `empty map round-trip`() {
        val encoded = CborLite.encodeCanonical(emptyMap())
        val decoded = CborLite.decodeMap(encoded)
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `nested maps round-trip correctly`() {
        val input = mapOf(
            "header" to mapOf(
                "fileName" to "test.txt",
                "fileSize" to 1024L,
                "chunkSizeBytes" to 65536,
                "totalBlocks" to 1
            )
        )
        val encoded = CborLite.encodeCanonical(input)
        val decoded = CborLite.decodeMap(encoded)

        @Suppress("UNCHECKED_CAST")
        val header = decoded["header"] as Map<String, Any?>
        assertEquals("test.txt", header["fileName"])
        assertEquals(1024L, header["fileSize"])
        assertEquals(65536L, header["chunkSizeBytes"])
        assertEquals(1L, header["totalBlocks"])
    }

    @Test
    fun `integers at boundaries`() {
        val input = mapOf(
            "zero" to 0,
            "max_int" to Int.MAX_VALUE,
            "min_int" to Int.MIN_VALUE,
            "byte_max" to 255,
            "65535" to 65535
        )
        val encoded = CborLite.encodeCanonical(input)
        val decoded = CborLite.decodeMap(encoded)

        assertEquals(0L, decoded["zero"])
        assertEquals(Int.MAX_VALUE.toLong(), decoded["max_int"])
        assertEquals(Int.MIN_VALUE.toLong(), decoded["min_int"])
        assertEquals(255L, decoded["byte_max"])
        assertEquals(65535L, decoded["65535"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decode rejects non-map root`() {
        // Encode just a string (major type 3) — decodeMap must reject non-map root
        val bytes = ByteArray(4)
        bytes[0] = 0x63.toByte() // major type 3 (text), length 3
        bytes[1] = 'a'.code.toByte()
        bytes[2] = 'b'.code.toByte()
        bytes[3] = 'c'.code.toByte()
        CborLite.decodeMap(bytes) // Should throw: root is a string, not a map
    }
}
