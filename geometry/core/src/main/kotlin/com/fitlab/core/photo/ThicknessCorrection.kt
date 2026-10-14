package com.fitlab.core.photo

import com.fitlab.core.geom.Vec2

/** Korekcja paralaksy: górna powierzchnia części jest powiększona względem nadiru o H/(H−t). */
object ThicknessCorrection {
    /** p' = n + (p − n)·(H − t)/H; bez zmian dla t <= 0 lub gdy [pose] = null. */
    fun apply(points: List<Vec2>, pose: CameraPose?, thicknessMm: Double): List<Vec2> {
        if (pose == null || thicknessMm <= 0.0) return points
        val k = (pose.heightMm - thicknessMm) / pose.heightMm
        val n = pose.nadirMm
        return points.map { n + (it - n) * k }
    }
}
