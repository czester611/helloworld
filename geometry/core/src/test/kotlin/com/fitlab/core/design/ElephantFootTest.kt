package com.fitlab.core.design

import com.fitlab.core.geom.Shape2
import com.fitlab.core.geom.Shapes
import com.fitlab.core.mesh.Prism
import com.fitlab.core.mesh.Solid
import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ElephantFootTest {
    @Test
    fun splitsFirstLayer() {
        val s = Solid(listOf(Prism(Shape2(Shapes.rect(0.0, 0.0, 10.0, 10.0)), 0.0, 4.0)))
        val r = ElephantFoot.apply(s, PrintSettings(0.2, 0.2))
        assertEquals(2, r.prisms.size)
        assertClose(9.6, r.prisms[0].shape.bounds().width, 1e-6)
        assertClose(10.0, r.prisms[1].shape.bounds().width, 1e-6)
    }

    @Test
    fun skipsThinWalls() {
        val s = Solid(listOf(Prism(Shape2(Shapes.rect(0.0, 0.0, 0.3, 10.0)), 0.0, 4.0)))
        val (r, w) = ElephantFoot.applyWithWarnings(s, PrintSettings(0.2, 0.2))
        assertEquals(s, r)
        assertEquals(listOf(ElephantFoot.SKIPPED_WARNING), w)
    }

    @Test
    fun disabledAndRaisedPrismsUntouched() {
        val s = Solid(listOf(Prism(Shape2(Shapes.rect(0.0, 0.0, 10.0, 10.0)), 1.0, 4.0)))
        assertEquals(s, ElephantFoot.apply(s, PrintSettings(0.2, 0.2)))
        assertEquals(s, ElephantFoot.apply(s, PrintSettings(0.2, 0.0)))
    }
}
