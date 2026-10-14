package com.fitlab.core.fit

import kotlinx.serialization.Serializable

/**
 * ORIGINAL = zmierzono tę samą cechę na oryginale (cel = wymiar zmierzony, klasa pasowania ignorowana).
 * MATING = zmierzono część współpracującą (cel uwzględnia luz klasy pasowania).
 */
@Serializable
enum class FitReference { ORIGINAL, MATING }
