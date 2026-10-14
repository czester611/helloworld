package com.fitlab.core.templates

import com.fitlab.core.design.PrintSettings
import com.fitlab.core.fit.CompensationTable
import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KnobTemplateTest {
    @Test
    fun dHoleGeometry() {
        val r = KnobTemplate.build(TemplateRegistry.defaultInput(TemplateId.KNOB), CompensationTable.ZERO, PrintSettings(0.2, 0.0))
        val hole = r.solid.prisms[0].shape.holes[0]
        assertClose(1.5, hole.points.maxOf { it.y }, 1e-9)
        assertClose(-3.0, hole.points.minOf { it.y }, 0.01)
        assertEquals(2, r.solid.prisms.size)
        assertEquals(2, r.features.size)
        TemplateTestSupport.assertClosed(r)
    }

    @Test
    fun smoothRoundKnob() {
        val i = TemplateTestSupport.input(TemplateId.KNOB, "gripCount" to 0.0, "shaftFlat" to 0.0)
        val r = KnobTemplate.build(i, CompensationTable.ZERO, PrintSettings())
        assertEquals(1, r.features.size)
        TemplateTestSupport.assertClosed(r)
    }

    @Test
    fun validationErrors() {
        val i = TemplateTestSupport.input(TemplateId.KNOB, "shaftFlat" to 7.0)
        org.junit.jupiter.api.Assertions.assertTrue(KnobTemplate.validate(i, CompensationTable.ZERO).any { it.key == "shaftFlat" })
    }
}
