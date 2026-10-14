package com.fitlab.core.photo

import com.fitlab.core.geom.Vec2
import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThicknessCorrectionTest {
    private val pose = CameraPose(300.0, Vec2(0.0, 0.0))

    @Test
    fun scalesTowardNadir() {
        val r = ThicknessCorrection.apply(listOf(Vec2(100.0, 0.0)), pose, 10.0)
        assertClose(96.6667, r[0].x, 1e-4)
        assertClose(0.0, r[0].y)
    }

    @Test
    fun noChange() {
        val p = listOf(Vec2(100.0, 0.0))
        assertEquals(p, ThicknessCorrection.apply(p, pose, 0.0))
        assertEquals(p, ThicknessCorrection.apply(p, null, 10.0))
    }
}
