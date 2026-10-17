package com.fitlab.core.enclosure

import com.fitlab.core.design.BuildResult
import com.fitlab.core.export.NamedSolid
import com.fitlab.core.fit.ResolvedFeature
import com.fitlab.core.geom.Ring
import com.fitlab.core.geom.Vec2
import com.fitlab.core.geom.Vec3
import com.fitlab.core.mesh.Solid

/** Ograniczenia maszyny używane do ostrzeżeń: średnica dyszy i pole druku (mm, wartość pola niezweryfikowana). */
data class MachineLimits(
    val nozzleMm: Double = 0.4,
    val buildVolumeMm: Vec3? = DEFAULT_BUILD_VOLUME_MM,
) {
    companion object {
        /** Snapmaker U1 (ok. 270×270×270 mm) — wartość z pamięci użytkownika, DO WERYFIKACJI. */
        val DEFAULT_BUILD_VOLUME_MM = Vec3(270.0, 270.0, 270.0)
    }
}

/** Słupek na wkręt: środek, średnica zewnętrzna i otworu; [ear] = wyprowadzony poza ściankę (ucho). */
data class PostPlan(
    val label: String,
    val center: Vec2,
    val postDiameterMm: Double,
    val pilotDiameterMm: Double,
    val ear: Boolean,
)

/** Podpórka obiektu w dnie. */
data class StandoffPlan(val center: Vec2, val diameterMm: Double)

/**
 * Geometria 2D (rzut z góry, mm, układ dna) do podglądu: obrys obiektu, wnęka (cel), obrys zewnętrzny, słupki,
 * pola wycięć, zakładka, szczeliny wentylacyjne i podpórki.
 */
data class EnclosureGeometry(
    val objectOutline: Ring,
    val cavity: Ring,
    val outer: Ring,
    val posts: List<PostPlan>,
    val cutoutBoxes: List<Ring>,
    val lipOuter: Ring?,
    val vents: List<Ring>,
    val standoffs: List<StandoffPlan>,
)

/**
 * Wynik budowy obudowy. [base] i [lid] leżą obok siebie na jednym stole (pokrywa w pozycji do druku: płaska,
 * zewnętrzna strona na stole, zakładka do góry); [warnings] po polsku.
 */
data class EnclosureResult(
    val base: Solid,
    val lid: Solid,
    val features: List<ResolvedFeature>,
    val warnings: List<String>,
    val geometry: EnclosureGeometry,
    val cavityHeightMm: Double,
    val baseSizeMm: Vec3,
    val lidSizeMm: Vec3,
) {
    /** Obie części jako jedna bryła (do podglądu i walidacji). */
    val plate: Solid get() = Solid(base.prisms + lid.prisms)

    /** Części do eksportu: „base” i „lid”. */
    val parts: List<NamedSolid> get() = listOf(NamedSolid("base", base), NamedSolid("lid", lid))

    /** Zgodność z wspólnym potokiem [BuildResult] (bryła = obie części na stole). */
    fun toBuildResult(): BuildResult = BuildResult(plate, features, warnings)
}
