package com.fitlab.core.mesh

import com.fitlab.core.geom.GeometryOps
import com.fitlab.core.geom.Shape2
import com.fitlab.core.geom.Vec3
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon

/** Bryła złożona z graniastosłupów (powłoki mogą się nakładać). */
data class Solid(val prisms: List<Prism>) {
    /**
     * Rzut z góry do miniatur: suma kształtów graniastosłupów bez transformacji, a dla przekształconych
     * (np. pionowa płyta) otoczka wypukła rzutu ich wierzchołków (pomijana, gdy rzut jest zdegenerowany).
     */
    fun footprint(): List<Shape2> {
        val shapes = ArrayList<Shape2>()
        val gf = GeometryFactory()
        for (p in prisms) {
            if (p.transform == Transform3.IDENTITY) {
                shapes.add(p.shape.normalized())
            } else {
                val coords = ArrayList<Coordinate>()
                for (ring in listOf(p.shape.outer)) {
                    for (v in ring.points) {
                        for (z in listOf(p.z0, p.z1)) {
                            val w = p.transform.apply(Vec3(v.x, v.y, z))
                            coords.add(Coordinate(w.x, w.y))
                        }
                    }
                }
                val hull = gf.createMultiPointFromCoords(coords.toTypedArray()).convexHull()
                if (hull is Polygon && hull.area > 1e-6) shapes.add(GeometryOps.fromJts(hull))
            }
        }
        if (shapes.isEmpty()) return emptyList()
        return GeometryOps.union(shapes)
    }
}
