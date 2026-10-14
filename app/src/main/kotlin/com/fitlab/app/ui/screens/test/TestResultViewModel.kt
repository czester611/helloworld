package com.fitlab.app.ui.screens.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitlab.app.AppContainer
import com.fitlab.app.data.repo.BiasChange
import com.fitlab.app.data.repo.FeatureOutcome
import com.fitlab.app.ui.Fmt
import com.fitlab.core.design.DesignJson
import com.fitlab.core.export.IterationType
import com.fitlab.core.fit.FeedbackNoteParser
import com.fitlab.core.fit.ResolvedFeature
import com.fitlab.core.fit.Verdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Edycja oceny jednej cechy. */
data class FeatureEdit(
    val verdict: Verdict? = null,
    val amountText: String = "",
    val measuredText: String = "",
) {
    val amount: Double? get() = Fmt.parse(amountText)
    val measured: Double? get() = Fmt.parse(measuredText)

    /** Czy wpis jest kompletny (pominięta cecha jest poprawna). */
    fun isValid(): Boolean = when (verdict) {
        null, Verdict.OK -> true
        Verdict.TOO_TIGHT, Verdict.TOO_LOOSE -> (amount ?: -1.0) > 0.0
        Verdict.MEASURED -> measured != null && (measured ?: 0.0) > 0.0
    }
}

data class TestResultUiState(
    val loading: Boolean = true,
    val title: String = "",
    val subtitle: String = "",
    val features: List<ResolvedFeature> = emptyList(),
    val edits: Map<String, FeatureEdit> = emptyMap(),
    val currentFeatureId: String? = null,
    val note: String = "",
    val learn: Boolean = true,
    val saving: Boolean = false,
    val changes: List<BiasChange>? = null,
    val message: String? = null,
) {
    val canSave: Boolean get() = !saving && edits.values.all { it.isValid() }
}

class TestResultViewModel(private val c: AppContainer, private val iterationId: Long) : ViewModel() {
    private val _state = MutableStateFlow(TestResultUiState())
    val state: StateFlow<TestResultUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val it = c.tests.get(iterationId) ?: run {
                _state.update { s -> s.copy(loading = false, message = "Nie znaleziono próby.") }
                return@launch
            }
            val features = DesignJson.decodeFeatures(it.featuresJson)
            val existing = c.tests.results(iterationId).associateBy { r -> r.featureId }
            val edits = features.associate { f ->
                val r = existing[f.id]
                f.id to if (r == null) {
                    FeatureEdit()
                } else {
                    FeatureEdit(
                        Verdict.valueOf(r.verdict),
                        r.amountMm?.let { a -> Fmt.num(a) } ?: "",
                        r.measuredMm?.let { m -> Fmt.num(m) } ?: "",
                    )
                }
            }
            val type = IterationType.valueOf(it.type)
            val partName = it.partId?.let { id -> c.parts.get(id)?.name }
            val typeLabel = when (type) {
                IterationType.FIT_TEST -> "Plaster"
                IterationType.FULL_PART -> "Pełna część"
                IterationType.CALIBRATION -> "Kalibracja"
            }
            val printer = c.profiles.printer(it.printerProfileId)?.name ?: "?"
            val filament = c.profiles.filament(it.filamentProfileId)?.name ?: "?"
            _state.value = TestResultUiState(
                loading = false,
                title = "Próba #${it.number} — $typeLabel",
                subtitle = "${partName ?: "Kupon kalibracyjny"} · ${Fmt.dateTime(it.createdAt)} · $printer · $filament",
                features = features,
                edits = edits,
                currentFeatureId = features.firstOrNull { f -> edits[f.id]?.verdict == null }?.id ?: features.firstOrNull()?.id,
                note = it.note,
                learn = existing.values.firstOrNull()?.includeInLearning ?: true,
            )
        }
    }

    private fun edit(id: String, f: (FeatureEdit) -> FeatureEdit) =
        _state.update { s -> s.copy(edits = s.edits + (id to f(s.edits[id] ?: FeatureEdit()))) }

    fun setVerdict(id: String, v: Verdict?) = edit(id) { it.copy(verdict = v) }
    fun setAmountText(id: String, t: String) = edit(id) { it.copy(amountText = t) }
    fun setMeasuredText(id: String, t: String) = edit(id) { it.copy(measuredText = t) }
    fun setNote(t: String) = _state.update { it.copy(note = t) }
    fun setLearn(v: Boolean) = _state.update { it.copy(learn = v) }
    fun setCurrent(id: String) = _state.update { it.copy(currentFeatureId = id) }
    fun dismissChanges() = _state.update { it.copy(changes = null) }

    /** Parser notatki wypełnia formularz bieżącej cechy (użytkownik zatwierdza). */
    fun fillFromNote() {
        val s = _state.value
        val id = s.currentFeatureId ?: return
        val parsed = FeedbackNoteParser.parse(s.note)
        edit(id) { e ->
            e.copy(
                verdict = parsed.verdict ?: e.verdict,
                amountText = parsed.amountMm?.let { a -> Fmt.num(a) } ?: e.amountText,
            )
        }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val outcomes = s.features.mapNotNull { f ->
                    val e = s.edits[f.id] ?: return@mapNotNull null
                    val v = e.verdict ?: return@mapNotNull null
                    FeatureOutcome(
                        f.id, v,
                        if (v == Verdict.TOO_TIGHT || v == Verdict.TOO_LOOSE) e.amount else null,
                        if (v == Verdict.MEASURED) e.measured else null,
                    )
                }
                val changes = c.tests.saveResults(iterationId, s.note, outcomes, s.learn)
                _state.update { it.copy(saving = false, changes = changes) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, message = e.message ?: "Nie udało się zapisać wyniku") }
            }
        }
    }
}
