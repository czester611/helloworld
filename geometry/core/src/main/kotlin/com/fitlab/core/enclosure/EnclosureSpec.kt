package com.fitlab.core.enclosure

import com.fitlab.core.fit.FitClass
import com.fitlab.core.geom.Ring
import com.fitlab.core.geom.Shapes
import kotlinx.serialization.Serializable
import kotlin.math.abs

/** Sposób łączenia dna z pokrywą. */
@Serializable
enum class EnclosureJoin {
    /** Pokrywa ma wewnętrzny występ (zakładkę) wchodzący do ścianek dna. */
    LIP,

    /** Słupki narożne w dnie na wkręty samogwintujące i otwory przelotowe w pokrywie. */
    SCREWS,
}

/**
 * Strona wycięcia. Strony świata dotyczą prostokąta otaczającego obrys obiektu (N = +Y, E = +X); pozycja jest
 * liczona od zachodniego (N/S) lub południowego (E/W) końca krawędzi. [EDGE] oznacza krawędź obrysu o numerze
 * `edgeIndex` (od punktu `i` do `i+1` obrysu CCW), pozycja od początku tej krawędzi.
 */
@Serializable
enum class CutoutSide { NORTH, SOUTH, EAST, WEST, EDGE }

/** Kształt wycięcia: prostokąt albo okrąg. */
@Serializable
enum class CutoutShape { RECT, CIRCLE }

/**
 * Wycięcie w ściance (port USB, przełącznik, kabel). Wymiary nominalne w mm; kompensacja jak dla szczeliny
 * (prostokąt) lub otworu (okrąg).
 *
 * @property positionMm środek wycięcia wzdłuż krawędzi, od jej początku.
 * @property widthMm szerokość wzdłuż krawędzi (dla okręgu: średnica).
 * @property heightMm wysokość (dla okręgu ignorowana).
 * @property centerZMm wysokość środka nad górną powierzchnią podłogi (wnętrze dna).
 */
@Serializable
data class Cutout(
    val id: String,
    val side: CutoutSide = CutoutSide.NORTH,
    val edgeIndex: Int = 0,
    val positionMm: Double,
    val widthMm: Double,
    val heightMm: Double,
    val centerZMm: Double,
    val shape: CutoutShape = CutoutShape.RECT,
)

/**
 * Parametry obudowy: obrys obiektu (mm, CCW), jego wysokość podana ręcznie i wymiary obudowy. Wartości domyślne
 * dla Snapmaker U1 z dyszą 0,4 mm.
 *
 * @property clearanceMm luz obiektu na stronę (wnęka = obrys + luz; kompensowany jak szczelina).
 * @property headroomMm zapas wewnętrzny nad obiektem.
 * @property lipHeightMm wysokość zakładki pokrywy (tryb [EnclosureJoin.LIP]).
 * @property lipThicknessMm grubość pierścienia zakładki.
 * @property lipFit klasa pasowania zakładki w ściankach dna.
 * @property screwPilotMm średnica otworu w słupku (np. 2,5 dla M3 samogwintującego w plastiku).
 * @property screwNominalMm średnica nominalna wkręta (M2 = 2, M3 = 3); otwór w pokrywie ma klasę „luźne”.
 * @property screwEars gdy słupek koliduje z obiektem: wyprowadź go na zewnątrz ścianki (uszy) zamiast pominąć.
 * @property ventCount liczba szczelin wentylacyjnych w pokrywie (0 = brak).
 * @property ventLengthMm długość szczeliny; 0 = automatycznie.
 * @property standoffs czy dodać 4 niskie podpórki pod narożnikami obiektu.
 * @property outdoor obudowa na zewnątrz (zalecenie filamentu ASA/PETG zamiast PLA).
 */
@Serializable
data class EnclosureSpec(
    val outline: Ring,
    val objectHeightMm: Double,
    val clearanceMm: Double = DEFAULT_CLEARANCE_MM,
    val wallMm: Double = 2.0,
    val floorMm: Double = 1.6,
    val lidMm: Double = 1.6,
    val cornerRadiusMm: Double = 2.0,
    val headroomMm: Double = 2.0,
    val join: EnclosureJoin = EnclosureJoin.LIP,
    val lipHeightMm: Double = 2.0,
    val lipThicknessMm: Double = 1.2,
    val lipFit: FitClass = FitClass.SLIDE,
    val screwPilotMm: Double = 2.5,
    val screwNominalMm: Double = 3.0,
    val screwEars: Boolean = true,
    val cutouts: List<Cutout> = emptyList(),
    val ventCount: Int = 0,
    val ventWidthMm: Double = 1.5,
    val ventLengthMm: Double = 0.0,
    val standoffs: Boolean = false,
    val standoffHeightMm: Double = 3.0,
    val outdoor: Boolean = false,
) {
    /** Wymiary (długość X, szerokość Y), gdy obrys jest osiowym prostokątem; w przeciwnym razie null. */
    fun rectDimensions(): Pair<Double, Double>? {
        if (outline.points.size != 4) return null
        val b = outline.bounds()
        return if (abs(abs(outline.signedArea()) - b.width * b.height) < 1e-6) b.width to b.height else null
    }

    companion object {
        const val DEFAULT_CLEARANCE_MM = 0.5

        /** Prostokątny obrys wycentrowany w (0,0): długość [lengthMm] wzdłuż X, szerokość [widthMm] wzdłuż Y. */
        fun rectOutline(lengthMm: Double, widthMm: Double): Ring =
            Shapes.rect(-lengthMm / 2.0, -widthMm / 2.0, lengthMm / 2.0, widthMm / 2.0)

        /** Obudowa dla prostopadłościanu o wymiarach [lengthMm] × [widthMm] × [heightMm] (tryb bez zdjęcia). */
        fun forBox(lengthMm: Double, widthMm: Double, heightMm: Double): EnclosureSpec =
            EnclosureSpec(rectOutline(lengthMm, widthMm), heightMm)
    }
}
