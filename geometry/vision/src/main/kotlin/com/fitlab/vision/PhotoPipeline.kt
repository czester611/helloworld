package com.fitlab.vision

import com.fitlab.core.photo.CameraPose
import com.fitlab.core.photo.OutlinePostProcessor
import com.fitlab.core.photo.PhotoOutline
import com.fitlab.core.photo.RawContours
import com.fitlab.core.sheet.MarkerSheetSpec
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max

/** Błąd przetwarzania zdjęcia; komunikat po polsku dla użytkownika. */
class PipelineException(message: String) : RuntimeException(message)

/** Wejście pipeline: obraz RGBA/BGR/szary, ogniskowa 35 mm (z EXIF lub null), grubość części, parametry. */
data class PipelineInput(
    val rgbaOrGray: Mat,
    val focal35mm: Double?,
    val thicknessMm: Double,
    val segmentation: SegmentationParams = SegmentationParams(),
    val sheet: MarkerSheetSpec = MarkerSheetSpec(),
)

/** Wynik pipeline; [rectified] i [mask] zwalnia wywołujący. */
data class PipelineResult(
    val outline: PhotoOutline,
    val rectified: Mat,
    val mask: Mat,
    val threshold: Int,
    val pose: CameraPose?,
    val warnings: List<String>,
    val meanReprojErrorPx: Double,
    /** Surowe kontury w pikselach obrazu zrektyfikowanego (do nakładki w UI). */
    val raw: RawContours,
)

/** Pełny pipeline: zdjęcie -> markery -> rektyfikacja -> segmentacja -> obrys w mm. */
object PhotoPipeline {
    const val MAX_PHOTO_SIDE_PX = 4000

    /** Uruchamia pipeline. Wywoływać na Dispatchers.Default. */
    fun run(input: PipelineInput): PipelineResult {
        var gray = MatUtils.toGray(input.rgbaOrGray)
        var rect: RectifyResult? = null
        try {
            val longSide = max(gray.cols(), gray.rows())
            if (longSide > MAX_PHOTO_SIDE_PX) {
                val f = MAX_PHOTO_SIDE_PX.toDouble() / longSide
                val small = Mat()
                Imgproc.resize(gray, small, Size(Math.round(gray.cols() * f).toDouble(), Math.round(gray.rows() * f).toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
                gray.release()
                gray = small
            }
            val markers = MarkerDetector.detect(gray)
            rect = SheetRectifier.rectify(gray, markers, input.sheet)
            val pose = CameraPoseEstimator.estimate(markers.corners, input.sheet, gray.cols(), gray.rows(), input.focal35mm)
            val warnings = ArrayList(rect.warnings)
            if (pose == null) warnings.add(CameraPoseEstimator.HEIGHT_WARNING)
            val result = reprocess(rect.rectified, input.thicknessMm, pose, input.segmentation, warnings, rect.meanReprojErrorPx)
            rect = null // rectified przekazany wywołującemu
            return result
        } finally {
            gray.release()
            rect?.rectified?.release()
            rect?.homography?.release()
        }
    }

    /**
     * Ponowna segmentacja + post-processing na już zrektyfikowanym obrazie (np. po zmianie progu).
     * Zwrócony [PipelineResult.rectified] to ten sam [rectified] (nie kopia).
     */
    fun reprocess(
        rectified: Mat,
        thicknessMm: Double,
        pose: CameraPose?,
        params: SegmentationParams,
        baseWarnings: List<String> = emptyList(),
        meanReprojErrorPx: Double = 0.0,
    ): PipelineResult {
        val seg = Segmenter.segment(rectified, params)
        try {
            val raw = RawContours(seg.outerPx, seg.holesPx)
            val outline = OutlinePostProcessor.process(raw, thicknessMm, pose)
            val warnings = baseWarnings + seg.warnings + outline.warnings
            return PipelineResult(outline, rectified, seg.mask, seg.threshold, pose, warnings, meanReprojErrorPx, raw)
        } catch (e: Throwable) {
            seg.mask.release()
            throw e
        }
    }
}
