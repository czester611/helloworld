package com.fitlab.app.ui.screens.markers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitlab.app.AppContainer
import com.fitlab.app.platform.MarkerSheetPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

data class MarkerSheetUiState(
    val preview: android.graphics.Bitmap? = null,
    val scaleX: Double = 1.0,
    val scaleY: Double = 1.0,
    val busy: Boolean = false,
    val message: String? = null,
)

class MarkerSheetViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(
        MarkerSheetUiState(scaleX = c.settings.sheetScaleX.value, scaleY = c.settings.sheetScaleY.value),
    )
    val state: StateFlow<MarkerSheetUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.Default) { MarkerSheetPdf.bitmap(2.0) }
            _state.update { it.copy(preview = bmp) }
        }
    }

    /** Zapisuje PDF do strumienia (CreateDocument). */
    fun savePdf(out: OutputStream, onDone: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            try {
                withContext(Dispatchers.IO) { out.use { MarkerSheetPdf.write(it) } }
                _state.update { it.copy(busy = false, message = "Zapisano PDF.") }
                onDone()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = e.message ?: "Nie udało się zapisać PDF") }
            }
        }
    }

    /** Generuje PDF w cache do udostępnienia. */
    fun preparePdfForShare(onReady: (File) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            try {
                val f = withContext(Dispatchers.IO) {
                    val file = c.fileStore.exportFile("fitlab_markery_A4.pdf")
                    file.outputStream().use { MarkerSheetPdf.write(it) }
                    file
                }
                _state.update { it.copy(busy = false) }
                onReady(f)
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = e.message ?: "Nie udało się utworzyć PDF") }
            }
        }
    }

    /** Korekta skali: sheetScaleX = zmierzone/180, sheetScaleY = zmierzone/267. */
    fun applyScale(measured01: Double?, measured03: Double?) {
        val sx = measured01?.let { it / EXPECTED_X_MM } ?: c.settings.sheetScaleX.value
        val sy = measured03?.let { it / EXPECTED_Y_MM } ?: c.settings.sheetScaleY.value
        c.settings.setSheetScale(sx, sy)
        _state.update { it.copy(scaleX = sx, scaleY = sy, message = "Zapisano korektę skali.") }
    }

    fun resetScale() {
        c.settings.setSheetScale(1.0, 1.0)
        _state.update { it.copy(scaleX = 1.0, scaleY = 1.0, message = "Przywrócono skalę 1,000.") }
    }

    companion object {
        const val EXPECTED_X_MM = 180.0
        const val EXPECTED_Y_MM = 267.0
    }
}
