package com.fitlab.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitlab.app.AppContainer
import com.fitlab.app.data.db.PartEntity
import com.fitlab.core.design.DesignBuilder
import com.fitlab.core.design.DesignJson
import com.fitlab.core.design.PrintSettings
import com.fitlab.core.fit.CompensationTable
import com.fitlab.core.geom.Shape2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PartRow(
    val id: Long,
    val name: String,
    val status: String,
    val tests: Int,
    val updatedAt: Long,
    val shapes: List<Shape2>,
)

data class HomeUiState(
    val parts: List<PartRow> = emptyList(),
    val printerName: String = "",
    val filamentName: String = "",
    val material: String = "",
    val loaded: Boolean = false,
)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val thumbs = HashMap<Pair<Long, Long>, List<Shape2>>()

    private fun footprint(p: PartEntity): List<Shape2> = thumbs.getOrPut(p.id to p.updatedAt) {
        try {
            val design = DesignJson.decode(p.designJson)
            DesignBuilder.build(design, CompensationTable.ZERO, PrintSettings(0.2, 0.0)).solid.footprint()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private val active = combine(
        c.profiles.observePrinters(),
        c.profiles.observeFilaments(),
        c.settings.activePrinterId,
        c.settings.activeFilamentId,
    ) { printers, filaments, pid, fid ->
        Triple(printers.firstOrNull { it.id == pid }?.name ?: "", filaments.firstOrNull { it.id == fid }, 0)
    }

    val state: StateFlow<HomeUiState> = combine(
        c.parts.observeAll(),
        c.tests.observeCounts(),
        active,
    ) { parts, counts, act ->
        val byPart = counts.associate { it.partId to it.n }
        HomeUiState(
            parts = parts.map { PartRow(it.id, it.name, it.status, byPart[it.id] ?: 0, it.updatedAt, footprint(it)) },
            printerName = act.first,
            filamentName = act.second?.name ?: "",
            material = act.second?.material ?: "",
            loaded = true,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())
}
