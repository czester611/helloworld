package com.fitlab.core.fit

import kotlin.math.abs
import kotlin.math.pow

/** Uczenie kompensacji: średnia ważona próbek z priorem i odrzucaniem odstających. */
object CompensationEstimator {
    /** Waga priora. */
    const val PRIOR_WEIGHT = 0.5

    /** Współczynnik zaniku wagi starszych próbek. */
    const val DECAY = 0.7

    /** Maksymalna liczba uwzględnianych próbek. */
    const val MAX_SAMPLES = 10

    /** Próg odrzucania odstających od mediany, mm. */
    const val OUTLIER_MM = 0.3

    /** Obcięcie wyniku do [−CLAMP_MM, CLAMP_MM], mm. */
    const val CLAMP_MM = 1.0

    /** Szacuje bias z [prior] i [samples] (dowolna kolejność wejściowa). Brak próbek zwraca [prior]. */
    fun estimate(prior: Double, samples: List<BiasSample>): Double {
        if (samples.isEmpty()) return prior
        var s = samples.sortedByDescending { it.createdAtMillis }.take(MAX_SAMPLES).map { it.biasMm }
        if (s.size >= 3) {
            val sorted = s.sorted()
            val n = sorted.size
            val med = if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
            s = s.filter { abs(it - med) <= OUTLIER_MM }
        }
        var wSum = 0.0
        var wsSum = 0.0
        s.forEachIndexed { i, v ->
            val w = DECAY.pow(i)
            wSum += w
            wsSum += w * v
        }
        val bias = (PRIOR_WEIGHT * prior + wsSum) / (PRIOR_WEIGHT + wSum)
        return bias.coerceIn(-CLAMP_MM, CLAMP_MM)
    }
}
