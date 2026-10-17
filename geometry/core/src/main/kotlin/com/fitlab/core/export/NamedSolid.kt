package com.fitlab.core.export

import com.fitlab.core.mesh.Solid

/** Bryła z nazwą części (np. „base”, „lid”) do wspólnego eksportu kilku części. */
data class NamedSolid(val name: String, val solid: Solid)
