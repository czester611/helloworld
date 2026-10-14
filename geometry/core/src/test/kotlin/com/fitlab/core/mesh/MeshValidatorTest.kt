package com.fitlab.core.mesh

import com.fitlab.core.geom.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class MeshValidatorTest {
    @Test
    fun openMeshIsNotClosed() {
        val m = Mesh(listOf(Vec3(0.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0)), listOf(Tri(0, 1, 2)))
        assertFalse(MeshValidator.check(m).isClosed)
    }

    @Test
    fun degenerateTriangleCounted() {
        val m = Mesh(listOf(Vec3(0.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0), Vec3(2.0, 0.0, 0.0)), listOf(Tri(0, 1, 2)))
        assertEquals(1, MeshValidator.check(m).degenerateTriangles)
    }

    @Test
    fun flippedTriangleBreaksOrientation() {
        val cube = MeshBuilder.build(
            Solid(listOf(Prism(com.fitlab.core.geom.Shape2(com.fitlab.core.geom.Shapes.rect(0.0, 0.0, 1.0, 1.0)), 0.0, 1.0))),
        )
        val bad = cube.copy(triangles = cube.triangles.toMutableList().also { it[0] = Tri(it[0].a, it[0].c, it[0].b) })
        assertFalse(MeshValidator.check(bad).isConsistentlyOriented)
    }
}
