package com.fitlab.core.geom

import kotlinx.serialization.Serializable
import kotlin.math.abs

/** Kształt 2D: obrys zewnętrzny i dziury (mm). Obrys CCW, dziury CW po [normalized]. */
@Serializable
data class Shape2(val outer: Ring, val holes: List<Ring> = emptyList()) {
    /** Kształt z obrysem CCW i dziurami CW. */
    fun normalized(): Shape2 = Shape2(
        if (outer.isCcw()) outer else outer.reversed(),
        holes.map { if (it.isCcw()) it.reversed() else it },
    )

    /** Pole = |obrys| − suma |dziur| w mm². */
    fun area(): Double = abs(outer.signedArea()) - holes.sumOf { abs(it.signedArea()) }

    /** Prostokąt otaczający obrysu zewnętrznego. */
    fun bounds(): Bounds2 = outer.bounds()

    /** Kształt przesunięty o [d]. */
    fun translated(d: Vec2): Shape2 = Shape2(outer.translated(d), holes.map { it.translated(d) })
}
