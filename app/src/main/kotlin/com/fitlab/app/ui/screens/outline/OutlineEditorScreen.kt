package com.fitlab.app.ui.screens.outline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitlab.app.ui.Fmt
import com.fitlab.app.ui.components.ErrorBanner
import com.fitlab.app.ui.components.FitClassSelector
import com.fitlab.app.ui.components.FitScaffold
import com.fitlab.app.ui.components.MmField
import com.fitlab.app.ui.components.ShapeCanvas
import com.fitlab.core.fit.FitReference
import com.fitlab.core.geom.Shape2
import com.fitlab.core.geom.Shapes
import com.fitlab.core.geom.Vec2

@Composable
fun OutlineEditorScreen(viewModel: OutlineEditorViewModel, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedPartId) { state.savedPartId?.let(onSaved) }
    val design = state.design

    FitScaffold(
        title = "Edytor obrysu",
        onBack = onBack,
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    onClick = viewModel::save, enabled = design != null && !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Zapisz") }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.errors.forEach { ErrorBanner(it) }
            if (design == null) return@Column

            val holeRings = design.holes.map { Shapes.circle(it.center, it.nominalDiameterMm).reversed() }
            val shape = Shape2(design.outer, design.innerContours + holeRings)
            val labels = design.holes.map { it.center to "H" + it.id.removePrefix("hole-") }
            ShapeCanvas(
                shapes = listOf(shape), modifier = Modifier.fillMaxWidth().height(300.dp),
                dashedRings = design.innerContours, labels = labels, showGrid = true, showDims = true,
                onTapMm = viewModel::onTap,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dodaj otwór (dotknij kanwę)  ")
                Switch(checked = state.addMode, onCheckedChange = { viewModel.toggleAddMode() })
            }

            MmField("Grubość", state.thicknessText, viewModel::setThickness)

            Text("Wymiar referencyjny", style = MaterialTheme.typography.titleMedium)
            MmField("Zmierzona szerokość X", state.refWidthText, viewModel::setRefWidth)
            MmField("Zmierzona wysokość Y", state.refHeightText, viewModel::setRefHeight)
            OutlinedButton(onClick = viewModel::applyScale) { Text("Zastosuj skalę") }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::rotate90) { Text("Obróć 90°") }
                OutlinedButton(onClick = viewModel::mirror) { Text("Lustro") }
            }
            MmField("Fazka górnej krawędzi (0 = brak)", state.topChamferText, viewModel::setTopChamfer)

            Text("Otwory", style = MaterialTheme.typography.titleMedium)
            if (design.holes.isEmpty()) Text("Brak otworów.")
            design.holes.forEach { h ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("H" + h.id.removePrefix("hole-"), style = MaterialTheme.typography.titleSmall)
                        MmField(
                            "Średnica Ø", state.holeDiaTexts[h.id] ?: "", { viewModel.setHoleDiameter(h.id, it) },
                            hint = if (h.needsCaliperCheck) "Zmierz suwmiarką — wartość ze zdjęcia jest orientacyjna" else null,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = h.reference == FitReference.ORIGINAL,
                                onClick = { viewModel.setHoleReference(h.id, FitReference.ORIGINAL) },
                                label = { Text("Oryginalny otwór") },
                            )
                            FilterChip(
                                selected = h.reference == FitReference.MATING,
                                onClick = { viewModel.setHoleReference(h.id, FitReference.MATING) },
                                label = { Text("Wałek współpracujący") },
                            )
                        }
                        if (h.reference == FitReference.MATING) {
                            FitClassSelector(h.fit, { viewModel.setHoleFit(h.id, it) })
                        }
                        MmField("Fazka", state.holeChamferTexts[h.id] ?: "", { viewModel.setHoleChamfer(h.id, it) })
                        TextButton(onClick = { viewModel.removeHole(h.id) }) { Text("Usuń") }
                    }
                }
            }

            Text("Wewnętrzne kontury", style = MaterialTheme.typography.titleMedium)
            if (design.innerContours.isEmpty()) Text("Brak wewnętrznych konturów.")
            design.innerContours.forEachIndexed { i, r ->
                val b = r.bounds()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Wycięcie S${i + 1} (${Fmt.num(b.width, 1)} × ${Fmt.num(b.height, 1)} mm)",
                        Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.convertInnerToHole(i) }) { Text("Zamień na otwór") }
                    TextButton(onClick = { viewModel.removeInner(i) }) { Text("Usuń") }
                }
            }
        }
    }

    state.pendingHoleAt?.let { at -> AddHoleDialog(at, viewModel::confirmAddHole, viewModel::cancelAddHole) }
}

@Composable
private fun AddHoleDialog(at: Vec2, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val v = Fmt.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Średnica otworu (mm)") },
        text = {
            Column {
                Text("Pozycja: ${Fmt.num(at.x, 1)} ; ${Fmt.num(at.y, 1)} mm", style = MaterialTheme.typography.bodySmall)
                MmField("Średnica", text, { text = it }, if (text.isNotEmpty() && (v == null || v <= 0)) "Podaj liczbę > 0" else null)
            }
        },
        confirmButton = { TextButton(enabled = v != null && v > 0, onClick = { v?.let(onConfirm) }) { Text("Dodaj") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
