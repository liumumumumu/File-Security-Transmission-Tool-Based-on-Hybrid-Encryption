package com.filesecuritytool.android.core.files

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class DownloadsOutputStoreTest {
    @Test
    fun `sanitizes untrusted container file names`() {
        assertEquals("secret.txt", DownloadsOutputStore.sanitizeFileName("../../secret.txt"))
        assertEquals("evil_name", DownloadsOutputStore.sanitizeFileName("evil\u0000name"))
        assertEquals("output", DownloadsOutputStore.sanitizeFileName("..."))
    }

    @Test
    fun `FST2 encrypted artifact uses Java reference UUID naming`() {
        val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")

        assertEquals(
            "550e8400-e29b-41d4-a716-446655440000.FST2",
            DownloadsOutputStore.fst2ArtifactName(id)
        )
    }
}
