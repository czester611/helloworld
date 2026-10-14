package com.fitlab.core.templates

import com.fitlab.core.design.PrintSettings
import com.fitlab.core.fit.CompensationKind
import com.fitlab.core.fit.CompensationTable
import com.fitlab.core.fit.FitClass
import com.fitlab.core.fit.FitReference
import com.fitlab.core.fit.ResolvedFeature
import com.fitlab.core.geom.GeometryOps
import com.fitlab.core.geom.Shape2
import com.fitlab.core.mesh.Prism
import kotlin.math.abs

/** Koło zębate walcowe o zębach prostych, ewolwenta α = 20°. */
object SpurGearTemplate : BaseTemplate() {
    override val id = TemplateId.SPUR_GEAR
    override val displayName = "Koło zębate (ewolwentowe, α = 20°)"
    override val description = "Koło zębate walcowe o zębach prostych. Podaj liczbę zębów i zmierzoną średnicę wierzchołków."
    override val params = listOf(
        ParamSpec("teeth", "Liczba zębów", ParamType.COUNT, 20.0, 8.0, 150.0),
        ParamSpec("tipDiameter", "Średnica wierzchołków (zmierzona)", ParamType.MM, 22.0, 5.0, 300.0),
        ParamSpec("thickness", "Grubość koła", ParamType.MM, 6.0, 1.0, 50.0),
        ParamSpec("boreDiameter", "Średnica osi (0 = bez otworu)", ParamType.MM, 5.0, 0.0, 100.0),
        ParamSpec("backlash", "Luz międzyzębny", ParamType.MM, 0.1, 0.0, 0.5),
        ParamSpec("hubDiameter", "Średnica piasty (0 = brak)", ParamType.MM, 0.0, 0.0, 200.0),
        ParamSpec("hubHeight", "Wysokość piasty", ParamType.MM, 0.0, 0.0, 50.0),
    )
    override val fitSlots = listOf(FitSlotSpec("boreFit", "Pasowanie na osi", FitClass.PRESS))

    /** Standardowe moduły. */
    val STANDARD_MODULES = listOf(0.5, 0.6, 0.7, 0.75, 0.8, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0)

    /** Moduł m = średnica wierzchołków / (z + 2). */
    fun module(input: TemplateInput): Double = input.v("tipDiameter") / (input.count("teeth") + 2)

    /** Najbliższy moduł standardowy, gdy różnica < 3%; w przeciwnym razie null. */
    fun nearestStandardModule(m: Double): Double? {
        val best = STANDARD_MODULES.minByOrNull { abs(it - m) } ?: return null
        return if (abs(best - m) / best < 0.03) best else null
    }

    override fun validateRules(i: TemplateInput, comp: CompensationTable): List<ValidationError> {
        val e = ArrayList<ValidationError>()
        val z = i.count("teeth")
        val m = module(i)
        val rf = Involute.rootRadius(z, m)
        val bore = i.v("boreDiameter")
        var boreCad = 0.0
        if (bore > 0.0) {
            boreCad = matingCad(CompensationKind.HOLE, i.fit("boreFit"), bore, comp)
            if (boreCad + 2.0 > 2.0 * rf) e.add(ValidationError("boreDiameter", "Otwór na oś jest za duży w stosunku do koła"))
        }
        val hub = i.v("hubDiameter")
        if (hub > 0.0) {
            if (hub < boreCad + 2.0) e.add(ValidationError("hubDiameter", "Piasta jest za cienka wokół otworu"))
            if (hub > 2.0 * rf) e.add(ValidationError("hubDiameter", "Piasta jest większa od koła stóp"))
            if (i.v("hubHeight") <= 0.0) e.add(ValidationError("hubHeight", "Podaj wysokość piasty"))
        }
        return e
    }

    /** Obrys koła po kompensacji i luzie międzyzębnym. */
    fun gearCad(i: TemplateInput, comp: CompensationTable) =
        GeometryOps.offsetRing(
            Involute.gearRing(i.count("teeth"), module(i)),
            -(comp.bias(CompensationKind.OUTLINE) / 2.0 + i.v("backlash") / 4.0),
        )

    override fun solidAndFeatures(
        i: TemplateInput,
        comp: CompensationTable,
        print: PrintSettings,
        fitTest: Boolean,
    ): Pair<List<Prism>, List<ResolvedFeature>> {
        val gear = gearCad(i, comp)
        val bore = i.v("boreDiameter")
        val boreCad = if (bore > 0.0) matingCad(CompensationKind.HOLE, i.fit("boreFit"), bore, comp) else 0.0
        val tip = i.v("tipDiameter")
        val tipCad = 2.0 * gear.points.maxOf { it.length() }
        val tipTarget = tip
        val features = arrayListOf(
            ResolvedFeature(
                "tip", "Średnica wierzchołków Ø", CompensationKind.OUTLINE, FitReference.ORIGINAL, null,
                tip, tipTarget, comp.bias(CompensationKind.OUTLINE), tipCad,
            ),
        )
        if (bore > 0.0) {
            features.add(feature("bore", "Oś Ø", CompensationKind.HOLE, FitReference.MATING, i.fit("boreFit"), bore, comp))
        }
        val shape = if (bore > 0.0) Shape2(gear, listOf(circle(boreCad).reversed())) else Shape2(gear)
        if (fitTest) return listOf(Prism(shape, 0.0, 2.0)) to features
        val t = i.v("thickness")
        val prisms = arrayListOf(Prism(shape, 0.0, t))
        val hub = i.v("hubDiameter")
        if (hub > 0.0 && i.v("hubHeight") > 0.0) {
            prisms.add(Prism(ring(hub - comp.bias(CompensationKind.OUTLINE), boreCad), t, t + i.v("hubHeight")))
        }
        return prisms to features
    }
}
