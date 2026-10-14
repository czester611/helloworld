package com.fitlab.core.design

import com.fitlab.core.fit.CompensationKind
import com.fitlab.core.fit.CompensationTable
import com.fitlab.core.fit.FitClass
import com.fitlab.core.fit.FitReference
import com.fitlab.core.fit.ResolvedFeature
import com.fitlab.core.geom.Shapes
import com.fitlab.core.geom.Vec2
import com.fitlab.core.templates.TemplateId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignJsonTest {
    @Test
    fun outlineRoundTrip() {
        val d: PartDesign = OutlineDesign(
            Shapes.rect(-10.0, -10.0, 10.0, 10.0),
            innerContours = listOf(Shapes.rect(-1.0, -1.0, 1.0, 1.0).reversed()),
            holes = listOf(HoleFeature("hole-1", Vec2(1.0, 2.0), 5.0, FitReference.MATING, FitClass.LOOSE, 0.4, true)),
            thicknessMm = 4.0,
            topChamferMm = 0.4,
        )
        val s = DesignJson.encode(d)
        assertTrue(s.contains("\"type\":\"outline\""), s)
        assertEquals(d, DesignJson.decode(s))
    }

    @Test
    fun templateRoundTrip() {
        val d: PartDesign = TemplateDesign(TemplateId.BUSHING, mapOf("shaftDiameter" to 8.0), mapOf("innerFit" to FitClass.SLIDE))
        val s = DesignJson.encode(d)
        assertTrue(s.contains("\"type\":\"template\""), s)
        assertEquals(d, DesignJson.decode(s))
    }

    @Test
    fun tableAndFeaturesRoundTrip() {
        val t = CompensationTable(mapOf(CompensationKind.HOLE to -0.3, CompensationKind.PIN to 0.1))
        assertEquals(t, DesignJson.decodeCompensation(DesignJson.encodeCompensation(t)))
        val f = listOf(ResolvedFeature("hole-1", "Otwór", CompensationKind.HOLE, FitReference.ORIGINAL, null, 5.0, 5.0, -0.3, 5.3))
        assertEquals(f, DesignJson.decodeFeatures(DesignJson.encodeFeatures(f)))
    }
}
