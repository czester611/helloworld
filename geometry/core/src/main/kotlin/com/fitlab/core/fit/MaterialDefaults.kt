package com.fitlab.core.fit

/** Wartości startowe (prior) biasu dla materiału, mm średnicowo. */
object MaterialDefaults {
    /** Prior biasu dla pary materiał + typ cechy. */
    fun bias(material: Material, kind: CompensationKind): Double = when (material) {
        Material.PLA, Material.OTHER -> when (kind) {
            CompensationKind.HOLE -> -0.25
            CompensationKind.SLOT -> -0.20
            CompensationKind.PIN -> 0.10
            CompensationKind.OUTLINE -> 0.10
        }
        Material.PETG -> when (kind) {
            CompensationKind.HOLE -> -0.30
            CompensationKind.SLOT -> -0.25
            CompensationKind.PIN -> 0.15
            CompensationKind.OUTLINE -> 0.15
        }
        Material.ABS, Material.ASA -> when (kind) {
            CompensationKind.HOLE -> -0.30
            CompensationKind.SLOT -> -0.25
            CompensationKind.PIN -> 0.05
            CompensationKind.OUTLINE -> 0.00
        }
        Material.TPU -> when (kind) {
            CompensationKind.HOLE -> -0.35
            CompensationKind.SLOT -> -0.30
            CompensationKind.PIN -> 0.20
            CompensationKind.OUTLINE -> 0.20
        }
    }

    /** Pełna tabela priorów dla materiału. */
    fun table(material: Material): CompensationTable =
        CompensationTable(CompensationKind.entries.associateWith { bias(material, it) })
}
