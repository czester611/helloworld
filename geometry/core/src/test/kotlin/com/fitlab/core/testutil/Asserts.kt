package com.fitlab.core.testutil

import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.math.abs

/** Porównanie liczb z tolerancją. */
fun assertClose(expected: Double, actual: Double, tol: Double = 1e-6, message: String = "") {
    assertTrue(abs(expected - actual) <= tol, "$message expected=$expected actual=$actual tol=$tol")
}
