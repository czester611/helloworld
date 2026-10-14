package com.fitlab.core.geom

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/** Punkt lub wektor 3D w milimetrach. */
@Serializable
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3): Vec3 = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3): Vec3 = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double): Vec3 = Vec3(x * k, y * k, z * k)

    /** Iloczyn skalarny. */
    fun dot(o: Vec3): Double = x * o.x + y * o.y + z * o.z

    /** Iloczyn wektorowy. */
    fun cross(o: Vec3): Vec3 = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)

    /** Długość wektora w mm. */
    fun length(): Double = sqrt(dot(this))
}
