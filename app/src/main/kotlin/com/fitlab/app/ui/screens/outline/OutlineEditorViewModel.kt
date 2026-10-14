package com.fitlab.app.ui.screens.outline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitlab.app.AppContainer
import com.fitlab.app.data.repo.NewMeasurement
import com.fitlab.app.ui.Fmt
import com.fitlab.core.design.DesignJson
import com.fitlab.core.design.HoleFeature
import com.fitlab.core.design.OutlineBuilder
import com.fitlab.core.design.OutlineDesign
import com.fitlab.core.fit.FitClass
import com.fitlab.core.fit.FitReference
import com.fitlab.core.geom.CircleFit
import com.fitlab.core.geom.Ring
import com.fitlab.core.geom.Vec2
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.round

data class OutlineEditorUiState(
    val loading: Boolean = true,
    val design: OutlineDesign? = null,
    val thicknessText: String = "",
    val topChamferText: String = "0",
    val refWidthText: String = "",
    val refHeightText: String = "",
    val holeDiaTexts: Map<String, String> = emptyMap(),
    val holeChamferTexts: Map<String, String> = emptyMap(),
    val addMode: Boolean = false,
    val pendingHoleAt: Vec2? = null,
    val errors: List<String> = emptyList(),
    val saving: Boolean = false,
    val savedPartId: Long? = null,
)

class OutlineEditorViewModel(private val c: AppContainer, private val partId: Long) : ViewModel() {
    private val _state = MutableStateFlow(OutlineEditorUiState())
    val state: StateFlow<OutlineEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val part = c.parts.get(partId)
            val d = part?.let { runCatching { DesignJson.decode(it.designJson) }.getOrNull() }
            if (d is OutlineDesign) {
                _state.value = fromDesign(d)
            } else {
                _state.update { it.copy(loading = false, errors = listOf("Nie znaleziono obrysu części.")) }
            }
        }
    }

    private fun fromDesign(d: OutlineDesign) = OutlineEditorUiState(
        loading = false,
        design = d,
        thicknessText = Fmt.num(d.thicknessMm),
        topChamferText = Fmt.num(d.topChamferMm),
        holeDiaTexts = d.holes.associate { it.id to Fmt.num(it.nominalDiameterMm) },
        holeChamferTexts = d.holes.associate { it.id to Fmt.num(it.chamferMm) },
    )

    private fun update(f: (OutlineDesign) -> OutlineDesign) = _state.update { s ->
        val d = s.design ?: return@update s
        s.copy(design = f(d), errors = emptyList())
    }

    private fun mapRing(r: Ring, f: (Vec2) -> Vec2) = Ring(r.points.map(f))

    private fun transform(d: OutlineDesign, f: (Vec2) -> Vec2, flip: Boolean): OutlineDesign {
        var outer = mapRing(d.outer, f)
        var inner = d.innerContours.map { mapRing(it, f) }
        if (flip) {
            if (!outer.isCcw()) outer = outer.reversed()
            inner = inner.map { if (it.isCcw()) it.reversed() else it }
        }
        return d.copy(outer = outer, innerContours = inner, holes = d.holes.map { it.copy(center = f(it.center)) })
    }

    fun setThickness(t: String) {
        _state.update { it.copy(thicknessText = t) }
        Fmt.parse(t)?.let { v -> update { it.copy(thicknessMm = v) } }
    }

    fun setTopChamfer(t: String) {
        _state.update { it.copy(topChamferText = t) }
        Fmt.parse(t)?.let { v -> if (v >= 0) update { it.copy(topChamferMm = v) } }
    }

    fun setRefWidth(t: String) = _state.update { it.copy(refWidthText = t) }
    fun setRefHeight(t: String) = _state.update { it.copy(refHeightText = t) }

    /** Skala z wymiaru referencyjnego; pojedynczy wymiar daje skalę jednolitą. Średnic otworów nie skaluje. */
    fun applyScale() {
        val s = _state.value
        val d = s.design ?: return
        val w = Fmt.parse(s.refWidthText)?.takeIf { it > 0 }
        val h = Fmt.parse(s.refHeightText)?.takeIf { it > 0 }
        if (w == null && h == null) return
        val b = d.outer.bounds()
        val sx = w?.let { it / b.width } ?: (h!! / b.height)
        val sy = h?.let { it / b.height } ?: sx
        update { transform(it, { p -> Vec2(p.x * sx, p.y * sy) }, false) }
        _state.update { it.copy(refWidthText = "", refHeightText = "") }
        viewModelScope.launch {
            w?.let { c.parts.addMeasurement(partId, NewMeasurement("Szerokość X (ref.)", it, "refWidth")) }
            h?.let { c.parts.addMeasurement(partId, NewMeasurement("Wysokość Y (ref.)", it, "refHeight")) }
        }
    }

    fun rotate90() = update { transform(it, { p -> Vec2(-p.y, p.x) }, false) }
    fun mirror() = update { transform(it, { p -> Vec2(-p.x, p.y) }, true) }

    fun toggleAddMode() = _state.update { it.copy(addMode = !it.addMode) }

    fun onTap(mm: Vec2) = _state.update { if (it.addMode) it.copy(pendingHoleAt = mm) else it }
    fun cancelAddHole() = _state.update { it.copy(pendingHoleAt = null) }

    private fun nextHoleId(d: OutlineDesign): String =
        "hole-" + ((d.holes.mapNotNull { it.id.removePrefix("hole-").toIntOrNull() }.maxOrNull() ?: 0) + 1)

    fun confirmAddHole(diameter: Double) {
        val at = _state.value.pendingHoleAt ?: return
        val d = _state.value.design ?: return
        val id = nextHoleId(d)
        update { it.copy(holes = it.holes + HoleFeature(id, at, diameter)) }
        _state.update {
            it.copy(
                pendingHoleAt = null, addMode = false,
                holeDiaTexts = it.holeDiaTexts + (id to Fmt.num(diameter)),
                holeChamferTexts = it.holeChamferTexts + (id to Fmt.num(0.0)),
            )
        }
    }

    private fun updateHole(id: String, f: (HoleFeature) -> HoleFeature) =
        update { d -> d.copy(holes = d.holes.map { if (it.id == id) f(it) else it }) }

    fun setHoleDiameter(id: String, t: String) {
        _state.update { it.copy(holeDiaTexts = it.holeDiaTexts + (id to t)) }
        Fmt.parse(t)?.let { v -> if (v > 0) updateHole(id) { it.copy(nominalDiameterMm = v, needsCaliperCheck = false) } }
    }

    fun setHoleChamfer(id: String, t: String) {
        _state.update { it.copy(holeChamferTexts = it.holeChamferTexts + (id to t)) }
        Fmt.parse(t)?.let { v -> if (v >= 0) updateHole(id) { it.copy(chamferMm = v) } }
    }

    fun setHoleReference(id: String, r: FitReference) = updateHole(id) { it.copy(reference = r) }
    fun setHoleFit(id: String, f: FitClass) = updateHole(id) { it.copy(fit = f) }
    fun removeHole(id: String) = update { d -> d.copy(holes = d.holes.filter { it.id != id }) }

    fun removeInner(index: Int) = update { d -> d.copy(innerContours = d.innerContours.filterIndexed { i, _ -> i != index }) }

    /** Zamienia wewnętrzny kontur na okrągły otwór (dopasowanie Kåsa). */
    fun convertInnerToHole(index: Int) {
        val d = _state.value.design ?: return
        val ring = d.innerContours.getOrNull(index) ?: return
        val circle = try {
            CircleFit.kasa(ring.points)
        } catch (e: Exception) {
            _state.update { it.copy(errors = listOf("Nie udało się dopasować okręgu do konturu.")) }
            return
        }
        val dia = round(2.0 * circle.radius / 0.05) * 0.05
        val id = nextHoleId(d)
        update {
            it.copy(
                innerContours = it.innerContours.filterIndexed { i, _ -> i != index },
                holes = it.holes + HoleFeature(id, circle.center, dia, needsCaliperCheck = true),
            )
        }
        _state.update {
            it.copy(
                holeDiaTexts = it.holeDiaTexts + (id to Fmt.num(dia)),
                holeChamferTexts = it.holeChamferTexts + (id to Fmt.num(0.0)),
            )
        }
    }

    /** Walidacja jak w OutlineBuilder; zapis i przejście do części. */
    fun save() {
        val d = _state.value.design ?: return
        _state.update { it.copy(saving = true, errors = emptyList()) }
        viewModelScope.launch {
            try {
                val ctx = c.buildContext()
                val errors = OutlineBuilder.validate(d, ctx.comp)
                if (errors.isNotEmpty()) {
                    _state.update { it.copy(saving = false, errors = errors) }
                    return@launch
                }
                OutlineBuilder.build(d, ctx.comp, ctx.print)
                c.parts.updateDesign(partId, d)
                _state.update { it.copy(saving = false, savedPartId = partId) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, errors = listOf(e.message ?: "Nie udało się zapisać obrysu")) }
            }
        }
    }
}
