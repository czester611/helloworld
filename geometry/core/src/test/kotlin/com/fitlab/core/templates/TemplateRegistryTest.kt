package com.fitlab.core.templates

import com.fitlab.core.design.DesignBuilder
import com.fitlab.core.design.PrintSettings
import com.fitlab.core.design.TemplateDesign
import com.fitlab.core.fit.Material
import com.fitlab.core.fit.MaterialDefaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TemplateRegistryTest {
    private val comp = MaterialDefaults.table(Material.PLA)

    @Test
    fun allTemplatesBuildClosedMeshes() {
        assertEquals(TemplateId.entries.size, TemplateRegistry.all.size)
        for (t in TemplateRegistry.all) {
            val i = TemplateRegistry.defaultInput(t.id)
            assertTrue(t.validate(i, comp).isEmpty(), "${t.id}: ${t.validate(i, comp)}")
            TemplateTestSupport.assertClosed(t.build(i, comp, PrintSettings()))
            TemplateTestSupport.assertClosed(t.buildFitTest(i, comp, PrintSettings()))
            assertTrue(t.displayName.isNotBlank() && t.description.isNotBlank())
        }
    }

    @Test
    fun designBuilderDispatchesTemplates() {
        for (t in TemplateRegistry.all) {
            val i = TemplateRegistry.defaultInput(t.id)
            val d = TemplateDesign(t.id, i.values, i.fits)
            TemplateTestSupport.assertClosed(DesignBuilder.build(d, comp, PrintSettings()))
            TemplateTestSupport.assertClosed(DesignBuilder.buildFitTest(d, comp, PrintSettings()))
        }
    }

    @Test
    fun footprintsAreAvailable() {
        for (t in TemplateRegistry.all) {
            val i = TemplateRegistry.defaultInput(t.id)
            assertTrue(t.build(i, comp, PrintSettings()).solid.footprint().isNotEmpty(), "${t.id}")
        }
    }
}
