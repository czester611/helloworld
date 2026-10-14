package com.fitlab.core.fit

/** Zamiana oceny użytkownika na bezwzględną próbkę biasu. */
object FeedbackInterpreter {
    /**
     * Próbka biasu (mm, średnicowo) z oceny. [biasUsed] to bias z migawki iteracji, [cadMm] wymiar CAD cechy
     * (wymagany dla [Feedback.Measured]: próbka = zmierzone − CAD).
     */
    fun sample(kind: CompensationKind, biasUsed: Double, feedback: Feedback, cadMm: Double? = null): Double =
        when (feedback) {
            is Feedback.Measured -> feedback.measuredMm - requireNotNull(cadMm) { "Pomiar wymaga wymiaru CAD" }
            is Feedback.Fit -> {
                val a = feedback.amountMm
                val sign = when (feedback.verdict) {
                    Verdict.TOO_TIGHT -> -1.0
                    Verdict.TOO_LOOSE -> 1.0
                    else -> 0.0
                }
                // Wewnętrzne: ciasno => bias − a, luźno => bias + a. Zewnętrzne: odwrotnie.
                biasUsed + (if (kind.isInternal) sign else -sign) * a
            }
        }
}
