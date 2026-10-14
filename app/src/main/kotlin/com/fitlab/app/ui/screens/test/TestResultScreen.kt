package com.fitlab.app.ui.screens.test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitlab.app.ui.Fmt
import com.fitlab.app.ui.components.ErrorBanner
import com.fitlab.app.ui.components.FitScaffold
import com.fitlab.app.ui.components.InfoDialog
import com.fitlab.app.ui.components.MmField
import com.fitlab.app.ui.components.VerdictSelector
import com.fitlab.app.ui.screens.compensation.kindTitle
import com.fitlab.core.fit.ResolvedFeature
import com.fitlab.core.fit.Verdict

private val AMOUNTS = listOf(0.05, 0.1, 0.2, 0.3, 0.5)

@Composable
fun TestResultScreen(viewModel: TestResultViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FitScaffold(title = "Wynik próby", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.message?.let { ErrorBanner(it) }
            Text(state.title, style = MaterialTheme.typography.titleLarge)
            Text(state.subtitle, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                label = { Text("Notatka z próby") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            OutlinedButton(onClick = viewModel::fillFromNote) { Text("Wypełnij z notatki") }
            Text(
                "Kwota = o ile średnica/szerokość powinna być większa lub mniejsza.",
                style = MaterialTheme.typography.bodySmall,
            )
            state.features.forEach { f ->
                FeatureCard(
                    f = f,
                    edit = state.edits[f.id] ?: FeatureEdit(),
                    current = state.currentFeatureId == f.id,
                    onCurrent = { viewModel.setCurrent(f.id) },
                    onVerdict = { viewModel.setVerdict(f.id, it) },
                    onAmount = { viewModel.setAmountText(f.id, it) },
                    onMeasured = { viewModel.setMeasuredText(f.id, it) },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.learn, onCheckedChange = viewModel::setLearn)
                Text("Ucz kompensację na podstawie tej próby")
            }
            Button(onClick = viewModel::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                Text("Zapisz wynik")
            }
        }
    }
    state.changes?.let { changes ->
        val lines = if (changes.isEmpty()) listOf("Brak ocenionych cech.") else changes.map {
            "${kindTitle(it.kind)} ${Fmt.num(it.oldBias)} → ${Fmt.num(it.newBias)} mm"
        }
        InfoDialog("Kompensacja zaktualizowana", lines) {
            viewModel.dismissChanges()
            onBack()
        }
    }
}

@Composable
private fun FeatureCard(
    f: ResolvedFeature,
    edit: FeatureEdit,
    current: Boolean,
    onCurrent: () -> Unit,
    onVerdict: (Verdict?) -> Unit,
    onAmount: (String) -> Unit,
    onMeasured: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(f.label, style = MaterialTheme.typography.titleSmall)
                    Text("CAD: ${Fmt.mm(f.cadMm)} · cel: ${Fmt.mm(f.targetMm)}", style = MaterialTheme.typography.bodySmall)
                }
                FilterChip(selected = current, onClick = onCurrent, label = { Text("Bieżąca") })
            }
            VerdictSelector(edit.verdict, onVerdict)
            if (edit.verdict == null) Text("Pominięto tę cechę", style = MaterialTheme.typography.bodySmall)
            if (edit.verdict == Verdict.TOO_TIGHT || edit.verdict == Verdict.TOO_LOOSE) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AMOUNTS.forEach { a ->
                        FilterChip(
                            selected = edit.amount == a,
                            onClick = { onAmount(Fmt.num(a)) },
                            label = { Text(Fmt.num(a)) },
                        )
                    }
                }
                MmField("Inna kwota", edit.amountText, onAmount, error = if ((edit.amount ?: 0.0) <= 0.0) "Podaj kwotę > 0" else null)
            }
            if (edit.verdict == Verdict.MEASURED) {
                MmField(
                    "Zmierzony wymiar", edit.measuredText, onMeasured,
                    error = if (edit.measured == null) "Podaj liczbę" else null,
                )
            }
        }
    }
}
