package com.example.render

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.physics.TextureStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

// Генератор программных 3D-сферических процедурных текстур (градиенты + 2D FBM шум)
object ProceduralTextureCache {

    private const val TEX_SIZE = 144
    private val bitmapCache = HashMap<TextureStyle, Bitmap>(24)
    private val composeCache = HashMap<TextureStyle, ImageBitmap>(24)

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

                // Сферическое отображение координат (долгота и широта)
                val lon = atan2(nx, nz) / PI.toFloat() * 2.2f
                val lat = asin(ny.coerceIn(-1f, 1f)) / (PI.toFloat() * 0.5f)

                val limbDarkening = (0.58f + 0.42f * nz).coerceIn(0.42f, 1.05f)
                val light = (0.68f + 0.34f * (-nx * 0.35f - ny * 0.32f + nz * 0.85f)).coerceIn(0.40f, 1.12f)
                val shade = (limbDarkening * 0.35f + light * 0.65f).coerceIn(0.38f, 1.12f)

                val baseColor = sampleSurfaceColor(style, nx, ny, nz, lon, lat, r, seed)
                val edgeAlpha = ((1.0f - r) * 36f).coerceIn(0f, 1f)
                val finalAlpha = (edgeAlpha * 255f).toInt().coerceIn(0, 255)

                val isEmissive = style == TextureStyle.SUN ||
                    style == TextureStyle.RED_GIANT ||
                    style == TextureStyle.BLUE_STAR ||
                    style == TextureStyle.WHITE_DWARF ||
                    style == TextureStyle.NEUTRON_STAR
                val finalShade = if (isEmissive) (0.78f + 0.28f * nz) else shade

                val red = (Color.red(baseColor) * finalShade).toInt().coerceIn(0, 255)
                val green = (Color.green(baseColor) * finalShade).toInt().coerceIn(0, 255)
                val blue = (Color.blue(baseColor) * finalShade).toInt().coerceIn(0, 255)
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
        lon: Float,
        lat: Float,
        r: Float,
        seed: Int
    ): Int {
        val u = lon * 2.6f
        val v = lat * 2.4f
        val n1 = fbm(u + 4.1f, v + 2.3f, seed)
        val n2 = fbm(u * 2.3f - 1.7f, v * 2.3f + 5.1f, seed + 53)
        val n3 = fbm(u * 4.5f + 8.3f, v * 4.5f - 2.9f, seed + 109)

        return when (style) {
            TextureStyle.SUN -> {
                val gran = fbm(lon * 5.5f, lat * 5.5f, seed)
                val flare = abs(n3 - 0.5f) * 2f
                val core = (1.0f - r * 0.55f).coerceIn(0.35f, 1.0f)
                val t = (core * 0.65f + gran * 0.25f + flare * 0.12f).coerceIn(0f, 1f)
                lerpColor(0xFFFF7A00.toInt(), 0xFFFFF8B0.toInt(), t)
            }
            TextureStyle.RED_GIANT -> {
                val cell = fbm(lon * 3.4f, lat * 3.4f, seed)
                val coolSpots = (n2 * 0.6f + cell * 0.4f).coerceIn(0f, 1f)
                lerpColor(0xFF991B1B.toInt(), 0xFFFF7043.toInt(), coolSpots)
            }
            TextureStyle.BLUE_STAR -> {
                val gran = fbm(lon * 5.0f, lat * 5.0f, seed)
                val core = (1.0f - r * 0.45f).coerceIn(0.4f, 1.0f)
                lerpColor(0xFF0EA5E9.toInt(), 0xFFECFDF5.toInt(), (core * 0.72f + gran * 0.32f).coerceIn(0f, 1f))
            }
            TextureStyle.WHITE_DWARF -> {
                val core = (1.0f - r * 0.35f).coerceIn(0.55f, 1.0f)
                lerpColor(0xFF6EE7B7.toInt(), 0xFFFFFFFF.toInt(), (core * 0.8f + n1 * 0.2f).coerceIn(0f, 1f))
            }
            TextureStyle.NEUTRON_STAR -> {
                val poles = (1f - abs(nx)).coerceIn(0f, 1f) * abs(ny)
                val base = lerpColor(0xFF047857.toInt(), 0xFF6EE7B7.toInt(), (n1 * 0.6f + (1f - r) * 0.5f).coerceIn(0f, 1f))
                lerpColor(base, 0xFFF0FDF4.toInt(), (poles * 1.1f).coerceIn(0f, 1f))
            }
            TextureStyle.EARTH -> {
                val polar = abs(lat) > 0.82f - n2 * 0.09f
                val continent = n1 * 0.72f + n2 * 0.28f
                val clouds = fbm(lon * 3.8f + 12f, lat * 3.2f - 7f, seed + 91)
                val specular = ((1f - abs(nx + 0.25f) - abs(ny + 0.25f)) * 1.4f).coerceIn(0f, 0.38f)
                val surface = when {
                    polar -> 0xFFF1F5F9.toInt()
                    continent > 0.52f -> {
                        val relief = ((continent - 0.52f) * 3.4f).coerceIn(0f, 1f)
                        val forest = lerpColor(0xFF059669.toInt(), 0xFF65A30D.toInt(), n3)
                        lerpColor(forest, 0xFFB45309.toInt(), relief)
                    }
                    else -> {
                        val depth = (continent / 0.52f).coerceIn(0f, 1f)
                        val ocean = lerpColor(0xFF0F2942.toInt(), 0xFF0EA5E9.toInt(), depth)
                        lerpColor(ocean, 0xFF6EE7B7.toInt(), specular * 0.45f)
                    }
                }
                if (clouds > 0.58f) {
                    lerpColor(surface, 0xFFFFFFFF.toInt(), ((clouds - 0.58f) * 2.2f).coerceIn(0f, 0.86f))
                } else surface
            }
            TextureStyle.JUPITER -> {
                val turb = (n1 - 0.5f) * 1.35f + (n3 - 0.5f) * 0.45f
                val band = sin((lat * 9.5f + turb) * PI.toFloat()) * 0.5f + 0.5f
                val stormDist = sqrt((nx - 0.26f) * (nx - 0.26f) * 1.4f + (ny - 0.20f) * (ny - 0.20f) * 3.6f)
                val darkBelt = lerpColor(0xFF8C4A27.toInt(), 0xFFD99B66.toInt(), n2)
                val base = lerpColor(darkBelt, 0xFFF7E7CE.toInt(), (band * 0.82f + n2 * 0.18f).coerceIn(0f, 1f))
                if (stormDist < 0.23f) lerpColor(base, 0xFFDC2626.toInt(), (1f - stormDist / 0.23f) * 0.9f) else base
            }
            TextureStyle.SATURN -> {
                val band = sin((lat * 8.5f + n1 * 0.55f) * PI.toFloat()) * 0.5f + 0.5f
                lerpColor(0xFFA68451.toInt(), 0xFFF5E6C4.toInt(), (band * 0.72f + n2 * 0.28f).coerceIn(0f, 1f))
            }
            TextureStyle.MARS -> {
                val cap = abs(lat) > 0.88f
                if (cap) 0xFFF8FAFC.toInt()
                else {
                    val basin = (n1 * 0.65f + n2 * 0.35f).coerceIn(0f, 1f)
                    lerpColor(0xFF6B2410.toInt(), 0xFFEA580C.toInt(), basin)
                }
            }
            TextureStyle.VENUS -> {
                val swirl = sin((lat * 4.5f + lon * 1.2f + n1 * 2.6f) * PI.toFloat()) * 0.5f + 0.5f
                lerpColor(0xFFB47D2D.toInt(), 0xFFFEF08A.toInt(), (swirl * 0.8f + n2 * 0.2f).coerceIn(0f, 1f))
            }
            TextureStyle.MERCURY, TextureStyle.MOON -> {
                val crater = abs(n2 - 0.5f) * 2.0f
                val maria = n1
                val base = lerpColor(0xFF3F3F46.toInt(), 0xFFD4D4D8.toInt(), (maria * 0.65f + crater * 0.35f).coerceIn(0f, 1f))
                if (n3 > 0.72f) lerpColor(base, 0xFFF4F4F5.toInt(), 0.35f) else base
            }
            TextureStyle.URANUS -> {
                val band = sin((lat * 3.8f + n1 * 0.4f) * PI.toFloat()) * 0.5f + 0.5f
                lerpColor(0xFF0D9488.toInt(), 0xFFA7F3D0.toInt(), band.coerceIn(0f, 1f))
            }
            TextureStyle.NEPTUNE -> {
                val band = sin((lat * 6.5f + n1 * 1.15f) * PI.toFloat()) * 0.5f + 0.5f
                lerpColor(0xFF1E3A8A.toInt(), 0xFF38BDF8.toInt(), (band * 0.75f + n2 * 0.25f).coerceIn(0f, 1f))
            }
            TextureStyle.PLUTO -> {
                val heart = sqrt((nx - 0.1f) * (nx - 0.1f) + (ny - 0.05f) * (ny - 0.05f))
                val base = lerpColor(0xFF6D4C3D.toInt(), 0xFFD6C2AA.toInt(), n1.coerceIn(0f, 1f))
                if (heart < 0.34f) lerpColor(base, 0xFFF5EBE0.toInt(), (1f - heart / 0.34f) * 0.78f) else base
            }
            TextureStyle.LAVA_WORLD -> {
                val crack = abs(n2 - 0.5f) * 2.0f
                val microCrack = abs(n3 - 0.5f) * 2.0f
                val minCrack = minOf(crack, microCrack * 1.3f)
                if (minCrack < 0.23f) lerpColor(0xFFFFF9C4.toInt(), 0xFFFF5722.toInt(), minCrack / 0.23f)
                else lerpColor(0xFF1C0D0A.toInt(), 0xFF6B1D1D.toInt(), n1.coerceIn(0f, 1f))
            }
            TextureStyle.COMET -> lerpColor(0xFF0D9488.toInt(), 0xFFCCFBF1.toInt(), n1.coerceIn(0f, 1f))
            TextureStyle.ASTEROID -> lerpColor(0xFF44403C.toInt(), 0xFFA8A29E.toInt(), (n1 * 0.55f + n2 * 0.45f).coerceIn(0f, 1f))
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
