package com.fitlab.core.export

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FileNamesTest {
    @Test
    fun exampleFromDesign() {
        assertEquals(
            "pokretlo-pralki_fit_2.stl",
            FileNames.exportName("Pokrętło pralki", IterationType.FIT_TEST, 2, ExportFormat.STL),
        )
    }

    @Test
    fun otherTypesAndLimits() {
        assertEquals("zolc-gesla_cal_1.3mf", FileNames.exportName("Żółć gęślą!", IterationType.CALIBRATION, 1, ExportFormat.THREE_MF))
        assertEquals(40, FileNames.slug("a".repeat(100)).length)
        assertEquals("part", FileNames.slug("???"))
    }
}
