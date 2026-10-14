package com.fitlab.app.platform

import org.opencv.android.OpenCVLoader

/** Jednorazowa inicjalizacja OpenCV. */
object OpenCvInit {
    @Volatile
    var ready: Boolean = false
        private set

    /** Ładuje natywne biblioteki OpenCV (idempotentne). */
    fun ensure(): Boolean {
        if (!ready) ready = OpenCVLoader.initLocal()
        return ready
    }
}
