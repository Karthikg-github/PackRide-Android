package com.karthik.packride.crash

import kotlin.math.ceil

/** Pure deadline calculation shared by live and process-restored crash countdowns. */
internal object SafetyCountdown {
    fun secondsRemaining(deadlineMs: Long, nowMs: Long): Int =
        ceil((deadlineMs - nowMs).coerceAtLeast(0L) / 1000.0).toInt()
}
