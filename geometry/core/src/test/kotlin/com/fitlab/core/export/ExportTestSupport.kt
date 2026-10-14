package com.fitlab.core.export

import com.fitlab.core.geom.Shape2
import com.fitlab.core.geom.Shapes
import com.fitlab.core.mesh.Prism
import com.fitlab.core.mesh.Solid

object ExportTestSupport {
    fun cube(): Solid = Solid(listOf(Prism(Shape2(Shapes.rect(0.0, 0.0, 10.0, 10.0)), 0.0, 5.0)))
}
