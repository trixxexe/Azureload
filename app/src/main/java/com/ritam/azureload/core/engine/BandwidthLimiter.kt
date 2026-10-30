package com.ritam.azureload.core.engine

import kotlinx.coroutines.delay

/**
 * Thread-safe, non-blocking coroutine rate limiter.
 * Can be shared across multiple concurrent chunk downloaders to throttle
 * total aggregated transfer rate to the target bytes per second.
 */
class BandwidthLimiter(
    private val limitBytesPerSecProvider: () -> Long
) {
    private val lock = Any()
    private var windowStartMs = System.currentTimeMillis()
    private var bytesInCurrentWindow = 0L

    suspend fun throttle(bytesCount: Int) {
        if (bytesCount <= 0) return
        val limit = limitBytesPerSecProvider()
        if (limit <= 0) return // 0 or negative means unlimited

        val delayMs: Long
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val windowElapsed = now - windowStartMs
            if (windowElapsed >= 1000L) {
                windowStartMs = now
                bytesInCurrentWindow = 0L
            }

            bytesInCurrentWindow += bytesCount
            val expectedDurationMs = (bytesInCurrentWindow * 1000L) / limit
            val currentElapsed = System.currentTimeMillis() - windowStartMs
            delayMs = if (expectedDurationMs > currentElapsed) {
                (expectedDurationMs - currentElapsed).coerceAtMost(1500L)
            } else {
                0L
            }
        }

        if (delayMs > 5L) {
            delay(delayMs)
        }
    }
}
