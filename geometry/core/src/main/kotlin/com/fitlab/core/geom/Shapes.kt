package com.fitlab.core.geom

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/** Fabryki podstawowych kształtów. */
object Shapes {
    /** Maksymalna długość cięciwy przybliżającej okrąg, mm. */
    const val CIRCLE_SEGMENT_MM = 0.4
    private const val MIN_SEGMENTS = 32
    private const val MAX_SEGMENTS = 256

    /** Liczba wierzchołków wielokąta przybliżającego okrąg o średnicy [d] mm. */
    fun circleSegments(d: Double): Int =
        ceil(PI * d / CIRCLE_SEGMENT_MM).toInt().coerceIn(MIN_SEGMENTS, MAX_SEGMENTS)

    /** Okrąg (CCW) o środku [center] i średnicy [d] mm jako wielokąt. */
    fun circle(center: Vec2, d: Double): Ring {
        val n = circleSegments(d)
        val r = d / 2.0
        return Ring(
            List(n) { k ->
                val th = 2.0 * PI * k / n
                Vec2(center.x + r * cos(th), center.y + r * sin(th))
            },
        )
    }

    /** Prostokąt (CCW) o narożnikach (x0,y0)-(x1,y1) w mm. */
    fun rect(x0: Double, y0: Double, x1: Double, y1: Double): Ring =
        Ring(listOf(Vec2(x0, y0), Vec2(x1, y0), Vec2(x1, y1), Vec2(x0, y1)))

    /** Kwadrat o boku [side] z zaokrąglonymi narożnikami o promieniu [r] (mm). */
    fun roundedSquare(center: Vec2, side: Double, r: Double): Ring {
        val h = (side - 2.0 * r) / 2.0
        val core = rect(center.x - h, center.y - h, center.x + h, center.y + h)
        return GeometryOps.offsetRing(core, r, GeometryOps.JoinStyle.ROUND)
    }

    /**
     * Kształt litery D: okrąg o średnicy [d] ze spłaszczeniem po stronie +Y.
     * [flatToEdge] to wymiar od przeciwległej krawędzi okręgu do płaskiego odcinka; płaszczyzna cięcia leży na
     * y = flatToEdge − d/2. Środek okręgu w (0,0).
     */
    fun dShape(d: Double, flatToEdge: Double): Ring {
        val c = Shape2(circle(Vec2(0.0, 0.0), d))
        val box = Shape2(rect(-d, -d, d, flatToEdge - d / 2.0))
        return GeometryOps.intersection(c, box).outer
    }
}
