package com.fitlab.core.fit

import kotlinx.serialization.Serializable

/** Klasa pasowania: luz średnicowy w mm (wcisk, suwliwe, luźne). */
@Serializable
enum class FitClass(val clearanceMm: Double) {
    PRESS(0.00),
    SLIDE(0.15),
    LOOSE(0.40),
}
