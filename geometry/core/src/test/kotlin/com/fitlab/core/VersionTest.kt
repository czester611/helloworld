package com.fitlab.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VersionTest {
    @Test
    fun versionIsSet() {
        assertEquals("0.1.0", FitLabCore.VERSION)
    }
}
