package com.fitlab.core.mesh

/** Walidacja szczelności i orientacji siatki. */
object MeshValidator {
    private const val DEGENERATE_AREA_MM2 = 1e-9

    /** Sprawdza siatkę: szczelność, spójną orientację, objętość i zdegenerowane trójkąty. */
    fun check(mesh: Mesh): MeshReport {
        val edges = HashMap<Long, Int>(mesh.triangles.size * 2)
        fun ek(a: Int, b: Int): Long = (a.toLong() shl 32) or (b.toLong() and 0xffffffffL)
        var volume6 = 0.0
        var degenerate = 0
        for (t in mesh.triangles) {
            for ((a, b) in listOf(t.a to t.b, t.b to t.c, t.c to t.a)) {
                edges.merge(ek(a, b), 1, Int::plus)
            }
            val va = mesh.vertices[t.a]
            val vb = mesh.vertices[t.b]
            val vc = mesh.vertices[t.c]
            val cr = (vb - va).cross(vc - va)
            if (cr.length() / 2.0 < DEGENERATE_AREA_MM2) degenerate++
            volume6 += va.dot(vb.cross(vc))
        }
        val consistent = edges.values.all { it == 1 }
        var closed = true
        for ((k, _) in edges) {
            val a = (k shr 32).toInt()
            val b = (k and 0xffffffffL).toInt()
            if (edges[ek(b, a)] != 1 || edges[k] != 1) {
                closed = false
                break
            }
        }
        return MeshReport(mesh.triangles.size, closed, consistent, volume6 / 6.0, degenerate)
    }
}
