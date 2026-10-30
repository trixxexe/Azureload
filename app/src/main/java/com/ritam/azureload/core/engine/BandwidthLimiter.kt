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
            if (now > lastRefillTime) {
                val elapsed = now - lastRefillTime
                lastRefillTime = now
                val maxCapacity = limit.coerceAtLeast(64L * 1024L)
                availableTokens = (availableTokens + (elapsed * limit) / 1000L).coerceAtMost(maxCapacity)
            }

            if (availableTokens >= bytesCount) {
                availableTokens -= bytesCount
                delayMs = 0L
            } else {
                val deficit = bytesCount - availableTokens
                availableTokens = 0L
                val timeNeededMs = (deficit * 1000L) / limit
                val scheduledTime = lastRefillTime + timeNeededMs
                lastRefillTime = scheduledTime
                delayMs = (scheduledTime - now).coerceAtLeast(0L)
            }
        }

        if (delayMs > 5L) {
            delay(delayMs)
        }
    }
}
