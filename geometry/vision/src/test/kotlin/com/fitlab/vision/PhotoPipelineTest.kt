package com.fitlab.vision

import com.fitlab.vision.testing.SyntheticPhotoFactory
import com.fitlab.vision.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar

class PhotoPipelineTest : OpenCvTestBase() {
    private fun photo(touch: Boolean = false): Mat {
        val sheet = SyntheticPhotoFactory.sheetWithPart(touch)
        val p = SyntheticPhotoFactory.photo(sheet)
        sheet.release()
        return p
    }

    @Test
    fun extractsOutlineAndHole() {
        val photo = photo()
        val r = PhotoPipeline.run(PipelineInput(photo, null, 0.0))
        try {
            val b = r.outline.outer.bounds()
            assertClose(40.0, b.width, 0.2, "width")
            assertClose(25.0, b.height, 0.2, "height")
            assertEquals(1, r.outline.holeCandidates.size)
            val h = r.outline.holeCandidates[0]
            assertClose(8.0, h.diameterMm, 0.2, "hole dia")
            assertClose(0.0, h.center.x, 0.2)
            assertClose(0.0, h.center.y, 0.2)
            assertTrue(r.outline.innerContours.isEmpty())
            assertTrue(r.meanReprojErrorPx < 1.0, "reproj ${r.meanReprojErrorPx}")
        } finally {
            photo.release(); r.rectified.release(); r.mask.release()
        }
    }

    @Test
    fun reprocessWithManualThreshold() {
        val photo = photo()
        val r = PhotoPipeline.run(PipelineInput(photo, null, 0.0))
        try {
            val r2 = PhotoPipeline.reprocess(r.rectified, 0.0, r.pose, SegmentationParams(manualThreshold = 150))
            assertEquals(150, r2.threshold)
            assertClose(40.0, r2.outline.outer.bounds().width, 0.3)
            r2.mask.release()
        } finally {
            photo.release(); r.rectified.release(); r.mask.release()
        }
    }

    @Test
    fun partTouchingBorderFails() {
        val photo = photo(touch = true)
        try {
            val e = assertThrows(PipelineException::class.java) { PhotoPipeline.run(PipelineInput(photo, null, 0.0)) }
            assertTrue(e.message!!.contains("poza pole robocze"), e.message)
        } finally {
            photo.release()
        }
    }

    @Test
    fun noMarkersFails() {
        val gray = Mat(1000, 1000, CvType.CV_8UC1, Scalar(128.0))
        try {
            val e = assertThrows(PipelineException::class.java) { PhotoPipeline.run(PipelineInput(gray, null, 0.0)) }
            assertTrue(e.message!!.contains("Nie znaleziono markerów"), e.message)
        } finally {
            gray.release()
        }
    }

    @Test
    fun rgbaInputAndThicknessAreAccepted() {
        val photo = photo()
        val rgba = Mat()
        org.opencv.imgproc.Imgproc.cvtColor(photo, rgba, org.opencv.imgproc.Imgproc.COLOR_GRAY2RGBA)
        val r = PhotoPipeline.run(PipelineInput(rgba, 26.0, 10.0))
        try {
            // pozycja aparatu może być null lub nie, ale wynik nie może być większy niż bez korekcji
            assertTrue(r.outline.outer.bounds().width <= 40.3)
            if (r.pose == null) assertNull(r.pose)
        } finally {
            photo.release(); rgba.release(); r.rectified.release(); r.mask.release()
        }
    }
}
