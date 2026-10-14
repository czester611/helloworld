package com.fitlab.app.ui.screens.photo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitlab.app.platform.ShareHelper
import com.fitlab.app.ui.Fmt
import com.fitlab.app.ui.components.ErrorBanner
import com.fitlab.app.ui.components.FitScaffold
import com.fitlab.app.ui.components.MmField
import com.fitlab.app.ui.components.WarningBanner
import com.fitlab.core.geom.Vec2
import com.fitlab.core.photo.RawContours
import com.fitlab.vision.Segmenter

@Composable
fun PhotoCaptureScreen(viewModel: PhotoCaptureViewModel, onBack: () -> Unit, onCreated: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> viewModel.onCaptured(ok) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> viewModel.onPicked(uri) }
    LaunchedEffect(state.createdPartId) { state.createdPartId?.let(onCreated) }

    val takePhoto = {
        val file = viewModel.prepareCaptureFile()
        camera.launch(ShareHelper.uriFor(context, file))
    }
    val pick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    FitScaffold(title = "Część ze zdjęcia", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.stage) {
                PhotoStage.Instructions -> {
                    Text("Jak zrobić dobre zdjęcie", style = MaterialTheme.typography.titleMedium)
                    listOf(
                        "Połóż kartkę na płaskim stole",
                        "Część w ramce pola roboczego",
                        "Rób zdjęcie z góry, z aparatem nad środkiem części, z wysokości ~30–50 cm",
                        "Rozproszone światło, bez lampy błyskowej",
                        "Jasna część? Połóż pod nią ciemną kartkę w polu roboczym",
                    ).forEach { Text("• $it") }
                    Button(onClick = takePhoto, modifier = Modifier.fillMaxWidth()) { Text("Zrób zdjęcie") }
                    OutlinedButton(onClick = pick, modifier = Modifier.fillMaxWidth()) { Text("Wybierz z galerii") }
                }
                PhotoStage.HasPhoto -> {
                    state.thumbnail?.let {
                        Image(
                            bitmap = it.asImageBitmap(), contentDescription = "Zdjęcie", contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().height(280.dp),
                        )
                    }
                    MmField(
                        "Grubość części (zmierz suwmiarką)", state.thicknessText, viewModel::setThickness,
                        error = if (state.thicknessText.isNotEmpty()) state.thicknessError else null,
                    )
                    OutlinedTextField(
                        value = state.nameText, onValueChange = viewModel::setName, label = { Text("Nazwa części") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = viewModel::process, enabled = state.thicknessError == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Przetwórz") }
                    OutlinedButton(onClick = viewModel::retake, modifier = Modifier.fillMaxWidth()) { Text("Inne zdjęcie") }
                }
                PhotoStage.Processing -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Wykrywanie markerów i obrysu…")
                    }
                }
                PhotoStage.Result -> ResultContent(state, viewModel)
                PhotoStage.Error -> {
                    ErrorBanner(state.error ?: "Wystąpił błąd.")
                    Button(onClick = viewModel::retake) { Text("Spróbuj ponownie") }
                }
            }
        }
    }
}

@Composable
private fun ResultContent(state: PhotoUiState, vm: PhotoCaptureViewModel) {
    val outline = state.outline
    val crop = state.rectifiedCrop
    if (crop != null) {
        Box(Modifier.fillMaxWidth().aspectRatio(Segmenter.ROI.width.toFloat() / Segmenter.ROI.height)) {
            Image(
                bitmap = crop.asImageBitmap(), contentDescription = "Zrektyfikowane zdjęcie",
                contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize(),
            )
            val primary = MaterialTheme.colorScheme.primary
            val tertiary = MaterialTheme.colorScheme.tertiary
            val raw = state.raw
            Canvas(Modifier.fillMaxSize()) {
                if (raw != null) {
                    val k = size.width / Segmenter.ROI.width
                    fun path(pts: List<Vec2>) = Path().apply {
                        pts.forEachIndexed { i, p ->
                            val o = Offset(((p.x - Segmenter.ROI.x) * k).toFloat(), ((p.y - Segmenter.ROI.y) * k).toFloat())
                            if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
                        }
                        close()
                    }
                    drawPath(path(raw.outerPx), primary, style = Stroke(2.dp.toPx()))
                    raw.holesPx.forEach { drawPath(path(it), tertiary, style = Stroke(2.dp.toPx())) }
                }
            }
        }
    }
    if (outline != null) {
        val b = outline.outer.bounds()
        Text(
            "Szer. × Wys.: ${Fmt.num(b.width, 1)} × ${Fmt.num(b.height, 1)} mm",
            style = MaterialTheme.typography.titleMedium,
        )
        outline.holeCandidates.forEachIndexed { i, h -> Text("Otwór H${i + 1}: Ø ${Fmt.num(h.diameterMm, 2)} mm (orientacyjnie)") }
        Text(
            "Średnice otworów ze zdjęcia są tylko orientacyjne — zawsze zmierz je suwmiarką.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    state.error?.let { ErrorBanner(it) }
    state.warnings.forEach { WarningBanner(it) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Auto (Otsu)  ")
        Switch(checked = state.autoThreshold, onCheckedChange = { vm.resegment(it, state.threshold) })
        if (state.busy) CircularProgressIndicator(Modifier.padding(start = 12.dp))
    }
    var slider by remember(state.threshold) { mutableStateOf(state.threshold.toFloat()) }
    Text("Próg: ${slider.toInt()}")
    Slider(
        value = slider, onValueChange = { slider = it }, valueRange = 0f..255f,
        enabled = !state.autoThreshold, onValueChangeFinished = { vm.resegment(false, slider.toInt()) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = vm::retake) { Text("Ponów zdjęcie") }
        Button(onClick = vm::accept, enabled = outline != null && !state.busy) { Text("Dalej") }
    }
}
