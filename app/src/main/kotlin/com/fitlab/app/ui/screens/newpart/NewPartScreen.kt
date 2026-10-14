package com.fitlab.app.ui.screens.newpart

import androidx.compose.foundation.clickable
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

@Composable
fun NewPartScreen(onBack: () -> Unit, onPhoto: () -> Unit, onTemplates: () -> Unit, onMarkers: () -> Unit) {
    FitScaffold(title = "Nowa część", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
            Card(Modifier.fillMaxWidth().clickable(onClick = onPhoto)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Ze zdjęcia", style = MaterialTheme.typography.titleLarge)
                    Text("Płaska część położona na arkuszu A4 z markerami. Aplikacja wykryje obrys i otwory.")
                }
            }
            Card(Modifier.fillMaxWidth().clickable(onClick = onTemplates)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Z szablonu", style = MaterialTheme.typography.titleLarge)
                    Text("Tuleja, pokrętło, zaślepka, wspornik, koło zębate — wpisz wymiary z suwmiarki.")
                }
            }
            TextButton(onClick = onMarkers) { Text("Nie masz arkusza? Wydrukuj arkusz markerów") }
        }
    }
}
