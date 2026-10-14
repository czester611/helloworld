package com.fitlab.app.platform

import com.fitlab.app.AppContainer
import com.fitlab.app.data.repo.BuildContext
import com.fitlab.core.design.BuildResult
import com.fitlab.core.design.DesignJson
import com.fitlab.core.export.ExportFormat
import com.fitlab.core.export.FileNames
import com.fitlab.core.export.IterationType
import com.fitlab.core.export.PartExporter
import com.fitlab.core.mesh.MeshReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Plan eksportu: co zbudowano, dla której części i w jakim kontekście. */
data class ExportPlan(
    val partId: Long?,
    val partName: String,
    val type: IterationType,
    val designJson: String,
    val build: BuildResult,
    val context: BuildContext,
)

/** Wynik eksportu: plik w cache i zapisana próba. */
data class ExportOutcome(
    val file: File,
    val fileName: String,
    val iterationId: Long,
    val number: Int,
    val format: ExportFormat,
    val report: MeshReport,
)

/** Eksport bryły do pliku i zapis próby (TestIteration) w bazie. */
class ExportService(private val c: AppContainer) {
    /** Zapisuje plik w cache/exports i tworzy próbę; rzuca [IllegalStateException] przy błędzie siatki. */
    suspend fun export(plan: ExportPlan, format: ExportFormat): ExportOutcome = withContext(Dispatchers.IO) {
        val number = c.tests.nextNumber(plan.partId)
        val fileName = FileNames.exportName(plan.partName, plan.type, number, format)
        val file = c.fileStore.exportFile(fileName)
        val report: MeshReport
        try {
            report = FileOutputStream(file).use { PartExporter.export(plan.build.solid, format, plan.partName, it) }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        val ctx = plan.context
        val id = c.tests.createIteration(
            partId = plan.partId,
            number = number,
            type = plan.type,
            printerId = ctx.printer.id,
            filamentId = ctx.filament.id,
            designJson = plan.designJson,
            compensationJson = DesignJson.encodeCompensation(ctx.comp),
            featuresJson = DesignJson.encodeFeatures(plan.build.features),
            format = format,
            fileName = fileName,
        )
        plan.partId?.let { c.parts.markTestingIfDraft(it) }
        ExportOutcome(file, fileName, id, number, format, report)
    }
}
