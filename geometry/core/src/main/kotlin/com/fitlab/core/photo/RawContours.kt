package com.fitlab.core.photo

import com.fitlab.core.geom.Vec2

/** Kontury z segmentacji w pikselach obrazu zrektyfikowanego (środki pikseli pierwszego planu). */
data class RawContours(
    val outerPx: List<Vec2>,
    val holesPx: List<List<Vec2>>,
    val pxPerMm: Double = PixelMapping.RECT_PX_PER_MM,
)
