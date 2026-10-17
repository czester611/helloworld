package com.fitlab.app.platform

import com.fitlab.app.AppContainer
import com.fitlab.app.data.repo.BuildContext
import com.fitlab.core.design.BuildResult
import com.fitlab.core.design.DesignJson
import com.fitlab.core.export.ExportFormat
import com.fitlab.core.export.FileNames
import com.fitlab.core.export.IterationType
import com.fitlab.core.export.NamedSolid
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
    /** Części do osobnego eksportu (obudowa: dno i pokrywa); puste = jedna bryła z [build]. */
    val parts: List<NamedSolid> = emptyList(),
)

/** Wynik eksportu: plik w cache; próba jest zapisywana dopiero przez [ExportService.commit]. */
data class ExportOutcome(
    val file: File,
    val fileName: String,
    val number: Int,
    val format: ExportFormat,
    val report: MeshReport,
    /** Dodatkowe pliki (STL kolejnych części): plik i nazwa. */
    val extra: List<Pair<File, String>> = emptyList(),
) {
    /** Wszystkie pliki wyniku, główny pierwszy. */
    val allFiles: List<Pair<File, String>> get() = listOf(file to fileName) + extra
}

/** Eksport bryły do pliku; próbę (TestIteration) zapisuje [commit] po faktycznym zapisaniu/udostępnieniu. */
class ExportService(private val c: AppContainer) {
    /** Zapisuje plik w cache/exports (bez tworzenia próby); rzuca [IllegalStateException] przy błędzie siatki. */
    suspend fun export(plan: ExportPlan, format: ExportFormat): ExportOutcome = withContext(Dispatchers.IO) {
        val number = c.tests.nextNumber(plan.partId)
        if (plan.parts.isNotEmpty()) return@withContext exportParts(plan, format, number)
        val fileName = FileNames.exportName(plan.partName, plan.type, number, format)
        val file = c.fileStore.exportFile(fileName)
        val report: MeshReport
        try {
            report = FileOutputStream(file).use { PartExporter.export(plan.build.solid, format, plan.partName, it) }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        ExportOutcome(file, fileName, number, format, report)
    }

    /** Obudowa: 3MF z dwoma obiektami albo osobny STL dla każdej części (nazwy wg [FileNames]). */
    private fun exportParts(plan: ExportPlan, format: ExportFormat, number: Int): ExportOutcome {
        val created = ArrayList<File>()
        try {
            if (format == ExportFormat.THREE_MF) {
                val name = FileNames.exportName(plan.partName, plan.type, number, format)
                val file = c.fileStore.exportFile(name).also { created.add(it) }
                val report = FileOutputStream(file).use { PartExporter.exportGroups3mf(plan.parts, plan.partName, it) }
                return ExportOutcome(file, name, number, format, report)
            }
            val files = plan.parts.map { part ->
                val name = FileNames.exportName("${plan.partName} ${part.name}", plan.type, number, format)
                val file = c.fileStore.exportFile(name).also { created.add(it) }
                val report = FileOutputStream(file).use { PartExporter.export(part.solid, format, part.name, it) }
                Triple(file, name, report)
            }
            val first = files.first()
            return ExportOutcome(
                first.first, first.second, number, format, first.third,
                files.drop(1).map { it.first to it.second },
            )
        } catch (e: Exception) {
            created.forEach { it.delete() }
            throw e
        }
    }

    /** Zapisuje próbę w bazie; wywołać dopiero po udanym zapisaniu lub udostępnieniu pliku. Zwraca ID próby. */
    suspend fun commit(plan: ExportPlan, outcome: ExportOutcome): Long = withContext(Dispatchers.IO) {
        val ctx = plan.context
        val id = c.tests.createIteration(
            partId = plan.partId,
            number = outcome.number,
            type = plan.type,
            printerId = ctx.printer.id,
            filamentId = ctx.filament.id,
            designJson = plan.designJson,
            compensationJson = DesignJson.encodeCompensation(ctx.comp),
            featuresJson = DesignJson.encodeFeatures(plan.build.features),
            format = outcome.format,
            fileName = outcome.fileName,
        )
        plan.partId?.let { c.parts.markTestingIfDraft(it) }
        id
    }

    /** Usuwa plik z cache po anulowaniu lub błędzie zapisu; próba nie powstaje. */
    fun discard(outcome: ExportOutcome) {
        outcome.allFiles.forEach { it.first.delete() }
    }
}
