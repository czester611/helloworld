package com.fitlab.core.fit

import com.fitlab.core.fit.CompensationKind.HOLE
import com.fitlab.core.fit.CompensationKind.PIN
import com.fitlab.core.fit.FitClass.LOOSE
import com.fitlab.core.fit.FitClass.PRESS
import com.fitlab.core.fit.FitClass.SLIDE
import com.fitlab.core.fit.FitReference.MATING
import com.fitlab.core.fit.FitReference.ORIGINAL
import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FitMathTest {
    @Test
    fun targets() {
        assertClose(8.15, FitMath.target(HOLE, MATING, SLIDE, 8.0))
        assertClose(12.0, FitMath.target(PIN, MATING, PRESS, 12.0))
        assertClose(11.6, FitMath.target(PIN, MATING, LOOSE, 12.0))
        assertClose(5.0, FitMath.target(HOLE, ORIGINAL, LOOSE, 5.0))
    }

    @Test
    fun cad() {
        assertClose(10.3, FitMath.cad(10.0, -0.3))
        assertClose(19.8, FitMath.cad(20.0, 0.2))
    }

    @Test
    fun tableAndEnums() {
        assertEquals(0.0, CompensationTable(emptyMap()).bias(HOLE))
        assertEquals(true, HOLE.isInternal)
        assertEquals(false, PIN.isInternal)
    }
}
