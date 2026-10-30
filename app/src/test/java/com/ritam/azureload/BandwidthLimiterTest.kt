package com.ritam.azureload

import com.ritam.azureload.core.engine.BandwidthLimiter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong
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
        val speedLimitBytesPerSec = 200L * 1024L // 200 KB/s
        val limiter = BandwidthLimiter { speedLimitBytesPerSec }

        // Throttle 200 KB: at 200 KB/s, token bucket must enforce ~1000ms delay
        val elapsed = measureTimeMillis {
            limiter.throttle(200 * 1024)
        }

        assertTrue(
            "Throttling 200KB at 200KB/s should enforce at least 800ms delay, took: $elapsed ms",
            elapsed >= 800
        )
    }

    @Test
    fun testDynamicSpeedLimitAdjustment() = runBlocking {
        val dynamicLimit = AtomicLong(0L) // starts unlimited
        val limiter = BandwidthLimiter { dynamicLimit.get() }

        // Step 1: Unlimited transfer runs instantly
        val elapsedUnlimited = measureTimeMillis {
            limiter.throttle(100 * 1024)
        }
        assertTrue("Unlimited transfer should be < 100ms, took $elapsedUnlimited ms", elapsedUnlimited < 100)

        // Step 2: Switch to 200 KB/s throttle mid-stream
        dynamicLimit.set(200L * 1024L)
        val elapsedThrottled = measureTimeMillis {
            limiter.throttle(100 * 1024)
        }
        assertTrue("Throttled transfer should delay ~500ms, took $elapsedThrottled ms", elapsedThrottled >= 400)

        // Step 3: Switch back to unlimited
        dynamicLimit.set(0L)
        val elapsedReset = measureTimeMillis {
            limiter.throttle(100 * 1024)
        }
        assertTrue("Reset to unlimited should be fast, took $elapsedReset ms", elapsedReset < 100)
    }

    @Test
    fun testConcurrentParallelThrottling() = runBlocking {
        val speedLimitBytesPerSec = 250L * 1024L // 250 KB/s
        val limiter = BandwidthLimiter { speedLimitBytesPerSec }

        // 4 concurrent tasks, each transferring 50 KB (total 200 KB)
        val elapsed = measureTimeMillis {
            coroutineScope {
                val jobs = (1..4).map {
                    async {
                        limiter.throttle(50 * 1024)
                    }
                }
                jobs.awaitAll()
            }
        }

        // 200 KB at 250 KB/s should take ~800ms
        assertTrue("Concurrent throttling should take at least 600ms, took $elapsed ms", elapsed >= 600)
    }
}
