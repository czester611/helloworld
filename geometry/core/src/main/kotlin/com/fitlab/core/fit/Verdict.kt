package com.fitlab.core.fit

import kotlinx.serialization.Serializable

/** Ocena wyniku próby dla cechy. */
@Serializable
enum class Verdict { TOO_TIGHT, OK, TOO_LOOSE, MEASURED }
