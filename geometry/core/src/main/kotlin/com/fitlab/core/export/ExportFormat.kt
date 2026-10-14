package com.fitlab.core.export

/** Format eksportu modelu. */
enum class ExportFormat(val extension: String, val mimeType: String) {
    STL("stl", "model/stl"),
    THREE_MF("3mf", "model/3mf"),
}
