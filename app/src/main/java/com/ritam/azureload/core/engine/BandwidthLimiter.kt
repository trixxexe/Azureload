package com.ritam.azureload.core.engine

import kotlinx.coroutines.delay

/**
 * Thread-safe, non-blocking coroutine rate limiter using the token bucket algorithm.
 * Can be shared across multiple concurrent chunk downloaders to throttle
 * total aggregated transfer rate to the target bytes per second without jitter,
 * thread starvation, or window reset bursts.
 */
class BandwidthLimiter(
    private val limitBytesPerSecProvider: () -> Long
) {
    private val lock = Any()
    private var lastRefillTime = System.currentTimeMillis()
    private var availableTokens = 0L

    suspend fun throttle(bytesCount: Int) {
        if (bytesCount <= 0) return
        val limit = limitBytesPerSecProvider()
        if (limit <= 0) return // 0 or negative means unlimited

        val delayMs: Long
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val elapsed = (now - lastRefillTime).coerceAtLeast(0L)
            lastRefillTime = now
            val maxCapacity = limit.coerceAtLeast(64L * 1024L)

            // Refill tokens based on elapsed time
            availableTokens = (availableTokens + (elapsed * limit) / 1000L).coerceAtMost(maxCapacity)

            if (availableTokens >= bytesCount) {
                availableTokens -= bytesCount
                delayMs = 0L
            } else {
                val deficit = bytesCount - availableTokens
                availableTokens = 0L
                delayMs = (deficit * 1000L) / limit
                // Advance virtual refill point so next arrival accounts for this delay
                lastRefillTime += delayMs
            }
        }

        if (delayMs > 5L) {
            delay(delayMs)
        }
    }
}
