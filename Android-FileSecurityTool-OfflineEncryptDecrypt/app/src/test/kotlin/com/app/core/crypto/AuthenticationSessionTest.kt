package com.filesecuritytool.android.core.crypto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticationSessionTest {
    @Test
    fun `grant expires after five minutes and clear revokes immediately`() {
        var now = 10L
        val session = AuthenticationSession { now }
        session.grant()
        assertTrue(session.isValid())
        now += AuthenticationSession.WINDOW_MILLIS
        assertFalse(session.isValid())
        session.grant()
        session.clear()
        assertFalse(session.isValid())
    }
}
