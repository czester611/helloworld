package com.fitlab.core.templates

import com.fitlab.core.design.PrintSettings
import com.fitlab.core.fit.CompensationKind
import com.fitlab.core.fit.CompensationTable
import com.fitlab.core.fit.FitClass
import com.fitlab.core.fit.FitReference
import com.fitlab.core.fit.ResolvedFeature
import com.fitlab.core.geom.Ring
import com.fitlab.core.geom.Shape2
import com.fitlab.core.geom.Vec2
import com.fitlab.core.geom.Shapes
import com.fitlab.core.mesh.Prism
import kotlin.math.min

/** Zaślepka do rury lub profilu (okrągłego albo kwadratowego). */
object CapTemplate : BaseTemplate() {
    override val id = TemplateId.CAP
    override val displayName = "Zaślepka"
    override val description = "Zaślepka z kapturkiem i wtykiem do rury lub profilu okrągłego albo kwadratowego."
    override val params = listOf(
        ParamSpec("square", "Profil kwadratowy (0 = rura okrągła)", ParamType.BOOL, 0.0, 0.0, 1.0),
        ParamSpec("tubeInner", "Wewnętrzny wymiar rury/profilu (Ø lub bok)", ParamType.MM, 20.0, 3.0, 150.0),
        ParamSpec("plugLength", "Długość wtyku", ParamType.MM, 8.0, 2.0, 50.0),
        ParamSpec("capSize", "Wymiar kapturka (Ø lub bok, zwykle = zewn. wymiar rury)", ParamType.MM, 22.0, 4.0, 200.0),
        ParamSpec("capThickness", "Grubość kapturka", ParamType.MM, 2.0, 0.8, 10.0),
        ParamSpec("wall", "Ścianka wtyku (0 = pełny)", ParamType.MM, 2.0, 0.0, 20.0),
    )
    override val fitSlots = listOf(FitSlotSpec("plugFit", "Pasowanie wtyku", FitClass.PRESS))

    private fun kind(i: TemplateInput) = if (i.bool("square")) CompensationKind.OUTLINE else CompensationKind.PIN

    private fun shapeOf(i: TemplateInput, size: Double): Ring =
        if (i.bool("square")) {
            Shapes.roundedSquare(Vec2(0.0, 0.0), size, min(1.0, size / 4.0))
        } else {
            Shapes.circle(Vec2(0.0, 0.0), size)
        }

    override fun validateRules(i: TemplateInput, comp: CompensationTable): List<ValidationError> {
        val e = ArrayList<ValidationError>()
        if (i.v("capSize") < i.v("tubeInner") + 1.0) {
            e.add(ValidationError("capSize", "Kapturek musi być większy od wymiaru wewnętrznego rury o co najmniej 1 mm"))
        }
        val wall = i.v("wall")
        val plug = matingCad(kind(i), i.fit("plugFit"), i.v("tubeInner"), comp)
        if (wall > 0.0 && plug - 2.0 * wall < 1.0) {
            e.add(ValidationError("wall", "Ścianka wtyku jest za gruba — wnętrze wtyku musi mieć co najmniej 1 mm"))
        }
        return e
    }

    override fun solidAndFeatures(
        i: TemplateInput,
        comp: CompensationTable,
        print: PrintSettings,
        fitTest: Boolean,
    ): Pair<List<Prism>, List<ResolvedFeature>> {
        val k = kind(i)
        val plugCad = matingCad(k, i.fit("plugFit"), i.v("tubeInner"), comp)
        val capCad = i.v("capSize") - comp.bias(CompensationKind.OUTLINE)
        val wall = i.v("wall")
        val plugShape = if (wall > 0.0) {
            Shape2(shapeOf(i, plugCad), listOf(shapeOf(i, plugCad - 2.0 * wall).reversed()))
        } else {
            Shape2(shapeOf(i, plugCad))
        }
        val features = listOf(
            feature("plug", "Wtyk", k, FitReference.MATING, i.fit("plugFit"), i.v("tubeInner"), comp),
            feature("cap", "Kapturek", CompensationKind.OUTLINE, FitReference.ORIGINAL, null, i.v("capSize"), comp),
        )
        val capT = if (fitTest) 1.2 else snapZ(i.v("capThickness"), print).coerceAtLeast(print.layerHeightMm)
        val plugLen = if (fitTest) min(i.v("plugLength"), 5.0) else i.v("plugLength")
        return listOf(
            Prism(Shape2(shapeOf(i, capCad)), 0.0, capT),
            Prism(plugShape, capT, capT + plugLen),
        ) to features
    }
}
