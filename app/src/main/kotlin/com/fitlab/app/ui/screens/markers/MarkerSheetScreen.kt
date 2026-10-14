package com.fitlab.app.ui.screens.markers

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitlab.app.platform.ShareHelper
import com.fitlab.app.ui.Fmt
import com.fitlab.app.ui.components.FitScaffold
import com.fitlab.app.ui.components.MmField

@Composable
fun MarkerSheetScreen(viewModel: MarkerSheetViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var m01 by remember { mutableStateOf("") }
    var m03 by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            val out = context.contentResolver.openOutputStream(uri)
            if (out != null) viewModel.savePdf(out) { }
        }
    }
    FitScaffold(title = "Arkusz markerów", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val bmp = state.preview
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Podgląd arkusza markerów",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                )
            } else {
                CircularProgressIndicator()
            }
            Text(
                "Drukuj w skali 100% (wyłącz dopasowanie do strony). Sprawdź linijką kontrolną: 100 mm.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { launcher.launch("fitlab_markery_A4.pdf") }, enabled = !state.busy) { Text("Zapisz PDF") }
                OutlinedButton(
                    onClick = {
                        viewModel.preparePdfForShare { f -> ShareHelper.share(context, f, "application/pdf", "Udostępnij PDF") }
                    },
                    enabled = !state.busy,
                ) { Text("Udostępnij PDF") }
            }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            Text("Korekta skali wydruku", style = MaterialTheme.typography.titleMedium)
            Text(
                "Zmierz suwmiarką lub linijką odległość zewnętrznych krawędzi markerów. " +
                    "Aktualna skala: X = ${Fmt.num(state.scaleX, 4)}, Y = ${Fmt.num(state.scaleY, 4)}.",
                style = MaterialTheme.typography.bodySmall,
            )
            MmField("Odległość 0–1 (oczekiwane 180,0)", m01, { m01 = it })
            MmField("Odległość 0–3 (oczekiwane 267,0)", m03, { m03 = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.applyScale(Fmt.parse(m01)?.takeIf { it > 100 }, Fmt.parse(m03)?.takeIf { it > 100 }) },
                    enabled = Fmt.parse(m01) != null || Fmt.parse(m03) != null,
                ) { Text("Zapisz korektę") }
                OutlinedButton(onClick = viewModel::resetScale) { Text("Wyzeruj") }
            }
        }
    }
}
