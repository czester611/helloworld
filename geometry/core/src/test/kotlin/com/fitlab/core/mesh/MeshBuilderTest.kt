package com.fitlab.core.mesh

import com.fitlab.core.geom.Shape2
import com.fitlab.core.geom.Shapes
import com.fitlab.core.geom.Vec2
import com.fitlab.core.geom.Vec3
import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MeshBuilderTest {
    private fun square10() = Shape2(Shapes.rect(0.0, 0.0, 10.0, 10.0))

    @Test
    fun cubeHas12TrianglesAndVolume500() {
        val m = MeshBuilder.build(Solid(listOf(Prism(square10(), 0.0, 5.0))))
        assertEquals(12, m.triangles.size)
        val r = MeshValidator.check(m)
        assertTrue(r.isClosed)
        assertTrue(r.isConsistentlyOriented)
        assertClose(500.0, r.volumeMm3, 1e-9)
        assertEquals(0, r.degenerateTriangles)
    }

    @Test
    fun squareWithRoundHole() {
        val hole = Shapes.circle(Vec2(10.0, 10.0), 10.0).reversed()
        val s = Shape2(Shapes.rect(0.0, 0.0, 20.0, 20.0), listOf(hole))
        val m = MeshBuilder.build(Solid(listOf(Prism(s, 0.0, 5.0))))
        val r = MeshValidator.check(m)
        assertTrue(r.isClosed && r.isConsistentlyOriented)
        assertClose((400.0 - s.holes[0].let { kotlin.math.abs(it.signedArea()) }) * 5.0, r.volumeMm3, 1e-6)
    }

    @Test
    fun rotatedPrism() {
        val tr = Transform3.fromAxes(Vec3(0.0, 1.0, 0.0), Vec3(0.0, 0.0, 1.0), Vec3(1.0, 0.0, 0.0))
        val shape = Shape2(Shapes.rect(0.0, 0.0, 20.0, 30.0))
        val m = MeshBuilder.build(Solid(listOf(Prism(shape, 0.0, 3.0, tr))))
        val r = MeshValidator.check(m)
        assertTrue(r.isClosed && r.isConsistentlyOriented)
        assertClose(20.0 * 30.0 * 3.0, r.volumeMm3, 1e-9)
        val (lo, hi) = m.bounds()
        assertClose(3.0, hi.x - lo.x)
        assertClose(20.0, hi.y - lo.y)
        assertClose(30.0, hi.z - lo.z)
    }

    @Test
    fun mirroredTransformKeepsPositiveVolume() {
        val tr = Transform3.fromAxes(Vec3(-1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), Vec3(0.0, 0.0, 1.0))
        assertTrue(tr.determinant() < 0)
        val m = MeshBuilder.build(Solid(listOf(Prism(square10(), 0.0, 5.0, tr))))
        val r = MeshValidator.check(m)
        assertTrue(r.isClosed && r.isConsistentlyOriented)
        assertClose(500.0, r.volumeMm3, 1e-9)
    }

    @Test
    fun normalizedForPrint() {
        val m = MeshBuilder.build(Solid(listOf(Prism(square10(), 2.0, 7.0)))).normalizedForPrint()
        val (lo, hi) = m.bounds()
        assertClose(0.0, lo.z)
        assertClose(0.0, (lo.x + hi.x) / 2)
        assertClose(0.0, (lo.y + hi.y) / 2)
    }

    @Test
    fun shellsAndPlus() {
        val solid = Solid(listOf(Prism(square10(), 0.0, 5.0), Prism(square10(), 5.0, 8.0)))
        assertEquals(2, MeshBuilder.buildShells(solid).size)
        val r = MeshValidator.check(MeshBuilder.build(solid))
        assertTrue(r.isClosed)
        assertClose(800.0, r.volumeMm3, 1e-9)
    }

    @Test
    fun footprintUnionsPrisms() {
        val solid = Solid(listOf(Prism(square10(), 0.0, 5.0), Prism(square10(), 5.0, 8.0)))
        val fp = solid.footprint()
        assertEquals(1, fp.size)
        assertClose(100.0, fp[0].area())
    }
}
