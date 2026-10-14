package com.fitlab.core.geom

import kotlin.math.abs
import kotlin.math.sqrt

/** Wynik dopasowania okręgu. */
data class Circle(val center: Vec2, val radius: Double)

/** Dopasowanie okręgu do punktów. */
object CircleFit {
    /**
     * Dopasowanie algebraiczne Kåsa (najmniejsze kwadraty) dla co najmniej 3 punktów.
     * Rzuca [GeometryException] dla zdegenerowanych danych.
     */
    fun kasa(points: List<Vec2>): Circle {
        require(points.size >= 3) { "Dopasowanie okręgu wymaga co najmniej 3 punktów" }
        val mx = points.sumOf { it.x } / points.size
        val my = points.sumOf { it.y } / points.size
        // Równania normalne 3x3: A^T A [D,E,F]^T = A^T b, wiersz A = [x, y, 1], b = -(x^2+y^2)
        val m = Array(3) { DoubleArray(4) }
        for (p in points) {
            val x = p.x - mx
            val y = p.y - my
            val row = doubleArrayOf(x, y, 1.0)
            val b = -(x * x + y * y)
            for (i in 0..2) {
                for (j in 0..2) m[i][j] += row[i] * row[j]
                m[i][3] += row[i] * b
            }
        }
        val sol = gauss(m)
        val cx = -sol[0] / 2.0
        val cy = -sol[1] / 2.0
        val r2 = sol[0] * sol[0] / 4.0 + sol[1] * sol[1] / 4.0 - sol[2]
        if (r2 <= 0.0) throw GeometryException("Nie można dopasować okręgu")
        return Circle(Vec2(mx + cx, my + cy), sqrt(r2))
    }

    private fun gauss(m: Array<DoubleArray>): DoubleArray {
        val n = 3
        for (c in 0 until n) {
            var piv = c
            for (r in c + 1 until n) if (abs(m[r][c]) > abs(m[piv][c])) piv = r
            if (abs(m[piv][c]) < 1e-12) throw GeometryException("Nie można dopasować okręgu")
            val t = m[c]; m[c] = m[piv]; m[piv] = t
            for (r in c + 1 until n) {
                val f = m[r][c] / m[c][c]
                for (k in c..n) m[r][k] -= f * m[c][k]
            }
        }
        val x = DoubleArray(n)
        for (r in n - 1 downTo 0) {
            var s = m[r][n]
            for (k in r + 1 until n) s -= m[r][k] * x[k]
            x[r] = s / m[r][r]
        }
        return x
    }
}
