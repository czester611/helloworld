package com.fitlab.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PrinterProfileEntity::class,
        FilamentProfileEntity::class,
        CompensationRuleEntity::class,
        PartEntity::class,
        MeasurementEntity::class,
        TestIterationEntity::class,
        FitResultEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class FitLabDatabase : RoomDatabase() {
    abstract fun partDao(): PartDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun testIterationDao(): TestIterationDao
    abstract fun fitResultDao(): FitResultDao
    abstract fun profileDao(): ProfileDao
    abstract fun compensationRuleDao(): CompensationRuleDao

    companion object {
        fun create(context: Context): FitLabDatabase =
            Room.databaseBuilder(context.applicationContext, FitLabDatabase::class.java, "fitlab.db").build()
    }
}
