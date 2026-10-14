package com.fitlab.core.fit

import kotlinx.serialization.Serializable

/** Materiał filamentu (wyznacza wartości startowe kompensacji). */
@Serializable
enum class Material { PLA, PETG, ABS, ASA, TPU, OTHER }
