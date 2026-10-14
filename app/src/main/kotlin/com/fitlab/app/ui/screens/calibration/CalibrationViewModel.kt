package com.fitlab.app.ui.screens.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitlab.app.AppContainer
import com.fitlab.app.data.db.TestIterationEntity
import com.fitlab.app.data.repo.BiasChange
import com.fitlab.app.data.repo.FeatureOutcome
import com.fitlab.app.platform.ExportPlan
import com.fitlab.app.ui.Fmt
import com.fitlab.core.design.CalibrationCoupon
import com.fitlab.core.design.DesignJson
import com.fitlab.core.export.IterationType
import com.fitlab.core.fit.ResolvedFeature
import com.fitlab.core.fit.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CalibrationUiState(
    val pending: TestIterationEntity? = null,
    val features: List<ResolvedFeature> = emptyList(),
    val texts: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val changes: List<BiasChange>? = null,
    val message: String? = null,
) {
    val canSave: Boolean
        get() = !saving && features.isNotEmpty() &&
            features.all { (Fmt.parse(texts[it.id] ?: "") ?: 0.0) > 0.0 }
}

class CalibrationViewModel(private val c: AppContainer) : ViewModel() {
    private val texts = MutableStateFlow<Map<String, String>>(emptyMap())
    private val saving = MutableStateFlow(false)
    private val changes = MutableStateFlow<List<BiasChange>?>(null)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<CalibrationUiState> = combine(
        c.tests.observePendingCalibration(), texts, saving, changes, message,
    ) { pending, t, s, ch, m ->
        val features = pending?.let { runCatching { DesignJson.decodeFeatures(it.featuresJson) }.getOrNull() } ?: emptyList()
        CalibrationUiState(pending, features, t, s, ch, m)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalibrationUiState())

    fun setText(id: String, v: String) = texts.update { it + (id to v) }
    fun dismissChanges() { changes.value = null; texts.value = emptyMap() }

    /** Plan eksportu kuponu kalibracyjnego dla aktywnej pary profili. */
    suspend fun makePlan(): ExportPlan = withContext(Dispatchers.Default) {
        val ctx = c.buildContext()
        val build = CalibrationCoupon.build(ctx.comp, ctx.print)
        ExportPlan(null, "kupon", IterationType.CALIBRATION, "{\"type\":\"calibration\"}", build, ctx)
    }

    fun save() {
        val s = state.value
        val pending = s.pending ?: return
        if (!s.canSave) return
        saving.value = true
        viewModelScope.launch {
            try {
                val outcomes = s.features.map { f ->
                    FeatureOutcome(f.id, Verdict.MEASURED, null, Fmt.parse(s.texts[f.id] ?: ""))
                }
                changes.value = c.tests.saveResults(pending.id, "", outcomes, true)
            } catch (e: Exception) {
                message.value = e.message ?: "Nie udało się zapisać pomiarów"
            } finally {
                saving.value = false
            }
        }
    }
}
