package com.fitlab.core.fit

import kotlinx.serialization.Serializable

/** Cecha części po przeliczeniu: nominał, cel, użyta kompensacja i wymiar CAD (mm). */
@Serializable
data class ResolvedFeature(
    val id: String,
    val label: String,
    val kind: CompensationKind,
    val reference: FitReference,
    val fit: FitClass?,
    val nominalMm: Double,
    val targetMm: Double,
    val biasMm: Double,
    val cadMm: Double,
)
