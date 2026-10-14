package com.fitlab.core.design

import kotlinx.serialization.Serializable

/** Ustawienia druku wpływające na geometrię: wysokość warstwy i wcięcie pierwszej warstwy (mm). */
@Serializable
data class PrintSettings(val layerHeightMm: Double = 0.2, val elephantFootMm: Double = 0.2) {
    init {
        require(layerHeightMm > 0.0) { "Wysokość warstwy musi być dodatnia" }
    }
}
