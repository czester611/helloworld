package com.fitlab.core.geom

import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class CircleFitTest {
    private fun pts(noise: Boolean) = List(36) { k ->
        val th = 2 * PI * k / 36
        val r = 7.0 + if (noise) 0.05 * sin(17.0 * k) else 0.0
        Vec2(3.0 + r * cos(th), -2.0 + r * sin(th))
    }

    @Test
    fun exactCircle() {
        val c = CircleFit.kasa(pts(false))
        assertClose(3.0, c.center.x)
        assertClose(-2.0, c.center.y)
        assertClose(7.0, c.radius)
    }

    @Test
    fun noisyCircle() {
        val c = CircleFit.kasa(pts(true))
        assertClose(7.0, c.radius, 0.03)
        assertClose(3.0, c.center.x, 0.03)
    }
}
