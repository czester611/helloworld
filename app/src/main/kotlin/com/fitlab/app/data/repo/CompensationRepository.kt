package com.fitlab.app.data.repo

import com.fitlab.app.data.db.CompensationRuleEntity
import com.fitlab.app.data.db.FitLabDatabase
import com.fitlab.core.fit.BiasSample
import com.fitlab.core.fit.CompensationEstimator
import com.fitlab.core.fit.CompensationKind
import com.fitlab.core.fit.CompensationTable
import com.fitlab.core.fit.Material
import com.fitlab.core.fit.MaterialDefaults
import kotlinx.coroutines.flow.Flow

/** Zmiana biasu po przeliczeniu (do podsumowania w UI). */
data class BiasChange(val kind: CompensationKind, val oldBias: Double, val newBias: Double)

/** Kompensacja per para drukarka + filament i typ cechy. */
class CompensationRepository(private val db: FitLabDatabase) {
    private val rules = db.compensationRuleDao()
    private val profiles = db.profileDao()
    private val results = db.fitResultDao()

    fun observeRules(printerId: Long, filamentId: Long): Flow<List<CompensationRuleEntity>> =
        rules.observeForPair(printerId, filamentId)

    private suspend fun materialOf(filamentId: Long): Material =
        profiles.filament(filamentId)?.let { runCatching { Material.valueOf(it.material) }.getOrNull() } ?: Material.PLA

    /** Prior materiału filamentu dla typu cechy. */
    suspend fun prior(filamentId: Long, kind: CompensationKind): Double =
        MaterialDefaults.bias(materialOf(filamentId), kind)

    /** Aktualna tabela: reguła, a gdy jej brak — prior materiału. */
    suspend fun tableFor(printerId: Long, filamentId: Long): CompensationTable {
        val material = materialOf(filamentId)
        val byKind = rules.forPair(printerId, filamentId).associateBy { it.kind }
        return CompensationTable(
            CompensationKind.entries.associateWith { k ->
                byKind[k.name]?.biasMm ?: MaterialDefaults.bias(material, k)
            },
        )
    }

    /** Przelicza regułę z próbek (chyba że ręczne nadpisanie — wtedy tylko liczbę próbek). */
    suspend fun recompute(printerId: Long, filamentId: Long, kind: CompensationKind): BiasChange {
        val prior = prior(filamentId, kind)
        val samples = results.samples(printerId, filamentId, kind.name)
        val rule = rules.get(printerId, filamentId, kind.name)
        val old = rule?.biasMm ?: prior
        val now = System.currentTimeMillis()
        val updated = if (rule != null && rule.manualOverride) {
            rule.copy(sampleCount = samples.size, updatedAt = now)
        } else {
            val bias = CompensationEstimator.estimate(prior, samples.map { BiasSample(it.sampleBiasMm, it.createdAt) })
            CompensationRuleEntity(printerId, filamentId, kind.name, bias, samples.size, false, now)
        }
        rules.upsert(updated)
        return BiasChange(kind, old, updated.biasMm)
    }

    /** Ręczne nadpisanie biasu. */
    suspend fun setManual(printerId: Long, filamentId: Long, kind: CompensationKind, bias: Double) {
        val samples = results.samples(printerId, filamentId, kind.name)
        rules.upsert(
            CompensationRuleEntity(printerId, filamentId, kind.name, bias, samples.size, true, System.currentTimeMillis()),
        )
    }

    /** Wyłącza ręczne nadpisanie i przelicza wartość z próbek (bez kasowania ustawionej wartości, gdy brak próbek). */
    suspend fun releaseManual(printerId: Long, filamentId: Long, kind: CompensationKind): BiasChange {
        val rule = rules.get(printerId, filamentId, kind.name)
        if (rule != null) rules.upsert(rule.copy(manualOverride = false, updatedAt = System.currentTimeMillis()))
        return recompute(printerId, filamentId, kind)
    }

    /** Usuwa nadpisanie, ustawia prior i przelicza z próbek. */
    suspend fun resetToDefault(printerId: Long, filamentId: Long, kind: CompensationKind): BiasChange {
        val old = rules.get(printerId, filamentId, kind.name)?.biasMm ?: prior(filamentId, kind)
        rules.upsert(
            CompensationRuleEntity(
                printerId, filamentId, kind.name, prior(filamentId, kind), 0, false, System.currentTimeMillis(),
            ),
        )
        val c = recompute(printerId, filamentId, kind)
        return BiasChange(kind, old, c.newBias)
    }
}
