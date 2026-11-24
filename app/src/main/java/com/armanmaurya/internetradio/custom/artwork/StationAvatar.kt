package com.armanmaurya.internetradio.custom.artwork

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.ColorUtils

/**
 * Network-free, deterministic station artwork.
 *
 * The same station key always produces the same colours and initials, on every device and after
 * every app restart, so nothing has to be cached or stored.
 */
object StationAvatar {

    private const val MAX_INITIALS = 2
    private val TOKEN_SPLIT = Regex("[^\\p{L}\\p{N}]+")

    /** Stable FNV-1a hash (not [String.hashCode], whose contract we do not want to depend on). */
    fun seed(key: String): Int {
        var hash = -2128831035 // 0x811C9DC5
        for (ch in key) {
            hash = hash xor ch.code
            hash *= 16777619
        }
        return hash
    }

    fun initials(name: String): String {
        val tokens = name.split(TOKEN_SPLIT).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return "?"

        val base = tokens.filter { token -> token.any { it.isLetter() } }.ifEmpty { tokens }

        return base
            .take(MAX_INITIALS)
            .map { token -> token.first().uppercaseChar() }
            .joinToString("")
    }

    /** Top and bottom colour of the radial gradient, darkened until the text is readable. */
    fun backgroundColors(seed: Int): IntArray {
        val unsigned = seed and 0x7FFFFFFF
        val hue = (unsigned % 360).toFloat()
        val saturation = 0.55f + ((unsigned ushr 9) and 0xFF) / 255f * 0.20f
        var value = 0.55f + ((unsigned ushr 17) and 0xFF) / 255f * 0.20f

        var top = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
        while (bestContrast(top) < MIN_CONTRAST && value > 0.15f) {
            value -= 0.04f
            top = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
        }

        val bottom = android.graphics.Color.HSVToColor(
            floatArrayOf((hue + 32f) % 360f, minOf(1f, saturation + 0.10f), value * 0.62f)
        )
        return intArrayOf(top, bottom)
    }

    fun textColor(seed: Int): Int {
        val top = backgroundColors(seed)[0]
        return bestTextColor(top)
    }

    private fun bestTextColor(background: Int): Int =
        if (contrast(DARK_TEXT, background) >= contrast(android.graphics.Color.WHITE, background)) {
            DARK_TEXT
        } else {
            android.graphics.Color.WHITE
        }

    private fun bestContrast(background: Int): Double =
        maxOf(
            contrast(DARK_TEXT, background),
            contrast(android.graphics.Color.WHITE, background)
        )

    private fun contrast(foreground: Int, background: Int): Double =
        ColorUtils.calculateContrast(foreground, background)

    private const val DARK_TEXT = 0xFF14202B.toInt()
    private const val MIN_CONTRAST = 4.5

    fun draw(canvas: Canvas, width: Float, height: Float, name: String, key: String) {
        if (width <= 0f || height <= 0f) return
        val seedValue = seed(key.ifBlank { name })
        val colors = backgroundColors(seedValue)

        val background = Paint(Paint.ANTI_ALIAS_FLAG)
        background.shader = RadialGradient(
            width * 0.35f,
            height * 0.25f,
            maxOf(width, height),
            intArrayOf(colors[0], colors[1]),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width, height, background)

        val text = initials(name)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = bestTextColor(colors[0])
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = minOf(width, height) * if (text.length > 1) 0.34f else 0.42f
        }
        val metrics = textPaint.fontMetrics
        val baseline = height / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, width / 2f, baseline, textPaint)
    }

    fun bitmap(name: String, key: String, sizePx: Int): Bitmap {
        val safeSize = sizePx.coerceIn(16, 1024)
        val bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap), safeSize.toFloat(), safeSize.toFloat(), name, key)
        return bitmap
    }
}

/** Compose painter that renders [StationAvatar] directly: no bitmap allocation per frame. */
class StationAvatarPainter(
    private val name: String,
    private val key: String,
) : Painter() {

    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        if (size.width <= 0f || size.height <= 0f) return
        drawIntoCanvas { canvas ->
            StationAvatar.draw(canvas.nativeCanvas, size.width, size.height, name, key)
        }
    }

    override fun equals(other: Any?): Boolean =
        other is StationAvatarPainter && other.name == name && other.key == key

    override fun hashCode(): Int = 31 * name.hashCode() + key.hashCode()
}

@Composable
fun StationAvatar(
    name: String,
    key: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val painter = remember(name, key) { StationAvatarPainter(name, key) }
    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
    )
}
