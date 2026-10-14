package com.fitlab.app.ui.screens.part

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitlab.app.data.repo.PartRepository
import com.fitlab.app.ui.Fmt
import com.fitlab.app.ui.components.ConfirmDialog
import com.fitlab.app.ui.components.ErrorBanner
import com.fitlab.app.ui.components.ExportSheet
import com.fitlab.app.ui.components.FitScaffold
import com.fitlab.app.ui.components.ShapeCanvas
import com.fitlab.app.ui.screens.compensation.kindTitle
import com.fitlab.app.ui.screens.templates.FeatureTable
import com.fitlab.core.design.TemplateDesign
import com.fitlab.core.export.IterationType
import com.fitlab.core.fit.CompensationKind
import kotlinx.coroutines.launch

fun iterationTypeLabel(t: IterationType) = when (t) {
    IterationType.FIT_TEST -> "Plaster"
    IterationType.FULL_PART -> "Pełna część"
    IterationType.CALIBRATION -> "Kalibracja"
}

@Composable
fun PartDetailScreen(
    viewModel: PartDetailViewModel,
    onBack: () -> Unit,
    onEdit: (PartRoute) -> Unit,
    onOpenIteration: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var exportType by remember { mutableStateOf<IterationType?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val part = state.part
    var notes by remember(part?.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    LaunchedEffect(part?.notes) { if (notes == null && part != null) notes = part.notes }

    FitScaffold(
        title = part?.name ?: "Część",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (part == null) return@FitScaffold
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { renaming = true }) { Text("Zmień nazwę") }
                TextButton(onClick = { deleting = true }) { Text("Usuń") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    PartRepository.STATUS_DRAFT to "Szkic",
                    PartRepository.STATUS_TESTING to "Testowanie",
                    PartRepository.STATUS_DONE to "Gotowe",
                ).forEach { (key, label) ->
                    FilterChip(selected = part.status == key, onClick = { viewModel.setStatus(key) }, label = { Text(label) })
                }
            }
            OutlinedTextField(
                value = notes ?: "",
                onValueChange = { notes = it; viewModel.saveNotes(it) },
                label = { Text("Notatki") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            ShapeCanvas(state.shapes, Modifier.fillMaxWidth().height(200.dp), showDims = true)
            OutlinedButton(onClick = {
                val route = if (part.source == PartRepository.SOURCE_PHOTO) {
                    PartRoute.Outline(part.id)
                } else {
                    PartRoute.Template(part.templateId ?: "", part.id)
                }
                onEdit(route)
            }) { Text("Edytuj") }
            state.buildError?.let { ErrorBanner(it) }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Kompensacja dla: ${state.printerName} · ${state.filamentName}", style = MaterialTheme.typography.titleSmall)
                    CompensationKind.entries.forEach { k ->
                        Text("${kindTitle(k)}: ${Fmt.mm(state.comp.bias(k))}")
                    }
                }
            }
            FeatureTable(state.features, showBias = false)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportType = IterationType.FIT_TEST }, enabled = state.buildError == null) {
                    Text("Wydruk próbny (plaster)")
                }
                OutlinedButton(onClick = { exportType = IterationType.FULL_PART }, enabled = state.buildError == null) {
                    Text("Pełna część")
                }
            }

            Text("Historia prób", style = MaterialTheme.typography.titleMedium)
            if (state.iterations.isEmpty()) Text("Brak prób.")
            state.iterations.forEach { it ->
                Card(Modifier.fillMaxWidth().clickable { onOpenIteration(it.id) }) {
                    Text(
                        "#${it.number} · ${iterationTypeLabel(it.type)} · ${Fmt.dayMonth(it.createdAt)} · " +
                            "${it.format ?: "-"} · ${it.resultSummary}",
                        Modifier.padding(12.dp),
                    )
                }
            }
        }
    }

    exportType?.let { type ->
        ExportSheet(
            title = if (type == IterationType.FIT_TEST) "Wydruk próbny (plaster)" else "Pełna część",
            planProvider = { viewModel.makePlan(type) },
            onDismiss = { exportType = null },
            onExported = { outcome ->
                scope.launch { snackbar.showSnackbar("Zapisano próbę #${outcome.number}. Po wydruku wpisz wynik.") }
            },
        )
    }
    if (renaming && part != null) {
        var text by remember { mutableStateOf(part.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Nazwa części") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { viewModel.rename(text); renaming = false }) { Text("Zapisz") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Anuluj") } },
        )
    }
    if (deleting) {
        ConfirmDialog("Usuń część", "Usunąć część wraz z historią prób?", {
            deleting = false
            viewModel.delete()
        }, { deleting = false })
    }
}

/** Cel edycji części. */
sealed interface PartRoute {
    data class Outline(val partId: Long) : PartRoute
    data class Template(val templateId: String, val partId: Long) : PartRoute
}
