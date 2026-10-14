package com.fitlab.vision

import com.fitlab.core.sheet.MarkerSheetSpec
import com.fitlab.vision.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MarkerSheetRendererTest : OpenCvTestBase() {
    @Test
    fun rendersAndDetectsFourMarkers() {
        val img = MarkerSheetRenderer.render()
        try {
            assertEquals(2520, img.cols())
            assertEquals(3564, img.rows())
            val d = MarkerDetector.detect(img)
            assertEquals(setOf(0, 1, 2, 3), d.corners.keys)
            val spec = MarkerSheetSpec()
            for (id in 0..3) {
                val exp = spec.cornersMm(id)
                for (k in 0 until 4) {
                    assertClose(exp[k].x * 12 - 0.5, d.corners.getValue(id)[k].x, 1.0, "id$id x$k")
                    assertClose(exp[k].y * 12 - 0.5, d.corners.getValue(id)[k].y, 1.0, "id$id y$k")
                }
            }
        } finally {
            img.release()
        }
    }
}
