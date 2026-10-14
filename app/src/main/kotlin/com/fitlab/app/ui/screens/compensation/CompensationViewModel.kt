package com.fitlab.app.ui.screens.compensation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitlab.app.AppContainer
import com.fitlab.core.fit.CompensationKind
import com.fitlab.core.fit.Verdict
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class KindCard(val kind: CompensationKind, val bias: Double, val sampleCount: Int, val manual: Boolean)

data class SampleRow(
    val id: Long,
    val kind: CompensationKind,
    val whenMs: Long,
    val source: String,
    val verdict: Verdict,
    val sampleMm: Double,
    val include: Boolean,
)

data class CompensationUiState(
    val printerName: String = "",
    val filamentName: String = "",
    val cards: List<KindCard> = emptyList(),
    val samples: List<SampleRow> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class CompensationViewModel(private val c: AppContainer) : ViewModel() {
    private val pair = combine(c.settings.activePrinterId, c.settings.activeFilamentId) { p, f -> p to f }

    val state: StateFlow<CompensationUiState> = pair.flatMapLatest { (p, f) ->
        combine(c.compensation.observeRules(p, f), c.tests.observeResultsForProfile(p, f)) { rules, results ->
            val byKind = rules.associateBy { it.kind }
            val cards = CompensationKind.entries.map { k ->
                val r = byKind[k.name]
                KindCard(k, r?.biasMm ?: c.compensation.prior(f, k), r?.sampleCount ?: 0, r?.manualOverride ?: false)
            }
            val partNames = HashMap<Long, String>()
            val rows = results.map { r ->
                val iter = c.tests.get(r.testIterationId)
                val partId = iter?.partId
                val name = if (partId == null) {
                    "Kalibracja"
                } else {
                    partNames.getOrPut(partId) { c.parts.get(partId)?.name ?: "?" }
                }
                SampleRow(
                    r.id, CompensationKind.valueOf(r.kind), r.createdAt, name, Verdict.valueOf(r.verdict),
                    r.sampleBiasMm, r.includeInLearning,
                )
            }
            CompensationUiState(
                c.profiles.printer(p)?.name ?: "",
                c.profiles.filament(f)?.name ?: "",
                cards,
                rows,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CompensationUiState())

    private fun ids() = c.settings.activePrinterId.value to c.settings.activeFilamentId.value

    fun setManual(kind: CompensationKind, bias: Double) {
        val (p, f) = ids()
        viewModelScope.launch { c.compensation.setManual(p, f, kind, bias) }
    }

    fun releaseManual(kind: CompensationKind) {
        val (p, f) = ids()
        viewModelScope.launch { c.compensation.releaseManual(p, f, kind) }
    }

    fun resetToDefault(kind: CompensationKind) {
        val (p, f) = ids()
        viewModelScope.launch { c.compensation.resetToDefault(p, f, kind) }
    }

    fun setInclude(resultId: Long, include: Boolean) {
        viewModelScope.launch { c.tests.setInclude(resultId, include) }
    }
}
