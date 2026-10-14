package com.fitlab.core.geom

import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RingTest {
    private val sq = Ring(listOf(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(10.0, 10.0), Vec2(0.0, 10.0)))

    @Test
    fun signedAreaAndReverse() {
        assertClose(100.0, sq.signedArea())
        assertClose(-100.0, sq.reversed().signedArea())
        assertTrue(sq.isCcw())
    }

    @Test
    fun centroidPerimeterBounds() {
        assertClose(5.0, sq.centroid().x)
        assertClose(5.0, sq.centroid().y)
        assertClose(40.0, sq.perimeter())
        assertClose(10.0, sq.bounds().width)
    }

    @Test
    fun cleanedMergesDuplicates() {
        val r = Ring(sq.points + listOf(Vec2(0.0, 0.0001)) + sq.points.take(1))
        assertEquals(4, r.cleaned().points.size)
    }

    @Test
    fun shapeNormalizedAndArea() {
        val s = Shape2(sq.reversed(), listOf(Shapes.rect(2.0, 2.0, 4.0, 4.0)))
        val n = s.normalized()
        assertTrue(n.outer.isCcw())
        assertTrue(!n.holes[0].isCcw())
        assertClose(96.0, s.area())
        assertClose(1.0, s.translated(Vec2(1.0, 0.0)).bounds().minX)
    }
}
