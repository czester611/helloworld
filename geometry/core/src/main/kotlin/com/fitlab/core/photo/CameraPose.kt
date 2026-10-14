package com.fitlab.core.photo

import com.fitlab.core.geom.Vec2

/** Pozycja aparatu nad arkuszem: wysokość nad kartką i nadir (rzut na kartkę) w mm arkusza. */
data class CameraPose(val heightMm: Double, val nadirMm: Vec2)
