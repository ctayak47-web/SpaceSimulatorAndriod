package com.example.render

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.physics.TextureStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

// Генератор программных процедурных текстур (градиенты + 2D FBM шум, без внешних картинок)
object ProceduralTextureCache {

    private const val TEX_SIZE = 96
    private val bitmapCache = HashMap<TextureStyle, Bitmap>(20)
    private val composeCache = HashMap<TextureStyle, ImageBitmap>(20)

    fun getTexture(style: TextureStyle): Bitmap {
        return bitmapCache.getOrPut(style) { generateBitmap(style) }
    }

    fun getImageBitmap(style: TextureStyle): ImageBitmap {
        return composeCache.getOrPut(style) { getTexture(style).asImageBitmap() }
    }

    private fun generateBitmap(style: TextureStyle): Bitmap {
        val bmp = Bitmap.createBitmap(TEX_SIZE, TEX_SIZE, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(TEX_SIZE * TEX_SIZE)
        val half = TEX_SIZE * 0.5f
        val seed = style.ordinal * 137 + 19

        for (py in 0 until TEX_SIZE) {
            val ny = (py + 0.5f - half) / half
            for (px in 0 until TEX_SIZE) {
                val nx = (px + 0.5f - half) / half
                val rSq = nx * nx + ny * ny
                if (rSq > 1.0f) {
                    pixels[py * TEX_SIZE + px] = Color.TRANSPARENT
                    continue
                }

                val r = sqrt(rSq)
                val nz = sqrt((1.0f - rSq).coerceAtLeast(0f))
                val light = (0.62f + 0.38f * (-nx * 0.35f - ny * 0.35f + nz * 0.85f)).coerceIn(0.35f, 1.12f)

                val baseColor = sampleSurfaceColor(style, nx, ny, nz, r, seed)
                val edgeAlpha = ((1.0f - r) * 30f).coerceIn(0f, 1f)
                val finalAlpha = (edgeAlpha * 255f).toInt().coerceIn(0, 255)

                val red = (Color.red(baseColor) * light).toInt().coerceIn(0, 255)
                val green = (Color.green(baseColor) * light).toInt().coerceIn(0, 255)
                val blue = (Color.blue(baseColor) * light).toInt().coerceIn(0, 255)
                pixels[py * TEX_SIZE + px] = Color.argb(finalAlpha, red, green, blue)
            }
        }
        bmp.setPixels(pixels, 0, TEX_SIZE, 0, 0, TEX_SIZE, TEX_SIZE)
        return bmp
    }

    private fun sampleSurfaceColor(
        style: TextureStyle,
        nx: Float,
        ny: Float,
        nz: Float,
        r: Float,
        seed: Int
    ): Int {
        val u = nx * 2.8f + nz * 0.5f
        val v = ny * 2.8f
        val n1 = fbm(u + 4.1f, v + 2.3f, seed)
        val n2 = fbm(u * 2.4f - 1.7f, v * 2.4f + 5.1f, seed + 53)

        return when (style) {
            TextureStyle.SUN -> {
                val gran = fbm(nx * 5.2f, ny * 5.2f, seed)
                val core = (1.0f - r * 0.55f).coerceIn(0.35f, 1.0f)
                lerpColor(0xFFFF8F00.toInt(), 0xFFFFF59D.toInt(), (core * 0.75f + gran * 0.35f).coerceIn(0f, 1f))
            }
            TextureStyle.RED_GIANT -> {
                val cell = fbm(nx * 3.8f, ny * 3.8f, seed)
                lerpColor(0xFFB71C1C.toInt(), 0xFFFF7043.toInt(), cell.coerceIn(0f, 1f))
            }
            TextureStyle.BLUE_STAR -> {
                val gran = fbm(nx * 4.8f, ny * 4.8f, seed)
                val core = (1.0f - r * 0.5f).coerceIn(0.4f, 1.0f)
                lerpColor(0xFF2563EB.toInt(), 0xFFE0F2FE.toInt(), (core * 0.7f + gran * 0.35f).coerceIn(0f, 1f))
            }
            TextureStyle.WHITE_DWARF -> {
                val core = (1.0f - r * 0.4f).coerceIn(0.5f, 1.0f)
                lerpColor(0xFF93C5FD.toInt(), 0xFFFFFFFF.toInt(), (core * 0.8f + n1 * 0.2f).coerceIn(0f, 1f))
            }
            TextureStyle.EARTH -> {
                val polar = abs(ny) > 0.84f - n2 * 0.08f
                val continent = n1 * 0.75f + n2 * 0.25f
                val clouds = fbm(nx * 4.0f + 12f, ny * 3.5f - 7f, seed + 91)
                val surface = when {
                    polar -> 0xFFF1F5F9.toInt()
                    continent > 0.53f -> {
                        val desert = (continent - 0.53f) * 3.2f
                        lerpColor(0xFF16A34A.toInt(), 0xFFA16207.toInt(), desert.coerceIn(0f, 1f))
                    }
                    else -> lerpColor(0xFF1E3A8A.toInt(), 0xFF2563EB.toInt(), (continent / 0.53f).coerceIn(0f, 1f))
                }
                if (clouds > 0.60f) {
                    lerpColor(surface, 0xFFFFFFFF.toInt(), ((clouds - 0.60f) * 2.1f).coerceIn(0f, 0.8f))
                } else surface
            }
            TextureStyle.JUPITER -> {
                val band = sin((ny * 10.5f + n1 * 1.4f) * PI.toFloat()) * 0.5f + 0.5f
                val stormDist = sqrt((nx - 0.28f) * (nx - 0.28f) * 1.5f + (ny - 0.22f) * (ny - 0.22f) * 3.5f)
                val base = lerpColor(0xFF9A5B32.toInt(), 0xFFF3DEC0.toInt(), (band * 0.78f + n2 * 0.22f).coerceIn(0f, 1f))
                if (stormDist < 0.22f) lerpColor(base, 0xFFDC2626.toInt(), (1f - stormDist / 0.22f) * 0.85f) else base
            }
            TextureStyle.SATURN -> {
                val band = sin((ny * 8.5f + n1 * 0.6f) * PI.toFloat()) * 0.5f + 0.5f
                lerpColor(0xFFB5925B.toInt(), 0xFFF3E2B8.toInt(), (band * 0.7f + n2 * 0.3f).coerceIn(0f, 1f))
            }
            TextureStyle.MARS -> {
                val cap = abs(ny) > 0.89f
                if (cap) 0xFFF8FAFC.toInt()
                else lerpColor(0xFF7C2D12.toInt(), 0xFFEA580C.toInt(), (n1 * 0.65f + n2 * 0.35f).coerceIn(0f, 1f))
            }
            TextureStyle.VENUS -> {
                val swirl = sin((ny * 4.5f + n1 * 2.5f) * PI.toFloat()) * 0.5f + 0.5f
                lerpColor(0xFFC28E3A.toInt(), 0xFFFDE68A.toInt(), swirl.coerceIn(0f, 1f))
            }
            TextureStyle.MERCURY, TextureStyle.MOON -> {
                val crater = abs(n2 - 0.5f) * 2.0f
                lerpColor(0xFF52525B.toInt(), 0xFFD4D4D8.toInt(), (n1 * 0.6f + crater * 0.4f).coerceIn(0f, 1f))
            }
            TextureStyle.URANUS -> {
                val band = sin((ny * 3.5f + n1 * 0.4f) * PI.toFloat()) * 0.5f + 0.5f
                lerpColor(0xFF06B6D4.toInt(), 0xFFA5F3FC.toInt(), band.coerceIn(0f, 1f))
            }
            TextureStyle.NEPTUNE -> {
                val band = sin((ny * 6.0f + n1 * 1.1f) * PI.toFloat()) * 0.5f + 0.5f
                lerpColor(0xFF1E40AF.toInt(), 0xFF60A5FA.toInt(), (band * 0.75f + n2 * 0.25f).coerceIn(0f, 1f))
            }
            TextureStyle.PLUTO -> {
                val heart = sqrt((nx - 0.1f) * (nx - 0.1f) + (ny - 0.05f) * (ny - 0.05f))
                val base = lerpColor(0xFF785A40.toInt(), 0xFFD6C2AA.toInt(), n1.coerceIn(0f, 1f))
                if (heart < 0.34f) lerpColor(base, 0xFFF5EBE0.toInt(), (1f - heart / 0.34f) * 0.7f) else base
            }
            TextureStyle.LAVA_WORLD -> {
                val crack = abs(n2 - 0.5f) * 2.0f
                if (crack < 0.24f) lerpColor(0xFFFFF59D.toInt(), 0xFFFF5722.toInt(), crack / 0.24f)
                else lerpColor(0xFF271310.toInt(), 0xFF7F1D1D.toInt(), n1.coerceIn(0f, 1f))
            }
            TextureStyle.COMET -> lerpColor(0xFF0284C7.toInt(), 0xFFE0F2FE.toInt(), n1.coerceIn(0f, 1f))
            TextureStyle.ASTEROID -> lerpColor(0xFF44403C.toInt(), 0xFFA8A29E.toInt(), (n1 * 0.5f + n2 * 0.5f).coerceIn(0f, 1f))
            TextureStyle.BLACK_HOLE -> 0xFF000000.toInt()
        }
    }

    private fun fbm(x: Float, y: Float, seed: Int): Float {
        var value = 0f
        var amp = 0.5f
        var freq = 1f
        for (i in 0 until 3) {
            value += amp * valueNoise(x * freq, y * freq, seed + i * 31)
            freq *= 2.05f
            amp *= 0.5f
        }
        return value
    }

    private fun valueNoise(x: Float, y: Float, seed: Int): Float {
        val xi = floor(x).toInt()
        val yi = floor(y).toInt()
        val xf = x - xi
        val yf = y - yi
        val u = xf * xf * (3f - 2f * xf)
        val v = yf * yf * (3f - 2f * yf)

        val n00 = hash2d(xi, yi, seed)
        val n10 = hash2d(xi + 1, yi, seed)
        val n01 = hash2d(xi, yi + 1, seed)
        val n11 = hash2d(xi + 1, yi + 1, seed)

        val nx0 = n00 + (n10 - n00) * u
        val nx1 = n01 + (n11 - n01) * u
        return nx0 + (nx1 - nx0) * v
    }

    private fun hash2d(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) ushr 1) / Int.MAX_VALUE.toFloat()
    }

    private fun lerpColor(c1: Int, c2: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        val r = (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * f).toInt()
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * f).toInt()
        val b = (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * f).toInt()
        return Color.rgb(r, g, b)
    }
}
