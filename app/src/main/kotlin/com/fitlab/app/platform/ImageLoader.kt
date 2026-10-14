package com.fitlab.app.platform

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Ładowanie zdjęć do Mat (RGBA) z automatyczną obsługą orientacji EXIF przez ImageDecoder. */
object ImageLoader {
    const val MAX_PHOTO_SIDE_PX = 4000

    private fun decode(source: ImageDecoder.Source, maxSide: Int): Bitmap =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Pułapka 11: bez ALLOCATOR_SOFTWARE bitmapa jest HARDWARE i Utils.bitmapToMat się wywala.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val longSide = max(w, h)
            if (longSide > maxSide) {
                val f = maxSide.toDouble() / longSide
                decoder.setTargetSize((w * f).roundToInt(), (h * f).roundToInt())
            }
        }

    private fun toMat(bitmap: Bitmap): Mat {
        try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            return mat
        } finally {
            bitmap.recycle()
        }
    }

    /** Wczytuje zdjęcie z [uri]; dłuższy bok ograniczony do [maxSide]. Wywołujący zwalnia Mat. */
    fun load(resolver: ContentResolver, uri: Uri, maxSide: Int = MAX_PHOTO_SIDE_PX): Mat =
        toMat(decode(ImageDecoder.createSource(resolver, uri), maxSide))

    /** Wczytuje zdjęcie z pliku. Wywołujący zwalnia Mat. */
    fun load(file: File, maxSide: Int = MAX_PHOTO_SIDE_PX): Mat = toMat(decode(ImageDecoder.createSource(file), maxSide))

    /** Miniatura do podglądu (nie Mat). */
    fun thumbnail(file: File, maxSide: Int = 1000): Bitmap = decode(ImageDecoder.createSource(file), maxSide)
}
