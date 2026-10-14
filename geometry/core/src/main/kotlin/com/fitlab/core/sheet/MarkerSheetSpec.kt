package com.fitlab.core.sheet

import com.fitlab.core.geom.Bounds2
import com.fitlab.core.geom.Vec2

/**
 * Układ arkusza A4 z markerami ArUco (DICT_4X4_50, ID 0–3). Układ współrzędnych arkusza: x w prawo, y w dół, mm,
 * (0,0) = lewy górny róg strony. [scaleX] i [scaleY] to korekta skali wydruku (zmierzone/oczekiwane).
 */
data class MarkerSheetSpec(val scaleX: Double = 1.0, val scaleY: Double = 1.0) {
    /** Narożniki markera [id] w mm arkusza w kolejności OpenCV: TL, TR, BR, BL, z uwzględnieniem skali. */
    fun cornersMm(id: Int): List<Vec2> {
        val o = ORIGINS[id] ?: throw IllegalArgumentException("Nieznany marker $id")
        val s = MARKER_SIZE_MM
        return listOf(
            Vec2(o.x, o.y), Vec2(o.x + s, o.y), Vec2(o.x + s, o.y + s), Vec2(o.x, o.y + s),
        ).map { Vec2(it.x * scaleX, it.y * scaleY) }
    }

    /** Pole robocze w mm arkusza (po skali). */
    fun workAreaMm(): Bounds2 = Bounds2(WORK_X0 * scaleX, WORK_Y0 * scaleY, WORK_X1 * scaleX, WORK_Y1 * scaleY)

    companion object {
        const val MARKER_SIZE_MM = 30.0
        const val PAGE_WIDTH_MM = 210.0
        const val PAGE_HEIGHT_MM = 297.0
        const val WORK_X0 = 20.0
        const val WORK_Y0 = 55.0
        const val WORK_X1 = 190.0
        const val WORK_Y1 = 242.0

        /** Identyfikatory markerów. */
        val IDS = listOf(0, 1, 2, 3)

        /** Lewe górne rogi markerów (mm, bez skali). */
        val ORIGINS: Map<Int, Vec2> = mapOf(
            0 to Vec2(15.0, 15.0),
            1 to Vec2(165.0, 15.0),
            2 to Vec2(165.0, 252.0),
            3 to Vec2(15.0, 252.0),
        )
    }
}
