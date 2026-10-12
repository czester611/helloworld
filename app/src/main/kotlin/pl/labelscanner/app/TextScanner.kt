package pl.labelscanner.app

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import pl.labelscanner.parser.LabelInfo
import pl.labelscanner.parser.LabelParser
import pl.labelscanner.parser.OcrLine

data class ScanResult(val info: LabelInfo, val rawText: String)

/** OCR (ML Kit, na urządzeniu) + wyszukanie modelu i numeru seryjnego. */
class TextScanner {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun scan(context: Context, uri: Uri): ScanResult {
        // fromFilePath uwzględnia rotację EXIF, więc współrzędne ramek zgadzają się z obrazem po obróceniu
        val image = InputImage.fromFilePath(context, uri)
        val text = recognizer.process(image).await()
        val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            line.boundingBox?.let { OcrLine(line.text, it.left, it.top, it.right, it.bottom) }
        }
        return ScanResult(LabelParser.parse(lines), text.text)
    }

    fun close() = recognizer.close()
}
