package com.fitlab.app.ui.screens.compensation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitlab.app.ui.Fmt
import com.fitlab.app.ui.components.FitScaffold
import com.fitlab.app.ui.components.MmField
import com.fitlab.app.ui.components.label
import com.fitlab.core.fit.CompensationKind

fun kindTitle(k: CompensationKind): String = when (k) {
    CompensationKind.HOLE -> "Otwory"
    CompensationKind.SLOT -> "Szczeliny"
    CompensationKind.PIN -> "Czopy"
    CompensationKind.OUTLINE -> "Obrysy"
}

@Composable
fun CompensationScreen(viewModel: CompensationViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FitScaffold(title = "Kompensacja", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Dla: ${state.printerName} · ${state.filamentName}", style = MaterialTheme.typography.titleMedium)
            Text(
                "Bias = wymiar wydrukowany − wymiar w modelu (średnicowo). Ujemny dla otworów oznacza, że drukują się za małe.",
                style = MaterialTheme.typography.bodySmall,
            )
            state.cards.forEach { card ->
                KindCardView(
                    card,
                    onManualChange = { viewModel.setManual(card.kind, it) },
                    onManualToggle = { on ->
                        if (on) viewModel.setManual(card.kind, card.bias) else viewModel.releaseManual(card.kind)
                    },
                    onReset = { viewModel.resetToDefault(card.kind) },
                )
            }
            Text("Próbki", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            if (state.samples.isEmpty()) Text("Brak próbek. Wpisz wyniki wydruku próbnego albo użyj kalibracji.")
            state.samples.forEach { s ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${kindTitle(s.kind)} · ${s.source}")
                        Text(
                            "${Fmt.dateTime(s.whenMs)} · ${s.verdict.label()} · próbka ${Fmt.num(s.sampleMm)} mm",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text("Uwzględnij  ", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = s.include, onCheckedChange = { viewModel.setInclude(s.id, it) })
                }
            }
        }
    }
}

@Composable
private fun KindCardView(
    card: KindCard,
    onManualChange: (Double) -> Unit,
    onManualToggle: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    var text by remember(card.kind, card.manual) { mutableStateOf(Fmt.num(card.bias)) }
    val parsed = Fmt.parse(text)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(kindTitle(card.kind), style = MaterialTheme.typography.titleMedium)
            Text("${Fmt.mm(card.bias)}  ·  Próbek: ${card.sampleCount}", style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ręcznie  ")
                Switch(checked = card.manual, onCheckedChange = onManualToggle)
            }
            if (card.manual) {
                MmField(
                    label = "Bias",
                    value = text,
                    onValueChange = {
                        text = it
                        Fmt.parse(it)?.let { v -> if (v in -1.0..1.0) onManualChange(v) }
                    },
                    error = if (parsed == null || parsed < -1.0 || parsed > 1.0) "Podaj liczbę od -1 do 1" else null,
                    allowNegative = true,
                )
            }
            OutlinedButton(onClick = onReset) { Text("Przywróć domyślne") }
        }
    }
}
