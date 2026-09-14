package com.karthik.packride.motorun

import kotlin.random.Random

/** Chrome-dino style jump runner — closer to iOS MotoRun solo. */
class MotoRunEngine {
    var riderY = 0f
        private set
    var velocityY = 0f
        private set
    var speed = BASE_SPEED
        private set
    var score = 0
        private set
    var alive = true
        private set
    var obstacles = listOf<Obstacle>()
        private set
    var groundScroll = 0f
        private set

    data class Obstacle(
        val x: Float,
        // Aug 31, 2026 — sized relative to RIDER_H/RIDER_W (matches iOS's
        // fixed 26x22pt obstacle vs a 50x44pt player, roughly half the
        // rider's height) rather than a bare screen-height fraction — the
        // old 0.10-0.18 range routinely stood as tall as, or taller than,
        // the 0.09-tall rider, reading as a wall instead of a bump. Flagged
        // by Karthik Aug 31, 2026.
        val width: Float = 0.04f,
        val height: Float = 0.04f
    )

    fun reset() {
        riderY = 0f
        velocityY = 0f
        speed = BASE_SPEED
        score = 0
        alive = true
        obstacles = emptyList()
        groundScroll = 0f
    }

    fun jump() {
        if (!alive) return
        if (riderY <= 0.001f) velocityY = JUMP_VELOCITY
    }

    fun tick(dt: Float) {
        if (!alive) return
        velocityY += GRAVITY * dt
        riderY = (riderY + velocityY * dt).coerceAtLeast(0f)
        if (riderY == 0f) velocityY = 0f

        groundScroll = (groundScroll + speed * dt * 0.5f) % 1f
        score += (speed * dt * 18f).toInt().coerceAtLeast(0)
        speed = (BASE_SPEED + score * SPEED_RAMP).coerceAtMost(MAX_SPEED)

        obstacles = obstacles
            .map { it.copy(x = it.x - speed * dt * 0.42f) }
            .filter { it.x > -0.25f }

        val minGap = (0.45f - score * 0.00008f).coerceAtLeast(0.28f)
        val lastX = obstacles.maxOfOrNull { it.x } ?: -1f
        if (lastX < 1f - minGap && Random.nextFloat() < 0.035f * (speed / BASE_SPEED)) {
            obstacles = obstacles + Obstacle(
                x = 1.25f + Random.nextFloat() * 0.15f,
                width = 0.035f + Random.nextFloat() * 0.02f,
                height = 0.03f + Random.nextFloat() * 0.025f
            )
        }

        val rx = 0.18f
        val ry = 1f - GROUND - riderY - RIDER_H / 2f
        for (o in obstacles) {
            val oy = 1f - GROUND - o.height / 2f
            if (overlap(rx, ry, RIDER_W, RIDER_H, o.x, oy, o.width, o.height)) {
                alive = false
                break
            }
        }
    }

    private fun overlap(
        ax: Float, ay: Float, aw: Float, ah: Float,
        bx: Float, by: Float, bw: Float, bh: Float
    ): Boolean {
        return ax - aw / 2 < bx + bw / 2 &&
            ax + aw / 2 > bx - bw / 2 &&
            ay - ah / 2 < by + bh / 2 &&
            ay + ah / 2 > by - bh / 2
    }

    companion object {
        const val GROUND = 0.22f
        const val RIDER_W = 0.07f
        const val RIDER_H = 0.09f
        private const val BASE_SPEED = 1.15f
        private const val MAX_SPEED = 3.8f
        private const val SPEED_RAMP = 0.00035f
        private const val GRAVITY = -2.8f
        private const val JUMP_VELOCITY = 1.15f
    }
}
