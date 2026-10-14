package com.fitlab.core.design

import com.fitlab.core.fit.CompensationKind
import com.fitlab.core.fit.CompensationTable
import com.fitlab.core.fit.FitReference
import com.fitlab.core.fit.ResolvedFeature
import com.fitlab.core.geom.GeometryOps
import com.fitlab.core.geom.Shape2
import com.fitlab.core.geom.Shapes
import com.fitlab.core.geom.Vec2
import com.fitlab.core.mesh.Prism
import com.fitlab.core.mesh.Solid

/** Kupon kalibracyjny: płytka 44×20×3 mm z otworem Ø10, szczeliną 6 mm i czopem Ø8. */
object CalibrationCoupon {
    const val LENGTH_MM = 44.0
    const val WIDTH_MM = 20.0
    const val PLATE_MM = 3.0
    const val HOLE_MM = 10.0
    const val SLOT_MM = 6.0
    const val SLOT_LENGTH_MM = 12.0
    const val PIN_MM = 8.0
    const val PIN_TOP_MM = 11.0

    /** Buduje kupon z kompensacją [comp]; 5 cech ORIGINAL do pomiaru suwmiarką. */
    fun build(comp: CompensationTable, print: PrintSettings): BuildResult {
        val bOut = comp.bias(CompensationKind.OUTLINE)
        val bHole = comp.bias(CompensationKind.HOLE)
        val bSlot = comp.bias(CompensationKind.SLOT)
        val bPin = comp.bias(CompensationKind.PIN)

        val plate = GeometryOps.offsetRing(Shapes.rect(0.0, 0.0, LENGTH_MM, WIDTH_MM), -bOut / 2.0)
        val holeCad = HOLE_MM - bHole
        val slotW = SLOT_MM - bSlot
        val slotL = SLOT_LENGTH_MM - bSlot
        val pinCad = PIN_MM - bPin
        val shape = GeometryOps.difference(
            Shape2(plate),
            listOf(
                Shape2(Shapes.circle(Vec2(10.0, 10.0), holeCad)),
                Shape2(Shapes.rect(23.0 - slotW / 2, 10.0 - slotL / 2, 23.0 + slotW / 2, 10.0 + slotL / 2)),
            ),
        )
        val pin = Shape2(Shapes.circle(Vec2(35.0, 10.0), pinCad))
        val raw = Solid(listOf(Prism(shape, 0.0, PLATE_MM), Prism(pin, PLATE_MM, PIN_TOP_MM)))
        val (solid, warnings) = ElephantFoot.applyWithWarnings(raw, print)

        fun f(id: String, label: String, kind: CompensationKind, nominal: Double, bias: Double) =
            ResolvedFeature(id, label, kind, FitReference.ORIGINAL, null, nominal, nominal, bias, nominal - bias)
        val features = listOf(
            f("plateLength", "Długość płytki", CompensationKind.OUTLINE, LENGTH_MM, bOut),
            f("plateWidth", "Szerokość płytki", CompensationKind.OUTLINE, WIDTH_MM, bOut),
            f("hole", "Otwór Ø", CompensationKind.HOLE, HOLE_MM, bHole),
            f("slot", "Szczelina (szerokość)", CompensationKind.SLOT, SLOT_MM, bSlot),
            f("pin", "Czop Ø", CompensationKind.PIN, PIN_MM, bPin),
        )
        return BuildResult(solid, features, warnings)
    }
}
