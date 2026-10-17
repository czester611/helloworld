package com.fitlab.core.geom

import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeometryOpsMultiTest {
    private val square = Shape2(Shapes.rect(0.0, 0.0, 10.0, 10.0))

    @Test
    fun differenceAllCanSplitShape() {
        val parts = GeometryOps.differenceAll(square, listOf(Shape2(Shapes.rect(4.0, -1.0, 6.0, 11.0))))
        assertEquals(2, parts.size)
        assertClose(80.0, parts.sumOf { it.area() }, 1e-9)
        assertEquals(1, GeometryOps.differenceAll(square, emptyList()).size)
    }

    @Test
    fun intersectionAllMayBeEmptyOrMany() {
        assertTrue(GeometryOps.intersectionAll(square, Shape2(Shapes.rect(20.0, 20.0, 30.0, 30.0))).isEmpty())
        val u = Shape2(Shapes.rect(5.0, 5.0, 15.0, 15.0))
        assertClose(25.0, GeometryOps.intersectionAll(square, u).single().area(), 1e-9)
    }

    @Test
    fun containsIntersectsDistance() {
        val inner = Shape2(Shapes.rect(2.0, 2.0, 3.0, 3.0))
        val far = Shape2(Shapes.rect(13.0, 0.0, 14.0, 1.0))
        assertTrue(GeometryOps.contains(square, inner))
        assertFalse(GeometryOps.contains(inner, square))
        assertTrue(GeometryOps.intersects(square, inner))
        assertFalse(GeometryOps.intersects(square, far))
        assertClose(3.0, GeometryOps.distance(square, far), 1e-9)
        assertClose(0.0, GeometryOps.distance(square, inner), 1e-9)
    }

    @Test
    fun rayEntryReturnsFirstHitFromStart() {
        val p = GeometryOps.rayEntry(square, Vec2(20.0, 5.0), Vec2(-5.0, 5.0))
        assertNotNull(p)
        assertClose(10.0, p!!.x, 1e-9)
        assertClose(5.0, p.y, 1e-9)
        assertNull(GeometryOps.rayEntry(square, Vec2(20.0, 50.0), Vec2(-5.0, 50.0)))
    }
}
