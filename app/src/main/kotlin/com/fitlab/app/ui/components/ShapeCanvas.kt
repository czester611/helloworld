package com.fitlab.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlab.app.ui.Fmt
import com.fitlab.core.geom.Bounds2
import com.fitlab.core.geom.Ring
import com.fitlab.core.geom.Shape2
import com.fitlab.core.geom.Vec2
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Odwzorowanie mm -> ekran (oś Y w górę) z dopasowaniem bbox i marginesem. */
class ViewMap(private val bounds: Bounds2, width: Float, height: Float, margin: Float) {
    val scale: Float
    private val ox: Float
    private val oy: Float

    init {
        val bw = max(bounds.width, 1e-6).toFloat()
        val bh = max(bounds.height, 1e-6).toFloat()
        scale = max(min((width - 2 * margin) / bw, (height - 2 * margin) / bh), 1e-6f)
        ox = (width - bw * scale) / 2f
        oy = (height - bh * scale) / 2f
    }

    fun toScreen(p: Vec2) = Offset(ox + ((p.x - bounds.minX) * scale).toFloat(), oy + ((bounds.maxY - p.y) * scale).toFloat())

    fun toMm(o: Offset) = Vec2(bounds.minX + (o.x - ox) / scale, bounds.maxY - (o.y - oy) / scale)
}

private fun path(rings: List<Ring>, vm: ViewMap): Path = Path().apply {
    fillType = PathFillType.EvenOdd
    for (r in rings) {
        r.points.forEachIndexed { i, p ->
            val o = vm.toScreen(p)
            if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
        }
        close()
    }
}

/**
 * Widok 2D z góry: kształty (wypełnione z dziurami), kontury przerywane, etykiety, siatka 10 mm i wymiary bbox.
 * [onTapMm] dostaje współrzędne dotknięcia w mm układu części.
 */
@Composable
fun ShapeCanvas(
    shapes: List<Shape2>,
    modifier: Modifier = Modifier,
    dashedRings: List<Ring> = emptyList(),
    labels: List<Pair<Vec2, String>> = emptyList(),
    showGrid: Boolean = false,
    showDims: Boolean = false,
    marginDp: Float = 16f,
    onTapMm: ((Vec2) -> Unit)? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }

    val all = shapes.map { it.outer } + dashedRings
    val bounds: Bounds2? = if (all.isEmpty()) null else {
        val bs = all.map { it.bounds() }
        Bounds2(bs.minOf { it.minX }, bs.minOf { it.minY }, bs.maxOf { it.maxX }, bs.maxOf { it.maxY })
    }
    val vm: ViewMap? = if (bounds == null || size.width == 0 || size.height == 0) {
        null
    } else {
        ViewMap(bounds, size.width.toFloat(), size.height.toFloat(), with(density) { marginDp.dp.toPx() })
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(vm, onTapMm) {
                detectTapGestures { off -> if (vm != null) onTapMm?.invoke(vm.toMm(off)) }
            },
    ) {
        if (vm == null || bounds == null) return@Canvas
        val stroke2dp = 2.dp.toPx()
        if (showGrid) {
            var gx = ceil(bounds.minX / 10.0) * 10.0
            while (gx <= bounds.maxX) {
                drawLine(grid, vm.toScreen(Vec2(gx, bounds.minY)), vm.toScreen(Vec2(gx, bounds.maxY)), 1f)
                gx += 10.0
            }
            var gy = ceil(bounds.minY / 10.0) * 10.0
            while (gy <= bounds.maxY) {
                drawLine(grid, vm.toScreen(Vec2(bounds.minX, gy)), vm.toScreen(Vec2(bounds.maxX, gy)), 1f)
                gy += 10.0
            }
        }
        for (s in shapes) {
            val p = path(listOf(s.outer) + s.holes, vm)
            drawPath(p, primary.copy(alpha = 0.25f))
            drawPath(p, primary, style = Stroke(width = stroke2dp))
        }
        if (dashedRings.isNotEmpty()) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
            for (r in dashedRings) drawPath(path(listOf(r), vm), onSurface, style = Stroke(width = 1.5.dp.toPx(), pathEffect = dash))
        }
        val style = TextStyle(fontSize = 11.sp, color = onSurface)
        for ((pos, text) in labels) {
            val o = vm.toScreen(pos)
            val m = measurer.measure(text, style)
            drawText(measurer, text, Offset(o.x - m.size.width / 2f, o.y - m.size.height / 2f), style)
        }
        if (showDims) {
            val w = "${Fmt.num(bounds.width, 1)} mm"
            val h = "${Fmt.num(bounds.height, 1)} mm"
            val bl = vm.toScreen(Vec2(bounds.minX, bounds.minY))
            val tr = vm.toScreen(Vec2(bounds.maxX, bounds.maxY))
            val mw = measurer.measure(w, style)
            drawText(measurer, w, Offset((bl.x + tr.x) / 2f - mw.size.width / 2f, bl.y + 2f), style)
            drawText(measurer, h, Offset(tr.x + 2f, (bl.y + tr.y) / 2f), style)
        }
    }
}
