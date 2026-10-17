package com.fitlab.app.ui.screens.enclosure

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitlab.app.ui.components.FitScaffold
import com.fitlab.core.enclosure.EnclosureBuilder

/** Wybór źródła obrysu obiektu dla obudowy: zdjęcie albo wymiary prostopadłościanu. */
@Composable
fun EnclosureSourceScreen(onBack: () -> Unit, onPhoto: () -> Unit, onDimensions: () -> Unit, onMarkers: () -> Unit) {
    FitScaffold(title = "Obudowa", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(EnclosureBuilder.MANUAL_INPUT_NOTE, style = MaterialTheme.typography.bodyMedium)
            Card(Modifier.fillMaxWidth().clickable(onClick = onPhoto)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Ze zdjęcia obiektu", style = MaterialTheme.typography.titleLarge)
                    Text("Połóż płytkę lub moduł na arkuszu A4 z markerami. Aplikacja wykryje obrys, a Ty podasz wysokość.")
                }
            }
            Card(Modifier.fillMaxWidth().clickable(onClick = onDimensions)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Wg wymiarów", style = MaterialTheme.typography.titleLarge)
                    Text("Prostokąt: długość × szerokość × wysokość. Bez zdjęcia.")
                }
            }
            TextButton(onClick = onMarkers) { Text("Nie masz arkusza? Wydrukuj arkusz markerów") }
        }
    }
}
