package com.fitlab.core.geom

import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Zamknięty wielokąt: ostatni punkt NIE powtarza pierwszego. Minimum 3 punkty.
 */
@Serializable
data class Ring(val points: List<Vec2>) {
    init {
        require(points.size >= 3) { "Pierścień wymaga co najmniej 3 punktów" }
    }

    /** Pole ze znakiem (wzór shoelace) w mm²; dodatnie dla CCW. */
    fun signedArea(): Double {
        var s = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            s += a.x * b.y - b.x * a.y
        }
        return s / 2.0
    }

    /** Czy pierścień jest zorientowany przeciwnie do ruchu wskazówek zegara. */
    fun isCcw(): Boolean = signedArea() > 0

    /** Pierścień o odwróconej kolejności punktów. */
    fun reversed(): Ring = Ring(points.reversed())

    /** Prostokąt otaczający. */
    fun bounds(): Bounds2 {
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        return Bounds2(minX, minY, maxX, maxY)
    }

    /** Obwód w mm. */
    fun perimeter(): Double {
        var s = 0.0
        for (i in points.indices) s += points[i].distanceTo(points[(i + 1) % points.size])
        return s
    }

    /** Centroid pola wielokąta (mm). Dla zdegenerowanego pola zwraca średnią punktów. */
    fun centroid(): Vec2 {
        var a2 = 0.0
        var cx = 0.0
        var cy = 0.0
        for (i in points.indices) {
            val p = points[i]
            val q = points[(i + 1) % points.size]
            val cr = p.x * q.y - q.x * p.y
            a2 += cr
            cx += (p.x + q.x) * cr
            cy += (p.y + q.y) * cr
        }
        if (abs(a2) < 1e-12) {
            return Vec2(points.sumOf { it.x } / points.size, points.sumOf { it.y } / points.size)
        }
        return Vec2(cx / (3.0 * a2), cy / (3.0 * a2))
    }

    /** Pierścień przesunięty o wektor [d]. */
    fun translated(d: Vec2): Ring = Ring(points.map { it + d })

    /**
     * Usuwa kolejne punkty bliższe niż [minDistMm] (także ostatni vs pierwszy).
     * Jeśli zostałoby mniej niż 3 punkty, zwraca pierścień bez zmian.
     */
    fun cleaned(minDistMm: Double = MIN_POINT_DIST_MM): Ring {
        val out = ArrayList<Vec2>(points.size)
        for (p in points) {
            if (out.isEmpty() || out.last().distanceTo(p) >= minDistMm) out.add(p)
        }
        while (out.size > 1 && out.last().distanceTo(out.first()) < minDistMm) out.removeAt(out.size - 1)
        return if (out.size >= 3) Ring(out) else this
    }

    companion object {
        /** Minimalna odległość między kolejnymi punktami, mm. */
        const val MIN_POINT_DIST_MM = 0.001
    }
}
