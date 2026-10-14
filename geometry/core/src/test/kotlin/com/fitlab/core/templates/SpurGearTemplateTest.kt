package com.fitlab.core.templates

import com.fitlab.core.design.PrintSettings
import com.fitlab.core.fit.CompensationTable
import com.fitlab.core.geom.GeometryOps
import com.fitlab.core.geom.Shape2
import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI

class SpurGearTemplateTest {
    @Test
    fun z20m1Profile() {
        val ring = Involute.gearRing(20, 1.0)
        val radii = ring.points.map { it.length() }
        assertTrue(radii.all { it <= 11.0 + 1e-9 })
        assertTrue(radii.all { it >= 8.75 - 1e-9 })
        assertClose(11.0, radii.max(), 1e-9)
        assertTrue(GeometryOps.isValid(Shape2(ring)))
        val a = ring.signedArea()
        assertTrue(a > PI * 8.75 * 8.75 && a < PI * 121, "area $a")
        assertTrue(ring.isCcw())
    }

    @Test
    fun z50RootAboveBase() {
        val ring = Involute.gearRing(50, 1.0)
        assertTrue(GeometryOps.isValid(Shape2(ring)))
        assertTrue(ring.isCcw())
    }

    @Test
    fun smallGearIsValid() {
        assertTrue(GeometryOps.isValid(Shape2(Involute.gearRing(8, 1.0))))
    }

    @Test
    fun buildDefaults() {
        val r = SpurGearTemplate.build(TemplateRegistry.defaultInput(TemplateId.SPUR_GEAR), CompensationTable.ZERO, PrintSettings())
        TemplateTestSupport.assertClosed(r)
        assertEquals(2, r.features.size)
        val i = TemplateRegistry.defaultInput(TemplateId.SPUR_GEAR)
        assertClose(1.0, SpurGearTemplate.module(i), 1e-9)
        assertEquals(1.0, SpurGearTemplate.nearestStandardModule(1.0))
    }

    @Test
    fun hubAndBoreValidation() {
        val big = TemplateTestSupport.input(TemplateId.SPUR_GEAR, "boreDiameter" to 20.0)
        assertTrue(SpurGearTemplate.validate(big, CompensationTable.ZERO).any { it.key == "boreDiameter" })
        val hub = TemplateTestSupport.input(TemplateId.SPUR_GEAR, "hubDiameter" to 10.0, "hubHeight" to 4.0)
        val r = SpurGearTemplate.build(hub, CompensationTable.ZERO, PrintSettings())
        assertEquals(3, r.solid.prisms.size)
        TemplateTestSupport.assertClosed(r)
    }
}
