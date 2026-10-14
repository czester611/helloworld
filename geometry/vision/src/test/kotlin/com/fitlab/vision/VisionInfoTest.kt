package com.fitlab.vision

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class VisionInfoTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun load() {
            nu.pattern.OpenCV.loadLocally()
        }
    }

    @Test
    fun openCvVersionStartsWith4() {
        assertTrue(openCvVersion().startsWith("4."), openCvVersion())
    }
}
