package pl.labelscanner.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private val scanner = TextScanner()
    private var sharedUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedUri = extractSharedUri(intent)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxWidth()) { ScannerScreen(scanner, sharedUri) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedUri = extractSharedUri(intent)
    }

    override fun onDestroy() {
        scanner.close()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun extractSharedUri(intent: Intent?): Uri? =
        if (intent?.action == Intent.ACTION_SEND) intent.getParcelableExtra(Intent.EXTRA_STREAM) else null
}

@Composable
private fun ScannerScreen(scanner: TextScanner, sharedUri: Uri?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showRaw by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    fun process(uri: Uri) {
        busy = true; error = null; result = null
        scope.launch {
            try {
                result = scanner.scan(context, uri)
            } catch (e: Exception) {
                error = "Nie udało się odczytać zdjęcia: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(::process)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(::process)
    }

    androidx.compose.runtime.LaunchedEffect(sharedUri) { sharedUri?.let(::process) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Skaner tabliczki znamionowej", style = MaterialTheme.typography.headlineSmall)
        Text("Zrób zdjęcie tabliczki albo wybierz je z galerii — aplikacja znajdzie model i numer seryjny.")

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                val file = File(context.cacheDir, "photos").apply { mkdirs() }
                    .let { File(it, "label_${System.currentTimeMillis()}.jpg") }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                cameraUri = uri
                takePicture.launch(uri)
            }) { Text("Zrób zdjęcie") }
            OutlinedButton(onClick = {
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text("Z galerii") }
        }

        if (busy) CircularProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        result?.let { r ->
            ResultCard("Model", r.info.model, context)
            ResultCard("Numer seryjny", r.info.serial, context)
            TextButton(onClick = { showRaw = !showRaw }) {
                Text(if (showRaw) "Ukryj rozpoznany tekst" else "Pokaż cały rozpoznany tekst")
            }
            if (showRaw) Text(r.rawText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ResultCard(title: String, value: String?, context: Context) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value ?: "nie znaleziono", style = MaterialTheme.typography.titleLarge)
            if (value != null) {
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText(title, value))
                    Toast.makeText(context, "Skopiowano", Toast.LENGTH_SHORT).show()
                }) { Text("Kopiuj") }
            }
        }
    }
}
