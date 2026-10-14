package com.fitlab.core.geom

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.operation.buffer.BufferOp
import org.locationtech.jts.operation.buffer.BufferParameters
import org.locationtech.jts.simplify.TopologyPreservingSimplifier

/** Most do JTS: jedyne miejsce konwersji Shape2 <-> Polygon i operacji 2D (mm). */
object GeometryOps {
    /** Limit ostrości narożnika dla łączenia MITRE. */
    const val MITRE_LIMIT = 3.0
    private const val ROUND_QUADRANT_SEGMENTS = 8

    enum class JoinStyle { MITRE, ROUND }

    private val factory = GeometryFactory()

    private fun linearRing(r: Ring): LinearRing {
        val coords = ArrayList<Coordinate>(r.points.size + 1)
        for (p in r.points) coords.add(Coordinate(p.x, p.y))
        coords.add(Coordinate(r.points[0].x, r.points[0].y))
        return factory.createLinearRing(coords.toTypedArray())
    }

    private fun ringOf(ls: org.locationtech.jts.geom.LineString): Ring {
        val c = ls.coordinates
        val pts = ArrayList<Vec2>(c.size)
        for (i in 0 until c.size - 1) pts.add(Vec2(c[i].x, c[i].y))
        return Ring(pts)
    }

    /** Konwersja do wielokąta JTS (domyka pierścienie). */
    fun toJts(shape: Shape2): Polygon =
        factory.createPolygon(linearRing(shape.outer), shape.holes.map { linearRing(it) }.toTypedArray())

    /** Konwersja z wielokąta JTS; usuwa powtórzony punkt końcowy i normalizuje orientację. */
    fun fromJts(polygon: Polygon): Shape2 {
        if (polygon.isEmpty) throw GeometryException("Kształt jest pusty")
        val holes = ArrayList<Ring>()
        for (i in 0 until polygon.numInteriorRing) holes.add(ringOf(polygon.getInteriorRingN(i)))
        return Shape2(ringOf(polygon.exteriorRing), holes).normalized()
    }

    /** Konwersja dowolnej geometrii na listę kształtów (wielokąty niepuste). */
    fun fromJtsMulti(g: Geometry): List<Shape2> {
        if (g.isEmpty) return emptyList()
        return when (g) {
            is Polygon -> listOf(fromJts(g))
            is MultiPolygon -> (0 until g.numGeometries).map { fromJts(g.getGeometryN(it) as Polygon) }
            else -> {
                val out = ArrayList<Shape2>()
                for (i in 0 until g.numGeometries) {
                    val sub = g.getGeometryN(i)
                    if (sub is Polygon) out.add(fromJts(sub))
                }
                out
            }
        }
    }

    private fun single(g: Geometry): Shape2 {
        val list = fromJtsMulti(g)
        if (list.isEmpty()) throw GeometryException("Kształt zniknął w wyniku operacji")
        if (list.size > 1) throw GeometryException("Kształt rozpadł się na kilka części")
        return list[0]
    }

    private fun bufferParams(join: JoinStyle): BufferParameters =
        when (join) {
            JoinStyle.MITRE ->
                BufferParameters(ROUND_QUADRANT_SEGMENTS, BufferParameters.CAP_FLAT, BufferParameters.JOIN_MITRE, MITRE_LIMIT)
            JoinStyle.ROUND ->
                BufferParameters(ROUND_QUADRANT_SEGMENTS, BufferParameters.CAP_FLAT, BufferParameters.JOIN_ROUND, MITRE_LIMIT)
        }

    /**
     * Offset kształtu o [distanceMm]. Dodatni powiększa obrys i ZMNIEJSZA dziury (semantyka JTS).
     * Rzuca [GeometryException], gdy wynik jest pusty lub rozpada się na kilka części.
     */
    fun offset(shape: Shape2, distanceMm: Double, join: JoinStyle = JoinStyle.MITRE): Shape2 {
        if (distanceMm == 0.0) return shape.normalized()
        val g = BufferOp.bufferOp(toJts(shape), distanceMm, bufferParams(join))
        return single(g)
    }

    /** Offset pierścienia traktowanego jako pełny wielokąt; wynik bez dziur. */
    fun offsetRing(ring: Ring, distanceMm: Double, join: JoinStyle = JoinStyle.MITRE): Ring {
        val r = offset(Shape2(ring), distanceMm, join)
        if (r.holes.isNotEmpty()) throw GeometryException("Offset wytworzył nieoczekiwane dziury")
        return r.outer
    }

    /** Różnica: [a] minus suma [cutters]. */
    fun difference(a: Shape2, cutters: List<Shape2>): Shape2 {
        var g: Geometry = toJts(a)
        if (cutters.isNotEmpty()) {
            val cut = cutters.map { toJts(it) as Geometry }.reduce { x, y -> x.union(y) }
            g = g.difference(cut)
        }
        return single(g)
    }

    /** Część wspólna dwóch kształtów. */
    fun intersection(a: Shape2, b: Shape2): Shape2 = single(toJts(a).intersection(toJts(b)))

    /** Suma kształtów; wynik może składać się z kilku rozłącznych wielokątów. */
    fun union(shapes: List<Shape2>): List<Shape2> {
        if (shapes.isEmpty()) return emptyList()
        val g = shapes.map { toJts(it) as Geometry }.reduce { x, y -> x.union(y) }
        return fromJtsMulti(g)
    }

    /** Upraszczanie Douglasa–Peuckera z zachowaniem topologii (tolerancja w mm). */
    fun simplify(shape: Shape2, toleranceMm: Double): Shape2 {
        val g = TopologyPreservingSimplifier.simplify(toJts(shape), toleranceMm)
        return single(g)
    }

    /** Czy kształt jest poprawnym wielokątem (bez samoprzecięć). */
    fun isValid(shape: Shape2): Boolean = toJts(shape).isValid
}
