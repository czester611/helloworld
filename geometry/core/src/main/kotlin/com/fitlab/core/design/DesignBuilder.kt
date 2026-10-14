package com.fitlab.core.design

import com.fitlab.core.fit.CompensationTable

/** Wspólny punkt wejścia budowy części z dowolnego [PartDesign]. */
object DesignBuilder {
    /** Pełna część. */
    fun build(design: PartDesign, comp: CompensationTable, print: PrintSettings): BuildResult = when (design) {
        is OutlineDesign -> OutlineBuilder.build(design, comp, print)
        is TemplateDesign -> throw NotImplementedError("Szablony: krok M6")
    }

    /** Wydruk próbny (plaster). */
    fun buildFitTest(design: PartDesign, comp: CompensationTable, print: PrintSettings): BuildResult = when (design) {
        is OutlineDesign -> OutlineBuilder.buildFitTest(design, comp, print)
        is TemplateDesign -> throw NotImplementedError("Szablony: krok M6")
    }
}
