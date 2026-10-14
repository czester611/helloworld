package com.fitlab.app.platform

import androidx.exifinterface.media.ExifInterface
import java.io.File

/** Odczyt ogniskowej (ekwiwalent 35 mm) z EXIF. */
object ExifReader {
    /** Ogniskowa 35 mm albo null, gdy brak danych. */
    fun focal35mm(file: File): Double? = try {
        val v = ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
        if (v > 0) v.toDouble() else null
    } catch (e: Exception) {
        null
    }
}
