package com.fitlab.core.templates

import com.fitlab.core.design.PrintSettings
import com.fitlab.core.fit.CompensationKind
import com.fitlab.core.fit.CompensationTable
import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BushingTemplateTest {
    private val comp = CompensationTable(mapOf(CompensationKind.HOLE to -0.25, CompensationKind.PIN to 0.10))
    private val noFoot = PrintSettings(0.2, 0.0)

    @Test
    fun designExample() {
        val r = BushingTemplate.build(TemplateRegistry.defaultInput(TemplateId.BUSHING), comp, noFoot)
        assertClose(8.40, r.features.first { it.id == "inner" }.cadMm, 1e-9)
        assertClose(11.90, r.features.first { it.id == "outer" }.cadMm, 1e-9)
        assertEquals(1, r.solid.prisms.size)
        val s = r.solid.prisms[0].shape
        assertClose(11.90, s.bounds().width, 1e-6)
        assertClose(8.40, s.holes[0].bounds().width, 1e-6)
        TemplateTestSupport.assertClosed(r)
    }

    @Test
    fun flangeGivesTwoPrisms() {
        val i = TemplateTestSupport.input(TemplateId.BUSHING, "flangeDiameter" to 16.0)
        val r = BushingTemplate.build(i, comp, noFoot)
        assertEquals(2, r.solid.prisms.size)
        assertTrue(r.features.any { it.id == "flange" })
        TemplateTestSupport.assertClosed(r)
    }

    @Test
    fun wallValidation() {
        val i = TemplateTestSupport.input(TemplateId.BUSHING, "shaftDiameter" to 10.0, "housingDiameter" to 11.0)
        assertTrue(BushingTemplate.validate(i, comp).any { it.message.contains("Ścianka") })
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) { BushingTemplate.build(i, comp, noFoot) }
    }

    @Test
    fun rangeValidationMessage() {
        val i = TemplateTestSupport.input(TemplateId.BUSHING, "length" to 500.0)
        val e = BushingTemplate.validate(i, comp)
        assertEquals("length", e[0].key)
        assertTrue(e[0].message.contains("wartość spoza zakresu"))
    }
}
