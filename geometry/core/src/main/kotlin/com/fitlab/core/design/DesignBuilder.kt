package com.fitlab.core.design

import com.fitlab.core.enclosure.EnclosureBuilder
import com.fitlab.core.fit.CompensationTable
import com.fitlab.core.templates.TemplateInput
import com.fitlab.core.templates.TemplateRegistry

/** Wspólny punkt wejścia budowy części z dowolnego [PartDesign]. */
object DesignBuilder {
    /** Pełna część. */
    fun build(design: PartDesign, comp: CompensationTable, print: PrintSettings): BuildResult = when (design) {
        is OutlineDesign -> OutlineBuilder.build(design, comp, print)
        is TemplateDesign -> TemplateRegistry.get(design.templateId).build(TemplateInput(design.values, design.fits), comp, print)
        is EnclosureDesign -> EnclosureBuilder.build(design.spec, comp, print).toBuildResult()
    }

    /** Wydruk próbny (plaster). */
    fun buildFitTest(design: PartDesign, comp: CompensationTable, print: PrintSettings): BuildResult = when (design) {
        is OutlineDesign -> OutlineBuilder.buildFitTest(design, comp, print)
        is TemplateDesign ->
            TemplateRegistry.get(design.templateId).buildFitTest(TemplateInput(design.values, design.fits), comp, print)
        is EnclosureDesign -> EnclosureBuilder.buildFitTest(design.spec, comp, print).toBuildResult()
    }
}
