package com.fitlab.core.templates

import com.fitlab.core.design.PrintSettings
import com.fitlab.core.fit.CompensationKind
import com.fitlab.core.fit.CompensationTable
import com.fitlab.core.fit.FitClass
import com.fitlab.core.fit.FitReference
import com.fitlab.core.fit.ResolvedFeature
import com.fitlab.core.geom.GeometryOps
import com.fitlab.core.geom.Shape2
import com.fitlab.core.geom.Shapes
import com.fitlab.core.geom.Vec2
import com.fitlab.core.geom.Vec3
import com.fitlab.core.mesh.Prism
import com.fitlab.core.mesh.Transform3
import kotlin.math.min

/** Wspornik kątowy L z otworami na śruby. */
object BracketTemplate : BaseTemplate() {
    override val id = TemplateId.BRACKET
    override val displayName = "Wspornik kątowy L"
    override val description = "Wspornik w kształcie litery L z opcjonalnymi otworami na śruby w obu ramionach."
    override val params = listOf(
        ParamSpec("width", "Szerokość", ParamType.MM, 20.0, 5.0, 200.0),
        ParamSpec("legA", "Długość ramienia poziomego", ParamType.MM, 30.0, 5.0, 200.0),
        ParamSpec("legB", "Wysokość ramienia pionowego", ParamType.MM, 30.0, 5.0, 200.0),
        ParamSpec("thickness", "Grubość", ParamType.MM, 3.0, 1.2, 20.0),
        ParamSpec("screwDiameter", "Średnica śruby (0 = bez otworów)", ParamType.MM, 4.0, 0.0, 20.0),
        ParamSpec("holesPerLeg", "Otworów na ramię", ParamType.COUNT, 1.0, 1.0, 2.0),
    )
    override val fitSlots = listOf(FitSlotSpec("holeFit", "Pasowanie śruby", FitClass.LOOSE))

    override fun validateRules(i: TemplateInput, comp: CompensationTable): List<ValidationError> {
        val e = ArrayList<ValidationError>()
        val t = i.v("thickness")
        val a = i.v("legA")
        val b = i.v("legB")
        if (t >= min(a, b)) e.add(ValidationError("thickness", "Grubość musi być mniejsza od długości ramion"))
        val screw = i.v("screwDiameter")
        if (screw > 0.0) {
            val hole = matingCad(CompensationKind.HOLE, i.fit("holeFit"), screw, comp)
            if (hole + 3.0 > min(a - t, b - t)) {
                e.add(ValidationError("screwDiameter", "Otwór na śrubę nie mieści się w ramieniu"))
            }
            if (hole + 3.0 > i.v("width") / i.count("holesPerLeg")) {
                e.add(ValidationError("screwDiameter", "Otwory na śruby nie mieszczą się na szerokości wspornika"))
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
        val t = i.v("thickness")
        val a = i.v("legA")
        val b = i.v("legB")
        val w = i.v("width")
        val screw = i.v("screwDiameter")
        val n = i.count("holesPerLeg")
        val features = ArrayList<ResolvedFeature>()
        var holeCad = 0.0
        if (screw > 0.0) {
            holeCad = matingCad(CompensationKind.HOLE, i.fit("holeFit"), screw, comp)
            features.add(feature("hole", "Otwór na śrubę Ø", CompensationKind.HOLE, FitReference.MATING, i.fit("holeFit"), screw, comp))
        }
        val ws = List(n) { w * (it + 0.5) / n }
        // Płyta pozioma: X = długość ramienia, Y = szerokość
        var horizontal = Shape2(Shapes.rect(0.0, 0.0, a, w))
        if (screw > 0.0) {
            horizontal = GeometryOps.difference(
                horizontal,
                ws.map { Shape2(Shapes.circle(Vec2(t + (a - t) / 2.0, it), holeCad)) },
            )
        }
        val zTop = if (fitTest) min(t, 2.0) else t
        val prisms = arrayListOf(Prism(horizontal, 0.0, zTop))
        if (!fitTest) {
            var vertical = Shape2(Shapes.rect(0.0, 0.0, w, b))
            if (screw > 0.0) {
                vertical = GeometryOps.difference(
                    vertical,
                    ws.map { Shape2(Shapes.circle(Vec2(it, t + (b - t) / 2.0), holeCad)) },
                )
            }
            val tr = Transform3.fromAxes(Vec3(0.0, 1.0, 0.0), Vec3(0.0, 0.0, 1.0), Vec3(1.0, 0.0, 0.0))
            prisms.add(Prism(vertical, 0.0, t, tr))
        }
        return prisms to features
    }
}
