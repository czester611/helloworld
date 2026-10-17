package com.fitlab.app.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Udostępnianie plików przez FileProvider i Android Share. */
object ShareHelper {
    fun authority(context: Context) = "${context.packageName}.fileprovider"

    /** URI pliku z cache (capture/ lub exports/). */
    fun uriFor(context: Context, file: File): Uri = FileProvider.getUriForFile(context, authority(context), file)

    /** Otwiera systemowy wybór aplikacji do udostępnienia [file]. */
    fun share(context: Context, file: File, mime: String, title: String = "Udostępnij") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, title).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(chooser)
    }

    /** Udostępnia kilka plików naraz (np. dno i pokrywę obudowy jako STL). */
    fun shareMany(context: Context, files: List<File>, mime: String, title: String = "Udostępnij") {
        if (files.size == 1) return share(context, files[0], mime, title)
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mime
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files.map { uriFor(context, it) }))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, title).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(chooser)
    }
}
