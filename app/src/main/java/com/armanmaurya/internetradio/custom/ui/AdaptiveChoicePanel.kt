package com.armanmaurya.internetradio.custom.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max

/**
 * Pannello di scelta multipla adattivo (stile "Livello buffer").
 *
 * Dispone le opzioni in una griglia che occupa esattamente lo spazio disponibile:
 * - se l'altezza è vincolata (pannello a schermo intero) riempie tutta l'area visibile;
 * - se l'altezza è libera (dentro un `ExpandableItem` con scroll di pagina) usa l'altezza
 *   minima che evita lo scroll, privilegiando il numero di colonne più compatto.
 *
 * Logica spaziale: colonne, padding, gap e indicatore sono calcolati da numero di opzioni
 * e spazio disponibile. Logica tipografica: il font viene scalato (mai sotto 12sp) finché
 * ogni etichetta entra nella cella senza troncamenti né parole spezzate.
 */
@Composable
fun AdaptiveChoicePanel(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minFontSize: TextUnit = 12.sp,
    maxFontSize: TextUnit = 20.sp
) {
    if (options.isEmpty()) return

    val baseStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
    val measurer = rememberTextMeasurer(cacheSize = 64)

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val bounded = heightPx > 0 && heightPx != Constraints.Infinity
        if (widthPx <= 0 || widthPx == Constraints.Infinity) return@BoxWithConstraints

        val layout = remember(options, widthPx, heightPx, bounded, baseStyle, density, minFontSize, maxFontSize) {
            computeChoiceLayout(
                labels = options,
                widthPx = widthPx,
                heightPx = heightPx,
                bounded = bounded,
                measurer = measurer,
                baseStyle = baseStyle,
                density = density,
                minFontSp = minFontSize.value,
                maxFontSp = maxFontSize.value
            )
        }

        val grid: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(layout.gap)) {
                options.indices.chunked(layout.columns).forEach { rowIndices ->
                    val isFullRow = rowIndices.size == layout.columns
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isFullRow) {
                            Arrangement.spacedBy(layout.gap)
                        } else {
                            // Ultima riga parziale: celle della stessa larghezza, gruppo centrato.
                            Arrangement.spacedBy(layout.gap, Alignment.CenterHorizontally)
                        }
                    ) {
                        rowIndices.forEach { index ->
                            ChoiceCell(
                                label = options[index],
                                isSelected = index == selectedIndex,
                                layout = layout,
                                onClick = { onSelect(index) },
                                modifier = Modifier
                                    .then(
                                        if (isFullRow) Modifier.weight(1f)
                                        else Modifier.width(layout.cellWidth)
                                    )
                                    .height(layout.cellHeight)
                            )
                        }
                    }
                }
            }
        }

        if (bounded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                grid()
            }
        } else {
            grid()
        }
    }
}

private const val LINE_HEIGHT_MULTIPLIER = 1.15f
private const val MAX_UNBOUNDED_LINES = 3

private data class ChoiceLayout(
    val columns: Int,
    val cellWidth: Dp,
    val cellHeight: Dp,
    val gap: Dp,
    val padH: Dp,
    val padV: Dp,
    val indicator: Dp,
    val dotGap: Dp,
    val fontSize: TextUnit
)

private fun computeChoiceLayout(
    labels: List<String>,
    widthPx: Int,
    heightPx: Int,
    bounded: Boolean,
    measurer: TextMeasurer,
    baseStyle: TextStyle,
    density: Density,
    minFontSp: Float,
    maxFontSp: Float
): ChoiceLayout {
    val count = labels.size
    val gapMin = with(density) { 4.dp.toPx() }
    val gapMax = with(density) { 10.dp.toPx() }
    val gap = (widthPx * if (count >= 8) 0.012f else 0.02f).coerceIn(gapMin, gapMax)
    val padMin = with(density) { 6.dp.toPx() }
    val padMax = with(density) { 14.dp.toPx() }
    val indicatorMin = with(density) { 10.dp.toPx() }
    val indicatorMax = with(density) { 18.dp.toPx() }

    var bestColumns = 1
    var bestCellW = widthPx.toFloat()
    var bestCellH = if (bounded) heightPx.toFloat() else widthPx * 0.32f
    var bestPadH = padMin
    var bestIndicator = indicatorMin
    var bestFont = minFontSp
    var bestScore = Double.NEGATIVE_INFINITY

    for (columns in 1..count) {
        val rows = (count + columns - 1) / columns
        val cellW = (widthPx - gap * (columns - 1)) / columns
        if (cellW <= 0f) continue

        val padH = (cellW * 0.07f).coerceIn(padMin, padMax)
        val padV = padH * 0.75f
        val indicator = (cellW * 0.10f).coerceIn(indicatorMin, indicatorMax)
        val dotGap = gap * 0.6f
        val textWidth = (cellW - 2f * padH).toInt()
        if (textWidth <= 0) continue

        // Spazio verticale non occupato dal testo (padding + indicatore + distanza).
        val fixedHeight = 2f * padV + indicator + dotGap
        val cellH = if (bounded) (heightPx - gap * (rows - 1)) / rows else 0f
        val maxTextHeight = if (bounded) (cellH - fixedHeight).toInt() else Int.MAX_VALUE
        if (bounded && maxTextHeight <= 0) continue

        val fontSp = fitFontSize(
            measurer = measurer,
            labels = labels,
            textWidthPx = textWidth,
            maxTextHeightPx = maxTextHeight,
            baseStyle = baseStyle,
            density = density,
            minSp = minFontSp,
            maxSp = maxFontSp
        )
        if (fontSp <= 0f) continue

        val textStyle = baseStyle.copy(
            fontSize = fontSp.sp,
            lineHeight = (fontSp * LINE_HEIGHT_MULTIPLIER).sp
        )
        val textHeight = labels.maxOf { label ->
            measurer.measure(
                text = AnnotatedString(label),
                style = textStyle,
                constraints = Constraints(maxWidth = textWidth)
            ).size.height
        }

        val resolvedCellH = if (bounded) {
            // Mai celle sproporzionate (troppo alte e strette): l'area in eccesso viene
            // distribuita come margine attorno alla griglia.
            minOf(cellH, cellW * 1.35f, with(density) { 160.dp.toPx() })
        } else {
            textHeight + fixedHeight + gap * 0.6f
        }
        val wastedCells = rows * columns - count
        val dividesEvenly = count % columns == 0
        val score = if (bounded) {
            // Pannello a schermo intero: prima leggibilità, poi griglie piene e bilanciate.
            fontSp * 300.0 +
                (if (dividesEvenly) 500.0 else 0.0) -
                wastedCells * 150.0 -
                abs(cellW - resolvedCellH) / 8.0
        } else {
            // Pannello libero: prima compattezza (zero scroll), poi leggibilità.
            fontSp * 15.0 +
                (if (dividesEvenly) 60.0 else 0.0) -
                (rows * resolvedCellH + gap * (rows - 1)) -
                wastedCells * 4.0
        }

        if (score > bestScore) {
            bestScore = score
            bestColumns = columns
            bestCellW = cellW
            bestCellH = max(resolvedCellH, 0f)
            bestPadH = padH
            bestIndicator = indicator
            bestFont = fontSp
        }
    }

    val padV = bestPadH * 0.75f
    return with(density) {
        ChoiceLayout(
            columns = bestColumns,
            cellWidth = bestCellW.toDp(),
            cellHeight = if (bestCellH > 0f) bestCellH.toDp() else bestCellW.toDp() * 0.32f,
            gap = gap.toDp(),
            padH = bestPadH.toDp(),
            padV = padV.toDp(),
            indicator = bestIndicator.toDp(),
            dotGap = (gap * 0.6f).toDp(),
            fontSize = bestFont.sp
        )
    }
}

/**
 * Trova la dimensione di font più grande (fra [minSp] e [maxSp]) che fa entrare tutte le
 * etichette in [textWidthPx] senza spezzare parole e in [maxTextHeightPx] di altezza.
 * Restituisce -1 quando nemmeno il minimo entra.
 */
private fun fitFontSize(
    measurer: TextMeasurer,
    labels: List<String>,
    textWidthPx: Int,
    maxTextHeightPx: Int,
    baseStyle: TextStyle,
    density: Density,
    minSp: Float,
    maxSp: Float
): Float {
    if (textWidthPx <= 0) return -1f

    var candidate = maxSp
    while (candidate >= minSp) {
        val style = baseStyle.copy(
            fontSize = candidate.sp,
            lineHeight = (candidate * LINE_HEIGHT_MULTIPLIER).sp
        )
        val lineHeightPx = with(density) { style.lineHeight.toPx() }
        val heightLimit = if (maxTextHeightPx == Int.MAX_VALUE) {
            (lineHeightPx * MAX_UNBOUNDED_LINES).toInt()
        } else {
            maxTextHeightPx
        }

        var fits = true
        for (label in labels) {
            // Nessuna parola spezzata: ogni singola parola deve stare su una riga.
            for (word in label.split(' ', '\n')) {
                if (word.isBlank()) continue
                val wordWidth = measurer.measure(
                    text = AnnotatedString(word),
                    style = style,
                    constraints = Constraints()
                ).size.width
                if (wordWidth > textWidthPx) {
                    fits = false
                    break
                }
            }
            if (!fits) break

            val measured = measurer.measure(
                text = AnnotatedString(label),
                style = style,
                constraints = Constraints(maxWidth = textWidthPx)
            )
            if (measured.size.height > heightLimit || measured.lineCount > MAX_UNBOUNDED_LINES) {
                fits = false
                break
            }
        }

        if (fits) return candidate
        candidate -= 0.5f
    }
    return -1f
}

@Composable
private fun ChoiceCell(
    label: String,
    isSelected: Boolean,
    layout: ChoiceLayout,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) colors.primaryContainer else colors.surfaceContainerHighest,
        label = "choiceCellContainer"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) colors.primary else colors.outlineVariant.copy(alpha = 0.45f),
        label = "choiceCellBorder"
    )
    val contentColor = if (isSelected) colors.onPrimaryContainer else colors.onSurface
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, shape)
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = layout.padH, vertical = layout.padV),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        SelectionDot(
            isSelected = isSelected,
            size = layout.indicator
        )
        Spacer(modifier = Modifier.height(layout.dotGap))
        Text(
            text = label,
            color = contentColor,
            fontSize = layout.fontSize,
            lineHeight = layout.fontSize * LINE_HEIGHT_MULTIPLIER,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SelectionDot(
    isSelected: Boolean,
    size: Dp
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isSelected) colors.primary else Color.Transparent)
            .border(
                width = if (isSelected) 0.dp else 1.5.dp,
                color = if (isSelected) Color.Transparent else colors.outline,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(size * 0.62f)
            )
        }
    }
}
