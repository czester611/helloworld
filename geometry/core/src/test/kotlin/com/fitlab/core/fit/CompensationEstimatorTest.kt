package com.fitlab.core.fit

import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Test

class CompensationEstimatorTest {
    private fun s(vararg v: Double) = v.mapIndexed { i, b -> BiasSample(b, 1000L - i) } // najnowsza pierwsza

    @Test
    fun noSamplesGivesPrior() = assertClose(-0.25, CompensationEstimator.estimate(-0.25, emptyList()))

    @Test
    fun oneSample() = assertClose(-0.383333, CompensationEstimator.estimate(-0.25, s(-0.45)), 1e-6)

    @Test
    fun threeSamples() = assertClose(-0.364312, CompensationEstimator.estimate(-0.25, s(-0.40, -0.30, -0.50)), 1e-6)

    @Test
    fun outlierRejected() = assertClose(-0.307361, CompensationEstimator.estimate(-0.25, s(-0.30, -0.35, 0.40, -0.32)), 1e-6)

    @Test
    fun twoSamplesNoFiltering() = assertClose(0.222727, CompensationEstimator.estimate(0.10, s(0.30, 0.20)), 1e-6)

    @Test
    fun clamped() = assertClose(1.0, CompensationEstimator.estimate(0.0, s(5.0)), 1e-9)

    @Test
    fun orderIndependent() {
        val shuffled = listOf(BiasSample(-0.50, 998), BiasSample(-0.40, 1000), BiasSample(-0.30, 999))
        assertClose(-0.364312, CompensationEstimator.estimate(-0.25, shuffled), 1e-6)
    }

    @Test
    fun onlyTenNewestUsed() {
        val many = (0 until 15).map { BiasSample(if (it < 10) -0.3 else 0.5, 1000L - it) }
        val r = CompensationEstimator.estimate(0.0, many)
        org.junit.jupiter.api.Assertions.assertTrue(r in -0.31..-0.2, "r=$r")
    }
}
