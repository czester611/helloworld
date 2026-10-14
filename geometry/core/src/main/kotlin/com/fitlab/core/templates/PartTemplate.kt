package com.fitlab.core.templates

import com.fitlab.core.design.BuildResult
import com.fitlab.core.design.ElephantFoot
import com.fitlab.core.design.PrintSettings
import com.fitlab.core.fit.CompensationKind
import com.fitlab.core.fit.CompensationTable
import com.fitlab.core.fit.FitClass
import com.fitlab.core.fit.FitMath
import com.fitlab.core.fit.FitReference
import com.fitlab.core.fit.ResolvedFeature
import com.fitlab.core.geom.Ring
import com.fitlab.core.geom.Shape2
import com.fitlab.core.geom.Shapes
import com.fitlab.core.geom.Vec2
import com.fitlab.core.mesh.Prism
import com.fitlab.core.mesh.Solid
import java.util.Locale
import kotlin.math.roundToLong

/** Szablon parametryczny części. */
interface PartTemplate {
    val id: TemplateId
    val displayName: String
    val description: String
    val params: List<ParamSpec>
    val fitSlots: List<FitSlotSpec>

    /** Walidacja: najpierw zakresy z [params], potem reguły szablonu. Pusta lista = OK. */
    fun validate(input: TemplateInput, comp: CompensationTable): List<ValidationError>

    /** Pełna część; rzuca [IllegalArgumentException] gdy [validate] zwraca błędy. */
    fun build(input: TemplateInput, comp: CompensationTable, print: PrintSettings): BuildResult

    /** Wydruk próbny (plaster) z pasowaniami. */
    fun buildFitTest(input: TemplateInput, comp: CompensationTable, print: PrintSettings): BuildResult
}

/** Wspólna logika szablonów: uzupełnianie domyślnych, zakresy, pomocniki kształtów i cech. */
abstract class BaseTemplate : PartTemplate {
    /** Reguły specyficzne dla szablonu (wartości są już w zakresach). */
    protected abstract fun validateRules(i: TemplateInput, comp: CompensationTable): List<ValidationError>

    protected abstract fun solidAndFeatures(i: TemplateInput, comp: CompensationTable, print: PrintSettings, fitTest: Boolean): Pair<List<Prism>, List<ResolvedFeature>>

    private fun resolve(input: TemplateInput): TemplateInput = TemplateInput(
        params.associate { it.key to (input.values[it.key] ?: it.default) },
        fitSlots.associate { it.key to (input.fits[it.key] ?: it.default) },
    )

    private fun fmt(v: Double): String =
        String.format(Locale.forLanguageTag("pl"), "%.2f", v).trimEnd('0').trimEnd(',').ifEmpty { "0" }

    final override fun validate(input: TemplateInput, comp: CompensationTable): List<ValidationError> {
        val i = resolve(input)
        val range = params.mapNotNull { p ->
            val v = i.v(p.key)
            if (v.isNaN() || v < p.min || v > p.max) {
                ValidationError(p.key, "${p.label}: wartość spoza zakresu ${fmt(p.min)}–${fmt(p.max)}")
            } else {
                null
            }
        }
        if (range.isNotEmpty()) return range
        return validateRules(i, comp)
    }

    private fun make(input: TemplateInput, comp: CompensationTable, print: PrintSettings, fitTest: Boolean): BuildResult {
        val errors = validate(input, comp)
        require(errors.isEmpty()) { errors.joinToString("; ") { it.message } }
        val (prisms, features) = solidAndFeatures(resolve(input), comp, print, fitTest)
        val (solid, warnings) = ElephantFoot.applyWithWarnings(Solid(prisms), print)
        return BuildResult(solid, features, warnings)
    }

    final override fun build(input: TemplateInput, comp: CompensationTable, print: PrintSettings): BuildResult =
        make(input, comp, print, false)

    final override fun buildFitTest(input: TemplateInput, comp: CompensationTable, print: PrintSettings): BuildResult =
        make(input, comp, print, true)

    /** Okrąg o średnicy [d] w (0,0). */
    protected fun circle(d: Double): Ring = Shapes.circle(Vec2(0.0, 0.0), d)

    /** Pierścień: zewnętrzny okrąg [dOuter] z otworem [dInner] (0 = pełny). */
    protected fun ring(dOuter: Double, dInner: Double): Shape2 =
        if (dInner > 0.0) Shape2(circle(dOuter), listOf(circle(dInner).reversed())) else Shape2(circle(dOuter))

    /** Cecha z przeliczonym celem i wymiarem CAD. */
    protected fun feature(
        id: String,
        label: String,
        kind: CompensationKind,
        reference: FitReference,
        fit: FitClass?,
        nominal: Double,
        comp: CompensationTable,
    ): ResolvedFeature {
        val target = FitMath.target(kind, reference, fit, nominal)
        val bias = comp.bias(kind)
        return ResolvedFeature(id, label, kind, reference, if (reference == FitReference.ORIGINAL) null else fit, nominal, target, bias, FitMath.cad(target, bias))
    }

    /** Wymiar CAD dla pasowania z częścią współpracującą. */
    protected fun matingCad(kind: CompensationKind, fit: FitClass, nominal: Double, comp: CompensationTable): Double =
        FitMath.cad(FitMath.target(kind, FitReference.MATING, fit, nominal), comp.bias(kind))

    /** Zaokrągla wewnętrzną granicę Z do wielokrotności wysokości warstwy. */
    protected fun snapZ(z: Double, print: PrintSettings): Double {
        val h = print.layerHeightMm
        return ((z / h).roundToLong() * h * 1e6).roundToLong() / 1e6
    }
}
