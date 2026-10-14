package com.fitlab.core.photo

import com.fitlab.core.geom.Vec2
import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class OutlinePostProcessorTest {
    private val outerPx = listOf(Vec2(800.0, 1200.0), Vec2(1199.0, 1200.0), Vec2(1199.0, 1449.0), Vec2(800.0, 1449.0))
    private fun circlePx(cx: Double, cy: Double, r: Double, n: Int = 72) =
        List(n) { Vec2(cx + r * cos(2 * PI * it / n), cy + r * sin(2 * PI * it / n)) }

    @Test
    fun rectangleWithRoundHole() {
        val raw = RawContours(outerPx, listOf(circlePx(999.5, 1324.5, 40.5)))
        val o = OutlinePostProcessor.process(raw, 0.0, null)
        assertClose(40.0, o.outer.bounds().width, 0.02)
        assertClose(25.0, o.outer.bounds().height, 0.02)
        assertClose(0.0, o.outer.centroid().x, 1e-6)
        assertClose(0.0, o.outer.centroid().y, 1e-6)
        assertTrue(o.outer.isCcw())
        assertEquals(1, o.holeCandidates.size)
        assertClose(8.0, o.holeCandidates[0].diameterMm, 0.05)
        assertClose(0.0, o.holeCandidates[0].center.x, 0.02)
        assertClose(0.0, o.holeCandidates[0].center.y, 0.02)
        assertTrue(o.innerContours.isEmpty())
    }

    @Test
    fun squareHoleBecomesInnerContour() {
        val sq = listOf(Vec2(980.0, 1300.0), Vec2(1020.0, 1300.0), Vec2(1020.0, 1340.0), Vec2(980.0, 1340.0))
        val o = OutlinePostProcessor.process(RawContours(outerPx, listOf(sq)), 0.0, null)
        assertEquals(0, o.holeCandidates.size)
        assertEquals(1, o.innerContours.size)
        assertTrue(!o.innerContours[0].isCcw())
    }

    @Test
    fun tinyHoleDiscarded() {
        val tiny = circlePx(900.0, 1300.0, 8.0, 24) // ~2 mm^2
        val o = OutlinePostProcessor.process(RawContours(outerPx, listOf(tiny)), 0.0, null)
        assertTrue(o.holeCandidates.isEmpty() && o.innerContours.isEmpty())
    }

    @Test
    fun thicknessCorrectionAndNadirWarning() {
        // Nadir w środku części: bez ostrzeżenia, obrys zmniejszony o (H-t)/H
        val pose = CameraPose(300.0, Vec2(100.0, 132.5))
        val o = OutlinePostProcessor.process(RawContours(outerPx, emptyList()), 10.0, pose)
        assertClose(40.0 * 290.0 / 300.0, o.outer.bounds().width, 0.1)
        assertTrue(o.warnings.isEmpty())
        val far = CameraPose(300.0, Vec2(10.0, 10.0))
        val w = OutlinePostProcessor.process(RawContours(outerPx, emptyList()), 10.0, far).warnings
        assertEquals(listOf(OutlinePostProcessor.NADIR_WARNING), w)
    }
}
