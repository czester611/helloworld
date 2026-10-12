package pl.labelscanner.parser

/** Jedna linia tekstu rozpoznana przez OCR wraz z ramką (w pikselach obrazu). */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerY get() = (top + bottom) / 2
    val height get() = (bottom - top).coerceAtLeast(1)
}

data class LabelInfo(val model: String?, val serial: String?)

/**
 * Wyciąga model i numer seryjny z linii OCR tabliczki znamionowej.
 *
 * Wartość szukana jest kolejno: w tej samej linii po słowie-kluczu ("MODEL: X"),
 * w linii po prawej na tej samej wysokości, a na końcu w linii poniżej.
 */
object LabelParser {

    private val SEP = """[\s:.#=\-]*"""
    private val LETTER_GUARD = """(?![\p{L}])"""
    private val LEFT_GUARD = """(?<![\p{L}\p{N}])"""

    private val MODEL_KEY = Regex(
        LEFT_GUARD + """(?:model\s*(?:no|nr|number|name)?|modell|mod|type|typ|typ\s*/\s*model|modèle|modelo|modello)\b\.?""" + SEP,
        RegexOption.IGNORE_CASE,
    )
    private val SERIAL_KEY = Regex(
        LEFT_GUARD + """(?:serial\s*(?:no|nr|number|num)?|ser\.?\s*(?:no|nr)|s\s*/\s*n|sn|(?:numer|nr)\s+seryjny|nr\s+ser|seriennummer|seriennr|n°\s*de\s*série|numéro\s+de\s+série)""" +
            LETTER_GUARD + """\.?""" + SEP,
        RegexOption.IGNORE_CASE,
    )

    fun parse(lines: List<OcrLine>): LabelInfo {
        val clean = lines.filter { it.text.isNotBlank() }
        val model = findValue(clean, MODEL_KEY, SERIAL_KEY, ::validModel)?.let(::cleanModel)
            ?: fallbackModel(clean)
        val serial = findValue(clean, SERIAL_KEY, MODEL_KEY, ::validSerial)?.let(::cleanSerial)
        return LabelInfo(model, serial)
    }

    /** Wersja dla surowego tekstu bez geometrii (np. wklejony tekst). */
    fun parseText(text: String): LabelInfo {
        val lines = text.lines().mapIndexed { i, t -> OcrLine(t.trim(), 0, i * 10, 1000, i * 10 + 9) }
        return parse(lines)
    }

    private fun findValue(
        lines: List<OcrLine>,
        key: Regex,
        otherKey: Regex,
        valid: (String) -> Boolean,
    ): String? {
        for (line in lines.filter { key.containsMatchIn(it.text) }) {
            val m = key.find(line.text)!!
            // 1) ta sama linia: "MODEL: ABC-123"
            val inline = line.text.substring(m.range.last + 1).trim()
            if (inline.isNotEmpty() && !otherKey.containsMatchIn(inline) && valid(inline)) return inline

            // 2) linia po prawej na tej samej wysokości, 3) linia poniżej
            val others = lines.filter { it !== line }
            val right = others
                .filter { it.left >= line.left + (line.right - line.left) / 2 && sameRow(line, it) && it.left >= line.right - line.height }
                .sortedBy { it.left }
            val below = others
                .filter { it.top >= line.centerY && it.top - line.bottom < line.height * 3 && overlapsHorizontally(line, it) }
                .sortedBy { it.top }
            for (c in right + below) {
                val text = c.text.trim().trimStart(':', '.', '#', '=', ' ')
                if (!key.containsMatchIn(text) && !otherKey.containsMatchIn(text) && valid(text)) return text
            }
        }
        return null
    }

    private fun sameRow(a: OcrLine, b: OcrLine): Boolean =
        b.centerY in (a.top - a.height / 3)..(a.bottom + a.height / 3)

    private fun overlapsHorizontally(a: OcrLine, b: OcrLine): Boolean =
        minOf(a.right, b.right) > maxOf(a.left, b.left)

    private fun validModel(s: String) = s.any { it.isDigit() } && s.any { it.isLetter() } && s.length >= 3
    private fun validSerial(s: String) = s.count { it.isDigit() } >= 3

    /** "MSZ-EF25VE3W -E1" -> "MSZ-EF25VE3W"; wielowyrazowe modele zostają w całości. */
    private fun cleanModel(s: String): String {
        val first = s.trim().split(Regex("""\s+""")).first()
        return if (validModel(first) && first.length >= 4) first.trimEnd('-', '.', ',') else s.trim()
    }

    private fun cleanSerial(s: String): String =
        s.trim().trimEnd('.', ',', ';').replace(Regex("""\s+"""), " ")

    /** Gdy brak słowa "MODEL": token typu LITERY-CYFRY z myślnikiem. */
    private fun fallbackModel(lines: List<OcrLine>): String? {
        val pattern = Regex("""[A-Z0-9]{2,}(?:-[A-Z0-9]{2,})+""")
        return lines.asSequence()
            .flatMap { pattern.findAll(it.text.uppercase()).map { m -> m.value } }
            .filter { v -> v.any(Char::isDigit) && v.any(Char::isLetter) }
            .maxByOrNull { it.length }
    }
}
