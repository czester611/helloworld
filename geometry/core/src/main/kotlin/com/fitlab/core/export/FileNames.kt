package com.fitlab.core.export

/** Rodzaj próby wydruku (wpływa na nazwę pliku). */
enum class IterationType(val fileCode: String) {
    FIT_TEST("fit"),
    FULL_PART("full"),
    CALIBRATION("cal"),
}

/** Nazwy plików eksportu. */
object FileNames {
    private const val MAX_SLUG = 40

    /** Slug: małe litery, polskie znaki bez ogonków, inne znaki specjalne jako „-”, max 40 znaków. */
    fun slug(name: String): String {
        val sb = StringBuilder()
        for (ch in name.lowercase(java.util.Locale.ROOT)) {
            val c = when (ch) {
                'ą' -> 'a'; 'ć' -> 'c'; 'ę' -> 'e'; 'ł' -> 'l'; 'ń' -> 'n'
                'ó' -> 'o'; 'ś' -> 's'; 'ź', 'ż' -> 'z'
                else -> ch
            }
            if (c in 'a'..'z' || c in '0'..'9') sb.append(c) else if (sb.isNotEmpty() && sb.last() != '-') sb.append('-')
        }
        val s = sb.toString().trim('-').take(MAX_SLUG).trim('-')
        return s.ifEmpty { "part" }
    }

    /** Nazwa pliku: slug_{fit|full|cal}_{numer}.{stl|3mf}. */
    fun exportName(partName: String, type: IterationType, number: Int, format: ExportFormat): String =
        "${slug(partName)}_${type.fileCode}_$number.${format.extension}"
}
