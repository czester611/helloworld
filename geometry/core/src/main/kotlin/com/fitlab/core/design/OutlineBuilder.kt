package com.fitlab.core.design

import com.fitlab.core.fit.CompensationKind
import com.fitlab.core.fit.CompensationTable
import com.fitlab.core.fit.FitMath
import com.fitlab.core.fit.FitReference
import com.fitlab.core.fit.ResolvedFeature
import com.fitlab.core.geom.GeometryException
import com.fitlab.core.geom.GeometryOps
import com.fitlab.core.geom.Ring
import com.fitlab.core.geom.Shape2
import com.fitlab.core.geom.Shapes
import com.fitlab.core.mesh.Prism
import com.fitlab.core.mesh.Solid
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Budowa części z obrysu: warstwy, fazki schodkowe, stopa słonia, cechy z kompensacją. */
object OutlineBuilder {
    /** Minimalna ścianka i minimalny wymiar otworu CAD, mm. */
    const val MIN_WALL_MM = 0.8

    /** Maksymalna wysokość plastra próbnego, mm. */
    const val FIT_TEST_MAX_HEIGHT_MM = 3.0
    private const val MIN_THICKNESS_MM = 0.4
    private const val MAX_THICKNESS_MM = 200.0
    private const val MIN_LAYER_MM = 0.05
    private const val EPS = 1e-9

    private fun fmt(v: Double) = String.format(Locale.forLanguageTag("pl"), "%.2f", v)

    private fun outerCad(d: OutlineDesign, comp: CompensationTable): Ring =
        GeometryOps.offsetRing(d.outer, -comp.bias(CompensationKind.OUTLINE) / 2.0)

    private fun holeTarget(h: HoleFeature) =
        FitMath.target(CompensationKind.HOLE, h.reference, if (h.reference == FitReference.ORIGINAL) null else h.fit, h.nominalDiameterMm)

    /** Walidacja wejścia; lista komunikatów po polsku (pusta = OK). */
    fun validate(d: OutlineDesign, comp: CompensationTable): List<String> {
        val errors = ArrayList<String>()
        if (d.thicknessMm < MIN_THICKNESS_MM || d.thicknessMm > MAX_THICKNESS_MM) {
            errors.add("Grubość musi mieścić się w zakresie 0,4–200 mm")
        }
        val outerPoly = try {
            GeometryOps.toJts(Shape2(outerCad(d, comp)))
        } catch (e: GeometryException) {
            errors.add(e.message ?: "Nieprawidłowy obrys")
            return errors
        }
        val bHole = comp.bias(CompensationKind.HOLE)
        val inner = outerPoly.buffer(-MIN_WALL_MM)
        val circles = d.holes.map { Shapes.circle(it.center, holeTarget(it) - bHole) }
        d.holes.forEachIndexed { i, h ->
            val cad = holeTarget(h) - bHole
            val label = "Otwór H${h.id.removePrefix("hole-")}"
            if (cad < MIN_WALL_MM) {
                errors.add("$label: średnica po kompensacji (${fmt(cad)} mm) jest mniejsza niż 0,8 mm")
            } else if (!inner.contains(GeometryOps.toJts(Shape2(circles[i])))) {
                errors.add("$label wychodzi poza obrys lub ścianka jest cieńsza niż 0,8 mm")
            }
        }
        for (i in d.holes.indices) {
            for (j in i + 1 until d.holes.size) {
                if (GeometryOps.toJts(Shape2(circles[i])).intersects(GeometryOps.toJts(Shape2(circles[j])))) {
                    errors.add("Otwory H${d.holes[i].id.removePrefix("hole-")} i H${d.holes[j].id.removePrefix("hole-")} nachodzą na siebie")
                }
            }
        }
        return errors
    }

    /** Buduje część. Rzuca [IllegalArgumentException] z komunikatami walidacji lub [GeometryException]. */
    fun build(
        d: OutlineDesign,
        comp: CompensationTable,
        print: PrintSettings,
        heightOverrideMm: Double? = null,
        includeChamfers: Boolean = true,
    ): BuildResult {
        val errors = validate(d, comp)
        require(errors.isEmpty()) { errors.joinToString("; ") }

        val t = heightOverrideMm ?: d.thicknessMm
        val h = print.layerHeightMm
        val bOut = comp.bias(CompensationKind.OUTLINE)
        val bSlot = comp.bias(CompensationKind.SLOT)
        val bHole = comp.bias(CompensationKind.HOLE)
        val outerCad = outerCad(d, comp)
        val innerCad = d.innerContours.map { GeometryOps.offsetRing(it, -bSlot / 2.0) }
        val holeCad = d.holes.map { holeTarget(it) - bHole }
        val topChamfer = if (includeChamfers) d.topChamferMm else 0.0

        fun steps(c: Double) = max(1, ceil(c / h - EPS).toInt())

        // 1. Punkty podziału
        val cuts = sortedSetOf(0.0, t)
        fun roundZ(z: Double): Double = ((z / h).roundToLong() * h).let { (it * 1e6).roundToLong() / 1e6 }
        if (print.elephantFootMm > 0) cuts.add(roundZ(h))
        val chamferSources = ArrayList<Double>()
        if (includeChamfers) d.holes.forEach { if (it.chamferMm > 0) chamferSources.add(it.chamferMm) }
        if (topChamfer > 0) chamferSources.add(topChamfer)
        for (c in chamferSources) for (k in 1..steps(c)) cuts.add(roundZ(t - k * h))
        val zs = cuts.filter { it >= 0.0 && it <= t }.toSortedSet().toList()

        val warnings = ArrayList<String>()
        val prisms = ArrayList<Prism>()
        for (idx in 0 until zs.size - 1) {
            val za = zs[idx]
            val zb = zs[idx + 1]
            if (zb - za < MIN_LAYER_MM) continue
            val zm = (za + zb) / 2.0
            fun stepK() = ceil((t - zm) / h - EPS).toInt()

            var outer = outerCad
            if (topChamfer > 0) {
                val n = steps(topChamfer)
                if (zm > t - n * h) {
                    val k = stepK()
                    outer = GeometryOps.offsetRing(outerCad, -topChamfer * (n - k + 1) / n)
                }
            }
            val cutters = ArrayList<Shape2>()
            innerCad.forEach { cutters.add(Shape2(it)) }
            d.holes.forEachIndexed { i, hole ->
                var dia = holeCad[i]
                if (includeChamfers && hole.chamferMm > 0) {
                    val n = steps(hole.chamferMm)
                    if (zm > t - n * h) dia += 2.0 * hole.chamferMm * (n - stepK() + 1) / n
                }
                cutters.add(Shape2(Shapes.circle(hole.center, dia)))
            }
            var shape = GeometryOps.difference(Shape2(outer), cutters)
            if (print.elephantFootMm > 0 && zb <= h + EPS) {
                val inset = ElephantFoot.inset(shape, print.elephantFootMm)
                if (inset == null) {
                    if (ElephantFoot.SKIPPED_WARNING !in warnings) warnings.add(ElephantFoot.SKIPPED_WARNING)
                } else {
                    shape = inset
                }
            }
            prisms.add(Prism(shape, za, zb))
        }

        // 3. Cechy
        val features = ArrayList<ResolvedFeature>()
        val width = d.outer.bounds().width
        features.add(
            ResolvedFeature(
                "outline", "Obrys (szerokość X)", CompensationKind.OUTLINE, FitReference.ORIGINAL, null,
                width, width, bOut, outerCad.bounds().width,
            ),
        )
        d.holes.forEachIndexed { i, hole ->
            val target = holeTarget(hole)
            features.add(
                ResolvedFeature(
                    hole.id, "Otwór H${hole.id.removePrefix("hole-")} Ø", CompensationKind.HOLE, hole.reference,
                    if (hole.reference == FitReference.ORIGINAL) null else hole.fit,
                    hole.nominalDiameterMm, target, bHole, holeCad[i],
                ),
            )
        }
        d.innerContours.forEachIndexed { i, r ->
            val w = r.bounds().width
            features.add(
                ResolvedFeature(
                    "slot-${i + 1}", "Wycięcie S${i + 1} (szerokość X)", CompensationKind.SLOT, FitReference.ORIGINAL, null,
                    w, w, bSlot, innerCad[i].bounds().width,
                ),
            )
        }
        return BuildResult(Solid(prisms), features, warnings)
    }

    /** Plaster próbny: wysokość min(grubość, 3 mm), bez fazek. */
    fun buildFitTest(d: OutlineDesign, comp: CompensationTable, print: PrintSettings): BuildResult =
        build(d, comp, print, heightOverrideMm = min(d.thicknessMm, FIT_TEST_MAX_HEIGHT_MM), includeChamfers = false)
}
