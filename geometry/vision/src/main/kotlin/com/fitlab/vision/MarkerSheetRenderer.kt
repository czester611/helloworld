package com.fitlab.vision

import com.fitlab.core.sheet.MarkerSheetSpec
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.Objdetect
import kotlin.math.roundToInt

/** Renderowanie arkusza A4 z markerami (obraz szary CV_8UC1). */
object MarkerSheetRenderer {
    const val SHEET_RENDER_PX_PER_MM = 12.0
    const val FOOTER_TEXT = "FitLab A4 v1 - drukuj w skali 100% (bez dopasowania do strony)"

    /** Renderuje arkusz w skali [pxPerMm] (domyślnie 12 px/mm = 2520x3564). Wywołujący zwalnia wynik. */
    fun render(pxPerMm: Double = SHEET_RENDER_PX_PER_MM): Mat {
        fun px(mm: Double) = (mm * pxPerMm).roundToInt()
        val w = px(MarkerSheetSpec.PAGE_WIDTH_MM)
        val h = px(MarkerSheetSpec.PAGE_HEIGHT_MM)
        val img = Mat(h, w, CvType.CV_8UC1, Scalar(255.0))
        val dict = Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50)
        val side = px(MarkerSheetSpec.MARKER_SIZE_MM)
        for (id in MarkerSheetSpec.IDS) {
            val o = MarkerSheetSpec.ORIGINS.getValue(id)
            val marker = Mat()
            Objdetect.generateImageMarker(dict, id, side, marker, 1)
            val dst = img.submat(Rect(px(o.x), px(o.y), side, side))
            marker.copyTo(dst)
            dst.release()
            marker.release()
        }
        // Ramka pola roboczego
        Imgproc.rectangle(
            img,
            Point(px(MarkerSheetSpec.WORK_X0).toDouble(), px(MarkerSheetSpec.WORK_Y0).toDouble()),
            Point(px(MarkerSheetSpec.WORK_X1).toDouble(), px(MarkerSheetSpec.WORK_Y1).toDouble()),
            Scalar(160.0), maxOf(1, (0.33 * pxPerMm).roundToInt()),
        )
        // Linijka kontrolna 100 mm: x 55–155, y 265–266, kreski co 10 mm
        Imgproc.rectangle(
            img, Point(px(55.0).toDouble(), px(265.0).toDouble()), Point(px(155.0).toDouble(), px(266.0).toDouble()),
            Scalar(0.0), -1,
        )
        for (i in 0..10) {
            val x = px(55.0 + 10.0 * i).toDouble()
            Imgproc.rectangle(img, Point(x - 1, px(266.0).toDouble()), Point(x + 1, px(269.0).toDouble()), Scalar(0.0), -1)
        }
        val scale = pxPerMm / SHEET_RENDER_PX_PER_MM
        Imgproc.putText(
            img, "100 mm", Point(px(95.0).toDouble(), px(274.0).toDouble()),
            Imgproc.FONT_HERSHEY_SIMPLEX, 1.6 * scale, Scalar(0.0), maxOf(1, (3 * scale).roundToInt()),
        )
        Imgproc.putText(
            img, FOOTER_TEXT, Point(px(15.0).toDouble(), px(290.0).toDouble()),
            Imgproc.FONT_HERSHEY_SIMPLEX, 1.6 * scale, Scalar(0.0), maxOf(1, (3 * scale).roundToInt()),
        )
        return img
    }
}
