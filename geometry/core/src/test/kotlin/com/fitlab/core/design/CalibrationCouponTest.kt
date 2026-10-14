package com.fitlab.core.design

import com.fitlab.core.fit.CompensationTable
import com.fitlab.core.mesh.MeshBuilder
import com.fitlab.core.mesh.MeshValidator
import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalibrationCouponTest {
    @Test
    fun zeroCompensationBoundsAndFeatures() {
        val r = CalibrationCoupon.build(CompensationTable.ZERO, PrintSettings())
        val m = MeshBuilder.build(r.solid).normalizedForPrint()
        val (lo, hi) = m.bounds()
        assertClose(44.0, hi.x - lo.x, 1e-6)
        assertClose(20.0, hi.y - lo.y, 1e-6)
        assertClose(11.0, hi.z - lo.z, 1e-6)
        assertEquals(5, r.features.size)
        val rep = MeshValidator.check(m)
        assertTrue(rep.isClosed && rep.isConsistentlyOriented && rep.volumeMm3 > 0)
    }

    @Test
    fun compensationChangesCad() {
        val c = CompensationTable(
            mapOf(
                com.fitlab.core.fit.CompensationKind.HOLE to -0.3,
                com.fitlab.core.fit.CompensationKind.PIN to 0.1,
            ),
        )
        val r = CalibrationCoupon.build(c, PrintSettings(0.2, 0.0))
        assertClose(10.3, r.features.first { it.id == "hole" }.cadMm)
        assertClose(7.9, r.features.first { it.id == "pin" }.cadMm)
    }
}
