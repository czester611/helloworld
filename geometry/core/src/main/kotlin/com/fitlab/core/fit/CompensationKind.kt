package com.fitlab.core.fit

import kotlinx.serialization.Serializable

/**
 * Typ cechy do kompensacji.
 * HOLE = otwór okrągły, SLOT = wewnętrzna cecha dowolna (szczelina, wycięcie),
 * PIN = czop/wałek, OUTLINE = obrys zewnętrzny lub wymiar liniowy zewnętrzny.
 */
@Serializable
enum class CompensationKind(val isInternal: Boolean) {
    HOLE(true),
    SLOT(true),
    PIN(false),
    OUTLINE(false),
}
