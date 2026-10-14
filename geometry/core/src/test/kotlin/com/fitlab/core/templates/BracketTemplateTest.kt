package com.fitlab.core.templates

import com.fitlab.core.design.PrintSettings
import com.fitlab.core.fit.CompensationTable
import com.fitlab.core.mesh.MeshBuilder
import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BracketTemplateTest {
    @Test
    fun defaultBoundingBox() {
        val r = BracketTemplate.build(TemplateRegistry.defaultInput(TemplateId.BRACKET), CompensationTable.ZERO, PrintSettings(0.2, 0.0))
        assertEquals(2, r.solid.prisms.size)
        TemplateTestSupport.assertClosed(r)
        val (lo, hi) = MeshBuilder.build(r.solid).bounds()
        assertClose(30.0, hi.x - lo.x, 1e-6)
        assertClose(20.0, hi.y - lo.y, 1e-6)
        assertClose(30.0, hi.z - lo.z, 1e-6)
    }

    @Test
    fun twoHolesPerLeg() {
        val i = TemplateTestSupport.input(TemplateId.BRACKET, "holesPerLeg" to 2.0, "width" to 30.0)
        TemplateTestSupport.assertClosed(BracketTemplate.build(i, CompensationTable.ZERO, PrintSettings()))
    }
}
