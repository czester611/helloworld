package com.fitlab.core.enclosure

import com.fitlab.core.design.ElephantFoot
import com.fitlab.core.design.PrintSettings
import com.fitlab.core.export.PartExporter
import com.fitlab.core.fit.CompensationKind
import com.fitlab.core.fit.CompensationTable
import com.fitlab.core.fit.FitClass
import com.fitlab.core.fit.FitMath
import com.fitlab.core.fit.FitReference
import com.fitlab.core.fit.ResolvedFeature
import com.fitlab.core.geom.Bounds2
import com.fitlab.core.geom.GeometryException
import com.fitlab.core.geom.GeometryOps
import com.fitlab.core.geom.Ring
import com.fitlab.core.geom.Shape2
import com.fitlab.core.geom.Shapes
import com.fitlab.core.geom.Vec2
import com.fitlab.core.geom.Vec3
import com.fitlab.core.mesh.Prism
import com.fitlab.core.mesh.Solid
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Budowa obudowy (dno ze ściankami + pokrywa) z obrysu obiektu.
 *
 * Wnęka = obrys obiektu + luz, kompensowana jak szczelina (SLOT); wymiary zewnętrzne i zakładka jak obrys
 * (OUTLINE), otwory pod wkręty jak otwory (HOLE). Ścianki z wycięciami powstają z warstw (graniastosłupów) o
 * wspólnym przedziale wysokości, od których odejmowany jest przekrój wycięcia.
 */
object EnclosureBuilder {
    /** Obrys mniejszy niż to (w którymkolwiek wymiarze) wywołuje ostrzeżenie o skali. */
    const val MIN_OBJECT_MM = 10.0
    const val MIN_WALL_MM = 0.8

    /** Grubość materiału wokół otworu w słupku. */
    const val POST_WALL_MM = 2.0
    const val STANDOFF_DIAMETER_MM = 4.0

    /** Odstęp dna i pokrywy na stole (3MF). */
    const val PLATE_GAP_MM = 10.0

    /** Bok plastra próbnego (narożnik obudowy). */
    const val FIT_TEST_SIZE_MM = 25.0

    /** Komunikat do pokazania w UI: co wynika ze zdjęcia, a co użytkownik podaje ręcznie. */
    const val MANUAL_INPUT_NOTE =
        "Zdjęcie daje tylko obrys obiektu. Wysokość obiektu i wystające elementy wpisz ręcznie (suwmiarka), " +
            "a położenie portów (USB itp.) zaznacz sam jako wycięcia."

    private const val POST_OBJECT_MARGIN_MM = 0.3
    private const val POST_WALL_OVERLAP_MM = 0.5
    private const val EAR_GAP_MM = 0.05
    private const val EPS = 1e-9
    private const val MIN_LAYER_MM = 0.01
    private const val MIN_AREA_MM2 = 1e-4
    private const val CUT_INWARD_MM = 0.5
    private const val CUT_OUTWARD_EXTRA_MM = 2.0
    private const val CUT_REACH_MM = 10.0
    private const val VENT_MARGIN_MM = 2.5
    private const val VENT_POST_MARGIN_MM = 1.0
    private const val MIN_COUNTERBORE_MM = 0.4
    private const val MAX_COUNTERBORE_MM = 2.0
    private const val MIN_LID_REST_MM = 0.8

    private fun fmt(v: Double) = String.format(Locale.forLanguageTag("pl"), "%.2f", v)

    private fun r6(v: Double) = (v * 1e6).roundToLong() / 1e6

    /** Walidacja parametrów; lista komunikatów po polsku (pusta = OK). */
    fun validate(spec: EnclosureSpec): List<String> {
        val e = ArrayList<String>()
        fun range(name: String, v: Double, lo: Double, hi: Double) {
            if (!(v >= lo && v <= hi)) e.add("$name musi mieścić się w zakresie ${fmt(lo)}–${fmt(hi)} mm")
        }
        val b = spec.outline.bounds()
        if (b.width < 1.0 || b.height < 1.0) e.add("Obrys obiektu jest zbyt mały")
        if (!GeometryOps.isValid(Shape2(spec.outline))) e.add("Obrys obiektu jest nieprawidłowy (samoprzecięcia)")
        range("Wysokość obiektu", spec.objectHeightMm, 0.5, 200.0)
        range("Luz", spec.clearanceMm, 0.0, 10.0)
        range("Grubość ścianki", spec.wallMm, MIN_WALL_MM, 10.0)
        range("Grubość podłogi", spec.floorMm, MIN_WALL_MM, 10.0)
        range("Grubość pokrywy", spec.lidMm, MIN_WALL_MM, 10.0)
        range("Promień zaokrągleń", spec.cornerRadiusMm, 0.0, 20.0)
        range("Zapas wewnętrzny", spec.headroomMm, 0.0, 50.0)
        when (spec.join) {
            EnclosureJoin.LIP -> {
                range("Wysokość zakładki", spec.lipHeightMm, 0.8, 10.0)
                range("Grubość zakładki", spec.lipThicknessMm, 0.8, 5.0)
                if (min(b.width, b.height) + 2.0 * spec.clearanceMm < 2.0 * spec.lipThicknessMm + 2.0) {
                    e.add("Obiekt jest zbyt mały na zakładkę o tej grubości")
                }
            }
            EnclosureJoin.SCREWS -> {
                range("Średnica otworu w słupku", spec.screwPilotMm, 1.0, 6.0)
                range("Średnica wkręta", spec.screwNominalMm, 1.5, 6.0)
            }
        }
        if (spec.ventCount !in 0..20) e.add("Liczba szczelin wentylacyjnych musi mieścić się w zakresie 0–20")
        if (spec.ventCount > 0) range("Szerokość szczeliny", spec.ventWidthMm, 0.8, 10.0)
        if (spec.standoffs) range("Wysokość podpórek", spec.standoffHeightMm, 0.8, 20.0)
        spec.cutouts.forEachIndexed { i, c ->
            val label = "Wycięcie ${i + 1}"
            if (!(c.widthMm >= 0.5)) e.add("$label: szerokość musi wynosić co najmniej 0,5 mm")
            if (c.shape == CutoutShape.RECT && !(c.heightMm >= 0.5)) e.add("$label: wysokość musi wynosić co najmniej 0,5 mm")
            if (!(c.centerZMm >= 0.0)) e.add("$label: wysokość środka nie może być ujemna")
            if (c.side == CutoutSide.EDGE && c.edgeIndex !in spec.outline.points.indices) {
                e.add("$label: nie ma krawędzi o numerze ${c.edgeIndex}")
            }
        }
        return e
    }

    /** Buduje obudowę. Rzuca [IllegalArgumentException] z komunikatami walidacji lub [GeometryException]. */
    fun build(
        spec: EnclosureSpec,
        comp: CompensationTable,
        print: PrintSettings,
        machine: MachineLimits = MachineLimits(),
        checkMesh: Boolean = true,
    ): EnclosureResult = compute(spec, comp, print, machine, fitTest = false, checkMesh = checkMesh)

    /**
     * Plaster próbny pasowania: narożnik obudowy (ok. 25×25 mm) z fragmentem ścianki dna i pokrywy z zakładką
     * (lub słupkiem i otworem), kilka warstw. Bez wycięć, szczelin i podpórek.
     */
    fun buildFitTest(
        spec: EnclosureSpec,
        comp: CompensationTable,
        print: PrintSettings,
        machine: MachineLimits = MachineLimits(),
    ): EnclosureResult = compute(
        spec.copy(cutouts = emptyList(), ventCount = 0, standoffs = false),
        comp, print, machine, fitTest = true, checkMesh = true,
    )

    /** Ostrzeżenia o siatce (nieszczelna, zła orientacja, zdegenerowane trójkąty) dla każdej części. */
    fun meshWarnings(r: EnclosureResult): List<String> = r.parts.mapNotNull { p ->
        val rep = PartExporter.validate(p.solid)
        val name = if (p.name == "base") "dna" else "pokrywy"
        when {
            !rep.isClosed || !rep.isConsistentlyOriented || rep.volumeMm3 <= 0.0 ->
                "Siatka $name jest nieszczelna lub źle zorientowana — zgłoś projekt"
            rep.degenerateTriangles > 0 -> "Siatka $name zawiera ${rep.degenerateTriangles} zdegenerowanych trójkątów"
            else -> null
        }
    }

    /** Odbicie lustrzane kształtu względem osi X (y -> −y), z normalizacją orientacji. */
    internal fun mirrorY(s: Shape2): Shape2 = Shape2(mirrorY(s.outer), s.holes.map { mirrorY(it) }).normalized()

    internal fun mirrorY(r: Ring): Ring = Ring(r.points.map { Vec2(it.x, -it.y) })

    private class Cutter(
        val id: String,
        val e: Vec2,
        val u: Vec2,
        val n: Vec2,
        val halfWidth: Double,
        val isCircle: Boolean,
        val radius: Double,
        val zc: Double,
        val zLo: Double,
        val zHi: Double,
        val outward: Double,
    ) {
        fun ringAt(halfW: Double): Ring {
            val a = e + n * (-CUT_INWARD_MM) - u * halfW
            val b = e + n * (-CUT_INWARD_MM) + u * halfW
            val c = e + n * outward + u * halfW
            val d = e + n * outward - u * halfW
            return Ring(listOf(a, b, c, d))
        }

        /** Przekrój wycięcia na wysokości [zm] albo null, gdy tam go nie ma. */
        fun shapeAt(zm: Double): Shape2? {
            if (zm < zLo || zm > zHi) return null
            val hw = if (!isCircle) {
                halfWidth
            } else {
                val dz = zm - zc
                val v = radius * radius - dz * dz
                if (v <= 1e-9) return null
                sqrt(v)
            }
            return Shape2(ringAt(hw)).normalized()
        }

        fun planBox(): Ring = ringAt(if (isCircle) radius else halfWidth)
    }

    private fun snapUp(v: Double, layer: Double): Double = r6(ceil(v / layer - 1e-9) * layer)

    private fun boundsOf(solid: Solid): Bounds2 {
        val bs = solid.prisms.map { it.shape.outer.bounds() }
        return Bounds2(bs.minOf { it.minX }, bs.minOf { it.minY }, bs.maxOf { it.maxX }, bs.maxOf { it.maxY })
    }

    private fun moved(solid: Solid, d: Vec2): Solid = Solid(solid.prisms.map { Prism(it.shape.translated(d), it.z0, it.z1) })

    private fun clip(prisms: List<Prism>, box: Shape2, zMax: Double): List<Prism> =
        prisms.filter { it.z0 < zMax - EPS }.flatMap { p ->
            GeometryOps.intersectionAll(p.shape, box).filter { it.area() > MIN_AREA_MM2 }
                .map { Prism(it, p.z0, min(p.z1, zMax)) }
        }

    private fun union1(rings: List<Ring>): Ring {
        val u = GeometryOps.union(rings.map { Shape2(it) })
        val best = u.maxByOrNull { it.area() } ?: throw GeometryException("Kształt zniknął w wyniku operacji")
        return best.outer
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun compute(
        spec: EnclosureSpec,
        comp: CompensationTable,
        print: PrintSettings,
        machine: MachineLimits,
        fitTest: Boolean,
        checkMesh: Boolean,
    ): EnclosureResult {
        val errors = validate(spec)
        require(errors.isEmpty()) { errors.joinToString("; ") }

        val warnings = ArrayList<String>()
        val layer = print.layerHeightMm
        val bSlot = comp.bias(CompensationKind.SLOT)
        val bOut = comp.bias(CompensationKind.OUTLINE)
        val bHole = comp.bias(CompensationKind.HOLE)

        val obj: Ring = Shape2(spec.outline).normalized().outer
        val ob = obj.bounds()
        if (min(ob.width, ob.height) < MIN_OBJECT_MM) {
            warnings.add("Obrys obiektu jest mniejszy niż 10 mm — sprawdź skalę zdjęcia lub wymiary")
        }
        val nozzleMultiples = spec.wallMm / machine.nozzleMm
        if (kotlin.math.abs(nozzleMultiples - Math.rint(nozzleMultiples)) > 0.01) {
            warnings.add(
                "Grubość ścianki ${fmt(spec.wallMm)} mm nie jest wielokrotnością średnicy dyszy " +
                    "(${fmt(machine.nozzleMm)} mm) — slicer może zostawić szczeliny",
            )
        }

        // --- Cele 2D (przed kompensacją) ---
        val cavityTarget = GeometryOps.offsetRing(obj, spec.clearanceMm)
        val cb = cavityTarget.bounds()
        val cavityShape = Shape2(cavityTarget)
        val r = spec.cornerRadiusMm
        val outerTarget = if (r > 0.0) {
            GeometryOps.offsetRing(GeometryOps.offsetRing(cavityTarget, spec.wallMm - r), r, GeometryOps.JoinStyle.ROUND)
        } else {
            GeometryOps.offsetRing(cavityTarget, spec.wallMm)
        }
        val wallTarget = Shape2(outerTarget, listOf(cavityTarget)).normalized()
        val objShape = Shape2(obj)

        // --- Słupki na wkręty ---
        val posts = ArrayList<PostPlan>()
        if (spec.join == EnclosureJoin.SCREWS) {
            val postD = spec.screwPilotMm + 2.0 * POST_WALL_MM
            val pr = postD / 2.0
            val names = listOf("NE", "NW", "SW", "SE")
            val signs = listOf(1 to 1, -1 to 1, -1 to -1, 1 to -1)
            val outerShapeT = Shape2(outerTarget)
            signs.forEachIndexed { i, (sx, sy) ->
                val corner = Vec2(if (sx > 0) cb.maxX else cb.minX, if (sy > 0) cb.maxY else cb.minY)
                val inC = Vec2(corner.x - sx * (pr - POST_WALL_OVERLAP_MM), corner.y - sy * (pr - POST_WALL_OVERLAP_MM))
                val inCircle = Shape2(Shapes.circle(inC, postD))
                if (GeometryOps.contains(outerShapeT, inCircle) &&
                    GeometryOps.distance(objShape, inCircle) >= POST_OBJECT_MARGIN_MM
                ) {
                    posts.add(PostPlan(names[i], inC, postD, spec.screwPilotMm, ear = false))
                    return@forEachIndexed
                }
                if (!spec.screwEars) {
                    warnings.add("Pominięto słupek ${names[i]}: koliduje z obiektem lub wychodzi poza obrys obudowy")
                    return@forEachIndexed
                }
                val d = (pr + EAR_GAP_MM) / sqrt(2.0)
                val earC = Vec2(corner.x + sx * d, corner.y + sy * d)
                val earCircle = Shape2(Shapes.circle(earC, postD))
                if (!GeometryOps.intersects(cavityShape, earCircle) && GeometryOps.intersects(wallTarget, earCircle)) {
                    posts.add(PostPlan(names[i], earC, postD, spec.screwPilotMm, ear = true))
                    warnings.add(
                        "Słupek ${names[i]} koliduje z obiektem — wyprowadzono go na zewnątrz ścianki (ucho). " +
                            "Zwiększ luz, by zmieścić słupki wewnątrz.",
                    )
                } else {
                    warnings.add("Pominięto słupek ${names[i]}: koliduje z obiektem i nie da się go oprzeć o ściankę")
                }
            }
            if (posts.isEmpty()) warnings.add("Brak słupków — pokrywy nie da się przykręcić")
        }

        // --- Wymiary po kompensacji ---
        val cavityCad = GeometryOps.offsetRing(cavityTarget, -bSlot / 2.0)
        val outerPlainCad = GeometryOps.offsetRing(outerTarget, -bOut / 2.0)
        var outerCad = outerPlainCad
        val earRings = posts.filter { it.ear }.map { Shapes.circle(it.center, it.postDiameterMm - bOut) }
        if (earRings.isNotEmpty()) outerCad = union1(listOf(outerCad) + earRings)
        val floorShape = Shape2(outerCad)
        val wallRegion = Shape2(outerCad, listOf(cavityCad)).normalized()

        // --- Wysokości ---
        val floorTop = snapUp(spec.floorMm, layer)
        val standoffH = if (spec.standoffs) spec.standoffHeightMm else 0.0
        val cavityH = snapUp(standoffH + spec.objectHeightMm + spec.headroomMm, layer)
        val wallTop = r6(floorTop + cavityH)
        val lidT = snapUp(spec.lidMm, layer)
        if (spec.join == EnclosureJoin.LIP && spec.lipHeightMm > spec.headroomMm + EPS) {
            warnings.add("Zakładka (${fmt(spec.lipHeightMm)} mm) jest wyższa niż zapas nad obiektem — może go dotykać")
        }

        // --- Podpórki ---
        val standoffs = ArrayList<StandoffPlan>()
        val standoffShapes = ArrayList<Shape2>()
        if (spec.standoffs) {
            val centroid = obj.centroid()
            val sr = STANDOFF_DIAMETER_MM / 2.0
            for ((sx, sy) in listOf(1 to 1, -1 to 1, -1 to -1, 1 to -1)) {
                val v = obj.points.maxByOrNull { sx * it.x + sy * it.y } ?: continue
                val dir = centroid - v
                val len = dir.length()
                if (len < 1e-6) continue
                var shift = sr + 0.3
                var c = v + dir * (shift / len)
                var circle = Shape2(Shapes.circle(c, STANDOFF_DIAMETER_MM))
                while (!GeometryOps.contains(objShape, circle) && shift < 4.0 * sr) {
                    shift += 0.25
                    c = v + dir * (shift / len)
                    circle = Shape2(Shapes.circle(c, STANDOFF_DIAMETER_MM))
                }
                val clashPost = posts.any { it.center.distanceTo(c) < it.postDiameterMm / 2.0 + sr + POST_OBJECT_MARGIN_MM }
                if (GeometryOps.contains(objShape, circle) && !clashPost) {
                    standoffs.add(StandoffPlan(c, STANDOFF_DIAMETER_MM))
                    standoffShapes.add(Shape2(Shapes.circle(c, STANDOFF_DIAMETER_MM - bOut)))
                } else {
                    warnings.add("Pominięto podpórkę przy narożniku obiektu (nie mieści się pod obiektem lub koliduje ze słupkiem)")
                }
            }
        }

        // --- Wycięcia ---
        val cutters = ArrayList<Cutter>()
        val lipBandBottom = wallTop - spec.lipHeightMm
        spec.cutouts.forEachIndexed { i, c ->
            val label = "Wycięcie ${i + 1}"
            val frame: Triple<Vec2, Vec2, Vec2>? = when (c.side) {
                CutoutSide.NORTH -> if (c.positionMm in 0.0..ob.width) Triple(Vec2(ob.minX + c.positionMm, ob.maxY), Vec2(0.0, 1.0), Vec2(1.0, 0.0)) else null
                CutoutSide.SOUTH -> if (c.positionMm in 0.0..ob.width) Triple(Vec2(ob.minX + c.positionMm, ob.minY), Vec2(0.0, -1.0), Vec2(1.0, 0.0)) else null
                CutoutSide.EAST -> if (c.positionMm in 0.0..ob.height) Triple(Vec2(ob.maxX, ob.minY + c.positionMm), Vec2(1.0, 0.0), Vec2(0.0, 1.0)) else null
                CutoutSide.WEST -> if (c.positionMm in 0.0..ob.height) Triple(Vec2(ob.minX, ob.minY + c.positionMm), Vec2(-1.0, 0.0), Vec2(0.0, 1.0)) else null
                CutoutSide.EDGE -> {
                    val a = obj.points[c.edgeIndex]
                    val b = obj.points[(c.edgeIndex + 1) % obj.points.size]
                    val len = a.distanceTo(b)
                    if (len < 1e-6 || c.positionMm !in 0.0..len) {
                        null
                    } else {
                        val u = (b - a) * (1.0 / len)
                        Triple(a + u * c.positionMm, Vec2(u.y, -u.x), u)
                    }
                }
            }
            if (frame == null) {
                warnings.add("$label pominięte: pozycja leży poza krawędzią")
                return@forEachIndexed
            }
            val (p, n, u) = frame
            val far = p + n * (spec.clearanceMm + spec.wallMm + CUT_REACH_MM)
            val deep = p - n * (max(ob.width, ob.height) + CUT_REACH_MM)
            val entry = GeometryOps.rayEntry(cavityShape, far, deep)
            if (entry == null) {
                warnings.add("$label pominięte: nie przecina ścianki obudowy")
                return@forEachIndexed
            }
            val zc = floorTop + c.centerZMm
            val isCircle = c.shape == CutoutShape.CIRCLE
            val halfW = (c.widthMm - (if (isCircle) bHole else bSlot)) / 2.0
            val halfH = if (isCircle) halfW else (c.heightMm - bSlot) / 2.0
            if (halfW <= 0.0 || halfH <= 0.0) {
                warnings.add("$label pominięte: po kompensacji wymiar jest niedodatni")
                return@forEachIndexed
            }
            val lo = zc - halfH
            val hi = zc + halfH
            if (lo < floorTop - EPS) warnings.add("$label sięga podłogi — przycięto do górnej powierzchni podłogi")
            if (hi < floorTop + 0.05 || lo > wallTop - 0.05) {
                warnings.add("$label pominięte: leży poza wysokością ścianki")
                return@forEachIndexed
            }
            if (spec.join == EnclosureJoin.LIP && hi > lipBandBottom + EPS) {
                warnings.add("$label sięga strefy zakładki pokrywy — zakładka może zasłaniać otwór")
            }
            cutters.add(
                Cutter(
                    c.id, entry, u, n, halfW, isCircle, halfW, zc,
                    max(lo, floorTop), min(hi, wallTop), spec.wallMm + CUT_OUTWARD_EXTRA_MM,
                ),
            )
        }

        // --- Dno: warstwy ---
        val zSet = java.util.TreeSet<Double>()
        zSet.add(0.0)
        zSet.add(floorTop)
        zSet.add(wallTop)
        if (standoffH > 0.0) zSet.add(r6(floorTop + standoffH))
        for (c in cutters) {
            zSet.add(r6(c.zLo))
            zSet.add(r6(c.zHi))
            if (c.isCircle) {
                var z = c.zc - c.radius
                while (z < c.zHi) {
                    if (z > c.zLo) zSet.add(r6(z))
                    z += layer
                }
            }
        }
        val pilots = posts.map { Shape2(Shapes.circle(it.center, it.pilotDiameterMm - bHole)) }
        val insidePosts = posts.filter { !it.ear }.map { Shape2(Shapes.circle(it.center, it.postDiameterMm - bOut)) }
        val zs = zSet.toList()
        val basePrisms = ArrayList<Prism>()
        for (k in 0 until zs.size - 1) {
            val za = zs[k]
            val zb = zs[k + 1]
            if (zb - za < MIN_LAYER_MM) continue
            if (zb <= floorTop + EPS) {
                basePrisms.add(Prism(floorShape, za, zb))
                continue
            }
            val zm = (za + zb) / 2.0
            val cut = cutters.mapNotNull { it.shapeAt(zm) }
            val wall = if (cut.isEmpty()) listOf(wallRegion) else GeometryOps.differenceAll(wallRegion, cut)
            val extra = ArrayList<Shape2>(insidePosts)
            if (standoffH > 0.0 && zm < floorTop + standoffH) extra.addAll(standoffShapes)
            val merged = GeometryOps.union(wall + extra)
            for (m in merged) {
                for (s in GeometryOps.differenceAll(m, pilots)) {
                    if (s.area() > MIN_AREA_MM2) basePrisms.add(Prism(s, za, zb))
                }
            }
        }

        // --- Pokrywa (współrzędne dna, z liczone w pozycji do druku: zewnętrzna strona na z = 0) ---
        var lipOuterCad: Ring? = null
        var lipOuterTarget: Ring? = null
        val vents = ArrayList<Ring>()
        run {
            val n0 = spec.ventCount
            if (n0 <= 0) return@run
            val w = spec.ventWidthMm
            val pitch = 2.0 * w
            val len = if (spec.ventLengthMm > 0.0) spec.ventLengthMm else min(30.0, 0.6 * cb.width)
            val avail = cb.height - 2.0 * VENT_MARGIN_MM
            val maxN = if (avail < w) 0 else ((avail - w) / pitch).toInt() + 1
            var n = n0
            if (n > maxN) {
                warnings.add("Szczeliny wentylacyjne nie mieszczą się — zmniejszono ich liczbę do $maxN")
                n = maxN
            }
            val safe: Shape2? = runCatching { Shape2(GeometryOps.offsetRing(cavityTarget, -VENT_MARGIN_MM)) }.getOrNull()
            val center = cb.center
            for (i in 0 until n) {
                val y = center.y + (i - (n - 1) / 2.0) * pitch
                val rw = (w - bSlot) / 2.0
                val rl = (len - bSlot) / 2.0
                if (rw <= 0.0 || rl <= 0.0) continue
                val slot = Shapes.rect(center.x - rl, y - rw, center.x + rl, y + rw)
                val inside = safe != null && GeometryOps.contains(safe, Shape2(slot))
                val clashPost = posts.any {
                    GeometryOps.distance(Shape2(slot), Shape2(Shapes.circle(it.center, it.postDiameterMm))) < VENT_POST_MARGIN_MM
                }
                if (inside && !clashPost) {
                    vents.add(slot)
                } else {
                    warnings.add("Pominięto szczelinę wentylacyjną ${i + 1}: nie mieści się nad wnęką")
                }
            }
        }
        val ventShapes = vents.map { Shape2(it) }

        val lidPrismsAssembled = ArrayList<Prism>()
        var lipInnerCad: Ring? = null
        var lidTop = lidT
        var cbDepth = 0.0
        val throughTarget = FitMath.target(CompensationKind.HOLE, FitReference.MATING, FitClass.LOOSE, spec.screwNominalMm)
        val throughCad = FitMath.cad(throughTarget, bHole)
        val cbTarget = 1.9 * spec.screwNominalMm + 0.4
        val cbCad = FitMath.cad(cbTarget, bHole)
        when (spec.join) {
            EnclosureJoin.LIP -> {
                for (s in GeometryOps.differenceAll(floorShape, ventShapes)) {
                    if (s.area() > MIN_AREA_MM2) lidPrismsAssembled.add(Prism(s, 0.0, lidT))
                }
                val lipT = FitMath.target(CompensationKind.OUTLINE, FitReference.MATING, spec.lipFit, cb.width)
                val shrink = (cb.width - lipT) / 2.0
                lipOuterTarget = GeometryOps.offsetRing(cavityTarget, -shrink)
                lipOuterCad = GeometryOps.offsetRing(lipOuterTarget, -bOut / 2.0)
                val lipInnerTarget = GeometryOps.offsetRing(lipOuterTarget, -spec.lipThicknessMm)
                lipInnerCad = GeometryOps.offsetRing(lipInnerTarget, -bSlot / 2.0)
                val ring = Shape2(lipOuterCad, listOf(lipInnerCad)).normalized()
                lidTop = r6(lidT + spec.lipHeightMm)
                lidPrismsAssembled.add(Prism(ring, lidT, lidTop))
            }
            EnclosureJoin.SCREWS -> {
                val throughs = posts.map { Shape2(Shapes.circle(it.center, throughCad)) }
                val cbs = posts.map { Shape2(Shapes.circle(it.center, cbCad)) }
                cbDepth = min(MAX_COUNTERBORE_MM, lidT - MIN_LID_REST_MM)
                if (cbDepth >= MIN_COUNTERBORE_MM && posts.isNotEmpty()) {
                    cbDepth = snapUp(cbDepth, layer).let { if (it > lidT - MIN_LID_REST_MM + EPS) it - layer else it }
                }
                if (cbDepth >= MIN_COUNTERBORE_MM && posts.isNotEmpty()) {
                    for (s in GeometryOps.differenceAll(floorShape, ventShapes + cbs)) {
                        if (s.area() > MIN_AREA_MM2) lidPrismsAssembled.add(Prism(s, 0.0, cbDepth))
                    }
                    for (s in GeometryOps.differenceAll(floorShape, ventShapes + throughs)) {
                        if (s.area() > MIN_AREA_MM2) lidPrismsAssembled.add(Prism(s, cbDepth, lidT))
                    }
                } else {
                    cbDepth = 0.0
                    for (s in GeometryOps.differenceAll(floorShape, ventShapes + throughs)) {
                        if (s.area() > MIN_AREA_MM2) lidPrismsAssembled.add(Prism(s, 0.0, lidT))
                    }
                }
            }
        }

        // --- Plaster próbny: przytnij do narożnika ---
        var basePr: List<Prism> = basePrisms
        var lidPr: List<Prism> = lidPrismsAssembled
        if (fitTest) {
            val ob2 = outerCad.bounds()
            val box = Shape2(
                Shapes.rect(
                    ob2.minX, ob2.minY,
                    ob2.minX + FIT_TEST_SIZE_MM, ob2.minY + FIT_TEST_SIZE_MM,
                ),
            )
            val zMax = min(
                wallTop,
                floorTop + if (spec.join == EnclosureJoin.LIP) spec.lipHeightMm + 1.0 else 4.0,
            )
            basePr = clip(basePrisms, box, zMax)
            lidPr = clip(lidPrismsAssembled, box, Double.MAX_VALUE)
        }

        // --- Orientacja pokrywy do druku (lustro względem osi X = obrót o 180° wokół X) i stopa słonia ---
        val lidPrinted = lidPr.map { Prism(mirrorY(it.shape), it.z0, it.z1) }
        val (base0, baseWarn) = ElephantFoot.applyWithWarnings(Solid(basePr), print)
        val (lid0, lidWarn) = ElephantFoot.applyWithWarnings(Solid(lidPrinted), print)
        (baseWarn + lidWarn).distinct().forEach { warnings.add(it) }

        // --- Układ na stole: dno i pokrywa obok siebie ---
        val bb = boundsOf(base0)
        val lb = boundsOf(lid0)
        val base = moved(base0, Vec2(-bb.minX, -bb.minY))
        val lid = moved(lid0, Vec2(bb.width + PLATE_GAP_MM - lb.minX, (bb.height - lb.height) / 2.0 - lb.minY))
        val baseSize = Vec3(bb.width, bb.height, base.prisms.maxOf { it.z1 })
        val lidSize = Vec3(lb.width, lb.height, lid.prisms.maxOf { it.z1 })

        // --- Pole druku ---
        machine.buildVolumeMm?.let { v ->
            fun fits(s: Vec3) = s.z <= v.z + EPS && ((s.x <= v.x + EPS && s.y <= v.y + EPS) || (s.y <= v.x + EPS && s.x <= v.y + EPS))
            val vol = "${fmt(v.x)} × ${fmt(v.y)} × ${fmt(v.z)} mm — wartość niezweryfikowana"
            if (!fits(baseSize)) {
                warnings.add("Dno (${fmt(baseSize.x)} × ${fmt(baseSize.y)} × ${fmt(baseSize.z)} mm) przekracza pole druku ($vol)")
            }
            if (!fits(lidSize)) {
                warnings.add("Pokrywa (${fmt(lidSize.x)} × ${fmt(lidSize.y)} mm) przekracza pole druku ($vol)")
            }
        }

        // --- Cechy do kompensacji ---
        val features = ArrayList<ResolvedFeature>()
        fun feat(
            id: String,
            label: String,
            kind: CompensationKind,
            reference: FitReference,
            fit: FitClass?,
            nominal: Double,
            target: Double,
            bias: Double,
            cad: Double,
        ) = features.add(ResolvedFeature(id, label, kind, reference, fit, nominal, target, bias, cad))
        feat("cavityX", "Wnęka (szerokość X)", CompensationKind.SLOT, FitReference.ORIGINAL, null, cb.width, cb.width, bSlot, cavityCad.bounds().width)
        feat("cavityY", "Wnęka (szerokość Y)", CompensationKind.SLOT, FitReference.ORIGINAL, null, cb.height, cb.height, bSlot, cavityCad.bounds().height)
        val ot = outerTarget.bounds()
        feat("outerX", "Obudowa (zewn. X)", CompensationKind.OUTLINE, FitReference.ORIGINAL, null, ot.width, ot.width, bOut, outerPlainCad.bounds().width)
        if (spec.join == EnclosureJoin.LIP && lipOuterCad != null) {
            val lipT = FitMath.target(CompensationKind.OUTLINE, FitReference.MATING, spec.lipFit, cb.width)
            feat("lip", "Zakładka pokrywy (X)", CompensationKind.OUTLINE, FitReference.MATING, spec.lipFit, cb.width, lipT, bOut, lipOuterCad.bounds().width)
        }
        if (spec.join == EnclosureJoin.SCREWS) {
            feat("pilot", "Otwór w słupku Ø", CompensationKind.HOLE, FitReference.ORIGINAL, null, spec.screwPilotMm, spec.screwPilotMm, bHole, spec.screwPilotMm - bHole)
            feat("lidHole", "Otwór w pokrywie Ø", CompensationKind.HOLE, FitReference.MATING, FitClass.LOOSE, spec.screwNominalMm, throughTarget, bHole, throughCad)
        }
        for (c in cutters) {
            val src = spec.cutouts.first { it.id == c.id }
            val nominal = src.widthMm
            val bias = if (c.isCircle) bHole else bSlot
            feat(
                "cut-${c.id}", "Wycięcie ${c.id} (szerokość)",
                if (c.isCircle) CompensationKind.HOLE else CompensationKind.SLOT,
                FitReference.ORIGINAL, null, nominal, nominal, bias, nominal - bias,
            )
        }

        val geometry = EnclosureGeometry(
            objectOutline = obj,
            cavity = cavityTarget,
            outer = outerTarget.let { if (earRings.isEmpty()) it else union1(listOf(it) + posts.filter { p -> p.ear }.map { p -> Shapes.circle(p.center, p.postDiameterMm) }) },
            posts = posts,
            cutoutBoxes = cutters.map { it.planBox() },
            lipOuter = lipOuterTarget,
            vents = vents,
            standoffs = standoffs,
        )
        val result = EnclosureResult(base, lid, features, warnings.distinct(), geometry, cavityH, baseSize, lidSize)
        if (!checkMesh) return result
        val mesh = meshWarnings(result)
        return if (mesh.isEmpty()) result else result.copy(warnings = result.warnings + mesh)
    }
}
