package com.fitlab.core.templates

/** Rejestr szablonów. */
object TemplateRegistry {
    val all: List<PartTemplate> = listOf(BushingTemplate, KnobTemplate, CapTemplate, BracketTemplate, SpurGearTemplate)

    /** Szablon o identyfikatorze [id]. */
    fun get(id: TemplateId): PartTemplate = all.first { it.id == id }

    /** Domyślne wartości i pasowania szablonu [id]. */
    fun defaultInput(id: TemplateId): TemplateInput {
        val t = get(id)
        return TemplateInput(t.params.associate { it.key to it.default }, t.fitSlots.associate { it.key to it.default })
    }
}
