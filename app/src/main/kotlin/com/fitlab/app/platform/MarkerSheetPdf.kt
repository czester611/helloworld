package com.fitlab.app.platform

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.fitlab.vision.MarkerSheetRenderer
import org.opencv.android.Utils
import java.io.OutputStream

/** Arkusz markerów A4 jako bitmapa i PDF. */
object MarkerSheetPdf {
    private const val PAGE_W_PT = 595
    private const val PAGE_H_PT = 842
    private const val A4_W_PT = 595.2756f
    private const val A4_H_PT = 841.8898f

    /** Bitmapa arkusza w skali [pxPerMm] (mała do podglądu, 12 do wydruku). */
    fun bitmap(pxPerMm: Double = MarkerSheetRenderer.SHEET_RENDER_PX_PER_MM): Bitmap {
        val mat = MarkerSheetRenderer.render(pxPerMm)
        try {
            val bmp = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, bmp)
            return bmp
        } finally {
            mat.release()
        }
    }

    /** Zapisuje jednostronicowy PDF A4 (bitmapa bez filtrowania, skala 100%) do [out]. */
    fun write(out: OutputStream) {
        val bmp = bitmap()
        val doc = PdfDocument()
        try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W_PT, PAGE_H_PT, 1).create())
            val paint = Paint().apply { isFilterBitmap = false }
            page.canvas.drawBitmap(bmp, null, RectF(0f, 0f, A4_W_PT, A4_H_PT), paint)
            doc.finishPage(page)
            doc.writeTo(out)
        } finally {
            doc.close()
            bmp.recycle()
        }
    }
}
