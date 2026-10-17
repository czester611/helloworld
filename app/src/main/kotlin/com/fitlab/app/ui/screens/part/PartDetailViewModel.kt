package com.fitlab.app.ui.screens.part

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitlab.app.AppContainer
import com.fitlab.app.data.db.FilamentProfileEntity
import com.fitlab.app.data.db.PartEntity
import com.fitlab.app.platform.ExportPlan
import com.fitlab.app.ui.components.label
import com.fitlab.core.design.BuildResult
import com.fitlab.core.design.DesignBuilder
import com.fitlab.core.design.DesignJson
import com.fitlab.core.design.EnclosureDesign
import com.fitlab.core.enclosure.EnclosureBuilder
import com.fitlab.core.enclosure.MachineLimits
import com.fitlab.core.design.PartDesign
import com.fitlab.core.export.IterationType
import com.fitlab.core.fit.CompensationKind
import com.fitlab.core.fit.CompensationTable
import com.fitlab.core.fit.ResolvedFeature
import com.fitlab.core.fit.Verdict
import com.fitlab.core.geom.Shape2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class IterationRow(
    val id: Long,
    val number: Int,
    val type: IterationType,
    val createdAt: Long,
    val format: String?,
    val resultSummary: String,
)

data class PartDetailUiState(
    val part: PartEntity? = null,
    val printerName: String = "",
    val filamentName: String = "",
    val comp: CompensationTable = CompensationTable.ZERO,
    val shapes: List<Shape2> = emptyList(),
    val features: List<ResolvedFeature> = emptyList(),
    val buildError: String? = null,
    val iterations: List<IterationRow> = emptyList(),
    val deleted: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PartDetailViewModel(private val c: AppContainer, private val partId: Long) : ViewModel() {
    private val pair = combine(c.settings.activePrinterId, c.settings.activeFilamentId) { p, f -> p to f }
    private val ruleChanges = pair.flatMapLatest { (p, f) -> c.compensation.observeRules(p, f) }

    val state: StateFlow<PartDetailUiState> = combine(
        c.parts.observe(partId),
        ruleChanges,
        c.tests.observeForPart(partId),
        pair,
    ) { part, _, iterations, _ ->
        if (part == null) return@combine PartDetailUiState(deleted = true)
        val ctx = c.buildContext()
        var build: BuildResult? = null
        var error: String? = null
        try {
            build = DesignBuilder.build(DesignJson.decode(part.designJson), ctx.comp, ctx.print)
        } catch (e: Exception) {
            error = e.message ?: "Nie udało się zbudować modelu"
        }
        PartDetailUiState(
            part = part,
            printerName = ctx.printer.name,
            filamentName = "${ctx.filament.name} (${ctx.filament.material})",
            comp = ctx.comp,
            shapes = build?.solid?.footprint() ?: emptyList(),
            features = build?.features ?: emptyList(),
            buildError = error,
            iterations = iterations.map { it ->
                IterationRow(
                    it.id, it.number, IterationType.valueOf(it.type), it.createdAt, it.exportFormat,
                    summaryOf(it.id, it.resultRecordedAt != null),
                )
            },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PartDetailUiState())

    val filaments: StateFlow<List<FilamentProfileEntity>> = c.profiles.observeFilaments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Ustawia aktywny filament (kompensacja uczona per filament zostaje bez zmian). */
    fun useFilament(id: Long) {
        c.settings.setActiveFilament(id)
    }

    private suspend fun summaryOf(iterationId: Long, recorded: Boolean): String {
        if (!recorded) return "wynik: brak"
        val rs = c.tests.results(iterationId)
        if (rs.isEmpty()) return "wynik: brak"
        return "wynik: " + rs.take(3).joinToString(", ") {
            "${Verdict.valueOf(it.verdict).label().lowercase()} (${kindWord(CompensationKind.valueOf(it.kind))})"
        }
    }

    private fun kindWord(k: CompensationKind) = when (k) {
        CompensationKind.HOLE -> "otwór"
        CompensationKind.SLOT -> "szczelina"
        CompensationKind.PIN -> "czop"
        CompensationKind.OUTLINE -> "obrys"
    }

    fun rename(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { c.parts.rename(partId, name.trim()) }
    }

    fun setStatus(status: String) {
        viewModelScope.launch { c.parts.setStatus(partId, status) }
    }

    /** Notatki zapisywane w zasięgu aplikacji, żeby nie zginęły przy zamknięciu ekranu. */
    fun saveNotes(notes: String) {
        c.applicationScope.launch(Dispatchers.IO) { c.parts.setNotes(partId, notes) }
    }

    fun delete() {
        viewModelScope.launch { c.parts.delete(partId) }
    }

    /** Buduje plan eksportu dla wybranego rodzaju próby (wywoływać poza wątkiem głównym). */
    suspend fun makePlan(type: IterationType): ExportPlan = withContext(Dispatchers.Default) {
        val part = requireNotNull(c.parts.get(partId)) { "Nie znaleziono części" }
        val ctx = c.buildContext()
        val design: PartDesign = DesignJson.decode(part.designJson)
        if (design is EnclosureDesign) {
            val machine = MachineLimits(nozzleMm = ctx.printer.nozzleDiameterMm)
            val r = if (type == IterationType.FIT_TEST) {
                EnclosureBuilder.buildFitTest(design.spec, ctx.comp, ctx.print, machine)
            } else {
                EnclosureBuilder.build(design.spec, ctx.comp, ctx.print, machine)
            }
            return@withContext ExportPlan(partId, part.name, type, part.designJson, r.toBuildResult(), ctx, r.parts)
        }
        val build = if (type == IterationType.FIT_TEST) {
            DesignBuilder.buildFitTest(design, ctx.comp, ctx.print)
        } else {
            DesignBuilder.build(design, ctx.comp, ctx.print)
        }
        ExportPlan(partId, part.name, type, part.designJson, build, ctx)
    }
}
