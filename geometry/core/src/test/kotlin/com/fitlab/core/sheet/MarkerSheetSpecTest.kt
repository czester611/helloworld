package com.fitlab.core.sheet

import com.fitlab.core.geom.Vec2
import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MarkerSheetSpecTest {
    @Test
    fun cornersOfMarker1() {
        assertEquals(
            listOf(Vec2(165.0, 15.0), Vec2(195.0, 15.0), Vec2(195.0, 45.0), Vec2(165.0, 45.0)),
            MarkerSheetSpec().cornersMm(1),
        )
    }

    @Test
    fun scaleApplied() {
        val c = MarkerSheetSpec(scaleX = 1.01, scaleY = 0.99).cornersMm(1)
        assertClose(165.0 * 1.01, c[0].x)
        assertClose(15.0 * 0.99, c[0].y)
        assertClose(170.0 * 1.0, MarkerSheetSpec().workAreaMm().width)
        assertClose(187.0, MarkerSheetSpec().workAreaMm().height)
    }
}
