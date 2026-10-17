package com.fitlab.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
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
import com.fitlab.app.ui.Fmt
import com.fitlab.app.ui.components.FitScaffold
import com.fitlab.app.ui.components.ShapeCanvas
import com.fitlab.app.ui.nav.Routes

fun statusLabel(status: String): String = when (status) {
    "TESTING" -> "Testowanie"
    "DONE" -> "Gotowe"
    else -> "Szkic"
}

@Composable
fun HomeScreen(viewModel: HomeViewModel, onNavigate: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(state, onNavigate)
}

@Composable
fun HomeContent(state: HomeUiState, onNavigate: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    FitScaffold(
        title = "Moje części",
        onBack = null,
        actions = {
            Box {
                TextButton(onClick = { menu = true }) { Text("Więcej") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf(
                        "Profile" to Routes.PROFILES,
                        "Kompensacja" to Routes.COMPENSATION,
                        "Kalibracja" to Routes.CALIBRATION,
                        "Arkusz markerów" to Routes.MARKERS,
                        "Diagnostyka" to Routes.DIAGNOSTICS,
                    ).forEach { (label, route) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; onNavigate(route) })
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { onNavigate(Routes.NEW) }) { Text("+ Nowa część") }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable { onNavigate(Routes.PROFILES) },
            ) {
                Text(
                    "Drukarka: ${state.printerName} · Filament: ${state.filamentName} (${state.material})",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { onNavigate(Routes.ENCLOSURE_SOURCE) },
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Obudowa", style = MaterialTheme.typography.titleMedium)
                    Text("Dobierz dno i pokrywę do płytki, modułu lub urządzenia.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (state.loaded && state.parts.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Brak części. Dodaj pierwszą — ze zdjęcia albo z szablonu.")
                    TextButton(onClick = { onNavigate(Routes.NEW) }) { Text("Nowa część") }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.parts, key = { it.id }) { p ->
                        Card(Modifier.fillMaxWidth().clickable { onNavigate(Routes.part(p.id)) }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                ShapeCanvas(p.shapes, Modifier.size(64.dp), marginDp = 4f)
                                Column(Modifier.padding(start = 12.dp)) {
                                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AssistChip(onClick = {}, label = { Text(statusLabel(p.status)) })
                                        Text("  Próby: ${p.tests}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(Fmt.dateTime(p.updatedAt), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
