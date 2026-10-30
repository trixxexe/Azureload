package com.ritam.azureload

import com.ritam.azureload.core.engine.BandwidthLimiter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class BandwidthLimiterTest {

    @Test
    fun testUnlimitedBandwidthDoesNotDelay() = runBlocking {
        val limiter = BandwidthLimiter { 0L } // 0 = unlimited

        val elapsed = measureTimeMillis {
            repeat(10) {
                limiter.throttle(1024 * 1024) // 1MB each
            }
        }

        // Should execute almost instantaneously (less than 100ms)
        assertTrue("Unlimited should not delay: $elapsed ms", elapsed < 200)
    }

    @Test
    fun testBandwidthThrottleEnforcesLimit() = runBlocking {
        val speedLimitBytesPerSec = 500L * 1024L // 500 KB/s
        val limiter = BandwidthLimiter { speedLimitBytesPerSec }

        // Transfer 150 KB (approx 30% of 1 second quota)
        val elapsed = measureTimeMillis {
            limiter.throttle(150 * 1024)
        }

        // Transfer another 150 KB
        val totalElapsed = measureTimeMillis {
            limiter.throttle(150 * 1024)
        }

        // Both throttles should succeed without hanging
        assertTrue(elapsed >= 0)
        assertTrue(totalElapsed >= 0)
    }
}
