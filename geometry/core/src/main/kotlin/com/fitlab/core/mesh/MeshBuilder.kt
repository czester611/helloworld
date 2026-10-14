package com.fitlab.core.mesh

import com.fitlab.core.geom.GeometryOps
import com.fitlab.core.geom.Ring
import com.fitlab.core.geom.Shape2
import com.fitlab.core.geom.Vec3
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.util.GeometryFixer
import org.locationtech.jts.triangulate.polygon.ConstrainedDelaunayTriangulator
import kotlin.math.roundToLong

/** Budowa siatek trójkątów z graniastosłupów. */
object MeshBuilder {
    private const val BOTTOM = 0
    private const val TOP = 1

    /** Lista siatek, po jednej szczelnej powłoce na graniastosłup. */
    fun buildShells(solid: Solid): List<Mesh> = solid.prisms.map { prismMesh(it) }

    /** Suma wszystkich powłok. */
    fun build(solid: Solid): Mesh = buildShells(solid).fold(Mesh.EMPTY) { acc, m -> acc + m }

    private fun key(x: Double, y: Double, level: Int) =
        Triple((x * 1e6).roundToLong(), (y * 1e6).roundToLong(), level)

    /** Szczelna siatka jednego graniastosłupa (po transformacji). */
    fun prismMesh(p: Prism): Mesh {
        var s = p.shape.normalized()
        s = Shape2(s.outer.cleaned(), s.holes.map { it.cleaned() })
        // Pułapka 23: triangulator wymaga poprawnego wielokąta.
        var poly: Polygon = GeometryOps.toJts(s)
        if (!poly.isValid) {
            val fixed = GeometryFixer.fix(poly)
            val parts = GeometryOps.fromJtsMulti(fixed)
            val biggest = parts.maxByOrNull { it.area() }
                ?: throw IllegalStateException("Niepoprawny kształt graniastosłupa")
            s = Shape2(biggest.outer.cleaned(), biggest.holes.map { it.cleaned() }).normalized()
            poly = GeometryOps.toJts(s)
        }

        val verts = ArrayList<Vec3>()
        val index = HashMap<Triple<Long, Long, Int>, Int>()
        val rings: List<Ring> = listOf(s.outer) + s.holes
        for (r in rings) {
            for (pt in r.points) {
                for (level in intArrayOf(BOTTOM, TOP)) {
                    val k = key(pt.x, pt.y, level)
                    if (k !in index) {
                        index[k] = verts.size
                        verts.add(Vec3(pt.x, pt.y, if (level == BOTTOM) p.z0 else p.z1))
                    }
                }
            }
        }
        val tris = ArrayList<Tri>()
        for (r in rings) {
            val n = r.points.size
            for (i in 0 until n) {
                val a = r.points[i]
                val b = r.points[(i + 1) % n]
                val bi = index.getValue(key(a.x, a.y, BOTTOM))
                val bj = index.getValue(key(b.x, b.y, BOTTOM))
                val ti = index.getValue(key(a.x, a.y, TOP))
                val tj = index.getValue(key(b.x, b.y, TOP))
                tris.add(Tri(bi, bj, tj))
                tris.add(Tri(bi, tj, ti))
            }
        }
        val cdt: Geometry = ConstrainedDelaunayTriangulator.triangulate(poly)
        for (i in 0 until cdt.numGeometries) {
            val t = cdt.getGeometryN(i)
            val c: Array<Coordinate> = t.coordinates
            fun idx(co: Coordinate, level: Int): Int =
                index[key(co.x, co.y, level)] ?: throw IllegalStateException("Triangulator added vertex")
            val area2 = (c[1].x - c[0].x) * (c[2].y - c[0].y) - (c[2].x - c[0].x) * (c[1].y - c[0].y)
            if (area2 == 0.0) continue
            val ccw = area2 > 0
            val ta = idx(c[0], TOP)
            val tb = idx(c[1], TOP)
            val tc = idx(c[2], TOP)
            val ba = idx(c[0], BOTTOM)
            val bb = idx(c[1], BOTTOM)
            val bc = idx(c[2], BOTTOM)
            if (ccw) {
                tris.add(Tri(ta, tb, tc))
                tris.add(Tri(ba, bc, bb))
            } else {
                tris.add(Tri(ta, tc, tb))
                tris.add(Tri(ba, bb, bc))
            }
        }

        val tr = p.transform
        val out = verts.map { tr.apply(it) }
        val finalTris = if (tr.determinant() < 0) tris.map { Tri(it.a, it.c, it.b) } else tris
        return Mesh(out, finalTris)
    }
}
