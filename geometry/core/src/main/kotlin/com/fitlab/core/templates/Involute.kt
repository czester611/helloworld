package com.fitlab.core.templates

import com.fitlab.core.geom.Ring
import com.fitlab.core.geom.Vec2
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Profil koła zębatego o zębach ewolwentowych. */
object Involute {
    const val PRESSURE_ANGLE = 20.0 * PI / 180.0
    private const val FLANK_POINTS = 12
    private const val TIP_POINTS = 4
    private const val ROOT_POINTS = 6

    private fun inv(x: Double) = tan(x) - x
    private fun theta(s: Double) = s - atan(s)

    /** Promień koła podziałowego, mm. */
    fun pitchRadius(z: Int, m: Double) = m * z / 2.0

    /** Promień koła stóp, mm. */
    fun rootRadius(z: Int, m: Double) = pitchRadius(z, m) - 1.25 * m

    /** Promień koła zasadniczego, mm. */
    fun baseRadius(z: Int, m: Double, alpha: Double = PRESSURE_ANGLE) = pitchRadius(z, m) * cos(alpha)

    /** Zamknięty wielokąt (CCW) koła zębatego o [z] zębach i module [m] (mm), wycentrowany w (0,0). */
    fun gearRing(z: Int, m: Double, alpha: Double = PRESSURE_ANGLE): Ring {
        val rp = m * z / 2.0
        val rb = rp * cos(alpha)
        val ra = rp + m
        val rf = rp - 1.25 * m
        val phi0 = PI / (2.0 * z) + inv(alpha)
        val sMax = sqrt((ra / rb) * (ra / rb) - 1.0)
        val sMin = if (rf > rb) sqrt((rf / rb) * (rf / rb) - 1.0) else 0.0
        fun rho(s: Double) = rb * sqrt(1.0 + s * s)
        fun pt(r: Double, a: Double) = Vec2(r * cos(a), r * sin(a))

        val pts = ArrayList<Vec2>()
        val rootHalf = phi0 - theta(sMin) // półkąt zęba na kole stóp
        val tipHalf = phi0 - theta(sMax)
        for (i in 0 until z) {
            val g = 2.0 * PI * i / z
            if (rf < rb) pts.add(pt(rf, g - phi0))
            for (k in 0 until FLANK_POINTS) {
                val s = sMin + (sMax - sMin) * k / (FLANK_POINTS - 1)
                pts.add(pt(rho(s), g - (phi0 - theta(s))))
            }
            for (k in 1..TIP_POINTS) {
                val f = k.toDouble() / (TIP_POINTS + 1)
                pts.add(pt(ra, g - tipHalf + 2.0 * tipHalf * f))
            }
            for (k in 0 until FLANK_POINTS) {
                val s = sMax - (sMax - sMin) * k / (FLANK_POINTS - 1)
                pts.add(pt(rho(s), g + (phi0 - theta(s))))
            }
            if (rf < rb) pts.add(pt(rf, g + phi0))
            val a0 = g + rootHalf
            val a1 = 2.0 * PI * (i + 1) / z - rootHalf
            for (k in 1..ROOT_POINTS) {
                val f = k.toDouble() / (ROOT_POINTS + 1)
                pts.add(pt(rf, a0 + (a1 - a0) * f))
            }
        }
        return Ring(pts).cleaned()
    }
}
