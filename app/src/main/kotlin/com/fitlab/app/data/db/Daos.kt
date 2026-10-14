package com.fitlab.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Liczba prób dla części (do listy). */
data class PartCount(val partId: Long, val n: Int)

@Dao
interface PartDao {
    @Query("SELECT * FROM part ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PartEntity>>

    @Query("SELECT * FROM part WHERE id = :id")
    suspend fun get(id: Long): PartEntity?

    @Query("SELECT * FROM part WHERE id = :id")
    fun observe(id: Long): Flow<PartEntity?>

    @Insert
    suspend fun insert(part: PartEntity): Long

    @Update
    suspend fun update(part: PartEntity)

    @Delete
    suspend fun delete(part: PartEntity)
}

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurement WHERE partId = :partId ORDER BY createdAt")
    suspend fun forPart(partId: Long): List<MeasurementEntity>

    @Insert
    suspend fun insert(m: MeasurementEntity): Long
}

@Dao
interface TestIterationDao {
    @Query("SELECT * FROM test_iteration WHERE partId = :partId ORDER BY number DESC")
    fun observeForPart(partId: Long): Flow<List<TestIterationEntity>>

    @Query("SELECT * FROM test_iteration WHERE id = :id")
    suspend fun get(id: Long): TestIterationEntity?

    @Insert
    suspend fun insert(t: TestIterationEntity): Long

    @Update
    suspend fun update(t: TestIterationEntity)

    @Query("SELECT MAX(number) FROM test_iteration WHERE partId = :partId")
    suspend fun maxNumber(partId: Long): Int?

    @Query("SELECT MAX(number) FROM test_iteration WHERE partId IS NULL")
    suspend fun maxCalibrationNumber(): Int?

    @Query(
        "SELECT * FROM test_iteration WHERE type = 'CALIBRATION' AND resultRecordedAt IS NULL " +
            "ORDER BY createdAt DESC LIMIT 1",
    )
    fun observePendingCalibration(): Flow<TestIterationEntity?>

    @Query("SELECT partId, COUNT(*) AS n FROM test_iteration WHERE partId IS NOT NULL GROUP BY partId")
    fun observeCounts(): Flow<List<PartCount>>
}

@Dao
interface FitResultDao {
    @Query("SELECT * FROM fit_result WHERE testIterationId = :id")
    suspend fun forIteration(id: Long): List<FitResultEntity>

    @Query("SELECT * FROM fit_result WHERE testIterationId = :id")
    fun observeForIteration(id: Long): Flow<List<FitResultEntity>>

    @Insert
    suspend fun insertAll(rows: List<FitResultEntity>)

    @Query("DELETE FROM fit_result WHERE testIterationId = :id")
    suspend fun deleteForIteration(id: Long)

    @Query("SELECT * FROM fit_result WHERE id = :id")
    suspend fun get(id: Long): FitResultEntity?

    @Query(
        "SELECT * FROM fit_result WHERE printerProfileId = :printerId AND filamentProfileId = :filamentId " +
            "AND kind = :kind AND includeInLearning = 1 ORDER BY createdAt DESC",
    )
    suspend fun samples(printerId: Long, filamentId: Long, kind: String): List<FitResultEntity>

    @Query("UPDATE fit_result SET includeInLearning = :include WHERE id = :id")
    suspend fun setInclude(id: Long, include: Boolean)

    @Query(
        "SELECT * FROM fit_result WHERE printerProfileId = :printerId AND filamentProfileId = :filamentId " +
            "ORDER BY createdAt DESC",
    )
    fun observeForProfile(printerId: Long, filamentId: Long): Flow<List<FitResultEntity>>
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM printer_profile ORDER BY id")
    fun observePrinters(): Flow<List<PrinterProfileEntity>>

    @Query("SELECT * FROM printer_profile ORDER BY id")
    suspend fun printers(): List<PrinterProfileEntity>

    @Query("SELECT * FROM printer_profile WHERE id = :id")
    suspend fun printer(id: Long): PrinterProfileEntity?

    @Insert
    suspend fun insertPrinter(p: PrinterProfileEntity): Long

    @Update
    suspend fun updatePrinter(p: PrinterProfileEntity)

    @Delete
    suspend fun deletePrinter(p: PrinterProfileEntity)

    @Query("SELECT COUNT(*) FROM printer_profile")
    suspend fun countPrinters(): Int

    @Query("SELECT * FROM filament_profile ORDER BY id")
    fun observeFilaments(): Flow<List<FilamentProfileEntity>>

    @Query("SELECT * FROM filament_profile ORDER BY id")
    suspend fun filaments(): List<FilamentProfileEntity>

    @Query("SELECT * FROM filament_profile WHERE id = :id")
    suspend fun filament(id: Long): FilamentProfileEntity?

    @Insert
    suspend fun insertFilament(f: FilamentProfileEntity): Long

    @Update
    suspend fun updateFilament(f: FilamentProfileEntity)

    @Delete
    suspend fun deleteFilament(f: FilamentProfileEntity)

    @Query("SELECT COUNT(*) FROM filament_profile")
    suspend fun countFilaments(): Int
}

@Dao
interface CompensationRuleDao {
    @Query("SELECT * FROM compensation_rule WHERE printerProfileId = :printerId AND filamentProfileId = :filamentId")
    suspend fun forPair(printerId: Long, filamentId: Long): List<CompensationRuleEntity>

    @Query("SELECT * FROM compensation_rule WHERE printerProfileId = :printerId AND filamentProfileId = :filamentId")
    fun observeForPair(printerId: Long, filamentId: Long): Flow<List<CompensationRuleEntity>>

    @Query(
        "SELECT * FROM compensation_rule WHERE printerProfileId = :printerId AND filamentProfileId = :filamentId " +
            "AND kind = :kind",
    )
    suspend fun get(printerId: Long, filamentId: Long, kind: String): CompensationRuleEntity?

    @Query(
        "DELETE FROM compensation_rule WHERE printerProfileId = :printerId AND filamentProfileId = :filamentId " +
            "AND kind = :kind",
    )
    suspend fun delete(printerId: Long, filamentId: Long, kind: String)

    @Upsert
    suspend fun upsert(rule: CompensationRuleEntity)
}
