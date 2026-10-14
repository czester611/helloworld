package com.fitlab.vision

import com.fitlab.core.geom.Vec2
import org.opencv.core.Mat
import org.opencv.objdetect.ArucoDetector
import org.opencv.objdetect.DetectorParameters
import org.opencv.objdetect.Objdetect

/** Wykryte markery arkusza: narożniki (TL, TR, BR, BL) w pikselach zdjęcia dla każdego ID 0–3. */
data class DetectedMarkers(val corners: Map<Int, List<Vec2>>, val warnings: List<String>)

/** Detekcja markerów ArUco (DICT_4X4_50). */
object MarkerDetector {
    const val TOO_FEW_MARKERS = "Nie znaleziono markerów arkusza (znaleziono %d z 4). " +
        "Upewnij się, że cały arkusz jest w kadrze i dobrze oświetlony."
    const val THREE_MARKERS_WARNING = "Wykryto tylko 3 markery — dokładność może być niższa"

    /** Wykrywa markery na obrazie szarym [gray]; rzuca [PipelineException] gdy < 3. */
    fun detect(gray: Mat): DetectedMarkers {
        val dict = Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50)
        val params = DetectorParameters()
        params.set_cornerRefinementMethod(Objdetect.CORNER_REFINE_SUBPIX)
        val detector = ArucoDetector(dict, params)
        val corners = ArrayList<Mat>()
        val ids = Mat()
        try {
            detector.detectMarkers(gray, corners, ids)
            val found = LinkedHashMap<Int, List<Vec2>>()
            val n = if (ids.empty()) 0 else ids.rows()
            if (n > 0) {
                val idArr = IntArray(n)
                ids.get(0, 0, idArr)
                for (i in 0 until n) {
                    val id = idArr[i]
                    if (id in 0..3 && id !in found) found[id] = MatUtils.cornersOf(corners[i])
                }
            }
            if (found.size < 3) throw PipelineException(String.format(TOO_FEW_MARKERS, found.size))
            val warnings = if (found.size == 3) listOf(THREE_MARKERS_WARNING) else emptyList()
            return DetectedMarkers(found, warnings)
        } finally {
            corners.forEach { it.release() }
            ids.release()
        }
    }
}
