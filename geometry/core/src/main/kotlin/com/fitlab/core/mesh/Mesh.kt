package com.fitlab.core.mesh

import com.fitlab.core.geom.Vec3

/** Trójkąt jako trzy indeksy wierzchołków (CCW patrząc z zewnątrz bryły). */
data class Tri(val a: Int, val b: Int, val c: Int)

/** Siatka trójkątów w mm. */
data class Mesh(val vertices: List<Vec3>, val triangles: List<Tri>) {
    /** Minimalny i maksymalny narożnik prostopadłościanu otaczającego. */
    fun bounds(): Pair<Vec3, Vec3> {
        require(vertices.isNotEmpty()) { "Pusta siatka" }
        return Pair(
            Vec3(vertices.minOf { it.x }, vertices.minOf { it.y }, vertices.minOf { it.z }),
            Vec3(vertices.maxOf { it.x }, vertices.maxOf { it.y }, vertices.maxOf { it.z }),
        )
    }

    /** Siatka przesunięta tak, by minZ = 0, a środek XY prostopadłościanu otaczającego leżał w (0,0). */
    fun normalizedForPrint(): Mesh {
        val (lo, hi) = bounds()
        return translated(Vec3(-(lo.x + hi.x) / 2.0, -(lo.y + hi.y) / 2.0, -lo.z))
    }

    /** Siatka przesunięta o [d]. */
    fun translated(d: Vec3): Mesh = Mesh(vertices.map { it + d }, triangles)

    /** Łączenie siatek (przesunięcie indeksów drugiej). */
    operator fun plus(other: Mesh): Mesh {
        val off = vertices.size
        return Mesh(
            vertices + other.vertices,
            triangles + other.triangles.map { Tri(it.a + off, it.b + off, it.c + off) },
        )
    }

    companion object {
        val EMPTY = Mesh(emptyList(), emptyList())
    }
}

/** Raport walidacji siatki. */
data class MeshReport(
    val triangleCount: Int,
    val isClosed: Boolean,
    val isConsistentlyOriented: Boolean,
    val volumeMm3: Double,
    val degenerateTriangles: Int,
)
