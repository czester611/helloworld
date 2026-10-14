package com.fitlab.app.ui.screens.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitlab.app.ui.components.ErrorBanner
import com.fitlab.app.ui.components.FitScaffold

@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FitScaffold(title = "Diagnostyka", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Wersja aplikacji: ${state.appVersion}")
            Text("Wersja OpenCV: ${state.openCvVersion}")
            Button(onClick = viewModel::runTest, enabled = !state.running) { Text("Test OpenCV") }
            if (state.running) CircularProgressIndicator()
            state.result?.let {
                if (state.ok == true) Text(it, style = MaterialTheme.typography.titleMedium) else ErrorBanner(it)
            }
        }
    }
}
