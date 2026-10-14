package com.fitlab.app.ui.screens.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitlab.app.ui.Fmt
import com.fitlab.app.ui.components.ErrorBanner
import com.fitlab.app.ui.components.FitClassSelector
import com.fitlab.app.ui.components.FitScaffold
import com.fitlab.app.ui.components.MmField
import com.fitlab.app.ui.components.ShapeCanvas
import com.fitlab.core.fit.FitClass
import com.fitlab.core.fit.ResolvedFeature
import com.fitlab.core.templates.ParamType
import com.fitlab.core.templates.TemplateRegistry

@Composable
fun TemplateEditorScreen(viewModel: TemplateEditorViewModel, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val template = TemplateRegistry.get(state.templateId)
    LaunchedEffect(state.savedPartId) { state.savedPartId?.let(onSaved) }

    val preview = state.preview
    FitScaffold(
        title = template.displayName,
        onBack = onBack,
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    onClick = viewModel::save,
                    enabled = preview.valid && !state.saving && !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Zapisz") }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            preview.validation.filter { it.key == null }.forEach { ErrorBanner(it.message) }
            OutlinedTextField(
                value = state.input.name,
                onValueChange = viewModel::setName,
                label = { Text("Nazwa części") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Wymiary", style = MaterialTheme.typography.titleMedium)
            template.params.forEach { p ->
                val error = preview.parseErrors[p.key] ?: preview.validation.firstOrNull { it.key == p.key }?.message
                val text = state.input.texts[p.key] ?: ""
                when (p.type) {
                    ParamType.MM -> MmField(p.label, text, { viewModel.setText(p.key, it) }, error, hint = p.hint)
                    ParamType.COUNT -> OutlinedTextField(
                        value = text,
                        onValueChange = { viewModel.setText(p.key, it.filter { ch -> ch.isDigit() }) },
                        label = { Text(p.label) },
                        isError = error != null,
                        supportingText = { error?.let { Text(it) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ParamType.BOOL -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.label, Modifier.weight(1f))
                        Switch(checked = text == "1", onCheckedChange = { viewModel.setText(p.key, if (it) "1" else "0") })
                    }
                }
            }
            Text("Pasowania", style = MaterialTheme.typography.titleMedium)
            template.fitSlots.forEach { slot ->
                Text(slot.label)
                FitClassSelector(state.input.fits[slot.key] ?: slot.default, { viewModel.setFit(slot.key, it) })
            }
            preview.gearInfo?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text("Podgląd", style = MaterialTheme.typography.titleMedium)
            ShapeCanvas(preview.shapes, Modifier.fillMaxWidth().height(220.dp), showDims = true)
            FeatureTable(preview.features)
            preview.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.tertiary) }
        }
    }
}

/** Tabela cech: Cecha | Nominał | Cel | Kompensacja | CAD. */
@Composable
fun FeatureTable(features: List<ResolvedFeature>, showBias: Boolean = true) {
    if (features.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        val head = MaterialTheme.typography.labelMedium
        Row(Modifier.fillMaxWidth()) {
            Text("Cecha", Modifier.weight(2f), style = head)
            Text("Nominał", Modifier.weight(1f), style = head)
            Text("Cel", Modifier.weight(1f), style = head)
            if (showBias) Text("Kompens.", Modifier.weight(1f), style = head)
            Text("CAD", Modifier.weight(1f), style = head)
        }
        features.forEach { f ->
            Row(Modifier.fillMaxWidth()) {
                Text(f.label, Modifier.weight(2f), style = MaterialTheme.typography.bodySmall)
                Text(Fmt.num(f.nominalMm), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(Fmt.num(f.targetMm), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                if (showBias) Text(Fmt.num(f.biasMm), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(Fmt.num(f.cadMm), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
