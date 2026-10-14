package com.fitlab.core.photo

import com.fitlab.core.geom.Vec2

/**
 * Konwersja mm arkusza <-> piksele obrazu zrektyfikowanego. Środek piksela (u, v) ma współrzędne całkowite
 * (konwencja OpenCV); piksel pokrywa [u/k, (u+1)/k) mm.
 */
data class PixelMapping(val pxPerMm: Double = RECT_PX_PER_MM) {
    /** mm -> px: u = x·k − 0.5. */
    fun mmToPx(p: Vec2): Vec2 = Vec2(p.x * pxPerMm - 0.5, p.y * pxPerMm - 0.5)

    /** px -> mm: x = (u + 0.5)/k. */
    fun pxToMm(p: Vec2): Vec2 = Vec2((p.x + 0.5) / pxPerMm, (p.y + 0.5) / pxPerMm)

    companion object {
        /** Skala obrazu zrektyfikowanego, px/mm. */
        const val RECT_PX_PER_MM = 10.0
    }
}
