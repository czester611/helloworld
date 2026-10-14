package com.fitlab.app.ui.screens.photo

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitlab.app.AppContainer
import com.fitlab.app.data.repo.NewMeasurement
import com.fitlab.app.data.repo.PartRepository
import com.fitlab.app.platform.ExifReader
import com.fitlab.app.platform.ImageLoader
import com.fitlab.app.ui.Fmt
import com.fitlab.core.design.HoleFeature
import com.fitlab.core.design.OutlineDesign
import com.fitlab.core.fit.FitClass
import com.fitlab.core.fit.FitReference
import com.fitlab.core.photo.CameraPose
import com.fitlab.core.photo.PhotoOutline
import com.fitlab.core.photo.RawContours
import com.fitlab.core.sheet.MarkerSheetSpec
import com.fitlab.vision.PhotoPipeline
import com.fitlab.vision.PipelineException
import com.fitlab.vision.PipelineInput
import com.fitlab.vision.Segmenter
import com.fitlab.vision.SegmentationParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PhotoStage { Instructions, HasPhoto, Processing, Result, Error }

data class PhotoUiState(
    val stage: PhotoStage = PhotoStage.Instructions,
    val thumbnail: Bitmap? = null,
    val thicknessText: String = "",
    val nameText: String = "",
    val outline: PhotoOutline? = null,
    val raw: RawContours? = null,
    val rectifiedCrop: Bitmap? = null,
    val warnings: List<String> = emptyList(),
    val threshold: Int = 128,
    val autoThreshold: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val createdPartId: Long? = null,
) {
    val thickness: Double? get() = Fmt.parse(thicknessText)
    val thicknessError: String?
        get() = if (thickness.let { it == null || it < 0.4 || it > 200.0 }) "Podaj grubość 0,4–200 mm" else null
}

class PhotoCaptureViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(PhotoUiState(nameText = defaultName()))
    val state: StateFlow<PhotoUiState> = _state.asStateFlow()

    private var photoFile: File? = null
    private var rectified: Mat? = null
    private var pose: CameraPose? = null
    private var baseWarnings: List<String> = emptyList()

    private fun defaultName() = "Część ze zdjęcia " + SimpleDateFormat("dd.MM", Locale.ROOT).format(Date())

    /** Przygotowuje plik docelowy dla aparatu. */
    fun prepareCaptureFile(): File = c.fileStore.newCaptureFile().also { photoFile = it }

    /** Zdjęcie z aparatu zapisane (lub nie) w pliku z [prepareCaptureFile]. */
    fun onCaptured(success: Boolean) {
        val f = photoFile
        if (success && f != null && f.length() > 0) loadPhoto(f)
    }

    /** Zdjęcie wybrane z galerii: kopiowane do cache. */
    fun onPicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            try {
                val f = withContext(Dispatchers.IO) {
                    val file = c.fileStore.newCaptureFile()
                    c.appContext.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    }
                    file
                }
                photoFile = f
                loadPhoto(f)
            } catch (e: Exception) {
                _state.update { it.copy(stage = PhotoStage.Error, error = "Nie udało się wczytać zdjęcia.") }
            }
        }
    }

    private fun loadPhoto(f: File) {
        viewModelScope.launch {
            try {
                val thumb = withContext(Dispatchers.Default) { ImageLoader.thumbnail(f) }
                _state.update { it.copy(stage = PhotoStage.HasPhoto, thumbnail = thumb, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(stage = PhotoStage.Error, error = "Nie udało się wczytać zdjęcia.") }
            }
        }
    }

    fun setThickness(t: String) = _state.update { it.copy(thicknessText = t) }
    fun setName(t: String) = _state.update { it.copy(nameText = t) }

    private fun releaseRectified() {
        rectified?.release()
        rectified = null
    }

    /** Wraca do ekranu z instrukcją („Ponów zdjęcie”). */
    fun retake() {
        releaseRectified()
        _state.update {
            PhotoUiState(nameText = it.nameText, thicknessText = it.thicknessText)
        }
    }

    private suspend fun cropBitmap(rect: Mat): Bitmap = withContext(Dispatchers.Default) {
        val crop = rect.submat(Segmenter.ROI)
        try {
            val bmp = Bitmap.createBitmap(crop.cols(), crop.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(crop, bmp)
            bmp
        } finally {
            crop.release()
        }
    }

    /** Uruchamia pełny pipeline (wykrycie markerów, rektyfikacja, segmentacja). */
    fun process() {
        val f = photoFile ?: return
        val s = _state.value
        val thickness = s.thickness
        if (s.thicknessError != null || thickness == null) return
        _state.update { it.copy(stage = PhotoStage.Processing, error = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val mat = ImageLoader.load(f)
                    try {
                        val sheet = MarkerSheetSpec(c.settings.sheetScaleX.value, c.settings.sheetScaleY.value)
                        PhotoPipeline.run(PipelineInput(mat, ExifReader.focal35mm(f), thickness, SegmentationParams(), sheet))
                    } finally {
                        mat.release()
                    }
                }
                releaseRectified()
                rectified = result.rectified
                result.mask.release()
                pose = result.pose
                baseWarnings = result.warnings.filter { w -> w !in result.outline.warnings }
                val crop = cropBitmap(result.rectified)
                _state.update {
                    it.copy(
                        stage = PhotoStage.Result, outline = result.outline, raw = result.raw, rectifiedCrop = crop,
                        warnings = result.warnings, threshold = result.threshold, autoThreshold = true,
                    )
                }
            } catch (e: PipelineException) {
                _state.update { it.copy(stage = PhotoStage.Error, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(stage = PhotoStage.Error, error = "Błąd przetwarzania: ${e.message}") }
            }
        }
    }

    /** Ponowna segmentacja z innym progiem (homografia z cache). [auto] = Otsu. */
    fun resegment(auto: Boolean, threshold: Int) {
        val rect = rectified ?: return
        val thickness = _state.value.thickness ?: return
        _state.update { it.copy(busy = true, autoThreshold = auto, threshold = threshold) }
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.Default) {
                    PhotoPipeline.reprocess(
                        rect, thickness, pose,
                        SegmentationParams(manualThreshold = if (auto) null else threshold),
                        baseWarnings,
                    )
                }
                r.mask.release()
                _state.update {
                    it.copy(
                        busy = false, outline = r.outline, raw = r.raw, warnings = r.warnings, error = null,
                        threshold = if (auto) r.threshold else threshold,
                    )
                }
            } catch (e: PipelineException) {
                _state.update { it.copy(busy = false, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = "Błąd przetwarzania: ${e.message}") }
            }
        }
    }

    /** Zapisuje część (OutlineDesign) i przechodzi do edytora obrysu. */
    fun accept() {
        val s = _state.value
        val outline = s.outline ?: return
        val thickness = s.thickness ?: return
        val f = photoFile ?: return
        viewModelScope.launch {
            try {
                val photoPath = withContext(Dispatchers.IO) { c.fileStore.savePhoto(f) }
                val holes = outline.holeCandidates.mapIndexed { i, h ->
                    HoleFeature(
                        id = "hole-${i + 1}", center = h.center, nominalDiameterMm = h.diameterMm,
                        reference = FitReference.ORIGINAL, fit = FitClass.SLIDE, chamferMm = 0.0, needsCaliperCheck = true,
                    )
                }
                val design = OutlineDesign(
                    outer = outline.outer, innerContours = outline.innerContours, holes = holes, thicknessMm = thickness,
                )
                val id = c.parts.create(
                    s.nameText.ifBlank { defaultName() }, design, PartRepository.SOURCE_PHOTO, null, photoPath,
                    listOf(NewMeasurement("Grubość", thickness, "thickness")),
                )
                _state.update { it.copy(createdPartId = id) }
            } catch (e: Exception) {
                _state.update { it.copy(stage = PhotoStage.Error, error = "Nie udało się zapisać części: ${e.message}") }
            }
        }
    }

    override fun onCleared() {
        releaseRectified()
        super.onCleared()
    }
}
