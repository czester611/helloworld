package com.fitlab.app.data.repo

import androidx.room.withTransaction
import com.fitlab.app.data.db.FitLabDatabase
import com.fitlab.app.data.db.FitResultEntity
import com.fitlab.app.data.db.PartCount
import com.fitlab.app.data.db.TestIterationEntity
import com.fitlab.core.design.DesignJson
import com.fitlab.core.export.ExportFormat
import com.fitlab.core.export.IterationType
import com.fitlab.core.fit.CompensationKind
import com.fitlab.core.fit.Feedback
import com.fitlab.core.fit.FeedbackInterpreter
import com.fitlab.core.fit.Verdict
import kotlinx.coroutines.flow.Flow

/** Ocena jednej cechy wpisana przez użytkownika. */
data class FeatureOutcome(
    val featureId: String,
    val verdict: Verdict,
    val amountMm: Double?,
    val measuredMm: Double?,
)

/** Próby wydruku i ich wyniki. */
class TestRepository(private val db: FitLabDatabase, private val compensation: CompensationRepository) {
    private val iterations = db.testIterationDao()
    private val results = db.fitResultDao()

    fun observeForPart(partId: Long): Flow<List<TestIterationEntity>> = iterations.observeForPart(partId)
    fun observeCounts(): Flow<List<PartCount>> = iterations.observeCounts()
    fun observePendingCalibration(): Flow<TestIterationEntity?> = iterations.observePendingCalibration()
    fun observeResultsForProfile(printerId: Long, filamentId: Long): Flow<List<FitResultEntity>> =
        results.observeForProfile(printerId, filamentId)

    suspend fun get(id: Long): TestIterationEntity? = iterations.get(id)
    suspend fun results(iterationId: Long): List<FitResultEntity> = results.forIteration(iterationId)

    /** Następny numer próby w obrębie części (lub globalny numer kalibracji dla partId = null). */
    suspend fun nextNumber(partId: Long?): Int =
        ((if (partId == null) iterations.maxCalibrationNumber() else iterations.maxNumber(partId)) ?: 0) + 1

    /** Zapisuje próbę po udanym eksporcie. Zwraca ID. */
    suspend fun createIteration(
        partId: Long?,
        number: Int,
        type: IterationType,
        printerId: Long,
        filamentId: Long,
        designJson: String,
        compensationJson: String,
        featuresJson: String,
        format: ExportFormat?,
        fileName: String?,
    ): Long = iterations.insert(
        TestIterationEntity(
            partId = partId, number = number, type = type.name, printerProfileId = printerId,
            filamentProfileId = filamentId, designJson = designJson, compensationJson = compensationJson,
            featuresJson = featuresJson, exportFormat = format?.name, exportFileName = fileName,
            createdAt = System.currentTimeMillis(),
        ),
    )

    /**
     * Zapisuje wyniki próby (zastępując poprzednie), aktualizuje notatkę i przelicza kompensację dla dotkniętych
     * typów. Próbka = bezwzględny bias liczony od biasu z migawki iteracji.
     */
    suspend fun saveResults(
        iterationId: Long,
        note: String,
        outcomes: List<FeatureOutcome>,
        includeInLearning: Boolean,
    ): List<BiasChange> = db.withTransaction {
        val iter = iterations.get(iterationId) ?: return@withTransaction emptyList()
        val features = DesignJson.decodeFeatures(iter.featuresJson)
        val touched = LinkedHashSet<CompensationKind>()
        results.forIteration(iterationId).forEach { r -> touched.add(CompensationKind.valueOf(r.kind)) }
        results.deleteForIteration(iterationId)
        val now = System.currentTimeMillis()
        val rows = ArrayList<FitResultEntity>()
        for (o in outcomes) {
            val f = features.firstOrNull { x -> x.id == o.featureId } ?: continue
            val feedback = if (o.verdict == Verdict.MEASURED) {
                Feedback.Measured(o.measuredMm ?: continue)
            } else {
                Feedback.Fit(o.verdict, o.amountMm ?: 0.0)
            }
            val sample = FeedbackInterpreter.sample(f.kind, f.biasMm, feedback, f.cadMm)
            rows.add(
                FitResultEntity(
                    testIterationId = iterationId, featureId = f.id, kind = f.kind.name, verdict = o.verdict.name,
                    amountMm = o.amountMm, measuredMm = o.measuredMm, biasUsedMm = f.biasMm, sampleBiasMm = sample,
                    printerProfileId = iter.printerProfileId, filamentProfileId = iter.filamentProfileId,
                    includeInLearning = includeInLearning, createdAt = now,
                ),
            )
            touched.add(f.kind)
        }
        results.insertAll(rows)
        iterations.update(iter.copy(note = note, resultRecordedAt = if (rows.isEmpty()) null else now))
        touched.map { k -> compensation.recompute(iter.printerProfileId, iter.filamentProfileId, k) }
    }

    /** Włącza/wyłącza próbkę w uczeniu i przelicza regułę. */
    suspend fun setInclude(resultId: Long, include: Boolean): BiasChange? = db.withTransaction {
        val r = results.get(resultId) ?: return@withTransaction null
        results.setInclude(resultId, include)
        compensation.recompute(r.printerProfileId, r.filamentProfileId, CompensationKind.valueOf(r.kind))
    }
}
