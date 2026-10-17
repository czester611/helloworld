package com.fitlab.app.ui.screens.enclosure

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitlab.app.ui.Fmt
import com.fitlab.app.ui.components.ErrorBanner
import com.fitlab.app.ui.components.ExportSheet
import com.fitlab.app.ui.components.FitClassSelector
import com.fitlab.app.ui.components.FitScaffold
import com.fitlab.app.ui.components.MmField
import com.fitlab.app.ui.components.ViewMap
import com.fitlab.app.ui.components.WarningBanner
import com.fitlab.app.ui.screens.templates.FeatureTable
import com.fitlab.core.advice.UsageAdvisor
import com.fitlab.core.enclosure.CutoutShape
import com.fitlab.core.enclosure.CutoutSide
import com.fitlab.core.enclosure.EnclosureBuilder
import com.fitlab.core.enclosure.EnclosureGeometry
import com.fitlab.core.enclosure.EnclosureJoin
import com.fitlab.core.export.IterationType
import com.fitlab.core.geom.Bounds2
import com.fitlab.core.geom.Ring
import com.fitlab.core.geom.Vec2

private fun sideLabel(s: CutoutSide) = when (s) {
    CutoutSide.NORTH -> "Północ"
    CutoutSide.SOUTH -> "Południe"
    CutoutSide.EAST -> "Wschód"
    CutoutSide.WEST -> "Zachód"
    CutoutSide.EDGE -> "Krawędź nr"
}

@Composable
fun EnclosureScreen(viewModel: EnclosureViewModel, onBack: () -> Unit, onDone: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val f = state.form
    val pv = state.preview

    LaunchedEffect(state.savedPartId, state.pendingExport) {
        val id = state.savedPartId
        if (id != null && state.pendingExport == null) onDone(id)
    }

    FitScaffold(
        title = "Obudowa",
        onBack = onBack,
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val ok = pv.valid && !state.saving && !state.loading
                Button(onClick = { viewModel.save() }, enabled = ok) { Text("Zapisz") }
                OutlinedButton(onClick = { viewModel.save(IterationType.FULL_PART) }, enabled = ok) { Text("Eksport…") }
                OutlinedButton(onClick = { viewModel.save(IterationType.FIT_TEST) }, enabled = ok) { Text("Plasterek") }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WarningBanner(EnclosureBuilder.MANUAL_INPUT_NOTE)
            OutlinedTextField(
                value = f.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                label = { Text("Nazwa") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Obiekt", style = MaterialTheme.typography.titleMedium)
            if (state.rectMode) {
                MmField("Długość (X)", f.lengthText, { v -> viewModel.update { it.copy(lengthText = v) } })
                MmField("Szerokość (Y)", f.widthText, { v -> viewModel.update { it.copy(widthText = v) } })
            } else {
                Text(state.outlineInfo)
            }
            MmField(
                "Wysokość obiektu", f.heightText, { v -> viewModel.update { it.copy(heightText = v) } },
                hint = "Zmierz suwmiarką razem z najwyższym wystającym elementem",
            )
            MmField(
                "Luz obiektu na stronę", f.clearanceText, { v -> viewModel.update { it.copy(clearanceText = v) } },
                hint = "Kompensowany jak szczelina (uczenie drukarka + filament)",
            )
            MmField("Zapas wewnętrzny nad obiektem", f.headroomText, { v -> viewModel.update { it.copy(headroomText = v) } })

            Text("Obudowa", style = MaterialTheme.typography.titleMedium)
            MmField("Grubość ścianki", f.wallText, { v -> viewModel.update { it.copy(wallText = v) } }, hint = "Najlepiej wielokrotność dyszy (0,4)")
            MmField("Grubość podłogi", f.floorText, { v -> viewModel.update { it.copy(floorText = v) } })
            MmField("Grubość pokrywy", f.lidText, { v -> viewModel.update { it.copy(lidText = v) } })
            MmField("Promień zaokrągleń rogów", f.cornerText, { v -> viewModel.update { it.copy(cornerText = v) } })

            Text("Połączenie dna z pokrywą", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = f.join == EnclosureJoin.LIP,
                    onClick = { viewModel.update { it.copy(join = EnclosureJoin.LIP) } },
                    label = { Text("Zakładka") },
                )
                FilterChip(
                    selected = f.join == EnclosureJoin.SCREWS,
                    onClick = { viewModel.update { it.copy(join = EnclosureJoin.SCREWS) } },
                    label = { Text("Śruby") },
                )
            }
            if (f.join == EnclosureJoin.LIP) {
                MmField("Wysokość zakładki", f.lipHeightText, { v -> viewModel.update { it.copy(lipHeightText = v) } })
                Text("Pasowanie zakładki w ściankach")
                FitClassSelector(f.lipFit, { c -> viewModel.update { it.copy(lipFit = c) } })
            } else {
                MmField(
                    "Średnica wkręta (M2 = 2, M3 = 3)", f.nominalText, { v -> viewModel.update { it.copy(nominalText = v) } },
                )
                MmField(
                    "Otwór w słupku", f.pilotText, { v -> viewModel.update { it.copy(pilotText = v) } },
                    hint = "2,5 mm dla wkręta samogwintującego M3 w plastiku",
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Słupek kolidujący z obiektem wyprowadź na zewnątrz (ucho)", Modifier.weight(1f))
                    Switch(checked = f.ears, onCheckedChange = { c -> viewModel.update { it.copy(ears = c) } })
                }
            }

            Text("Wycięcia w ściankach", style = MaterialTheme.typography.titleMedium)
            Text(
                "Pozycja = środek wycięcia mierzony od początku krawędzi (N/S od zachodu, E/W od południa); " +
                    "wysokość środka licz od wnętrza dna. Dodaj ok. 0,5 mm luzu do wymiaru portu.",
                style = MaterialTheme.typography.bodySmall,
            )
            f.cutouts.forEachIndexed { i, c ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Wycięcie ${i + 1}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { viewModel.removeCutout(c.id) }) { Text("Usuń") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            CutoutSide.entries.forEach { s ->
                                FilterChip(
                                    selected = c.side == s,
                                    onClick = { viewModel.updateCutout(c.id) { it.copy(side = s) } },
                                    label = { Text(sideLabel(s).take(5)) },
                                )
                            }
                        }
                        if (c.side == CutoutSide.EDGE) {
                            OutlinedTextField(
                                value = c.edgeText,
                                onValueChange = { v -> viewModel.updateCutout(c.id) { it.copy(edgeText = v) } },
                                label = { Text("Numer krawędzi obrysu (od 0)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = c.shape == CutoutShape.RECT,
                                onClick = { viewModel.updateCutout(c.id) { it.copy(shape = CutoutShape.RECT) } },
                                label = { Text("Prostokąt") },
                            )
                            FilterChip(
                                selected = c.shape == CutoutShape.CIRCLE,
                                onClick = { viewModel.updateCutout(c.id) { it.copy(shape = CutoutShape.CIRCLE) } },
                                label = { Text("Okrąg") },
                            )
                        }
                        MmField("Pozycja środka wzdłuż krawędzi", c.posText, { v -> viewModel.updateCutout(c.id) { it.copy(posText = v) } })
                        MmField(
                            if (c.shape == CutoutShape.CIRCLE) "Średnica" else "Szerokość", c.widthText,
                            { v -> viewModel.updateCutout(c.id) { it.copy(widthText = v) } },
                        )
                        if (c.shape == CutoutShape.RECT) {
                            MmField("Wysokość", c.heightText, { v -> viewModel.updateCutout(c.id) { it.copy(heightText = v) } })
                        }
                        MmField("Wysokość środka nad podłogą", c.zText, { v -> viewModel.updateCutout(c.id) { it.copy(zText = v) } })
                    }
                }
            }
            OutlinedButton(onClick = viewModel::addCutout) { Text("+ Dodaj wycięcie") }

            Text("Dodatki", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = f.ventCountText,
                onValueChange = { v -> viewModel.update { it.copy(ventCountText = v.filter { ch -> ch.isDigit() }) } },
                label = { Text("Szczeliny wentylacyjne w pokrywie (ilość)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if ((f.ventCountText.toIntOrNull() ?: 0) > 0) {
                MmField("Szerokość szczeliny", f.ventWidthText, { v -> viewModel.update { it.copy(ventWidthText = v) } })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Podpórki pod narożnikami obiektu", Modifier.weight(1f))
                Switch(checked = f.standoffs, onCheckedChange = { c -> viewModel.update { it.copy(standoffs = c) } })
            }
            if (f.standoffs) {
                MmField("Wysokość podpórek", f.standoffHeightText, { v -> viewModel.update { it.copy(standoffHeightText = v) } })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Obudowa na zewnątrz (UV, słońce)", Modifier.weight(1f))
                Switch(checked = f.outdoor, onCheckedChange = { c -> viewModel.update { it.copy(outdoor = c) } })
            }

            Text("Podgląd z góry", style = MaterialTheme.typography.titleMedium)
            pv.errors.forEach { ErrorBanner(it) }
            pv.result?.let { r ->
                EnclosureCanvas(r.geometry, Modifier.fillMaxWidth().height(260.dp))
                Text(
                    "Dno: ${Fmt.num(r.baseSizeMm.x, 1)} × ${Fmt.num(r.baseSizeMm.y, 1)} × ${Fmt.num(r.baseSizeMm.z, 1)} mm · " +
                        "Pokrywa: ${Fmt.num(r.lidSizeMm.x, 1)} × ${Fmt.num(r.lidSizeMm.y, 1)} × ${Fmt.num(r.lidSizeMm.z, 1)} mm",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Legenda: wypełnienie = ścianki, linia przerywana = obrys obiektu, czerwone = wycięcia, " +
                        "zielone = słupki/podpórki.",
                    style = MaterialTheme.typography.bodySmall,
                )
                r.warnings.forEach { WarningBanner(it) }
                FeatureTable(r.features)
            }
            val advice = UsageAdvisor.forEnclosure(f.outdoor)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Zalecany filament: ${advice.primary.label}", style = MaterialTheme.typography.titleSmall)
                    Text(advice.primary.reason)
                    if (advice.alternatives.isNotEmpty()) {
                        Text("Alternatywa: " + advice.alternatives.joinToString { it.label })
                    }
                    Text("Orientacja: ${advice.orientationTip}")
                }
            }
            Row(Modifier.height(24.dp)) {}
        }
    }

    state.pendingExport?.let { type ->
        ExportSheet(
            title = if (type == IterationType.FIT_TEST) "Plasterek pasowania (narożnik)" else "Obudowa: dno i pokrywa",
            planProvider = { viewModel.makePlan(type) },
            onDismiss = { viewModel.exportClosed() },
            onExported = { },
        )
    }
}

private fun ringPath(r: Ring, vm: ViewMap): Path = Path().apply {
    r.points.forEachIndexed { i, p ->
        val o = vm.toScreen(p)
        if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
    }
    close()
}

private fun DrawScope.circle(c: Vec2, d: Double, vm: ViewMap, color: Color, fill: Boolean) {
    val o: Offset = vm.toScreen(c)
    val rad = (d / 2.0 * vm.scale).toFloat()
    if (fill) drawCircle(color.copy(alpha = 0.35f), rad, o)
    drawCircle(color, rad, o, style = Stroke(width = 1.5.dp.toPx()))
}

/** Rzut z góry: ścianki, wnęka, obrys obiektu (przerywany), wycięcia, słupki, zakładka, szczeliny, podpórki. */
@Composable
private fun EnclosureCanvas(g: EnclosureGeometry, modifier: Modifier = Modifier) {
    val wallColor = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurface
    val cutColor = MaterialTheme.colorScheme.error
    val postColor = Color(0xFF2E7D32)
    val b = g.outer.bounds()
    Canvas(modifier.fillMaxSize()) {
        val margin = 12.dp.toPx()
        val bounds = Bounds2(b.minX, b.minY, b.maxX, b.maxY)
        val vm = ViewMap(bounds, size.width, size.height, margin)
        val stroke = Stroke(width = 2.dp.toPx())
        val wall = Path().apply {
            fillType = PathFillType.EvenOdd
            addPath(ringPath(g.outer, vm))
            addPath(ringPath(g.cavity, vm))
        }
        drawPath(wall, wallColor.copy(alpha = 0.3f))
        drawPath(ringPath(g.outer, vm), wallColor, style = stroke)
        drawPath(ringPath(g.cavity, vm), wallColor, style = Stroke(width = 1.dp.toPx()))
        val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
        drawPath(ringPath(g.objectOutline, vm), dim, style = Stroke(width = 1.5.dp.toPx(), pathEffect = dash))
        g.lipOuter?.let { drawPath(ringPath(it, vm), dim.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx(), pathEffect = dash)) }
        g.vents.forEach { drawPath(ringPath(it, vm), dim.copy(alpha = 0.6f), style = Stroke(width = 1.dp.toPx())) }
        g.cutoutBoxes.forEach {
            drawPath(ringPath(it, vm), cutColor.copy(alpha = 0.35f))
            drawPath(ringPath(it, vm), cutColor, style = Stroke(width = 1.5.dp.toPx()))
        }
        g.standoffs.forEach { circle(it.center, it.diameterMm, vm, postColor, true) }
        g.posts.forEach {
            circle(it.center, it.postDiameterMm, vm, postColor, true)
            circle(it.center, it.pilotDiameterMm, vm, dim, false)
        }
    }
}
