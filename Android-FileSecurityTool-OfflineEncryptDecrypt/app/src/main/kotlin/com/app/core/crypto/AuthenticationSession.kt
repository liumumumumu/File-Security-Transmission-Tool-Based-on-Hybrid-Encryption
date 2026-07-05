package com.filesecuritytool.android.core.crypto

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

class AuthenticationSession(
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime
) {
    private val authenticatedUntil = AtomicLong(0)

    fun grant() {
        authenticatedUntil.set(elapsedRealtime() + WINDOW_MILLIS)
    }

    fun clear() {
        authenticatedUntil.set(0)
    }

    fun isValid(): Boolean = elapsedRealtime() < authenticatedUntil.get()

    fun requireValid() {
        check(isValid()) { "Authentication required" }
    }

    companion object {
        const val WINDOW_MILLIS = 5 * 60 * 1000L
    }
}
