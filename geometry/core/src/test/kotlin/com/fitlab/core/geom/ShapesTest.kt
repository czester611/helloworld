package com.fitlab.core.geom

import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShapesTest {
    @Test
    fun circleHas79PointsOnRadius() {
        val c = Shapes.circle(Vec2(0.0, 0.0), 10.0)
        assertEquals(79, c.points.size)
        for (p in c.points) assertClose(5.0, p.length(), 1e-9)
    }

    @Test
    fun circleSegmentsAreClamped() {
        assertEquals(32, Shapes.circleSegments(1.0))
        assertEquals(256, Shapes.circleSegments(500.0))
    }

    @Test
    fun roundedSquareBounds() {
        val r = Shapes.roundedSquare(Vec2(0.0, 0.0), 20.0, 1.0)
        assertClose(20.0, r.bounds().width, 1e-6)
    }

    @Test
    fun dShapeFlat() {
        val d = Shapes.dShape(6.0, 4.5)
        assertClose(1.5, d.points.maxOf { it.y }, 1e-9)
        assertClose(-3.0, d.points.minOf { it.y }, 0.01)
    }
}
