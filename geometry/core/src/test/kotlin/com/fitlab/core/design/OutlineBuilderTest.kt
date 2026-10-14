package com.fitlab.core.design

import com.fitlab.core.fit.CompensationKind
import com.fitlab.core.fit.CompensationTable
import com.fitlab.core.geom.Ring
import com.fitlab.core.geom.Shapes
import com.fitlab.core.geom.Vec2
import com.fitlab.core.mesh.MeshBuilder
import com.fitlab.core.mesh.MeshValidator
import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OutlineBuilderTest {
    private val square = Shapes.rect(-10.0, -10.0, 10.0, 10.0)
    private fun design(t: Double = 5.0, hole: HoleFeature? = HoleFeature("hole-1", Vec2(0.0, 0.0), 10.0), top: Double = 0.0) =
        OutlineDesign(square, holes = listOfNotNull(hole), thicknessMm = t, topChamferMm = top)

    private val comp = CompensationTable(mapOf(CompensationKind.OUTLINE to 0.2, CompensationKind.HOLE to -0.3))
    private val noFoot = PrintSettings(0.2, 0.0)

    @Test
    fun designExample() {
        val r = OutlineBuilder.build(design(), comp, noFoot)
        assertEquals(1, r.solid.prisms.size)
        val p = r.solid.prisms[0]
        assertClose(0.0, p.z0)
        assertClose(5.0, p.z1)
        assertClose(19.8, p.shape.bounds().width, 1e-6)
        assertClose(19.8, p.shape.bounds().height, 1e-6)
        for (pt in p.shape.holes[0].points) assertClose(5.15, pt.length(), 1e-6)
        val rep = MeshValidator.check(MeshBuilder.build(r.solid))
        assertTrue(rep.isClosed && rep.isConsistentlyOriented)
        assertClose(1543.6, rep.volumeMm3, 1.5)
        assertClose(19.8, r.features.first { it.id == "outline" }.cadMm, 1e-6)
        assertClose(10.3, r.features.first { it.id == "hole-1" }.cadMm, 1e-6)
    }

    @Test
    fun elephantFootSplitsFirstLayer() {
        val r = OutlineBuilder.build(design(), comp, PrintSettings(0.2, 0.2))
        assertEquals(2, r.solid.prisms.size)
        assertClose(19.4, r.solid.prisms[0].shape.bounds().width, 1e-6)
        assertClose(19.8, r.solid.prisms[1].shape.bounds().width, 1e-6)
        assertClose(0.2, r.solid.prisms[0].z1, 1e-9)
        assertClose(0.2, r.solid.prisms[1].z0, 1e-9)
    }

    @Test
    fun holeChamferSteps() {
        val hole = HoleFeature("hole-1", Vec2(0.0, 0.0), 5.0, chamferMm = 0.6)
        val big = OutlineDesign(Shapes.rect(-20.0, -20.0, 20.0, 20.0), holes = listOf(hole), thicknessMm = 3.0)
        val r = OutlineBuilder.build(big, CompensationTable.ZERO, noFoot)
        val zs = (r.solid.prisms.map { it.z0 } + r.solid.prisms.last().z1)
        val expected = listOf(0.0, 2.4, 2.6, 2.8, 3.0)
        assertEquals(expected.size, zs.size)
        expected.forEachIndexed { i, e -> assertClose(e, zs[i], 1e-9) }
        // średnice od góry
        val dias = r.solid.prisms.reversed().map { p -> p.shape.holes[0].bounds().width }
        listOf(6.2, 5.8, 5.4, 5.0).forEachIndexed { i, e -> assertClose(e, dias[i], 0.01) }
    }

    @Test
    fun topChamferInsetsOuter() {
        val r = OutlineBuilder.build(design(hole = null, top = 0.4), CompensationTable.ZERO, noFoot)
        val top = r.solid.prisms.last()
        assertClose(20.0 - 0.8, top.shape.bounds().width, 1e-6)
        assertClose(20.0, r.solid.prisms.first().shape.bounds().width, 1e-6)
    }

    @Test
    fun fitTestHeights() {
        assertClose(3.0, OutlineBuilder.buildFitTest(design(8.0), comp, noFoot).solid.prisms.last().z1)
        assertClose(2.0, OutlineBuilder.buildFitTest(design(2.0), comp, noFoot).solid.prisms.last().z1)
    }

    @Test
    fun innerContourBecomesSlotFeature() {
        val inner = Shapes.rect(-3.0, -2.0, 3.0, 2.0).reversed()
        val d = OutlineDesign(square, innerContours = listOf(inner), thicknessMm = 4.0)
        val c = CompensationTable(mapOf(CompensationKind.SLOT to -0.2))
        val r = OutlineBuilder.build(d, c, noFoot)
        val f = r.features.first { it.id == "slot-1" }
        assertClose(6.2, f.cadMm, 1e-6)
        assertClose(6.2, r.solid.prisms[0].shape.holes[0].bounds().width, 1e-6)
    }

    @Test
    fun validation() {
        val outside = HoleFeature("hole-1", Vec2(9.0, 0.0), 10.0)
        assertThrows(IllegalArgumentException::class.java) { OutlineBuilder.build(design(hole = outside), comp, noFoot) }
        assertThrows(IllegalArgumentException::class.java) { OutlineBuilder.build(design(t = 0.2), comp, noFoot) }
        val tiny = HoleFeature("hole-1", Vec2(0.0, 0.0), 0.5)
        assertTrue(OutlineBuilder.validate(design(hole = tiny), CompensationTable.ZERO).isNotEmpty())
        val a = HoleFeature("hole-1", Vec2(-3.0, 0.0), 4.0)
        val b = HoleFeature("hole-2", Vec2(-1.0, 0.0), 4.0)
        val two = OutlineDesign(square, holes = listOf(a, b), thicknessMm = 3.0)
        assertTrue(OutlineBuilder.validate(two, CompensationTable.ZERO).any { it.contains("nachodzą") })
    }
}
