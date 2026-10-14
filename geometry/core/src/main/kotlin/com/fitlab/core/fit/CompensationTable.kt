package com.fitlab.core.fit

import kotlinx.serialization.Serializable

/** Bieżące wartości biasu (średnicowego, mm) dla każdego typu cechy. */
@Serializable
data class CompensationTable(val biasMm: Map<CompensationKind, Double>) {
    /** Bias dla [kind]; 0 gdy brak wpisu. */
    fun bias(kind: CompensationKind): Double = biasMm[kind] ?: 0.0

    companion object {
        val ZERO = CompensationTable(CompensationKind.entries.associateWith { 0.0 })
    }
}
