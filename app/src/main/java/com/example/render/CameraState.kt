package com.example.render

import com.example.physics.PhysicsUnits

// Состояние камеры: центр в метрах и половина ширины экрана в метрах
data class CameraState(
    val centerX: Double = 0.0,
    val centerY: Double = 0.0,
    val halfWidthMeters: Double = PhysicsUnits.AU_METERS * 2.4,
    val followBodyId: Long? = null
) {
    companion object {
        // От масштаба одной планеты (~2000 км) до 100 а.е.
        const val MIN_HALF_WIDTH_M = 2.0e6
        const val MAX_HALF_WIDTH_M = PhysicsUnits.AU_METERS * 80.0
    }

    fun metersPerPixel(viewWidthPx: Float): Double {
        if (viewWidthPx <= 1f) return halfWidthMeters / 540.0
        return (halfWidthMeters * 2.0) / viewWidthPx.toDouble()
    }

    fun worldToScreenX(worldX: Double, viewWidthPx: Float): Float {
        val mpp = metersPerPixel(viewWidthPx)
        return (viewWidthPx * 0.5 + (worldX - centerX) / mpp).toFloat()
    }

    fun worldToScreenY(worldY: Double, viewWidthPx: Float, viewHeightPx: Float): Float {
        val mpp = metersPerPixel(viewWidthPx)
        return (viewHeightPx * 0.5 + (worldY - centerY) / mpp).toFloat()
    }

    fun screenToWorldX(screenX: Float, viewWidthPx: Float): Double {
        val mpp = metersPerPixel(viewWidthPx)
        return centerX + (screenX - viewWidthPx * 0.5) * mpp
    }

    fun screenToWorldY(screenY: Float, viewWidthPx: Float, viewHeightPx: Float): Double {
        val mpp = metersPerPixel(viewWidthPx)
        return centerY + (screenY - viewHeightPx * 0.5) * mpp
    }

    fun metersToPx(meters: Double, viewWidthPx: Float): Float {
        return (meters / metersPerPixel(viewWidthPx)).toFloat()
    }
}
