package com.fitlab.core.design

import com.fitlab.core.fit.ResolvedFeature
import com.fitlab.core.mesh.Solid

/** Wynik budowy: bryła, cechy z przeliczoną kompensacją i ostrzeżenia (PL). */
data class BuildResult(val solid: Solid, val features: List<ResolvedFeature>, val warnings: List<String>)
