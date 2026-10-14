package com.fitlab.vision

import com.fitlab.core.geom.Vec2
import com.fitlab.core.photo.PixelMapping
import com.fitlab.core.sheet.MarkerSheetSpec
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot

/** Wynik rektyfikacji: obraz zrektyfikowany (do zwolnienia przez wywołującego), homografia i błąd reprojekcji. */
data class RectifyResult(
    val rectified: Mat,
    val homography: Mat,
    val meanReprojErrorPx: Double,
    val markerCornersPhotoPx: Map<Int, List<Vec2>>,
    val warnings: List<String>,
)

/** Prostowanie perspektywy: arkusz A4 -> obraz 2100x2970 px (0,1 mm/px). */
object SheetRectifier {
    /** Skala obrazu zrektyfikowanego, px/mm. */
    const val RECT_PX_PER_MM = PixelMapping.RECT_PX_PER_MM
    const val REPROJ_WARN_PX = 3.0
    const val REPROJ_WARNING = "Kartka może nie leżeć płasko lub zdjęcie jest poruszone"
    val RECT_WIDTH_PX = (MarkerSheetSpec.PAGE_WIDTH_MM * RECT_PX_PER_MM).toInt()
    val RECT_HEIGHT_PX = (MarkerSheetSpec.PAGE_HEIGHT_MM * RECT_PX_PER_MM).toInt()

    /** Prostuje [gray] na podstawie wykrytych [markers] i układu [sheet]. */
    fun rectify(gray: Mat, markers: DetectedMarkers, sheet: MarkerSheetSpec): RectifyResult {
        val mapping = PixelMapping(RECT_PX_PER_MM)
        val src = ArrayList<Point>()
        val dst = ArrayList<Point>()
        for ((id, photoCorners) in markers.corners) {
            val sheetCorners = sheet.cornersMm(id)
            for (k in 0 until 4) {
                src.add(Point(photoCorners[k].x, photoCorners[k].y))
                val d = mapping.mmToPx(sheetCorners[k])
                dst.add(Point(d.x, d.y))
            }
        }
        val srcMat = MatOfPoint2f().apply { fromList(src) }
        val dstMat = MatOfPoint2f().apply { fromList(dst) }
        val h = Calib3d.findHomography(srcMat, dstMat, 0)
        val warnings = ArrayList(markers.warnings)
        try {
            if (h.empty()) {
                h.release()
                throw PipelineException("Nie udało się wyznaczyć perspektywy.")
            }
            val proj = MatOfPoint2f()
            Core.perspectiveTransform(srcMat, proj, h)
            val p = proj.toArray()
            proj.release()
            var sum = 0.0
            for (i in p.indices) sum += hypot(p[i].x - dst[i].x, p[i].y - dst[i].y)
            val err = sum / p.size
            if (err > REPROJ_WARN_PX) warnings.add(REPROJ_WARNING)
            val rectified = Mat()
            Imgproc.warpPerspective(
                gray, rectified, h, Size(RECT_WIDTH_PX.toDouble(), RECT_HEIGHT_PX.toDouble()),
                Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(255.0),
            )
            return RectifyResult(rectified, h, err, markers.corners, warnings)
        } finally {
            srcMat.release()
            dstMat.release()
        }
    }
}
