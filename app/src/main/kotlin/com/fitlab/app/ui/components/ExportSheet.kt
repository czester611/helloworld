package com.fitlab.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fitlab.app.platform.ExportOutcome
import com.fitlab.app.platform.ExportPlan
import com.fitlab.app.platform.ExportService
import com.fitlab.app.platform.ShareHelper
import com.fitlab.app.ui.Fmt
import com.fitlab.app.ui.rememberContainer
import com.fitlab.core.export.ExportFormat
import com.fitlab.core.export.PartExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ExportInfo(val triangles: Int, val x: Double, val y: Double, val z: Double, val ok: Boolean)

/**
 * Dolny arkusz eksportu: wybór formatu (domyślnie 3MF), informacja o siatce, „Udostępnij” i „Zapisz plik…”.
 * [planProvider] buduje plan poza wątkiem głównym.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    title: String,
    planProvider: suspend () -> ExportPlan,
    onDismiss: () -> Unit,
    onExported: (ExportOutcome) -> Unit,
) {
    val container = rememberContainer()
    val service = remember { ExportService(container) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var plan by remember { mutableStateOf<ExportPlan?>(null) }
    var info by remember { mutableStateOf<ExportInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }
    var format by remember { mutableStateOf(ExportFormat.THREE_MF) }
    var pending by remember { mutableStateOf<ExportOutcome?>(null) }
    var saveIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            val p = withContext(Dispatchers.Default) { planProvider() }
            val i = withContext(Dispatchers.Default) {
                val r = if (p.parts.isEmpty()) PartExporter.validate(p.build.solid) else PartExporter.validateGroups(p.parts)
                val d = if (p.parts.isEmpty()) PartExporter.dimensions(p.build.solid) else PartExporter.dimensionsGroups(p.parts)
                ExportInfo(r.triangleCount, d.x, d.y, d.z, r.isClosed && r.isConsistentlyOriented && r.volumeMm3 > 0)
            }
            plan = p
            info = i
            if (!i.ok) error = PartExporter.MESH_ERROR
        } catch (e: Exception) {
            error = e.message ?: "Nie udało się zbudować modelu"
        } finally {
            busy = false
        }
    }

    val launcherRef = remember { arrayOfNulls<ActivityResultLauncher<String>>(1) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val outcome = pending
        val p = plan
        if (outcome != null && p != null) {
            if (uri == null) {
                // Anulowano „Zapisz plik…”: nie zapisujemy próby.
                pending = null
                service.discard(outcome)
            } else {
                scope.launch {
                    try {
                        val files = outcome.allFiles
                        val src = files[saveIndex].first
                        withContext(Dispatchers.IO) {
                            val out = context.contentResolver.openOutputStream(uri)
                                ?: throw IllegalStateException("Nie udało się otworzyć pliku docelowego")
                            out.use { o -> src.inputStream().use { it.copyTo(o) } }
                        }
                        if (saveIndex + 1 < files.size) {
                            // Kolejna część (np. pokrywa): osobne okno zapisu.
                            saveIndex += 1
                            launcherRef[0]?.launch(files[saveIndex].second)
                        } else {
                            pending = null
                            service.commit(p, outcome)
                            onExported(outcome)
                            onDismiss()
                        }
                    } catch (e: Exception) {
                        pending = null
                        service.discard(outcome)
                        error = e.message ?: "Błąd zapisu pliku"
                    }
                }
            }
        }
    }

    launcherRef[0] = saveLauncher

    fun run(save: Boolean) {
        val p = plan ?: return
        busy = true
        error = null
        scope.launch {
            try {
                val outcome = service.export(p, format)
                if (save) {
                    pending = outcome
                    saveIndex = 0
                    saveLauncher.launch(outcome.fileName)
                } else {
                    val mime = format.mimeType
                    ShareHelper.shareMany(context, outcome.allFiles.map { it.first }, mime, "Udostępnij model")
                    service.commit(p, outcome)
                    onExported(outcome)
                    onDismiss()
                }
            } catch (e: Exception) {
                error = e.message ?: "Błąd eksportu"
            } finally {
                busy = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = format == ExportFormat.THREE_MF, onClick = { format = ExportFormat.THREE_MF }, label = { Text("3MF") })
                FilterChip(selected = format == ExportFormat.STL, onClick = { format = ExportFormat.STL }, label = { Text("STL") })
            }
            info?.let {
                Text("Trójkąty: ${it.triangles}, wymiary: ${Fmt.num(it.x, 1)} × ${Fmt.num(it.y, 1)} × ${Fmt.num(it.z, 1)} mm")
            }
            error?.let { ErrorBanner(it) }
            plan?.build?.warnings?.forEach { WarningBanner(it) }
            if (busy) CircularProgressIndicator()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { run(false) }, enabled = !busy && plan != null && info?.ok == true) { Text("Udostępnij") }
                OutlinedButton(onClick = { run(true) }, enabled = !busy && plan != null && info?.ok == true) { Text("Zapisz plik…") }
                OutlinedButton(onClick = onDismiss) { Text("Anuluj") }
            }
        }
    }
}
