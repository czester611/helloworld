package com.fitlab.core.photo

import com.fitlab.core.geom.Vec2
import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Test

class PixelMappingTest {
    @Test
    fun conversions() {
        val m = PixelMapping(10.0)
        val px = m.mmToPx(Vec2(80.0, 120.0))
        assertClose(799.5, px.x)
        assertClose(1199.5, px.y)
        val mm = m.pxToMm(Vec2(800.0, 1200.0))
        assertClose(80.05, mm.x)
        assertClose(120.05, mm.y)
    }
}
