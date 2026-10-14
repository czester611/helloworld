package com.fitlab.core.photo

import com.fitlab.core.geom.Ring
import com.fitlab.core.geom.Vec2

/** Okrągły otwór wykryty na zdjęciu (układ części, mm); średnica zaokrąglona do 0,05 mm. */
data class HoleCandidate(val center: Vec2, val diameterMm: Double)

/** Obrys części ze zdjęcia w układzie części (mm, Y w górę, wycentrowany). */
data class PhotoOutline(
    val outer: Ring,
    val innerContours: List<Ring>,
    val holeCandidates: List<HoleCandidate>,
    val warnings: List<String>,
)
