package com.fitlab.app.ui.screens.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitlab.app.AppContainer
import com.fitlab.app.data.repo.NewMeasurement
import com.fitlab.app.data.repo.PartRepository
import com.fitlab.app.ui.Fmt
import com.fitlab.core.design.DesignJson
import com.fitlab.core.design.TemplateDesign
import com.fitlab.core.fit.FitClass
import com.fitlab.core.fit.ResolvedFeature
import com.fitlab.core.geom.Shape2
import com.fitlab.core.templates.ParamType
import com.fitlab.core.templates.SpurGearTemplate
import com.fitlab.core.templates.TemplateId
import com.fitlab.core.templates.TemplateInput
import com.fitlab.core.templates.TemplateRegistry
import com.fitlab.core.templates.ValidationError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Wejście edytora: teksty pól (BOOL jako "1"/"0"), pasowania i nazwa. */
data class EditorInput(
    val name: String,
    val texts: Map<String, String>,
    val fits: Map<String, FitClass>,
)

/** Wynik przeliczenia podglądu. */
data class EditorPreview(
    val parseErrors: Map<String, String> = emptyMap(),
    val validation: List<ValidationError> = emptyList(),
    val shapes: List<Shape2> = emptyList(),
    val features: List<ResolvedFeature> = emptyList(),
    val warnings: List<String> = emptyList(),
    val gearInfo: String? = null,
    val valid: Boolean = false,
)

data class TemplateEditorUiState(
    val templateId: TemplateId,
    val input: EditorInput,
    val preview: EditorPreview = EditorPreview(),
    val saving: Boolean = false,
    val savedPartId: Long? = null,
    val loading: Boolean = true,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class TemplateEditorViewModel(
    private val c: AppContainer,
    private val templateId: TemplateId,
    private val partId: Long,
) : ViewModel() {
    private val template = TemplateRegistry.get(templateId)

    private val input = MutableStateFlow(defaultInput())
    private val loading = MutableStateFlow(partId >= 0)
    private val saving = MutableStateFlow(false)
    private val savedId = MutableStateFlow<Long?>(null)

    private fun defaultInput(): EditorInput {
        val d = TemplateRegistry.defaultInput(templateId)
        return EditorInput(
            template.displayName,
            d.values.mapValues { (k, v) -> textOf(k, v) },
            d.fits,
        )
    }

    private fun textOf(key: String, v: Double): String {
        val spec = template.params.first { it.key == key }
        return when (spec.type) {
            ParamType.COUNT -> Math.round(v).toString()
            ParamType.BOOL -> if (v != 0.0) "1" else "0"
            ParamType.MM -> Fmt.num(v).trimEnd('0').trimEnd(',').ifEmpty { "0" }
        }
    }

    private val preview: Flow<EditorPreview> = input.debounce(300).mapLatest { compute(it) }
        .onStart { emit(EditorPreview()) }.flowOn(Dispatchers.Default)

    val state: StateFlow<TemplateEditorUiState> = combine(input, preview, saving, savedId, loading) { i, p, s, id, l ->
        TemplateEditorUiState(templateId, i, p, s, id, l)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TemplateEditorUiState(templateId, defaultInput()))

    init {
        if (partId >= 0) {
            viewModelScope.launch {
                val part = c.parts.get(partId)
                val d = part?.let { runCatching { DesignJson.decode(it.designJson) }.getOrNull() }
                if (part != null && d is TemplateDesign) {
                    input.value = EditorInput(
                        part.name,
                        template.params.associate { it.key to textOf(it.key, d.values[it.key] ?: it.default) },
                        template.fitSlots.associate { it.key to (d.fits[it.key] ?: it.default) },
                    )
                }
                loading.value = false
            }
        }
    }

    fun setName(v: String) = input.update { it.copy(name = v) }
    fun setText(key: String, v: String) = input.update { it.copy(texts = it.texts + (key to v)) }
    fun setFit(key: String, f: FitClass) = input.update { it.copy(fits = it.fits + (key to f)) }

    private fun parse(i: EditorInput): Pair<Map<String, Double>, Map<String, String>> {
        val values = HashMap<String, Double>()
        val errors = HashMap<String, String>()
        for (p in template.params) {
            val raw = i.texts[p.key] ?: ""
            val v = Fmt.parse(raw)
            if (v == null) errors[p.key] = "Podaj liczbę" else values[p.key] = v
        }
        return values to errors
    }

    private suspend fun compute(i: EditorInput): EditorPreview {
        val (values, parseErrors) = parse(i)
        if (parseErrors.isNotEmpty()) return EditorPreview(parseErrors = parseErrors)
        val ctx = c.buildContext()
        val ti = TemplateInput(values, i.fits)
        val validation = template.validate(ti, ctx.comp)
        val gear = if (templateId == TemplateId.SPUR_GEAR && validation.none { it.key == "teeth" || it.key == "tipDiameter" }) {
            val m = SpurGearTemplate.module(ti)
            val std = SpurGearTemplate.nearestStandardModule(m)
            "Moduł m = ${Fmt.num(m)}" + (if (std != null) " (standard: ${Fmt.num(std, 1)})" else "")
        } else {
            null
        }
        if (validation.isNotEmpty()) return EditorPreview(validation = validation, gearInfo = gear)
        return try {
            val r = template.build(ti, ctx.comp, ctx.print)
            EditorPreview(shapes = r.solid.footprint(), features = r.features, warnings = r.warnings, gearInfo = gear, valid = true)
        } catch (e: Exception) {
            EditorPreview(validation = listOf(ValidationError(null, e.message ?: "Nie udało się zbudować modelu")), gearInfo = gear)
        }
    }

    /** Zapisuje część (nowa lub aktualizacja). */
    fun save() {
        val i = input.value
        viewModelScope.launch {
            saving.value = true
            try {
                val (values, errors) = parse(i)
                if (errors.isNotEmpty()) return@launch
                val design = TemplateDesign(templateId, values, i.fits)
                val name = i.name.ifBlank { template.displayName }
                if (partId < 0) {
                    val ms = template.params.filter { it.type == ParamType.MM }
                        .map { NewMeasurement(it.label, values.getValue(it.key), it.key) }
                    savedId.value = c.parts.create(
                        name, design, PartRepository.SOURCE_TEMPLATE, templateId, null, ms,
                    )
                } else {
                    c.parts.updateDesign(partId, design)
                    c.parts.rename(partId, name)
                    savedId.value = partId
                }
            } finally {
                saving.value = false
            }
        }
    }
}
