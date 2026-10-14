package com.fitlab.core.mesh

import com.fitlab.core.geom.Vec3
import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TransformTest {
    @Test
    fun fromAxesMapsAxes() {
        val t = Transform3.fromAxes(Vec3(0.0, 1.0, 0.0), Vec3(0.0, 0.0, 1.0), Vec3(1.0, 0.0, 0.0))
        val p = t.apply(Vec3(1.0, 0.0, 0.0))
        assertEquals(Vec3(0.0, 1.0, 0.0), p)
        assertClose(1.0, t.determinant())
    }

    @Test
    fun translationAndIdentity() {
        val t = Transform3.translation(Vec3(1.0, 2.0, 3.0))
        assertEquals(Vec3(2.0, 3.0, 4.0), t.apply(Vec3(1.0, 1.0, 1.0)))
        assertEquals(Vec3(1.0, 1.0, 1.0), Transform3.IDENTITY.apply(Vec3(1.0, 1.0, 1.0)))
    }
}
