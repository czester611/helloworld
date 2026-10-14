package com.fitlab.app.data.repo

import androidx.room.withTransaction
import com.fitlab.app.data.db.CompensationRuleEntity
import com.fitlab.app.data.db.FilamentProfileEntity
import com.fitlab.app.data.db.FitLabDatabase
import com.fitlab.app.data.db.PrinterProfileEntity
import com.fitlab.app.data.settings.AppSettings
import com.fitlab.core.fit.CompensationKind
import com.fitlab.core.fit.Material
import com.fitlab.core.fit.MaterialDefaults
import kotlinx.coroutines.flow.Flow

/** Profile drukarek i filamentów oraz aktywna para. */
class ProfileRepository(private val db: FitLabDatabase, private val settings: AppSettings) {
    private val dao = db.profileDao()
    private val rules = db.compensationRuleDao()

    fun observePrinters(): Flow<List<PrinterProfileEntity>> = dao.observePrinters()
    fun observeFilaments(): Flow<List<FilamentProfileEntity>> = dao.observeFilaments()

    suspend fun printer(id: Long): PrinterProfileEntity? = dao.printer(id)
    suspend fun filament(id: Long): FilamentProfileEntity? = dao.filament(id)

    /** Aktywna drukarka (lub pierwsza dostępna). */
    suspend fun activePrinter(): PrinterProfileEntity? =
        dao.printer(settings.activePrinterId.value) ?: dao.printers().firstOrNull()

    /** Aktywny filament (lub pierwszy dostępny). */
    suspend fun activeFilament(): FilamentProfileEntity? =
        dao.filament(settings.activeFilamentId.value) ?: dao.filaments().firstOrNull()

    private suspend fun seedRules(printerId: Long, filamentId: Long, material: Material) {
        val now = System.currentTimeMillis()
        for (kind in CompensationKind.entries) {
            rules.upsert(
                CompensationRuleEntity(
                    printerId, filamentId, kind.name, MaterialDefaults.bias(material, kind), 0, false, now,
                ),
            )
        }
    }

    /** Tworzy domyślne profile przy pierwszym uruchomieniu oraz naprawia aktywne ID. */
    suspend fun ensureDefaults() {
        db.withTransaction {
            val now = System.currentTimeMillis()
            if (dao.countPrinters() == 0) {
                dao.insertPrinter(PrinterProfileEntity(name = "Moja drukarka", createdAt = now))
            }
            if (dao.countFilaments() == 0) {
                dao.insertFilament(FilamentProfileEntity(name = "PLA (domyślny)", material = "PLA", createdAt = now))
                dao.insertFilament(FilamentProfileEntity(name = "PETG (domyślny)", material = "PETG", createdAt = now))
            }
            val printers = dao.printers()
            val filaments = dao.filaments()
            for (p in printers) for (f in filaments) {
                if (rules.forPair(p.id, f.id).size < CompensationKind.entries.size) {
                    val existing = rules.forPair(p.id, f.id).map { it.kind }.toSet()
                    val material = materialOf(f)
                    for (kind in CompensationKind.entries) {
                        if (kind.name !in existing) {
                            rules.upsert(
                                CompensationRuleEntity(p.id, f.id, kind.name, MaterialDefaults.bias(material, kind), 0, false, now),
                            )
                        }
                    }
                }
            }
            if (dao.printer(settings.activePrinterId.value) == null) settings.setActivePrinter(printers.first().id)
            if (dao.filament(settings.activeFilamentId.value) == null) {
                val pla = filaments.firstOrNull { it.material == Material.PLA.name } ?: filaments.first()
                settings.setActiveFilament(pla.id)
            }
        }
    }

    private fun materialOf(f: FilamentProfileEntity): Material =
        runCatching { Material.valueOf(f.material) }.getOrDefault(Material.OTHER)

    suspend fun addPrinter(name: String, nozzleMm: Double, layerMm: Double, elephantFootMm: Double): Long =
        db.withTransaction {
            val id = dao.insertPrinter(
                PrinterProfileEntity(
                    name = name, nozzleDiameterMm = nozzleMm, layerHeightMm = layerMm,
                    elephantFootMm = elephantFootMm, createdAt = System.currentTimeMillis(),
                ),
            )
            for (f in dao.filaments()) seedRules(id, f.id, materialOf(f))
            id
        }

    suspend fun updatePrinter(p: PrinterProfileEntity) = dao.updatePrinter(p)

    /** Usuwa drukarkę; zablokowane dla aktywnej i ostatniej. Zwraca true, gdy usunięto. */
    suspend fun deletePrinter(p: PrinterProfileEntity): Boolean {
        if (p.id == settings.activePrinterId.value || dao.countPrinters() <= 1) return false
        dao.deletePrinter(p)
        return true
    }

    suspend fun addFilament(name: String, material: Material, brand: String, color: String): Long =
        db.withTransaction {
            val id = dao.insertFilament(
                FilamentProfileEntity(
                    name = name, material = material.name, brand = brand, color = color,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            for (p in dao.printers()) seedRules(p.id, id, material)
            id
        }

    /** Aktualizuje filament; przy zmianie materiału resetuje priory reguł bez próbek i bez ręcznego nadpisania. */
    suspend fun updateFilament(f: FilamentProfileEntity) {
        db.withTransaction {
            val old = dao.filament(f.id)
            dao.updateFilament(f)
            if (old != null && old.material != f.material) {
                val material = materialOf(f)
                val now = System.currentTimeMillis()
                for (p in dao.printers()) for (r in rules.forPair(p.id, f.id)) {
                    if (r.sampleCount == 0 && !r.manualOverride) {
                        val kind = CompensationKind.valueOf(r.kind)
                        rules.upsert(r.copy(biasMm = MaterialDefaults.bias(material, kind), updatedAt = now))
                    }
                }
            }
        }
    }

    suspend fun deleteFilament(f: FilamentProfileEntity): Boolean {
        if (f.id == settings.activeFilamentId.value || dao.countFilaments() <= 1) return false
        dao.deleteFilament(f)
        return true
    }
}
