package com.example.physics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Физические и астрономические константы (СИ)
object PhysicsUnits {
    const val G_SI = 6.67430e-11
    const val AU_METERS = 1.495978707e11
    const val EARTH_MASS_KG = 5.9722e24
    const val EARTH_RADIUS_M = 6.371e6
    const val SUN_MASS_KG = 1.98847e30
    const val SUN_RADIUS_M = 6.9634e8
    const val SUN_LUMINOSITY_W = 3.828e26
    const val STEFAN_BOLTZMANN = 5.670374419e-8
    const val MEGATON_JOULES = 4.184e15
    const val SECONDS_PER_MINUTE = 60.0
    const val SECONDS_PER_HOUR = 3600.0
    const val SECONDS_PER_DAY = 86400.0
    const val SECONDS_PER_YEAR = 31557600.0

    const val MAX_TIME_SCALE = 1_000_000.0
    private const val MAX_TIME_EXPONENT = 6.0 // 10^6 = 1 000 000x
    private const val PAUSE_THRESHOLD = 0.02f

    // Логарифмическая шкала ползунка [0f..1f] <-> timeScale [0 (пауза), 1..1_000_000x]
    fun sliderToTimeScale(sliderValue: Float): Double {
        val clamped = sliderValue.coerceIn(0f, 1f)
        if (clamped <= PAUSE_THRESHOLD) return 0.0
        val normalized = (clamped - PAUSE_THRESHOLD) / (1f - PAUSE_THRESHOLD)
        return 10.0.pow(normalized * MAX_TIME_EXPONENT)
    }

    fun timeScaleToSlider(timeScale: Double): Float {
        val mag = abs(timeScale)
        if (mag <= 0.0001) return 0f
        val clamped = mag.coerceIn(1.0, MAX_TIME_SCALE)
        val normalized = (log10(clamped) / MAX_TIME_EXPONENT).toFloat()
        return (PAUSE_THRESHOLD + normalized * (1f - PAUSE_THRESHOLD)).coerceIn(0f, 1f)
    }

    fun computeDensityKgM3(massKg: Double, radiusM: Double): Double {
        val r = radiusM.coerceAtLeast(1.0)
        val volume = (4.0 / 3.0) * PI * r * r * r
        return massKg / volume
    }

    fun computeSurfaceGravity(massKg: Double, radiusM: Double): Double {
        val r = radiusM.coerceAtLeast(1.0)
        return G_SI * massKg / (r * r)
    }

    fun computeEscapeVelocity(massKg: Double, radiusM: Double): Double {
        val r = radiusM.coerceAtLeast(1.0)
        return sqrt(2.0 * G_SI * massKg / r)
    }

    fun formatTimeScale(scale: Double, isReversed: Boolean, isPaused: Boolean): String {
        if (isPaused || abs(scale) <= 0.0001) return "Пауза (0×)"
        val mag = abs(scale)
        val prefix = if (isReversed) "◀ " else ""
        val formatted = when {
            mag < 10.0 -> "${(mag * 10).roundToInt() / 10.0}×"
            mag < 1_000.0 -> "${mag.roundToInt()}×"
            mag < 1_000_000.0 -> "${String.format("%.1f", mag / 1_000.0)} тыс. ×"
            else -> "${String.format("%.2f", mag / 1_000_000.0)} млн ×"
        }
        return "$prefix$formatted"
    }

    fun formatSimTime(seconds: Double): String {
        val absSec = abs(seconds)
        val sign = if (seconds < 0) "-" else ""
        return when {
            absSec < 60.0 -> "$sign${absSec.toInt()} сек"
            absSec < SECONDS_PER_HOUR -> "$sign${String.format("%.1f", absSec / 60.0)} мин"
            absSec < SECONDS_PER_DAY -> "$sign${String.format("%.1f", absSec / SECONDS_PER_HOUR)} ч"
            absSec < SECONDS_PER_YEAR -> "$sign${String.format("%.1f", absSec / SECONDS_PER_DAY)} дн"
            else -> "$sign${String.format("%.2f", absSec / SECONDS_PER_YEAR)} лет"
        }
    }

    fun formatMass(massKg: Double): String {
        val earthRatio = massKg / EARTH_MASS_KG
        val sunRatio = massKg / SUN_MASS_KG
        return when {
            sunRatio >= 0.05 -> "${String.format("%.2f", sunRatio)} M☉"
            earthRatio >= 0.01 -> "${String.format("%.2f", earthRatio)} M⊕"
            else -> String.format("%.2e кг", massKg)
        }
    }

    fun formatRadius(radiusM: Double): String {
        val km = radiusM / 1000.0
        return when {
            km >= 1_000_000.0 -> "${String.format("%.2f", km / 1_000_000.0)} млн км"
            km >= 10_000.0 -> "${String.format("%.1f", km / 1000.0)} тыс. км"
            else -> "${String.format("%.0f", km)} км"
        }
    }

    fun formatDensity(densityKgM3: Double): String {
        val gCm3 = densityKgM3 / 1000.0
        return when {
            gCm3 >= 1000.0 -> String.format("%.2e г/см³", gCm3)
            gCm3 >= 10.0 -> "${String.format("%.1f", gCm3)} г/см³"
            else -> "${String.format("%.2f", gCm3)} г/см³"
        }
    }

    fun formatGravity(gravityMs2: Double): String {
        return when {
            gravityMs2 >= 1000.0 -> "${String.format("%.0f", gravityMs2)} м/с²"
            gravityMs2 >= 10.0 -> "${String.format("%.1f", gravityMs2)} м/с²"
            else -> "${String.format("%.2f", gravityMs2)} м/с²"
        }
    }

    fun formatSpeed(speedMs: Double): String {
        val kms = speedMs / 1000.0
        return if (kms >= 1000.0) {
            "${String.format("%.0f", kms)} км/с"
        } else {
            "${String.format("%.2f", kms)} км/с"
        }
    }

    fun formatDistance(meters: Double): String {
        val au = meters / AU_METERS
        return when {
            au >= 0.05 -> "${String.format("%.3f", au)} а.е."
            meters >= 1_000_000.0 -> "${String.format("%.0f", meters / 1000.0)} км"
            else -> "${String.format("%.0f", meters)} м"
        }
    }

    fun formatTemperature(tempK: Double): String {
        val celsius = tempK - 273.15
        return "${tempK.roundToInt()} K (${celsius.roundToInt()} °C)"
    }

    fun formatPeriod(periodSeconds: Double?): String {
        if (periodSeconds == null || periodSeconds.isNaN() || periodSeconds.isInfinite() || periodSeconds <= 0.0) {
            return "Незамкнутая орбита"
        }
        val days = periodSeconds / SECONDS_PER_DAY
        return if (days >= 365.25) {
            "${String.format("%.2f", periodSeconds / SECONDS_PER_YEAR)} лет"
        } else {
            "${String.format("%.1f", days)} дн"
        }
    }

    fun formatEnergyMt(megatons: Double): String {
        return when {
            megatons >= 1_000_000_000.0 -> "${String.format("%.2f", megatons / 1_000_000_000.0)} Пт"
            megatons >= 1_000_000.0 -> "${String.format("%.2f", megatons / 1_000_000.0)} Тт"
            megatons >= 1_000.0 -> "${String.format("%.2f", megatons / 1_000.0)} Гт"
            megatons >= 1.0 -> "${String.format("%.1f", megatons)} Мт"
            else -> "${String.format("%.3f", megatons)} Мт"
        }
    }
}
