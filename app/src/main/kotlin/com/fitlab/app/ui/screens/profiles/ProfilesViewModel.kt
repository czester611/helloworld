package com.fitlab.app.ui.screens.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitlab.app.AppContainer
import com.fitlab.app.data.db.FilamentProfileEntity
import com.fitlab.app.data.db.PrinterProfileEntity
import com.fitlab.core.fit.Material
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfilesUiState(
    val printers: List<PrinterProfileEntity> = emptyList(),
    val filaments: List<FilamentProfileEntity> = emptyList(),
    val activePrinterId: Long = -1,
    val activeFilamentId: Long = -1,
    val message: String? = null,
)

class ProfilesViewModel(private val c: AppContainer) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<ProfilesUiState> = combine(
        c.profiles.observePrinters(),
        c.profiles.observeFilaments(),
        c.settings.activePrinterId,
        c.settings.activeFilamentId,
        message,
    ) { p, f, ap, af, m -> ProfilesUiState(p, f, ap, af, m) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfilesUiState())

    fun setActivePrinter(id: Long) = c.settings.setActivePrinter(id)
    fun setActiveFilament(id: Long) = c.settings.setActiveFilament(id)
    fun dismissMessage() = message.update { null }

    /** Zapis drukarki; [id] = 0 oznacza nową. */
    fun savePrinter(id: Long, name: String, nozzle: Double, layer: Double, foot: Double) {
        viewModelScope.launch {
            if (id == 0L) {
                c.profiles.addPrinter(name, nozzle, layer, foot)
            } else {
                val old = c.profiles.printer(id) ?: return@launch
                c.profiles.updatePrinter(
                    old.copy(name = name, nozzleDiameterMm = nozzle, layerHeightMm = layer, elephantFootMm = foot),
                )
            }
        }
    }

    fun deletePrinter(p: PrinterProfileEntity) {
        viewModelScope.launch {
            if (!c.profiles.deletePrinter(p)) message.value = "Nie można usunąć aktywnej ani ostatniej drukarki."
        }
    }

    /** Zapis filamentu; [id] = 0 oznacza nowy. */
    fun saveFilament(id: Long, name: String, material: Material, brand: String, color: String) {
        viewModelScope.launch {
            if (id == 0L) {
                c.profiles.addFilament(name, material, brand, color)
            } else {
                val old = c.profiles.filament(id) ?: return@launch
                c.profiles.updateFilament(old.copy(name = name, material = material.name, brand = brand, color = color))
            }
        }
    }

    fun deleteFilament(f: FilamentProfileEntity) {
        viewModelScope.launch {
            if (!c.profiles.deleteFilament(f)) message.value = "Nie można usunąć aktywnego ani ostatniego filamentu."
        }
    }
}
