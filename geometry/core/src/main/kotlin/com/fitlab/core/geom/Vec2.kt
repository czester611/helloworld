package com.fitlab.core.geom

import kotlinx.serialization.Serializable
import kotlin.math.hypot

/** Punkt lub wektor 2D w milimetrach. */
@Serializable
data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2): Vec2 = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2): Vec2 = Vec2(x - o.x, y - o.y)
    operator fun times(k: Double): Vec2 = Vec2(x * k, y * k)
    operator fun unaryMinus(): Vec2 = Vec2(-x, -y)

    /** Długość wektora w mm. */
    fun length(): Double = hypot(x, y)

    /** Odległość do innego punktu w mm. */
    fun distanceTo(o: Vec2): Double = hypot(x - o.x, y - o.y)
}
