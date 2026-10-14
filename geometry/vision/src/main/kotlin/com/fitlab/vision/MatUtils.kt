package com.fitlab.vision

import com.fitlab.core.geom.Vec2
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.imgproc.Imgproc

/** Konwersje między typami OpenCV a typami rdzenia. */
object MatUtils {
    /** Punkty konturu jako lista [Vec2] (piksele), z przesunięciem ([dx], [dy]). */
    fun toVec2List(c: MatOfPoint, dx: Double = 0.0, dy: Double = 0.0): List<Vec2> {
        val arr = IntArray(c.rows() * 2)
        c.get(0, 0, arr)
        return List(arr.size / 2) { Vec2(arr[2 * it] + dx, arr[2 * it + 1] + dy) }
    }

    /** Narożniki markera z Mat 1x4 CV_32FC2 (kolejność TL, TR, BR, BL). */
    fun cornersOf(m: Mat): List<Vec2> {
        val arr = FloatArray(8)
        m.get(0, 0, arr)
        return List(4) { Vec2(arr[2 * it].toDouble(), arr[2 * it + 1].toDouble()) }
    }

    /** Obraz szary: 4 kanały -> RGBA2GRAY, 3 -> BGR2GRAY, 1 -> kopia. Wywołujący zwalnia wynik. */
    fun toGray(src: Mat): Mat {
        val gray = Mat()
        when (src.channels()) {
            4 -> Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            3 -> Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
            1 -> src.copyTo(gray)
            else -> throw PipelineException("Nieobsługiwany format obrazu.")
        }
        if (gray.type() != CvType.CV_8UC1) {
            val g8 = Mat()
            gray.convertTo(g8, CvType.CV_8UC1)
            gray.release()
            return g8
        }
        return gray
    }
}
