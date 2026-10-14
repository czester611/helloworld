package com.fitlab.vision.testing

import com.fitlab.core.sheet.MarkerSheetSpec
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.Objdetect

/** Syntetyczne arkusze i „zdjęcia” do testów wizji. */
object SyntheticPhotoFactory {
    const val K = 10.0 // px/mm arkusza syntetycznego

    /** Arkusz CV_8UC1 2100x2970 z markerami, częścią 40x25 mm i otworem Ø8 mm. */
    fun sheetWithPart(touchBorder: Boolean = false): Mat {
        val sheet = Mat(2970, 2100, CvType.CV_8UC1, Scalar(255.0))
        val dict = Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50)
        for (id in MarkerSheetSpec.IDS) {
            val o = MarkerSheetSpec.ORIGINS.getValue(id)
            val m = Mat()
            Objdetect.generateImageMarker(dict, id, 300, m, 1)
            val dst = sheet.submat(Rect((o.x * K).toInt(), (o.y * K).toInt(), 300, 300))
            m.copyTo(dst)
            dst.release()
            m.release()
        }
        if (touchBorder) {
            // x 20–60 mm (styk z krawędzią pola roboczego)
            Imgproc.rectangle(sheet, Point(200.0, 1200.0), Point(599.0, 1449.0), Scalar(60.0), -1)
        } else {
            Imgproc.rectangle(sheet, Point(800.0, 1200.0), Point(1199.0, 1449.0), Scalar(60.0), -1)
            Imgproc.circle(sheet, Point(999.5 * 16, 1324.5 * 16), 40 * 16, Scalar(255.0), -1, Imgproc.LINE_8, 4)
        }
        return sheet
    }

    /** „Zdjęcie” CV_8UC1 3000x4000 (szer. x wys.): perspektywa + szum + lekkie rozmycie. */
    fun photo(sheet: Mat, noiseSeed: Long = 42): Mat {
        val src = MatOfPoint2f(Point(0.0, 0.0), Point(2100.0, 0.0), Point(2100.0, 2970.0), Point(0.0, 2970.0))
        val dst = MatOfPoint2f(Point(400.0, 300.0), Point(2650.0, 420.0), Point(2750.0, 3700.0), Point(300.0, 3600.0))
        val h = Imgproc.getPerspectiveTransform(src, dst)
        val photo = Mat()
        Imgproc.warpPerspective(sheet, photo, h, Size(3000.0, 4000.0), Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(128.0))
        val noise = Mat(photo.size(), CvType.CV_16SC1)
        Core.setRNGSeed(noiseSeed.toInt())
        Core.randn(noise, 0.0, 3.0)
        val p16 = Mat()
        photo.convertTo(p16, CvType.CV_16S)
        Core.add(p16, noise, p16)
        p16.convertTo(photo, CvType.CV_8U)
        Imgproc.GaussianBlur(photo, photo, Size(3.0, 3.0), 0.8)
        src.release(); dst.release(); h.release(); noise.release(); p16.release()
        return photo
    }
}
