package com.fitlab.core.templates

import com.fitlab.core.fit.FitClass

/** Typ parametru szablonu. */
enum class ParamType { MM, COUNT, BOOL }

/** Opis parametru szablonu (etykiety po polsku). */
data class ParamSpec(
    val key: String,
    val label: String,
    val type: ParamType,
    val default: Double,
    val min: Double,
    val max: Double,
    val hint: String? = null,
)

/** Opis gniazda pasowania szablonu. */
data class FitSlotSpec(val key: String, val label: String, val default: FitClass)

/** Błąd walidacji; [key] = null oznacza błąd ogólny. Komunikat po polsku. */
data class ValidationError(val key: String?, val message: String)

/** Wartości parametrów i klasy pasowań wpisane przez użytkownika. */
data class TemplateInput(val values: Map<String, Double>, val fits: Map<String, FitClass>) {
    /** Wartość liczbowa parametru [key]. */
    fun v(key: String): Double = values[key] ?: throw IllegalArgumentException("Brak parametru $key")

    /** Wartość całkowita (COUNT). */
    fun count(key: String): Int = Math.round(v(key)).toInt()

    /** Wartość logiczna (BOOL): różna od 0. */
    fun bool(key: String): Boolean = v(key) != 0.0

    /** Klasa pasowania gniazda [key]. */
    fun fit(key: String): FitClass = fits[key] ?: throw IllegalArgumentException("Brak pasowania $key")
}
