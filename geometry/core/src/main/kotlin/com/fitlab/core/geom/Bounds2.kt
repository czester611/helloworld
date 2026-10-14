package com.fitlab.core.geom

/** Prostokąt otaczający (bounding box) w mm. */
data class Bounds2(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    val width: Double get() = maxX - minX
    val height: Double get() = maxY - minY
    val center: Vec2 get() = Vec2((minX + maxX) / 2.0, (minY + maxY) / 2.0)
}
