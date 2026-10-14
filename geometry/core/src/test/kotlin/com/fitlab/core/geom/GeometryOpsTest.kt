package com.fitlab.core.geom

import com.fitlab.core.geom.GeometryOps.JoinStyle
import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GeometryOpsTest {
    private val squareWithHole = Shape2(
        Shapes.rect(-10.0, -10.0, 10.0, 10.0),
        listOf(Shapes.rect(-5.0, -5.0, 5.0, 5.0).reversed()),
    )

    @Test
    fun offsetInwardShrinksOuterAndGrowsHole() {
        val r = GeometryOps.offset(squareWithHole, -0.1, JoinStyle.MITRE)
        assertClose(19.8, r.bounds().width, 1e-6)
        assertClose(19.8, r.bounds().height, 1e-6)
        assertEquals(1, r.holes.size)
        assertClose(10.2, r.holes[0].bounds().width, 1e-6)
        assertClose(288.0, r.area(), 1e-6)
    }

    @Test
    fun offsetOutwardKeepsSharpCorners() {
        val r = GeometryOps.offset(Shape2(Shapes.rect(-10.0, -10.0, 10.0, 10.0)), 0.5, JoinStyle.MITRE)
        assertClose(21.0, r.bounds().width, 1e-6)
        assertClose(441.0, r.area(), 1e-6)
    }

    @Test
    fun differenceIntersectionUnion() {
        val a = Shape2(Shapes.rect(0.0, 0.0, 10.0, 10.0))
        val b = Shape2(Shapes.rect(5.0, 5.0, 15.0, 15.0))
        assertClose(75.0, GeometryOps.difference(a, listOf(b)).area())
        assertClose(25.0, GeometryOps.intersection(a, b).area())
        val u = GeometryOps.union(listOf(a, b))
        assertEquals(1, u.size)
        assertClose(175.0, u[0].area())
        assertEquals(2, GeometryOps.union(listOf(a, b.translated(Vec2(20.0, 0.0)))).size)
    }

    @Test
    fun splitThrowsGeometryException() {
        val bar = Shape2(Shapes.rect(0.0, 0.0, 10.0, 10.0))
        val cut = Shape2(Shapes.rect(4.0, -1.0, 6.0, 11.0))
        assertThrows(GeometryException::class.java) { GeometryOps.difference(bar, listOf(cut)) }
    }

    @Test
    fun simplifyAndValid() {
        val c = Shape2(Shapes.circle(Vec2(0.0, 0.0), 20.0))
        val s = GeometryOps.simplify(c, 0.1)
        assertTrue(s.outer.points.size < c.outer.points.size)
        assertTrue(GeometryOps.isValid(s))
        val bow = Shape2(Ring(listOf(Vec2(0.0, 0.0), Vec2(10.0, 10.0), Vec2(10.0, 0.0), Vec2(0.0, 10.0))))
        assertTrue(!GeometryOps.isValid(bow))
    }
}
