package com.fitlab.vision

import com.fitlab.core.geom.Vec2
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfInt4
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

/** Parametry segmentacji. */
data class SegmentationParams(
    val manualThreshold: Int? = null,
    val blurKernel: Int = 5,
    val openKernel: Int = 3,
    val openIterations: Int = 1,
    val closeKernel: Int = 5,
    val closeIterations: Int = 2,
    val minPartAreaMm2: Double = 100.0,
    val minHoleAreaMm2: Double = 3.0,
)

/** Wynik segmentacji; [mask] (rozmiar ROI) zwalnia wywołujący. */
data class SegmentationResult(
    val outerPx: List<Vec2>,
    val holesPx: List<List<Vec2>>,
    val threshold: Int,
    val mask: Mat,
    val warnings: List<String>,
)

/** Segmentacja części na obrazie zrektyfikowanym (Otsu lub ręczny próg). */
object Segmenter {
    const val K = SheetRectifier.RECT_PX_PER_MM
    const val LOW_CONTRAST = 30.0
    private const val BORDER_PX = 4
    private const val EDGE_MARGIN_PX = 2

    /** Pole robocze w pikselach obrazu zrektyfikowanego: x 20–190 mm, y 55–242 mm. */
    val ROI = Rect(200, 550, 1700, 1870)

    fun segment(rectified: Mat, params: SegmentationParams = SegmentationParams()): SegmentationResult {
        val warnings = ArrayList<String>()
        val roi = rectified.submat(ROI)
        val blurred = Mat()
        val tmp = Mat()
        val mask = Mat()
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        var ok = false
        try {
            Imgproc.GaussianBlur(roi, blurred, Size(params.blurKernel.toDouble(), params.blurKernel.toDouble()), 0.0)
            val t = params.manualThreshold?.toDouble()
                ?: Imgproc.threshold(blurred, tmp, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
            val borderMean = borderMean(blurred)
            val type = if (borderMean > t) Imgproc.THRESH_BINARY_INV else Imgproc.THRESH_BINARY
            Imgproc.threshold(blurred, mask, t, 255.0, type)

            val inv = Mat()
            Core.bitwise_not(mask, inv)
            val meanFg = Core.mean(blurred, mask).`val`[0]
            val meanBg = Core.mean(blurred, inv).`val`[0]
            inv.release()
            if (abs(meanFg - meanBg) < LOW_CONTRAST) {
                warnings.add("Słaby kontrast — połóż część na kartce w kontrastowym kolorze")
            }

            val open = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(params.openKernel.toDouble(), params.openKernel.toDouble()))
            val close = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(params.closeKernel.toDouble(), params.closeKernel.toDouble()))
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, open, org.opencv.core.Point(-1.0, -1.0), params.openIterations)
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, close, org.opencv.core.Point(-1.0, -1.0), params.closeIterations)
            open.release()
            close.release()

            Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_NONE)
            val n = contours.size
            val hier = IntArray(n * 4)
            if (n > 0) hierarchy.get(0, 0, hier)
            // [next, prev, firstChild, parent]
            var best = -1
            var bestArea = 0.0
            for (i in 0 until n) {
                if (hier[4 * i + 3] != -1) continue
                val a = Imgproc.contourArea(contours[i])
                if (a > bestArea) {
                    bestArea = a
                    best = i
                }
            }
            if (best < 0 || bestArea < params.minPartAreaMm2 * K * K) {
                throw PipelineException("Nie znaleziono części w polu roboczym.")
            }
            val bb = Imgproc.boundingRect(contours[best])
            if (bb.x <= EDGE_MARGIN_PX || bb.y <= EDGE_MARGIN_PX ||
                bb.x + bb.width >= ROI.width - 1 - EDGE_MARGIN_PX || bb.y + bb.height >= ROI.height - 1 - EDGE_MARGIN_PX
            ) {
                throw PipelineException("Część wychodzi poza pole robocze — przesuń ją do środka ramki.")
            }
            val dx = ROI.x.toDouble()
            val dy = ROI.y.toDouble()
            val outer = MatUtils.toVec2List(contours[best], dx, dy)
            val holes = ArrayList<List<Vec2>>()
            var child = hier[4 * best + 2]
            while (child != -1) {
                if (Imgproc.contourArea(contours[child]) >= params.minHoleAreaMm2 * K * K) {
                    holes.add(MatUtils.toVec2List(contours[child], dx, dy))
                }
                child = hier[4 * child]
            }
            ok = true
            return SegmentationResult(outer, holes, t.toInt(), mask, warnings)
        } finally {
            roi.release()
            blurred.release()
            tmp.release()
            hierarchy.release()
            contours.forEach { it.release() }
            if (!ok) mask.release()
        }
    }

    private fun borderMean(m: Mat): Double {
        val w = m.cols()
        val h = m.rows()
        val b = BORDER_PX
        val strips = listOf(Rect(0, 0, w, b), Rect(0, h - b, w, b), Rect(0, b, b, h - 2 * b), Rect(w - b, b, b, h - 2 * b))
        var sum = 0.0
        var cnt = 0.0
        for (r in strips) {
            val s = m.submat(r)
            sum += Core.sumElems(s).`val`[0]
            cnt += r.width.toDouble() * r.height
            s.release()
        }
        return sum / cnt
    }
}
