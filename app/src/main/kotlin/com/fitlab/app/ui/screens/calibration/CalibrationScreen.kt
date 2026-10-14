package com.fitlab.app.ui.screens.calibration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitlab.app.ui.Fmt
import com.fitlab.app.ui.components.ErrorBanner
import com.fitlab.app.ui.components.ExportSheet
import com.fitlab.app.ui.components.FitScaffold
import com.fitlab.app.ui.components.InfoDialog
import com.fitlab.app.ui.components.MmField
import com.fitlab.app.ui.screens.compensation.kindTitle

@Composable
fun CalibrationScreen(viewModel: CalibrationViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var exporting by remember { mutableStateOf(false) }
    FitScaffold(title = "Kalibracja", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.message?.let { ErrorBanner(it) }
            Text("Krok 1. Wydrukuj kupon", style = MaterialTheme.typography.titleMedium)
            Text(
                "Kupon to płytka 44×20×3 mm z otworem Ø10, szczeliną 6 mm i czopem Ø8. " +
                    "Wydrukuj go na aktywnym profilu drukarki i filamentu, a potem zmierz suwmiarką.",
            )
            Button(onClick = { exporting = true }) { Text("Eksportuj kupon") }

            val pending = state.pending
            if (pending != null) {
                Text("Krok 2. Wpisz pomiary (kupon #${pending.number})", style = MaterialTheme.typography.titleMedium)
                state.features.forEach { f ->
                    MmField(
                        label = "${f.label} (nominał ${Fmt.num(f.nominalMm, 1)})",
                        value = state.texts[f.id] ?: "",
                        onValueChange = { viewModel.setText(f.id, it) },
                        hint = kindTitle(f.kind),
                    )
                }
                Button(onClick = viewModel::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                    Text("Zapisz pomiary")
                }
            }
        }
    }
    if (exporting) {
        ExportSheet(
            title = "Kupon kalibracyjny",
            planProvider = { viewModel.makePlan() },
            onDismiss = { exporting = false },
            onExported = { },
        )
    }
    state.changes?.let { ch ->
        InfoDialog(
            "Kompensacja zaktualizowana",
            ch.map { "${kindTitle(it.kind)} ${Fmt.num(it.oldBias)} → ${Fmt.num(it.newBias)} mm" },
        ) { viewModel.dismissChanges() }
    }
}
