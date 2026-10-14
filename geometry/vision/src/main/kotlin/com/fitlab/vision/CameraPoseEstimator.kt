package com.fitlab.vision

import com.fitlab.core.geom.Vec2
import com.fitlab.core.photo.CameraPose
import com.fitlab.core.sheet.MarkerSheetSpec
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point
import org.opencv.core.Point3
import kotlin.math.abs
import kotlin.math.sqrt

/** Estymacja pozycji aparatu nad arkuszem (solvePnP na narożnikach markerów). */
object CameraPoseEstimator {
    const val DEFAULT_FOCAL_35MM = 26.0
    const val FULL_FRAME_DIAGONAL_MM = 43.2666
    const val MIN_HEIGHT_MM = 100.0
    const val MAX_HEIGHT_MM = 1500.0
    const val HEIGHT_WARNING = "Nie udało się oszacować wysokości aparatu — pominięto korekcję grubości"

    /** Ogniskowa w pikselach z ekwiwalentu 35 mm i rozmiaru zdjęcia. */
    fun focalPx(focal35mm: Double?, w: Int, h: Int): Double {
        val f35 = if (focal35mm != null && focal35mm > 0) focal35mm else DEFAULT_FOCAL_35MM
        return f35 / FULL_FRAME_DIAGONAL_MM * sqrt(w.toDouble() * w + h.toDouble() * h)
    }

    /**
     * Pozycja aparatu albo null, gdy wysokość poza [100, 1500] mm lub solvePnP zawiodło.
     * [imageWidth]/[imageHeight] to wymiary zdjęcia po skalowaniu.
     */
    fun estimate(
        markers: Map<Int, List<Vec2>>,
        sheet: MarkerSheetSpec,
        imageWidth: Int,
        imageHeight: Int,
        focal35mm: Double?,
    ): CameraPose? {
        val fPx = focalPx(focal35mm, imageWidth, imageHeight)
        val obj = ArrayList<Point3>()
        val img = ArrayList<Point>()
        for ((id, c) in markers) {
            val s = sheet.cornersMm(id)
            for (k in 0 until 4) {
                obj.add(Point3(s[k].x, s[k].y, 0.0))
                img.add(Point(c[k].x, c[k].y))
            }
        }
        val objMat = MatOfPoint3f().apply { fromList(obj) }
        val imgMat = MatOfPoint2f().apply { fromList(img) }
        val k = Mat(3, 3, CvType.CV_64FC1)
        k.put(0, 0, fPx, 0.0, imageWidth / 2.0, 0.0, fPx, imageHeight / 2.0, 0.0, 0.0, 1.0)
        val dist = MatOfDouble(0.0, 0.0, 0.0, 0.0, 0.0)
        val rvec = Mat()
        val tvec = Mat()
        val rot = Mat()
        try {
            val ok = Calib3d.solvePnP(objMat, imgMat, k, dist, rvec, tvec, false, Calib3d.SOLVEPNP_ITERATIVE)
            if (!ok) return null
            Calib3d.Rodrigues(rvec, rot)
            val r = DoubleArray(9)
            rot.get(0, 0, r)
            val t = DoubleArray(3)
            tvec.get(0, 0, t)
            // C = -R^T t
            val cx = -(r[0] * t[0] + r[3] * t[1] + r[6] * t[2])
            val cy = -(r[1] * t[0] + r[4] * t[1] + r[7] * t[2])
            val cz = -(r[2] * t[0] + r[5] * t[1] + r[8] * t[2])
            val height = abs(cz)
            if (cz >= 0 || height < MIN_HEIGHT_MM || height > MAX_HEIGHT_MM || height.isNaN()) return null
            return CameraPose(height, Vec2(cx, cy))
        } finally {
            objMat.release(); imgMat.release(); k.release(); dist.release()
            rvec.release(); tvec.release(); rot.release()
        }
    }
}
