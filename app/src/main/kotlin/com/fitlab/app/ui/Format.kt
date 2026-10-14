package com.fitlab.app.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formatowanie i parsowanie liczb oraz dat w UI (polski przecinek). */
object Fmt {
    private val pl: Locale = Locale.forLanguageTag("pl")

    /** Liczba z [digits] miejscami po przecinku, np. "10,30". */
    fun num(v: Double, digits: Int = 2): String = String.format(pl, "%.${digits}f", v)

    /** Wymiar w mm, np. "10,30 mm". */
    fun mm(v: Double, digits: Int = 2): String = num(v, digits) + " mm"

    /** Parsuje liczbę akceptując przecinek i kropkę; null gdy nie da się sparsować. */
    fun parse(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

    /** Data i godzina, np. "12.10.2026 14:05". */
    fun dateTime(ms: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", pl).format(Date(ms))

    /** Dzień i miesiąc, np. "12.10". */
    fun dayMonth(ms: Long): String = SimpleDateFormat("dd.MM", pl).format(Date(ms))
}
