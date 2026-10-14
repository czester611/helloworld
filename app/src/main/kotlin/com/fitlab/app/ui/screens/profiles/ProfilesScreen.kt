package com.fitlab.app.ui.screens.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitlab.app.data.db.FilamentProfileEntity
import com.fitlab.app.data.db.PrinterProfileEntity
import com.fitlab.app.ui.Fmt
import com.fitlab.app.ui.components.ConfirmDialog
import com.fitlab.app.ui.components.ErrorBanner
import com.fitlab.app.ui.components.FitScaffold
import com.fitlab.app.ui.components.MmField
import com.fitlab.core.fit.Material

private sealed interface Editing {
    data class Printer(val p: PrinterProfileEntity?) : Editing
    data class Filament(val f: FilamentProfileEntity?) : Editing
}

@Composable
fun ProfilesScreen(viewModel: ProfilesViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Editing?>(null) }
    var deletingPrinter by remember { mutableStateOf<PrinterProfileEntity?>(null) }
    var deletingFilament by remember { mutableStateOf<FilamentProfileEntity?>(null) }

    FitScaffold(title = "Profile", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.message?.let { ErrorBanner(it) }
            Text("Drukarki", style = MaterialTheme.typography.titleMedium)
            state.printers.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = p.id == state.activePrinterId, onClick = { viewModel.setActivePrinter(p.id) })
                    Column(Modifier.weight(1f)) {
                        Text(p.name)
                        Text(
                            "dysza ${Fmt.num(p.nozzleDiameterMm)} · warstwa ${Fmt.num(p.layerHeightMm)} · stopa ${Fmt.num(p.elephantFootMm)} mm",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = { editing = Editing.Printer(p) }) { Text("Edytuj") }
                    TextButton(onClick = { deletingPrinter = p }) { Text("Usuń") }
                }
            }
            OutlinedButton(onClick = { editing = Editing.Printer(null) }) { Text("Dodaj drukarkę") }

            Text("Filamenty", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            state.filaments.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = f.id == state.activeFilamentId, onClick = { viewModel.setActiveFilament(f.id) })
                    Column(Modifier.weight(1f)) {
                        Text(f.name)
                        Text("${f.material} ${f.brand} ${f.color}".trim(), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { editing = Editing.Filament(f) }) { Text("Edytuj") }
                    TextButton(onClick = { deletingFilament = f }) { Text("Usuń") }
                }
            }
            OutlinedButton(onClick = { editing = Editing.Filament(null) }) { Text("Dodaj filament") }
        }
    }

    when (val e = editing) {
        is Editing.Printer -> PrinterDialog(
            e.p,
            onSave = { name, n, l, f ->
                viewModel.savePrinter(e.p?.id ?: 0L, name, n, l, f)
                editing = null
            },
            onDismiss = { editing = null },
        )
        is Editing.Filament -> FilamentDialog(
            e.f,
            onSave = { name, m, b, c ->
                viewModel.saveFilament(e.f?.id ?: 0L, name, m, b, c)
                editing = null
            },
            onDismiss = { editing = null },
        )
        null -> Unit
    }
    deletingPrinter?.let { p ->
        ConfirmDialog("Usuń drukarkę", "Usunąć profil „${p.name}” wraz z regułami kompensacji?", {
            viewModel.deletePrinter(p); deletingPrinter = null
        }, { deletingPrinter = null })
    }
    deletingFilament?.let { f ->
        ConfirmDialog("Usuń filament", "Usunąć profil „${f.name}” wraz z regułami kompensacji?", {
            viewModel.deleteFilament(f); deletingFilament = null
        }, { deletingFilament = null })
    }
}

@Composable
private fun PrinterDialog(
    p: PrinterProfileEntity?,
    onSave: (String, Double, Double, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(p?.name ?: "Nowa drukarka") }
    var nozzle by remember { mutableStateOf(Fmt.num(p?.nozzleDiameterMm ?: 0.4)) }
    var layer by remember { mutableStateOf(Fmt.num(p?.layerHeightMm ?: 0.2)) }
    var foot by remember { mutableStateOf(Fmt.num(p?.elephantFootMm ?: 0.2)) }
    val nz = Fmt.parse(nozzle)
    val ly = Fmt.parse(layer)
    val ft = Fmt.parse(foot)
    val valid = name.isNotBlank() && nz != null && nz > 0 && ly != null && ly > 0 && ft != null && ft >= 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (p == null) "Nowa drukarka" else "Edytuj drukarkę") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nazwa") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                MmField("Średnica dyszy", nozzle, { nozzle = it }, if (nz == null || nz <= 0) "Podaj liczbę > 0" else null)
                MmField("Wysokość warstwy", layer, { layer = it }, if (ly == null || ly <= 0) "Podaj liczbę > 0" else null)
                MmField(
                    "Stopa słonia (0 = wyłączone)", foot, { foot = it },
                    if (ft == null || ft < 0) "Podaj liczbę ≥ 0" else null,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(name.trim(), nz!!, ly!!, ft!!) }) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
private fun FilamentDialog(
    f: FilamentProfileEntity?,
    onSave: (String, Material, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(f?.name ?: "Nowy filament") }
    var material by remember { mutableStateOf(runCatching { Material.valueOf(f?.material ?: "PLA") }.getOrDefault(Material.PLA)) }
    var brand by remember { mutableStateOf(f?.brand ?: "") }
    var color by remember { mutableStateOf(f?.color ?: "") }
    var menu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (f == null) "Nowy filament" else "Edytuj filament") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nazwa") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(onClick = { menu = true }) { Text("Materiał: ${material.name}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        Material.entries.forEach { m ->
                            DropdownMenuItem(text = { Text(m.name) }, onClick = { material = m; menu = false })
                        }
                    }
                }
                OutlinedTextField(brand, { brand = it }, label = { Text("Marka") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(color, { color = it }, label = { Text("Kolor") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), material, brand.trim(), color.trim()) }) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
