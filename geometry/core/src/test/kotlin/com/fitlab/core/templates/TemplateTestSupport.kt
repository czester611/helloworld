package com.fitlab.core.templates

import com.fitlab.core.design.BuildResult
import com.fitlab.core.mesh.MeshBuilder
import com.fitlab.core.mesh.MeshValidator
import org.junit.jupiter.api.Assertions.assertTrue

object TemplateTestSupport {
    fun assertClosed(r: BuildResult) {
        val rep = MeshValidator.check(MeshBuilder.build(r.solid))
        assertTrue(rep.isClosed, "not closed")
        assertTrue(rep.isConsistentlyOriented, "bad orientation")
        assertTrue(rep.volumeMm3 > 0, "volume ${rep.volumeMm3}")
    }

    fun input(id: TemplateId, vararg over: Pair<String, Double>, fits: Map<String, com.fitlab.core.fit.FitClass> = emptyMap()): TemplateInput {
        val d = TemplateRegistry.defaultInput(id)
        return TemplateInput(d.values + over.toMap(), d.fits + fits)
    }
}
