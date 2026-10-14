package com.fitlab.core.photo

import com.fitlab.core.geom.CircleFit
import com.fitlab.core.geom.GeometryException
import com.fitlab.core.geom.GeometryOps
import com.fitlab.core.geom.Ring
import com.fitlab.core.geom.Shape2
import com.fitlab.core.geom.Vec2
import org.locationtech.jts.geom.util.GeometryFixer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

/** Zamiana surowych konturów w pikselach na obrys części w mm. */
object OutlinePostProcessor {
    const val SIMPLIFY_TOL_MM = 0.1
    const val MIN_HOLE_AREA_MM2 = 3.0
    const val CIRCULARITY_MIN = 0.85
    const val CIRCLE_RMS_MIN_MM = 0.10
    const val CIRCLE_RMS_RATIO = 0.02
    const val NADIR_MARGIN_MM = 10.0
    const val DIAMETER_STEP_MM = 0.05
    const val NADIR_WARNING =
        "Zdjęcie nie było robione centralnie nad częścią — wymiary mogą być zawyżone. " +
            "Zrób zdjęcie z góry, z aparatem nad środkiem części."

    /** Przetwarza [raw] z uwzględnieniem grubości [thicknessMm] i pozycji aparatu [pose] (może być null). */
    fun process(raw: RawContours, thicknessMm: Double, pose: CameraPose?): PhotoOutline {
        val warnings = ArrayList<String>()
        val map = PixelMapping(raw.pxPerMm)
        // 1-2. px -> mm arkusza, korekcja grubości
        val outerSheet = ThicknessCorrection.apply(raw.outerPx.map { map.pxToMm(it) }, pose, thicknessMm)
        val holesSheet = raw.holesPx.map { h -> ThicknessCorrection.apply(h.map { map.pxToMm(it) }, pose, thicknessMm) }

        if (pose != null) {
            val b = Ring(outerSheet).bounds()
            val n = pose.nadirMm
            if (n.x < b.minX - NADIR_MARGIN_MM || n.x > b.maxX + NADIR_MARGIN_MM ||
                n.y < b.minY - NADIR_MARGIN_MM || n.y > b.maxY + NADIR_MARGIN_MM
            ) {
                warnings.add(NADIR_WARNING)
            }
        }

        // 3. Środek i odwrócenie osi Y
        val c = Ring(outerSheet).centroid()
        fun toPart(p: Vec2) = Vec2(p.x - c.x, -(p.y - c.y))
        val half = 0.5 / raw.pxPerMm
        // 4. Korekta półpiksela
        val outer = GeometryOps.offsetRing(Ring(outerSheet.map(::toPart)).let { fixOrient(it) }, half)
        val holes = holesSheet.mapNotNull { h ->
            try {
                GeometryOps.offsetRing(fixOrient(Ring(h.map(::toPart))), -half)
            } catch (e: GeometryException) {
                null
            }
        }
        // 5. Uproszczenie
        var shape = GeometryOps.simplify(Shape2(outer, holes.map { it.reversed() }).normalized(), SIMPLIFY_TOL_MM)
        // 6. Odrzucenie małych dziur
        shape = Shape2(shape.outer, shape.holes.filter { abs(it.signedArea()) >= MIN_HOLE_AREA_MM2 })
        // 7. Walidacja
        if (!GeometryOps.isValid(shape)) {
            val fixed = GeometryFixer.fix(GeometryOps.toJts(shape))
            val parts = GeometryOps.fromJtsMulti(fixed)
            shape = parts.maxByOrNull { it.area() } ?: throw GeometryException("Nie udało się naprawić obrysu")
        }
        // 8. Wykrywanie okrągłych otworów
        val candidates = ArrayList<HoleCandidate>()
        val inner = ArrayList<Ring>()
        for (h in shape.holes) {
            val cand = circleCandidate(h)
            if (cand != null) candidates.add(cand) else inner.add(h)
        }
        return PhotoOutline(shape.outer, inner, candidates, warnings)
    }

    private fun fixOrient(r: Ring): Ring = if (r.isCcw()) r else r.reversed()

    private fun circleCandidate(h: Ring): HoleCandidate? {
        if (h.points.size < 3) return null
        val circle = try {
            CircleFit.kasa(h.points)
        } catch (e: GeometryException) {
            return null
        }
        val a = abs(h.signedArea())
        val p = h.perimeter()
        val circularity = 4.0 * PI * a / (p * p)
        val rms = sqrt(h.points.sumOf { val d = it.distanceTo(circle.center) - circle.radius; d * d } / h.points.size)
        if (circularity >= CIRCULARITY_MIN && rms <= max(CIRCLE_RMS_MIN_MM, CIRCLE_RMS_RATIO * circle.radius)) {
            return HoleCandidate(circle.center, round(2.0 * circle.radius / DIAMETER_STEP_MM) * DIAMETER_STEP_MM)
        }
        return null
    }
}
