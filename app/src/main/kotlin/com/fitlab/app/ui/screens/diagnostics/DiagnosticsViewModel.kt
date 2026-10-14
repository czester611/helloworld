package com.fitlab.app.ui.screens.diagnostics

import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitlab.app.AppContainer
import com.fitlab.app.platform.OpenCvInit
import com.fitlab.app.ui.Fmt
import com.fitlab.vision.PhotoPipeline
import com.fitlab.vision.PipelineInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat

data class DiagnosticsUiState(
    val openCvVersion: String = "",
    val appVersion: String = "",
    val running: Boolean = false,
    val ok: Boolean? = null,
    val result: String? = null,
)

class DiagnosticsViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(DiagnosticsUiState(appVersion = readVersion()))
    val state: StateFlow<DiagnosticsUiState> = _state.asStateFlow()

    private fun readVersion(): String = try {
        c.appContext.packageManager.getPackageInfo(c.appContext.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    init {
        _state.update {
            it.copy(
                openCvVersion = if (OpenCvInit.ensure()) Core.VERSION else "OpenCV nie załadowany",
            )
        }
    }

    /** Uruchamia pipeline na wbudowanym syntetycznym zdjęciu (grubość 0). */
    fun runTest() {
        _state.update { it.copy(running = true, ok = null, result = null) }
        viewModelScope.launch {
            val text = withContext(Dispatchers.Default) {
                if (!OpenCvInit.ensure()) return@withContext false to "Błąd: nie udało się załadować OpenCV."
                try {
                    val bmp = c.appContext.assets.open("selftest_photo.jpg").use { BitmapFactory.decodeStream(it) }
                        ?: return@withContext false to "Błąd: brak zasobu selftest_photo.jpg."
                    val mat = Mat()
                    Utils.bitmapToMat(bmp, mat)
                    bmp.recycle()
                    try {
                        val r = PhotoPipeline.run(PipelineInput(mat, null, 0.0))
                        val b = r.outline.outer.bounds()
                        r.rectified.release()
                        r.mask.release()
                        val hole = r.outline.holeCandidates.firstOrNull()
                        val good = hole != null && Math.abs(b.width - 40.0) < 0.5 && Math.abs(b.height - 25.0) < 0.5
                        val holeText = hole?.let { ", otwór Ø${Fmt.num(it.diameterMm, 1)}" } ?: ", brak otworu"
                        good to ((if (good) "OK: " else "Niezgodny wynik: ") + "${Fmt.num(b.width, 1)} × ${Fmt.num(b.height, 1)} mm$holeText")
                    } finally {
                        mat.release()
                    }
                } catch (e: Throwable) {
                    false to "Błąd: ${e.message ?: e.javaClass.simpleName}"
                }
            }
            _state.update { it.copy(running = false, ok = text.first, result = text.second) }
        }
    }
}
