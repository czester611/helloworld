package com.fitlab.core.advice

import com.fitlab.core.fit.Material

/** Typ zastosowania części wybierany przez użytkownika. */
enum class UsageType(val label: String) {
    INDOOR_LIGHT("Wnętrze / mało obciążone"),
    PRECISION_FIT("Część pasowana (czop, tuleja, zatrzask)"),
    HEAT_SUN("Ciepło / słońce"),
    OUTDOOR_UV("Zewnątrz / UV"),
    SEAL_DAMPER("Uszczelka / amortyzator (elastyczne)"),
    MOVING_WEAR("Ruchome / ścieranie (zębatka, ślizgacz)"),
    LOAD_BEARING("Obciążone mechanicznie"),
    PROTOTYPE("Prototyp / próba dopasowania"),
    AESTHETIC("Estetyczne"),
}

/**
 * Propozycja filamentu. [material] wskazuje materiał w profilu kompensacji; nylon nie ma własnej wartości
 * w [Material], więc jest [Material.OTHER] z [nameHint] służącym do dopasowania po nazwie profilu.
 */
data class FilamentOption(
    val label: String,
    val material: Material,
    val nameHint: String? = null,
    val reason: String,
    val notes: List<String>,
)

/** Pełna rekomendacja: główny filament, alternatywy, orientacja i ostrzeżenia. */
data class UsageAdvice(
    val usage: UsageType,
    val primary: FilamentOption,
    val alternatives: List<FilamentOption>,
    val orientationTip: String,
    val warnings: List<String>,
)

object UsageAdvisor {
    const val PRINTER_NOTE =
        "Zamknięcie komory i limit temperatury dyszy Snapmaker U1: sprawdź w instrukcji drukarki."

    const val SAFETY_WARNING =
        "Nie drukuj części krytycznych dla bezpieczeństwa: hamulce, instalacja gazowa, elementy nośne, " +
            "kontakt z żywnością, wysokie napięcie."

    val PLA = FilamentOption(
        "PLA", Material.PLA,
        reason = "Łatwy w druku i dokładny wymiarowo; dobry do części niewymagających odporności na ciepło.",
        notes = listOf("Mięknie już ok. 55–60 °C, nie zostawiaj w nagrzanym aucie ani na słońcu.", "Jest kruchy."),
    )
    val PETG = FilamentOption(
        "PETG", Material.PETG,
        reason = "Wytrzymały i odporny na uderzenia, bardziej wyrozumiały niż PLA; dobry do ogólnych napraw.",
        notes = listOf(
            "Potrzebuje trochę większego luzu niż PLA (kompensację nauczysz na próbie).",
            "Ciągnie nitki; dobierz retrakcję i temperaturę.",
        ),
    )
    val ASA = FilamentOption(
        "ASA", Material.ASA,
        reason = "Odporny na UV i wyższą temperaturę, nie starzeje się na słońcu jak PLA i PETG.",
        notes = listOf(
            "Wymaga zamkniętej komory i wentylacji (opary), skłonny do odkształceń.",
            "Sprawdź w instrukcji drukarki, czy nadaje się do Twojej konfiguracji.",
        ),
    )
    val TPU = FilamentOption(
        "TPU", Material.TPU,
        reason = "Elastyczny, tłumi drgania i uszczelnia.",
        notes = listOf("Drukuj wolno, zwykle bez dużej retrakcji.", "Wybierz Shore zależnie od potrzebnej miękkości."),
    )
    val NYLON = FilamentOption(
        "Nylon (PA)", Material.OTHER, nameHint = "nylon",
        reason = "Bardzo dobra odporność na ścieranie i zmęczenie; dobry na zębatki i ślizgacze.",
        notes = listOf(
            "Wymaga suszenia przed drukiem (silnie chłonie wilgoć) i trzymania w suchym miejscu.",
            "Mocno się kurczy i wypacza; sprawdź w instrukcji drukarki, czy dysza i konfiguracja pasują do PA.",
        ),
    )

    private val PA_NAME = Regex("""\bPA\d*\b""", RegexOption.IGNORE_CASE)

    private const val ORIENT_GENERIC =
        "Ustaw część tak, by warstwy nie biegły wzdłuż linii obciążenia; siła powinna działać w płaszczyźnie warstw, " +
            "nie na rozwarstwianie."

    fun advise(usage: UsageType): UsageAdvice {
        val (primary, alts, tip) = when (usage) {
            UsageType.INDOOR_LIGHT -> Triple(PLA, listOf(PETG), ORIENT_GENERIC)
            UsageType.PRECISION_FIT -> Triple(
                PETG, listOf(PLA),
                "Czopy i tuleje drukuj osią wzdłuż płaszczyzny stołu (leżące), żeby warstwy nie rozwarstwiały się pod " +
                    "obciążeniem; otwory pionowe są dokładniejsze, poziome lekko owalne. " + ORIENT_GENERIC,
            )
            UsageType.HEAT_SUN -> Triple(ASA, listOf(PETG), ORIENT_GENERIC)
            UsageType.OUTDOOR_UV -> Triple(ASA, listOf(PETG), ORIENT_GENERIC)
            UsageType.SEAL_DAMPER -> Triple(
                TPU, emptyList(),
                "Połóż część płasko, żeby uzyskać jak najmniej podpór; przy elastycznym materiale kierunek warstw " +
                    "mniej wpływa na wytrzymałość.",
            )
            UsageType.MOVING_WEAR -> Triple(
                PETG, listOf(NYLON),
                "Zębatki i ślizgacze drukuj płasko (oś obrotu pionowo), by zęby były ścinane wzdłuż warstw, a nie " +
                    "rozwarstwiane. " + ORIENT_GENERIC,
            )
            UsageType.LOAD_BEARING -> Triple(PETG, listOf(NYLON), ORIENT_GENERIC)
            UsageType.PROTOTYPE -> Triple(PLA, emptyList(), ORIENT_GENERIC)
            UsageType.AESTHETIC -> Triple(PLA, listOf(PETG), "Wybierz orientację z najlepszą stroną widoczną; $ORIENT_GENERIC")
        }
        return UsageAdvice(usage, primary, alts, tip, listOf(SAFETY_WARNING, PRINTER_NOTE))
    }

    /** Orientacja druku obudowy (dno i pokrywa). */
    const val ENCLOSURE_ORIENTATION_TIP =
        "Dno drukuj otworem do góry, bez podpór. Pokrywę połóż płaską, zewnętrzną stroną na stole, zakładką do góry — " +
            "eksport już tak ją układa."

    /**
     * Zalecenie dla obudowy: wnętrze/mało obciążona (PLA, alternatywa PETG) albo na zewnątrz (ASA, alternatywa PETG).
     */
    fun forEnclosure(outdoor: Boolean): UsageAdvice {
        val a = advise(if (outdoor) UsageType.OUTDOOR_UV else UsageType.INDOOR_LIGHT)
        return a.copy(orientationTip = ENCLOSURE_ORIENTATION_TIP + " " + a.orientationTip)
    }

    /**
     * Wybiera indeks profilu pasującego do opcji: po materiale, a dla [FilamentOption.nameHint] po nazwie.
     * Zwraca null, gdy żaden profil nie pasuje.
     */
    fun matchProfile(option: FilamentOption, profiles: List<Pair<Material, String>>): Int? {
        val hint = option.nameHint
        val idx = profiles.indexOfFirst { (m, name) ->
            if (hint != null) {
                m == Material.OTHER && (name.contains(hint, ignoreCase = true) || PA_NAME.containsMatchIn(name))
            } else {
                m == option.material
            }
        }
        return idx.takeIf { it >= 0 }
    }
}
