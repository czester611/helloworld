package com.fitlab.core.mesh

import com.fitlab.core.geom.Shape2

/** Graniastosłup: kształt 2D wyciągnięty między z0 a z1 (mm), opcjonalnie przekształcony. */
data class Prism(
    val shape: Shape2,
    val z0: Double,
    val z1: Double,
    val transform: Transform3 = Transform3.IDENTITY,
) {
    init {
        require(z1 > z0) { "z1 musi być większe niż z0" }
    }
}
