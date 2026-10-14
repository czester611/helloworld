package com.fitlab.core.templates

import com.fitlab.core.design.PrintSettings
import com.fitlab.core.fit.CompensationKind
import com.fitlab.core.fit.CompensationTable
import com.fitlab.core.fit.FitClass
import com.fitlab.core.fit.FitReference
import com.fitlab.core.fit.ResolvedFeature
import com.fitlab.core.mesh.Prism
import kotlin.math.min

/** Tuleja (z opcjonalnym kołnierzem). */
object BushingTemplate : BaseTemplate() {
    override val id = TemplateId.BUSHING
    override val displayName = "Tuleja"
    override val description = "Tuleja ślizgowa lub dystansowa z opcjonalnym kołnierzem. Podaj średnicę wałka i otworu oprawy."
    override val params = listOf(
        ParamSpec("shaftDiameter", "Średnica wałka (wewnątrz)", ParamType.MM, 8.0, 1.0, 100.0),
        ParamSpec("housingDiameter", "Średnica otworu oprawy (na zewnątrz)", ParamType.MM, 12.0, 2.0, 150.0),
        ParamSpec("length", "Długość", ParamType.MM, 10.0, 1.0, 200.0),
        ParamSpec("flangeDiameter", "Średnica kołnierza (0 = brak)", ParamType.MM, 0.0, 0.0, 200.0),
        ParamSpec("flangeThickness", "Grubość kołnierza", ParamType.MM, 2.0, 0.6, 20.0),
    )
    override val fitSlots = listOf(
        FitSlotSpec("innerFit", "Pasowanie na wałku", FitClass.SLIDE),
        FitSlotSpec("outerFit", "Pasowanie w oprawie", FitClass.PRESS),
    )

    override fun validateRules(i: TemplateInput, comp: CompensationTable): List<ValidationError> {
        val e = ArrayList<ValidationError>()
        val idCad = matingCad(CompensationKind.HOLE, i.fit("innerFit"), i.v("shaftDiameter"), comp)
        val odCad = matingCad(CompensationKind.PIN, i.fit("outerFit"), i.v("housingDiameter"), comp)
        if (idCad + 1.6 > odCad) e.add(ValidationError("housingDiameter", "Ścianka tulei < 0,8 mm"))
        val fl = i.v("flangeDiameter")
        if (fl > 0.0) {
            if (fl < i.v("housingDiameter") + 2.0) {
                e.add(ValidationError("flangeDiameter", "Kołnierz musi być większy od otworu oprawy o co najmniej 2 mm"))
            }
            if (i.v("flangeThickness") >= i.v("length")) {
                e.add(ValidationError("flangeThickness", "Grubość kołnierza musi być mniejsza niż długość"))
            }
        }
        return e
    }

    override fun solidAndFeatures(
        i: TemplateInput,
        comp: CompensationTable,
        print: PrintSettings,
        fitTest: Boolean,
    ): Pair<List<Prism>, List<ResolvedFeature>> {
        val idCad = matingCad(CompensationKind.HOLE, i.fit("innerFit"), i.v("shaftDiameter"), comp)
        val odCad = matingCad(CompensationKind.PIN, i.fit("outerFit"), i.v("housingDiameter"), comp)
        val length = i.v("length")
        val features = arrayListOf(
            feature("inner", "Wałek (otwór) Ø", CompensationKind.HOLE, FitReference.MATING, i.fit("innerFit"), i.v("shaftDiameter"), comp),
            feature("outer", "Oprawa (zewnętrzna) Ø", CompensationKind.PIN, FitReference.MATING, i.fit("outerFit"), i.v("housingDiameter"), comp),
        )
        if (fitTest) return listOf(Prism(ring(odCad, idCad), 0.0, min(length, 5.0))) to features
        val fl = i.v("flangeDiameter")
        if (fl <= 0.0) return listOf(Prism(ring(odCad, idCad), 0.0, length)) to features
        val flCad = fl - comp.bias(CompensationKind.OUTLINE)
        val ft = snapZ(i.v("flangeThickness"), print).coerceAtLeast(print.layerHeightMm)
        features.add(feature("flange", "Kołnierz Ø", CompensationKind.OUTLINE, FitReference.ORIGINAL, null, fl, comp))
        return listOf(Prism(ring(flCad, idCad), 0.0, ft), Prism(ring(odCad, idCad), ft, length)) to features
    }
}
