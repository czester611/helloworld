package com.fitlab.app.data.files

import android.content.Context
import java.io.File
import java.util.UUID

/** Pliki aplikacji: zdjęcia (filesDir/photos), cache aparatu i eksportów. */
class FileStore(private val context: Context) {
    private val photosDir get() = File(context.filesDir, "photos").also { it.mkdirs() }
    val captureDir: File get() = File(context.cacheDir, "capture").also { it.mkdirs() }
    val exportsDir: File get() = File(context.cacheDir, "exports").also { it.mkdirs() }

    /** Nowy pusty plik na zdjęcie z aparatu w cache. */
    fun newCaptureFile(): File = File(captureDir, "${UUID.randomUUID()}.jpg")

    /** Kopiuje zdjęcie do trwałego magazynu; zwraca ścieżkę względną (`photos/<uuid>.jpg`). */
    fun savePhoto(source: File): String {
        val name = "${UUID.randomUUID()}.jpg"
        source.copyTo(File(photosDir, name), overwrite = true)
        return "photos/$name"
    }

    /** Plik z ścieżki względnej w filesDir. */
    fun resolve(relativePath: String): File = File(context.filesDir, relativePath)

    /** Usuwa zdjęcie ze ścieżki względnej. */
    fun deletePhoto(relativePath: String?) {
        if (relativePath != null) resolve(relativePath).delete()
    }

    /** Plik eksportu w cache (kasuje poprzedni o tej samej nazwie). */
    fun exportFile(name: String): File = File(exportsDir, name).also { if (it.exists()) it.delete() }

    /** Czyści cache aparatu. */
    fun clearCapture() {
        captureDir.listFiles()?.forEach { it.delete() }
    }
}
