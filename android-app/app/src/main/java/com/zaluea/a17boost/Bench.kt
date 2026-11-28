package com.zaluea.a17boost

import android.os.SystemClock
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Tiny CPU benchmark: hammers every core for [ms] milliseconds and counts work done. */
object Bench {
    fun run(ms: Long): Long {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val pool = Executors.newFixedThreadPool(cores)
        val end = SystemClock.uptimeMillis() + ms
        val jobs = (0 until cores).map { seed ->
            pool.submit(Callable {
                var x = 0x9E3779B97F4A7C15uL.toLong() + seed
                var rounds = 0L
                while (SystemClock.uptimeMillis() < end) {
                    for (k in 0 until 20_000) {
                        x = x * 6364136223846793005L + 1442695040888963407L
                        x = x xor (x ushr 29)
                    }
                    rounds++
                }
                if (x == 42L) rounds + 1 else rounds // keep the JIT from deleting the loop
            })
        }
        val total = jobs.sumOf { it.get() }
        pool.shutdown()
        return total * 1000 / ms
    }
}
