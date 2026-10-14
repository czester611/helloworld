package com.fitlab.vision

import org.junit.jupiter.api.BeforeAll

/** Ładuje natywną bibliotekę OpenCV (openpnp) raz dla testów. */
abstract class OpenCvTestBase {
    companion object {
        @JvmStatic
        @BeforeAll
        fun loadOpenCv() {
            nu.pattern.OpenCV.loadLocally()
        }
    }
}
