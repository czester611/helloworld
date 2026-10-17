package com.fitlab.app.ui.screens.enclosure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitlab.app.AppContainer
import com.fitlab.app.data.repo.PartRepository
import com.fitlab.app.platform.ExportPlan
import com.fitlab.app.ui.Fmt
import com.fitlab.core.design.DesignJson
import com.fitlab.core.design.EnclosureDesign
import com.fitlab.core.design.OutlineDesign
import com.fitlab.core.enclosure.Cutout
import com.fitlab.core.enclosure.CutoutShape
import com.fitlab.core.enclosure.CutoutSide
import com.fitlab.core.enclosure.EnclosureBuilder
import com.fitlab.core.enclosure.EnclosureJoin
import com.fitlab.core.enclosure.EnclosureResult
import com.fitlab.core.enclosure.EnclosureSpec
import com.fitlab.core.enclosure.MachineLimits
import com.fitlab.core.export.IterationType
import com.fitlab.core.fit.FitClass
import com.fitlab.core.geom.Ring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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
import kotlinx.coroutines.withContext

/** Wycięcie w formularzu (teksty pól). */
data class CutoutForm(
    val id: String,
    val side: CutoutSide = CutoutSide.NORTH,
    val edgeText: String = "0",
    val shape: CutoutShape = CutoutShape.RECT,
    val posText: String = "10",
    val widthText: String = "9",
    val heightText: String = "3,5",
    val zText: String = "4",
)

/** Formularz obudowy: wszystkie wartości jako teksty (przecinek lub kropka). */
data class EnclosureForm(
    val name: String = "Obudowa",
    val lengthText: String = "50",
    val widthText: String = "30",
    val heightText: String = "15",
    val clearanceText: String = "0,5",
    val wallText: String = "2",
    val floorText: String = "1,6",
    val lidText: String = "1,6",
    val cornerText: String = "2",
    val headroomText: String = "2",
    val join: EnclosureJoin = EnclosureJoin.LIP,
    val lipHeightText: String = "2",
    val lipFit: FitClass = FitClass.SLIDE,
    val pilotText: String = "2,5",
    val nominalText: String = "3",
    val ears: Boolean = true,
    val ventCountText: String = "0",
    val ventWidthText: String = "1,5",
    val standoffs: Boolean = false,
    val standoffHeightText: String = "3",
    val outdoor: Boolean = false,
    val cutouts: List<CutoutForm> = emptyList(),
)

/** Wynik przeliczenia podglądu. */
data class EnclosurePreview(
    val errors: List<String> = emptyList(),
    val result: EnclosureResult? = null,
    val valid: Boolean = false,
)

data class EnclosureUiState(
    val form: EnclosureForm = EnclosureForm(),
    /** true = prostokąt wg wymiarów (pola długość/szerokość), false = obrys ze zdjęcia. */
    val rectMode: Boolean = true,
    val outlineInfo: String = "",
    val preview: EnclosurePreview = EnclosurePreview(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val savedPartId: Long? = null,
    /** Po zapisie otwórz arkusz eksportu tego rodzaju. */
    val pendingExport: IterationType? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class EnclosureViewModel(private val c: AppContainer, private val partId: Long) : ViewModel() {
    private val form = MutableStateFlow(EnclosureForm())
    private val rectMode = MutableStateFlow(true)
    private val outlineInfo = MutableStateFlow("")
    private val loading = MutableStateFlow(partId >= 0)
    private val saving = MutableStateFlow(false)
    private val savedId = MutableStateFlow<Long?>(null)
    private val pending = MutableStateFlow<IterationType?>(null)
    private var photoOutline: Ring? = null
    private var nextCutoutId = 1

    private val preview = form.combine(rectMode) { f, r -> f to r }.debounce(300).mapLatest { (f, r) -> compute(f, r) }
        .onStart { emit(EnclosurePreview()) }.flowOn(Dispatchers.Default)

    val state: StateFlow<EnclosureUiState> = combine(
        combine(form, rectMode, outlineInfo, preview) { f, r, o, p -> Triple(f, r, o) to p },
        combine(loading, saving, savedId, pending) { l, s, id, pe -> listOf(l, s, id, pe) },
    ) { (fro, p), rest ->
        EnclosureUiState(
            form = fro.first, rectMode = fro.second, outlineInfo = fro.third, preview = p,
            loading = rest[0] as Boolean, saving = rest[1] as Boolean, savedPartId = rest[2] as Long?,
            pendingExport = rest[3] as IterationType?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EnclosureUiState())

    init {
        if (partId >= 0) {
            viewModelScope.launch {
                val part = c.parts.get(partId)
                val d = part?.let { runCatching { DesignJson.decode(it.designJson) }.getOrNull() }
                when (d) {
                    is OutlineDesign -> {
                        // Obrys ze zdjęcia; „grubość” podana przy zdjęciu to propozycja wysokości obiektu.
                        photoOutline = d.outer
                        rectMode.value = false
                        outlineInfo.value = outlineText(d.outer)
                        form.value = EnclosureForm(name = part.name, heightText = num(d.thicknessMm))
                    }
                    is EnclosureDesign -> applySpec(part.name, d.spec)
                    else -> Unit
                }
                loading.value = false
            }
        }
    }

    private fun outlineText(r: Ring): String {
        val b = r.bounds()
        return "Obrys ze zdjęcia: ${Fmt.num(b.width, 1)} × ${Fmt.num(b.height, 1)} mm"
    }

    private fun num(v: Double): String = Fmt.num(v).trimEnd('0').trimEnd(',').ifEmpty { "0" }

    private fun applySpec(name: String, s: EnclosureSpec) {
        val rect = s.rectDimensions()
        if (rect == null) {
            photoOutline = s.outline
            rectMode.value = false
            outlineInfo.value = outlineText(s.outline)
        } else {
            rectMode.value = true
        }
        form.value = EnclosureForm(
            name = name,
            lengthText = num(rect?.first ?: 50.0), widthText = num(rect?.second ?: 30.0),
            heightText = num(s.objectHeightMm), clearanceText = num(s.clearanceMm), wallText = num(s.wallMm),
            floorText = num(s.floorMm), lidText = num(s.lidMm), cornerText = num(s.cornerRadiusMm),
            headroomText = num(s.headroomMm), join = s.join, lipHeightText = num(s.lipHeightMm), lipFit = s.lipFit,
            pilotText = num(s.screwPilotMm), nominalText = num(s.screwNominalMm), ears = s.screwEars,
            ventCountText = s.ventCount.toString(), ventWidthText = num(s.ventWidthMm), standoffs = s.standoffs,
            standoffHeightText = num(s.standoffHeightMm), outdoor = s.outdoor,
            cutouts = s.cutouts.map {
                CutoutForm(
                    it.id, it.side, it.edgeIndex.toString(), it.shape, num(it.positionMm), num(it.widthMm),
                    num(it.heightMm), num(it.centerZMm),
                )
            },
        )
        nextCutoutId = (s.cutouts.mapNotNull { it.id.toIntOrNull() }.maxOrNull() ?: 0) + 1
    }

    fun update(f: (EnclosureForm) -> EnclosureForm) = form.update(f)

    fun addCutout() {
        val id = (nextCutoutId++).toString()
        form.update { it.copy(cutouts = it.cutouts + CutoutForm(id)) }
    }

    fun updateCutout(id: String, f: (CutoutForm) -> CutoutForm) =
        form.update { it.copy(cutouts = it.cutouts.map { c -> if (c.id == id) f(c) else c }) }

    fun removeCutout(id: String) = form.update { it.copy(cutouts = it.cutouts.filter { c -> c.id != id }) }

    /** Parsuje formularz do specyfikacji albo zwraca listę błędów po polsku. */
    private fun parse(f: EnclosureForm, rect: Boolean): Pair<EnclosureSpec?, List<String>> {
        val errors = ArrayList<String>()
        fun d(text: String, label: String): Double {
            val v = Fmt.parse(text)
            if (v == null) errors.add("$label: podaj liczbę")
            return v ?: 0.0
        }
        val outline: Ring = if (rect) {
            EnclosureSpec.rectOutline(d(f.lengthText, "Długość"), d(f.widthText, "Szerokość"))
        } else {
            photoOutline ?: run {
                errors.add("Brak obrysu obiektu")
                EnclosureSpec.rectOutline(1.0, 1.0)
            }
        }
        val cutouts = f.cutouts.mapIndexed { i, c ->
            val label = "Wycięcie ${i + 1}"
            Cutout(
                id = c.id, side = c.side, edgeIndex = c.edgeText.trim().toIntOrNull() ?: 0.also {
                    if (c.side == CutoutSide.EDGE) errors.add("$label: numer krawędzi musi być liczbą całkowitą")
                },
                positionMm = d(c.posText, "$label, pozycja"), widthMm = d(c.widthText, "$label, szerokość"),
                heightMm = if (c.shape == CutoutShape.CIRCLE) 0.0 else d(c.heightText, "$label, wysokość"),
                centerZMm = d(c.zText, "$label, wysokość środka"), shape = c.shape,
            )
        }
        val spec = EnclosureSpec(
            outline = outline,
            objectHeightMm = d(f.heightText, "Wysokość obiektu"),
            clearanceMm = d(f.clearanceText, "Luz"),
            wallMm = d(f.wallText, "Ścianka"),
            floorMm = d(f.floorText, "Podłoga"),
            lidMm = d(f.lidText, "Pokrywa"),
            cornerRadiusMm = d(f.cornerText, "Zaokrąglenie"),
            headroomMm = d(f.headroomText, "Zapas wewnętrzny"),
            join = f.join,
            lipHeightMm = d(f.lipHeightText, "Wysokość zakładki"),
            lipFit = f.lipFit,
            screwPilotMm = d(f.pilotText, "Otwór w słupku"),
            screwNominalMm = d(f.nominalText, "Średnica wkręta"),
            screwEars = f.ears,
            cutouts = cutouts,
            ventCount = f.ventCountText.trim().toIntOrNull() ?: 0.also { if (f.ventCountText.isNotBlank()) errors.add("Szczeliny: liczba całkowita") },
            ventWidthMm = d(f.ventWidthText, "Szerokość szczeliny"),
            standoffs = f.standoffs,
            standoffHeightMm = d(f.standoffHeightText, "Wysokość podpórek"),
            outdoor = f.outdoor,
        )
        return if (errors.isEmpty()) spec to emptyList() else null to errors
    }

    private suspend fun compute(f: EnclosureForm, rect: Boolean): EnclosurePreview {
        val (spec, errors) = parse(f, rect)
        if (spec == null) return EnclosurePreview(errors = errors)
        val v = EnclosureBuilder.validate(spec)
        if (v.isNotEmpty()) return EnclosurePreview(errors = v)
        return try {
            val ctx = c.buildContext()
            val r = EnclosureBuilder.build(spec, ctx.comp, ctx.print, MachineLimits(nozzleMm = ctx.printer.nozzleDiameterMm))
            EnclosurePreview(result = r, valid = true)
        } catch (e: Exception) {
            EnclosurePreview(errors = listOf(e.message ?: "Nie udało się zbudować obudowy"))
        }
    }

    private fun currentSpec(): EnclosureSpec? = parse(form.value, rectMode.value).first

    /** Zapisuje część (nowa lub zamiana obrysu ze zdjęcia w obudowę); [export] otwiera potem arkusz eksportu. */
    fun save(export: IterationType? = null) {
        val spec = currentSpec() ?: return
        if (EnclosureBuilder.validate(spec).isNotEmpty()) return
        viewModelScope.launch {
            saving.value = true
            try {
                val design = EnclosureDesign(spec)
                val name = form.value.name.ifBlank { "Obudowa" }
                val existing = if (partId >= 0) partId else savedId.value
                val id = if (existing == null) {
                    c.parts.create(name, design, PartRepository.SOURCE_ENCLOSURE, null, null, emptyList())
                } else {
                    c.parts.convertToEnclosure(existing, name, design)
                    existing
                }
                pending.value = export
                savedId.value = id
            } finally {
                saving.value = false
            }
        }
    }

    /** Zamyka arkusz eksportu (po nim ekran przechodzi do szczegółów części). */
    fun exportClosed() {
        pending.value = null
    }

    /** Plan eksportu zapisanej obudowy (poza wątkiem głównym). */
    suspend fun makePlan(type: IterationType): ExportPlan = withContext(Dispatchers.Default) {
        val id = requireNotNull(savedId.value) { "Najpierw zapisz obudowę" }
        val part = requireNotNull(c.parts.get(id)) { "Nie znaleziono części" }
        val design = DesignJson.decode(part.designJson) as EnclosureDesign
        val ctx = c.buildContext()
        val machine = MachineLimits(nozzleMm = ctx.printer.nozzleDiameterMm)
        val r = if (type == IterationType.FIT_TEST) {
            EnclosureBuilder.buildFitTest(design.spec, ctx.comp, ctx.print, machine)
        } else {
            EnclosureBuilder.build(design.spec, ctx.comp, ctx.print, machine)
        }
        ExportPlan(id, part.name, type, part.designJson, r.toBuildResult(), ctx, r.parts)
    }
}
