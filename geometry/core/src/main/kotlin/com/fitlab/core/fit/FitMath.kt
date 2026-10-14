package com.fitlab.core.fit

/** Wzory na wymiar docelowy i wymiar CAD (wszystko średnicowo, mm). */
object FitMath {
    /**
     * Wymiar docelowy po wydruku. ORIGINAL: nominał. MATING wewnętrzny: nominał + luz; MATING zewnętrzny: nominał − luz.
     * Dla MATING wymagane jest [fit].
     */
    fun target(kind: CompensationKind, reference: FitReference, fit: FitClass?, nominalMm: Double): Double {
        if (reference == FitReference.ORIGINAL) return nominalMm
        val clearance = requireNotNull(fit) { "Pasowanie MATING wymaga klasy pasowania" }.clearanceMm
        return if (kind.isInternal) nominalMm + clearance else nominalMm - clearance
    }

    /** Wymiar w modelu CAD = cel − bias. */
    fun cad(targetMm: Double, biasMm: Double): Double = targetMm - biasMm
}
