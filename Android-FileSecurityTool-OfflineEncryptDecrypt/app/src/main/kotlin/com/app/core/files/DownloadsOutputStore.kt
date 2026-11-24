package com.filesecuritytool.android.core.files

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import java.io.OutputStream
import java.util.UUID

class DownloadsOutputStore(
    private val resolver: ContentResolver
) {
    fun cleanupInterruptedOutputs() {
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.IS_PENDING}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
            arrayOf("1", RELATIVE_PATH),
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            while (cursor.moveToNext()) {
                resolver.delete(
                    ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idColumn)
                    ),
                    null,
                    null
                )
            }
        }
    }

    data class PendingOutput(
        val uri: Uri,
        val displayName: String,
        val stream: OutputStream
    )

    fun create(requestedName: String, mimeType: String): PendingOutput {
        val safeName = sanitizeFileName(requestedName)
        val uniqueName = uniqueName(safeName)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, uniqueName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Unable to create output file")
        return try {
            val stream = resolver.openOutputStream(uri, "w")
                ?: throw IllegalStateException("Unable to open output file")
            PendingOutput(uri, uniqueName, stream)
        } catch (failure: Throwable) {
            resolver.delete(uri, null, null)
            throw failure
        }
    }

    fun complete(output: PendingOutput) {
        output.stream.close()
        resolver.update(
            output.uri,
            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
            null,
            null
        )
    }

    fun discard(output: PendingOutput) {
        runCatching { output.stream.close() }
        resolver.delete(output.uri, null, null)
    }

    private fun uniqueName(requestedName: String): String {
        if (!exists(requestedName)) return requestedName
        val dot = requestedName.lastIndexOf('.').takeIf { it > 0 } ?: requestedName.length
        val stem = requestedName.substring(0, dot)
        val extension = requestedName.substring(dot)
        var index = 1
        while (true) {
            val candidate = "$stem ($index)$extension"
            if (!exists(candidate)) return candidate
            index++
        }
    }

    private fun exists(name: String): Boolean {
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
            arrayOf(name, RELATIVE_PATH),
            null
        )?.use { return it.moveToFirst() }
        return false
    }

    companion object {
        const val RELATIVE_PATH = "Download/FileSecurity/"

        fun fst2ArtifactName(artifactId: UUID = UUID.randomUUID()): String =
            "$artifactId.FST2"

        fun sanitizeFileName(value: String): String {
            val basic = value.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("[\\u0000-\\u001f\\u007f]"), "_")
                .replace("..", "_")
                .trim()
                .trim('.')
            return basic.takeIf { it.isNotEmpty() && it.any(Char::isLetterOrDigit) } ?: "output"
        }
    }
}
