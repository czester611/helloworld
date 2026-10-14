package com.fitlab.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Profil drukarki. Wszystkie enumy w bazie zapisywane jako String. */
@Entity(tableName = "printer_profile")
data class PrinterProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "Moja drukarka",
    val nozzleDiameterMm: Double = 0.4,
    val layerHeightMm: Double = 0.2,
    val elephantFootMm: Double = 0.2,
    val notes: String = "",
    val createdAt: Long,
)

/** Profil filamentu (materiał wyznacza priory kompensacji). */
@Entity(tableName = "filament_profile")
data class FilamentProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val material: String = "PLA",
    val brand: String = "",
    val color: String = "",
    val notes: String = "",
    val createdAt: Long,
)

/** Reguła kompensacji dla pary drukarka + filament i typu cechy. */
@Entity(
    tableName = "compensation_rule",
    primaryKeys = ["printerProfileId", "filamentProfileId", "kind"],
    foreignKeys = [
        ForeignKey(
            entity = PrinterProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["printerProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FilamentProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["filamentProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("filamentProfileId")],
)
data class CompensationRuleEntity(
    val printerProfileId: Long,
    val filamentProfileId: Long,
    val kind: String,
    val biasMm: Double,
    val sampleCount: Int = 0,
    val manualOverride: Boolean = false,
    val updatedAt: Long,
)

/** Część w bibliotece. source: PHOTO / TEMPLATE; status: DRAFT / TESTING / DONE. */
@Entity(tableName = "part")
data class PartEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val source: String,
    val templateId: String? = null,
    val designJson: String,
    val photoPath: String? = null,
    val status: String = "DRAFT",
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

/** Dziennik pomiarów suwmiarką. */
@Entity(
    tableName = "measurement",
    foreignKeys = [
        ForeignKey(
            entity = PartEntity::class,
            parentColumns = ["id"],
            childColumns = ["partId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("partId")],
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partId: Long,
    val label: String,
    val valueMm: Double,
    val targetKey: String? = null,
    val createdAt: Long,
)

/** Próba wydruku. type: FIT_TEST / FULL_PART / CALIBRATION; partId = null dla kalibracji. */
@Entity(
    tableName = "test_iteration",
    foreignKeys = [
        ForeignKey(
            entity = PartEntity::class,
            parentColumns = ["id"],
            childColumns = ["partId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("partId")],
)
data class TestIterationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partId: Long?,
    val number: Int,
    val type: String,
    val printerProfileId: Long,
    val filamentProfileId: Long,
    val designJson: String,
    val compensationJson: String,
    val featuresJson: String,
    val exportFormat: String? = null,
    val exportFileName: String? = null,
    val note: String = "",
    val createdAt: Long,
    val resultRecordedAt: Long? = null,
)

/** Wynik próby dla jednej cechy. */
@Entity(
    tableName = "fit_result",
    foreignKeys = [
        ForeignKey(
            entity = TestIterationEntity::class,
            parentColumns = ["id"],
            childColumns = ["testIterationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("testIterationId"),
        Index(value = ["printerProfileId", "filamentProfileId", "kind"]),
    ],
)
data class FitResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val testIterationId: Long,
    val featureId: String,
    val kind: String,
    val verdict: String,
    val amountMm: Double? = null,
    val measuredMm: Double? = null,
    val biasUsedMm: Double,
    val sampleBiasMm: Double,
    val printerProfileId: Long,
    val filamentProfileId: Long,
    val includeInLearning: Boolean = true,
    val createdAt: Long,
)
