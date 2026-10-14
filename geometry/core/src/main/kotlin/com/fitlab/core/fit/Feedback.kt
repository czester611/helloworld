package com.fitlab.core.fit

/** Informacja zwrotna o cesze po wydruku próbnym. */
sealed interface Feedback {
    /** Ocena jakościowa [verdict] (TOO_TIGHT, OK, TOO_LOOSE) z kwotą średnicową [amountMm] >= 0. */
    data class Fit(val verdict: Verdict, val amountMm: Double) : Feedback

    /** Pomiar suwmiarką wydrukowanego wymiaru. */
    data class Measured(val measuredMm: Double) : Feedback
}
