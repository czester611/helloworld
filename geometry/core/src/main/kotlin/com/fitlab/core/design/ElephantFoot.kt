package com.fitlab.core.design

import com.fitlab.core.geom.GeometryException
import com.fitlab.core.geom.GeometryOps
import com.fitlab.core.geom.Shape2
import com.fitlab.core.mesh.Prism
import com.fitlab.core.mesh.Solid
import com.fitlab.core.mesh.Transform3

/** Kompensacja stopy słonia: wcięcie pierwszej warstwy graniastosłupów leżących na z = 0. */
object ElephantFoot {
    /** Ostrzeżenie, gdy kompensacja została pominięta. */
    const val SKIPPED_WARNING = "Pominięto kompensację stopy słonia (zbyt cienkie ścianki)"
    private const val MIN_LAYER_MM = 0.05
    private const val MIN_AREA_RATIO = 0.5

    /**
     * Wcięcie [shape] o [footMm] (zmniejsza obrys, powiększa otwory) albo null, gdy wynik jest pusty,
     * rozpada się lub ma pole < 50% oryginału.
     */
    fun inset(shape: Shape2, footMm: Double): Shape2? {
        if (footMm <= 0.0) return shape
        val r = try {
            GeometryOps.offset(shape, -footMm, GeometryOps.JoinStyle.MITRE)
        } catch (e: GeometryException) {
            return null
        }
        if (r.area() < MIN_AREA_RATIO * shape.area()) return null
        return r
    }

    /** Bryła z wciętą pierwszą warstwą; [apply] bez ostrzeżeń. */
    fun apply(solid: Solid, print: PrintSettings): Solid = applyWithWarnings(solid, print).first

    /** Jak [apply], ale zwraca też ostrzeżenia. */
    fun applyWithWarnings(solid: Solid, print: PrintSettings): Pair<Solid, List<String>> {
        val ef = print.elephantFootMm
        if (ef <= 0.0) return solid to emptyList()
        val h = print.layerHeightMm
        val out = ArrayList<Prism>()
        var warned = false
        for (p in solid.prisms) {
            if (p.z0 != 0.0 || p.transform != Transform3.IDENTITY) {
                out.add(p)
                continue
            }
            val inset = inset(p.shape, ef)
            if (inset == null) {
                warned = true
                out.add(p)
            } else if (p.z1 - h < MIN_LAYER_MM) {
                out.add(Prism(inset, p.z0, p.z1))
            } else {
                out.add(Prism(inset, 0.0, h))
                out.add(Prism(p.shape, h, p.z1))
            }
        }
        return Solid(out) to (if (warned) listOf(SKIPPED_WARNING) else emptyList())
    }
}
