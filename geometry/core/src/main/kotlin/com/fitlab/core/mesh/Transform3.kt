package com.fitlab.core.mesh

import com.fitlab.core.geom.Vec3

/**
 * Przekształcenie afiniczne 3D: p' = M·p + t, gdzie M to macierz 3x3 zapisana wierszami (9 elementów).
 */
data class Transform3(val m: DoubleArray, val t: Vec3) {
    init {
        require(m.size == 9) { "Macierz musi mieć 9 elementów" }
    }

    /** Zastosowanie przekształcenia do punktu. */
    fun apply(p: Vec3): Vec3 = Vec3(
        m[0] * p.x + m[1] * p.y + m[2] * p.z + t.x,
        m[3] * p.x + m[4] * p.y + m[5] * p.z + t.y,
        m[6] * p.x + m[7] * p.y + m[8] * p.z + t.z,
    )

    /** Wyznacznik macierzy 3x3 (ujemny = lustro, odwraca orientację trójkątów). */
    fun determinant(): Double =
        m[0] * (m[4] * m[8] - m[5] * m[7]) -
            m[1] * (m[3] * m[8] - m[5] * m[6]) +
            m[2] * (m[3] * m[7] - m[4] * m[6])

    override fun equals(other: Any?): Boolean =
        other is Transform3 && m.contentEquals(other.m) && t == other.t

    override fun hashCode(): Int = 31 * m.contentHashCode() + t.hashCode()

    companion object {
        val IDENTITY = Transform3(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0), Vec3(0.0, 0.0, 0.0))

        /** Samo przesunięcie o [t]. */
        fun translation(t: Vec3): Transform3 = Transform3(IDENTITY.m.copyOf(), t)

        /** Kolumny macierzy = obrazy osi lokalnych X, Y, Z w świecie. */
        fun fromAxes(xAxis: Vec3, yAxis: Vec3, zAxis: Vec3, origin: Vec3 = Vec3(0.0, 0.0, 0.0)): Transform3 =
            Transform3(
                doubleArrayOf(
                    xAxis.x, yAxis.x, zAxis.x,
                    xAxis.y, yAxis.y, zAxis.y,
                    xAxis.z, yAxis.z, zAxis.z,
                ),
                origin,
            )
    }
}
