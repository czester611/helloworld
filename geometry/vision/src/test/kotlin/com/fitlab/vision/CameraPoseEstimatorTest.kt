package com.fitlab.vision

import com.fitlab.core.geom.Vec2
import com.fitlab.core.sheet.MarkerSheetSpec
import com.fitlab.vision.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point3

class CameraPoseEstimatorTest : OpenCvTestBase() {
    private fun project(rvec: DoubleArray, c: DoubleArray): Map<Int, List<Vec2>> {
        val rv = Mat(3, 1, CvType.CV_64FC1).also { it.put(0, 0, *rvec) }
        val rot = Mat()
        Calib3d.Rodrigues(rv, rot)
        val r = DoubleArray(9)
        rot.get(0, 0, r)
        val t = doubleArrayOf(
            -(r[0] * c[0] + r[1] * c[1] + r[2] * c[2]),
            -(r[3] * c[0] + r[4] * c[1] + r[5] * c[2]),
            -(r[6] * c[0] + r[7] * c[1] + r[8] * c[2]),
        )
        val tv = Mat(3, 1, CvType.CV_64FC1).also { it.put(0, 0, *t) }
        val k = Mat(3, 3, CvType.CV_64FC1).also { it.put(0, 0, 3000.0, 0.0, 2000.0, 0.0, 3000.0, 1500.0, 0.0, 0.0, 1.0) }
        val spec = MarkerSheetSpec()
        val out = LinkedHashMap<Int, List<Vec2>>()
        for (id in 0..3) {
            val obj = MatOfPoint3f(*spec.cornersMm(id).map { Point3(it.x, it.y, 0.0) }.toTypedArray())
            val img = MatOfPoint2f()
            Calib3d.projectPoints(obj, rv, tv, k, MatOfDouble(0.0, 0.0, 0.0, 0.0, 0.0), img)
            out[id] = img.toArray().map { Vec2(it.x, it.y) }
        }
        return out
    }

    // f = 3000 px dla obrazu 4000x3000 (przekątna 5000) odpowiada f35 = 3000/5000*43.2666
    private val f35 = 3000.0 / 5000.0 * CameraPoseEstimator.FULL_FRAME_DIAGONAL_MM

    @Test
    fun straightDown() {
        val m = project(doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(105.0, 148.0, -400.0))
        val p = CameraPoseEstimator.estimate(m, MarkerSheetSpec(), 4000, 3000, f35)
        assertNotNull(p)
        assertClose(400.0, p!!.heightMm, 2.0)
        assertClose(105.0, p.nadirMm.x, 1.0)
        assertClose(148.0, p.nadirMm.y, 1.0)
    }

    @Test
    fun tilted() {
        val m = project(doubleArrayOf(0.1, 0.05, 0.0), doubleArrayOf(105.0, 148.0, -400.0))
        val p = CameraPoseEstimator.estimate(m, MarkerSheetSpec(), 4000, 3000, f35)
        assertNotNull(p)
        assertClose(400.0, p!!.heightMm, 2.0)
        assertClose(105.0, p.nadirMm.x, 1.0)
        assertClose(148.0, p.nadirMm.y, 1.0)
    }

    @Test
    fun heightOutOfRangeGivesNull() {
        val m = project(doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(105.0, 148.0, -3000.0))
        assertNull(CameraPoseEstimator.estimate(m, MarkerSheetSpec(), 4000, 3000, f35))
    }
}
