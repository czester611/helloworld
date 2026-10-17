package com.fitlab.app.data.repo

import com.fitlab.app.data.db.FitLabDatabase
import com.fitlab.app.data.db.MeasurementEntity
import com.fitlab.app.data.db.PartEntity
import com.fitlab.app.data.files.FileStore
import com.fitlab.core.design.DesignJson
import com.fitlab.core.design.PartDesign
import com.fitlab.core.templates.TemplateId
import kotlinx.coroutines.flow.Flow

/** Pomiar do zapisania razem z nową częścią. */
data class NewMeasurement(val label: String, val valueMm: Double, val targetKey: String?)

/** Biblioteka części. */
class PartRepository(private val db: FitLabDatabase, private val files: FileStore) {
    private val parts = db.partDao()
    private val measurements = db.measurementDao()

    fun observeAll(): Flow<List<PartEntity>> = parts.observeAll()
    fun observe(id: Long): Flow<PartEntity?> = parts.observe(id)
    suspend fun get(id: Long): PartEntity? = parts.get(id)

    /** Tworzy część (status DRAFT) i zapisuje pomiary. Zwraca ID. */
    suspend fun create(
        name: String,
        design: PartDesign,
        source: String,
        templateId: TemplateId?,
        photoPath: String?,
        newMeasurements: List<NewMeasurement>,
    ): Long {
        val now = System.currentTimeMillis()
        val id = parts.insert(
            PartEntity(
                name = name, source = source, templateId = templateId?.name,
                designJson = DesignJson.encode(design), photoPath = photoPath,
                status = STATUS_DRAFT, createdAt = now, updatedAt = now,
            ),
        )
        for (m in newMeasurements) addMeasurement(id, m)
        return id
    }

    suspend fun addMeasurement(partId: Long, m: NewMeasurement) {
        measurements.insert(
            MeasurementEntity(
                partId = partId, label = m.label, valueMm = m.valueMm, targetKey = m.targetKey,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun measurements(partId: Long): List<MeasurementEntity> = measurements.forPart(partId)

    suspend fun updateDesign(id: Long, design: PartDesign) {
        val p = parts.get(id) ?: return
        parts.update(p.copy(designJson = DesignJson.encode(design), updatedAt = System.currentTimeMillis()))
    }

    /** Zamienia część (np. obrys ze zdjęcia) w obudowę: nowy projekt, nazwa i źródło ENCLOSURE; zdjęcie zostaje. */
    suspend fun convertToEnclosure(id: Long, name: String, design: PartDesign) {
        val p = parts.get(id) ?: return
        parts.update(
            p.copy(
                name = name, source = SOURCE_ENCLOSURE, designJson = DesignJson.encode(design),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun rename(id: Long, name: String) {
        val p = parts.get(id) ?: return
        parts.update(p.copy(name = name, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setStatus(id: Long, status: String) {
        val p = parts.get(id) ?: return
        parts.update(p.copy(status = status, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setNotes(id: Long, notes: String) {
        val p = parts.get(id) ?: return
        parts.update(p.copy(notes = notes))
    }

    /** Po pierwszym eksporcie: DRAFT -> TESTING. */
    suspend fun markTestingIfDraft(id: Long) {
        val p = parts.get(id) ?: return
        if (p.status == STATUS_DRAFT) parts.update(p.copy(status = STATUS_TESTING, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: Long) {
        val p = parts.get(id) ?: return
        parts.delete(p)
        files.deletePhoto(p.photoPath)
    }

    companion object {
        const val STATUS_DRAFT = "DRAFT"
        const val STATUS_TESTING = "TESTING"
        const val STATUS_DONE = "DONE"
        const val SOURCE_PHOTO = "PHOTO"
        const val SOURCE_TEMPLATE = "TEMPLATE"
        const val SOURCE_ENCLOSURE = "ENCLOSURE"
    }
}
