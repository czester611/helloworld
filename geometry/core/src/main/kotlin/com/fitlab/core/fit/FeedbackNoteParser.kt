package com.fitlab.core.fit

import java.util.Locale

/** Wynik parsowania notatki z próby (tylko podpowiedź dla formularza). */
data class ParsedNote(val verdict: Verdict?, val amountMm: Double?)

/** Prosty parser notatek po polsku, np. „za ciasno o 0,2 mm”. */
object FeedbackNoteParser {
    /** Maksymalna akceptowana kwota, mm. */
    const val NOTE_MAX_AMOUNT_MM = 2.0

    private val tight = Regex("ciasn|nie wchodzi|nie wejdzie|za grub|za szerok")
    private val loose = Regex("luźn|luzn|lata|wypada|za cienk|za wąsk|za wask|za duży luz|za duzy luz")
    private val ok = Regex("\\bok\\b|pasuje|idealn|dobrze")
    private val withMm = Regex("(\\d+(?:[.,]\\d+)?)\\s*mm")
    private val anyNumber = Regex("(\\d+[.,]\\d+|\\d+)")

    /** Wyciąga werdykt i kwotę z [text]. */
    fun parse(text: String): ParsedNote {
        val t = text.lowercase(Locale.ROOT)
        val verdict = when {
            tight.containsMatchIn(t) -> Verdict.TOO_TIGHT
            loose.containsMatchIn(t) -> Verdict.TOO_LOOSE
            ok.containsMatchIn(t) -> Verdict.OK
            else -> null
        }
        val raw = (withMm.find(t) ?: anyNumber.find(t))?.groupValues?.get(1)
        val amount = raw?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0.0 && it <= NOTE_MAX_AMOUNT_MM }
        return ParsedNote(verdict, amount)
    }
}
