package com.fitlab.core.fit

import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MaterialDefaultsTest {
    @Test
    fun values() {
        assertClose(-0.30, MaterialDefaults.bias(Material.PETG, CompensationKind.HOLE))
        assertClose(0.0, MaterialDefaults.bias(Material.ABS, CompensationKind.OUTLINE))
        assertEquals(MaterialDefaults.bias(Material.PLA, CompensationKind.SLOT), MaterialDefaults.bias(Material.OTHER, CompensationKind.SLOT))
        assertClose(0.20, MaterialDefaults.table(Material.TPU).bias(CompensationKind.PIN))
    }
}
