package com.fitlab.app.data.repo

import com.fitlab.app.data.db.FilamentProfileEntity
import com.fitlab.app.data.db.PrinterProfileEntity
import com.fitlab.core.design.PrintSettings
import com.fitlab.core.fit.CompensationTable

/** Kontekst budowy części: aktywna drukarka, filament, bieżąca kompensacja i ustawienia druku. */
data class BuildContext(
    val printer: PrinterProfileEntity,
    val filament: FilamentProfileEntity,
    val comp: CompensationTable,
) {
    val print: PrintSettings = PrintSettings(printer.layerHeightMm, printer.elephantFootMm)
}
