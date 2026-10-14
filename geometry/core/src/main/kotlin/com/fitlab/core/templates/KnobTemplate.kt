package com.fitlab.core.templates

import com.fitlab.core.design.PrintSettings
import com.fitlab.core.fit.CompensationKind
import com.fitlab.core.fit.CompensationTable
import com.fitlab.core.fit.FitClass
import com.fitlab.core.fit.FitReference
import com.fitlab.core.fit.ResolvedFeature
import com.fitlab.core.geom.Ring
import com.fitlab.core.geom.Shape2
import com.fitlab.core.geom.Shapes
import com.fitlab.core.geom.Vec2
import com.fitlab.core.mesh.Prism
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Pokrętło z otworem w kształcie litery D i rowkami chwytu. */
object KnobTemplate : BaseTemplate() {
    override val id = TemplateId.KNOB
    override val displayName = "Pokrętło"
    override val description = "Pokrętło z otworem na ośkę (okrągłym lub ze spłaszczeniem D) i rowkami ułatwiającymi chwyt."
    override val params = listOf(
        ParamSpec("shaftDiameter", "Średnica ośki", ParamType.MM, 6.0, 2.0, 30.0),
        ParamSpec("shaftFlat", "Wymiar na spłaszczeniu (0 = okrągła)", ParamType.MM, 4.5, 0.0, 30.0),
        ParamSpec("holeDepth", "Głębokość otworu", ParamType.MM, 10.0, 2.0, 100.0),
        ParamSpec("knobDiameter", "Średnica pokrętła", ParamType.MM, 30.0, 8.0, 150.0),
        ParamSpec("knobHeight", "Wysokość pokrętła", ParamType.MM, 15.0, 3.0, 100.0),
        ParamSpec("gripCount", "Liczba rowków chwytu (0 = gładkie)", ParamType.COUNT, 18.0, 0.0, 60.0),
        ParamSpec("gripDepth", "Głębokość rowków", ParamType.MM, 0.8, 0.2, 3.0),
    )
    override val fitSlots = listOf(FitSlotSpec("shaftFit", "Pasowanie na ośce", FitClass.PRESS))

    override fun validateRules(i: TemplateInput, comp: CompensationTable): List<ValidationError> {
        val e = ArrayList<ValidationError>()
        val d = i.v("shaftDiameter")
        val flat = i.v("shaftFlat")
        if (i.v("holeDepth") > i.v("knobHeight") - 1.0) {
            e.add(ValidationError("holeDepth", "Głębokość otworu musi być mniejsza od wysokości pokrętła o co najmniej 1 mm"))
        }
        if (flat > 0.0 && !(d / 2.0 < flat && flat < d)) {
            e.add(ValidationError("shaftFlat", "Wymiar na spłaszczeniu musi być większy od połowy i mniejszy od średnicy ośki"))
        }
        val dCad = matingCad(CompensationKind.HOLE, i.fit("shaftFit"), d, comp)
        if (dCad + 4.0 > i.v("knobDiameter") - 2.0 * i.v("gripDepth")) {
            e.add(ValidationError("knobDiameter", "Pokrętło jest za małe w stosunku do otworu"))
        }
        return e
    }

    private fun knobRing(i: TemplateInput, comp: CompensationTable): Ring {
        val r = i.v("knobDiameter") / 2.0 - comp.bias(CompensationKind.OUTLINE) / 2.0
        val grip = i.count("gripCount")
        if (grip == 0) return Shapes.circle(Vec2(0.0, 0.0), 2.0 * r)
        val depth = i.v("gripDepth")
        val n = max(grip * 16, 96)
        return Ring(
            List(n) { k ->
                val th = 2.0 * PI * k / n
                val rr = r - depth * (0.5 - 0.5 * cos(grip * th))
                Vec2(rr * cos(th), rr * sin(th))
            },
        )
    }

    private fun holeRing(i: TemplateInput, comp: CompensationTable): Ring {
        val fit = i.fit("shaftFit")
        val dCad = matingCad(CompensationKind.HOLE, fit, i.v("shaftDiameter"), comp)
        val flat = i.v("shaftFlat")
        if (flat <= 0.0) return Shapes.circle(Vec2(0.0, 0.0), dCad)
        val flatCad = matingCad(CompensationKind.HOLE, fit, flat, comp)
        return Shapes.dShape(dCad, flatCad)
    }

    override fun solidAndFeatures(
        i: TemplateInput,
        comp: CompensationTable,
        print: PrintSettings,
        fitTest: Boolean,
    ): Pair<List<Prism>, List<ResolvedFeature>> {
        val fit = i.fit("shaftFit")
        val hole = holeRing(i, comp)
        val features = arrayListOf(
            feature("shaft", "Ośka Ø", CompensationKind.HOLE, FitReference.MATING, fit, i.v("shaftDiameter"), comp),
        )
        if (i.v("shaftFlat") > 0.0) {
            features.add(feature("flat", "Spłaszczenie", CompensationKind.HOLE, FitReference.MATING, fit, i.v("shaftFlat"), comp))
        }
        val depth = i.v("holeDepth")
        if (fitTest) {
            val dCad = matingCad(CompensationKind.HOLE, fit, i.v("shaftDiameter"), comp)
            val s = Shape2(Shapes.circle(Vec2(0.0, 0.0), dCad + 6.0), listOf(hole.reversed()))
            return listOf(Prism(s, 0.0, min(depth, 6.0))) to features
        }
        val knob = knobRing(i, comp)
        val z = snapZ(depth, print).coerceAtLeast(print.layerHeightMm)
        return listOf(
            Prism(Shape2(knob, listOf(hole.reversed())), 0.0, z),
            Prism(Shape2(knob), z, i.v("knobHeight")),
        ) to features
    }
}
