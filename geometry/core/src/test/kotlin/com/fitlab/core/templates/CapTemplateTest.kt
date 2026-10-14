package com.fitlab.core.templates

import com.fitlab.core.design.PrintSettings
import com.fitlab.core.fit.CompensationKind
import com.fitlab.core.fit.CompensationTable
import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CapTemplateTest {
    @Test
    fun squarePlugSide() {
        val comp = CompensationTable(mapOf(CompensationKind.OUTLINE to 0.1))
        val i = TemplateTestSupport.input(TemplateId.CAP, "square" to 1.0, "wall" to 0.0)
        val r = CapTemplate.build(i, comp, PrintSettings(0.2, 0.0))
        assertClose(19.9, r.solid.prisms[1].shape.bounds().width, 1e-6)
        TemplateTestSupport.assertClosed(r)
    }

    @Test
    fun roundHollowPlug() {
        val r = CapTemplate.build(TemplateRegistry.defaultInput(TemplateId.CAP), CompensationTable.ZERO, PrintSettings())
        assertTrue(r.solid.prisms.last().shape.holes.isNotEmpty())
        TemplateTestSupport.assertClosed(r)
    }

    @Test
    fun wallTooThick() {
        val i = TemplateTestSupport.input(TemplateId.CAP, "tubeInner" to 10.0, "wall" to 5.0, "capSize" to 12.0)
        assertTrue(CapTemplate.validate(i, CompensationTable.ZERO).any { it.key == "wall" })
    }
}
