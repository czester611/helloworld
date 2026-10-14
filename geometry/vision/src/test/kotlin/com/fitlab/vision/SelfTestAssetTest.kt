package com.fitlab.vision

import com.fitlab.vision.testing.SyntheticPhotoFactory
import com.fitlab.vision.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.opencv.core.MatOfInt
import org.opencv.imgcodecs.Imgcodecs
import java.io.File

class SelfTestAssetTest : OpenCvTestBase() {
    @Test
    fun writesSelfTestAsset() {
        val sheet = SyntheticPhotoFactory.sheetWithPart()
        val photo = SyntheticPhotoFactory.photo(sheet)
        val file = File("build/selftest_photo.jpg")
        file.parentFile.mkdirs()
        assertTrue(Imgcodecs.imwrite(file.path, photo, MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 92)))
        assertTrue(file.length() > 10_000)
        // Zasób po kompresji JPEG nadal musi przejść pipeline (tak jak Diagnostyka w aplikacji)
        val back = Imgcodecs.imread(file.path, Imgcodecs.IMREAD_GRAYSCALE)
        val r = PhotoPipeline.run(PipelineInput(back, null, 0.0))
        try {
            assertClose(40.0, r.outline.outer.bounds().width, 0.3)
            assertClose(25.0, r.outline.outer.bounds().height, 0.3)
            assertEquals(1, r.outline.holeCandidates.size)
            assertClose(8.0, r.outline.holeCandidates[0].diameterMm, 0.3)
        } finally {
            sheet.release(); photo.release(); back.release(); r.rectified.release(); r.mask.release()
        }
    }
}
