package com.example.render

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.R
import com.example.physics.BodyType
import com.example.physics.PhysicsUnits
import com.example.physics.RenderBody
import com.example.physics.SimulationSnapshot
import com.example.physics.TextureStyle
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// Класс отрисовки космоса через Jetpack Compose Canvas с тёмно-зелёной цветокоррекцией,
// искривлением гравитационной сетки, туманностью и многослойными процедурными эффектами
class SimulationCanvas {

    private val trailTailPath = Path()
    private val trailMidPath = Path()
    private val trailHeadPath = Path()
    private val gridPath = Path()
    private val predictPath = Path()
    private val dashEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)

    // Преаллоцированные массивы тяжёлых тел для расчёта гравитационного искривления сетки
    private val maxWellBodies = 12
    private val wellScreenX = FloatArray(maxWellBodies)
    private val wellScreenY = FloatArray(maxWellBodies)
    private val wellStrength = FloatArray(maxWellBodies)

    private val gridTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(170, 143, 184, 168)
        textSize = 24f
        typeface = Typeface.MONOSPACE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(235, 240, 253, 248)
        textSize = 28f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        setShadowLayer(4f, 0f, 1f, android.graphics.Color.BLACK)
    }
    private val arrowTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(167, 243, 208)
        textSize = 30f
        typeface = Typeface.MONOSPACE
        setShadowLayer(5f, 0f, 1f, android.graphics.Color.BLACK)
    }

    // Преаллоцированные звёзды фона (изумрудная, тёплая золотая и холодная белая палитра)
    private val starCount = 210
    private val starX = FloatArray(starCount)
    private val starY = FloatArray(starCount)
    private val starSize = FloatArray(starCount)
    private val starParallax = FloatArray(starCount)
    private val starColor = Array(starCount) { Color.White }

    init {
        val rng = Random(1337)
        for (i in 0 until starCount) {
            starX[i] = rng.nextFloat()
            starY[i] = rng.nextFloat()
            starSize[i] = 0.7f + rng.nextFloat() * 2.0f
            starParallax[i] = 1.2f + rng.nextFloat() * 3.0f
            val alpha = 75 + rng.nextInt(165)
            starColor[i] = when (rng.nextInt(6)) {
                0 -> Color(110, 231, 183, alpha) // Нежная изумрудная
                1 -> Color(167, 243, 208, alpha)
                2 -> Color(254, 240, 138, alpha)
                else -> Color(236, 253, 245, alpha)
            }
        }
    }

    fun DrawScope.render(
        snapshot: SimulationSnapshot,
        camera: CameraState,
        selectedBodyId: Long?,
        showGrid: Boolean,
        showGravityGrid: Boolean,
        showTrails: Boolean,
        showLabels: Boolean,
        showHabitableZone: Boolean,
        showVelocityVectors: Boolean,
        launchStartScreenX: Float?,
        launchStartScreenY: Float?,
        launchEndScreenX: Float?,
        launchEndScreenY: Float?,
        launchSpeedLabel: String?,
        predictedPointsWorld: FloatArray?,
        backdropBitmap: ImageBitmap?
    ) {
        val widthPx = size.width
        val heightPx = size.height

        // 1. Тёмно-изумрудный фон глубокого космоса + сгенерированная туманность
        drawRect(color = Color(0xFF010806))
        if (backdropBitmap != null) {
            drawNebulaBackdrop(backdropBitmap, widthPx, heightPx, camera)
        }
        // Тёмно-зелёная цветокоррекционная виньетка по краям
        drawRect(
            brush = Brush.radialGradient(
                0.0f to Color(0x10064E3B),
                0.65f to Color(0x28021A12),
                1.0f to Color(0xAA010705),
                center = Offset(widthPx * 0.5f, heightPx * 0.5f),
                radius = max(widthPx, heightPx) * 0.78f
            )
        )

        // 2. Звёздное небо с многослойным параллаксом
        drawBackgroundStars(widthPx, heightPx, camera)

        // 3. Сетка расстояний (опционально искривляется гравитационными колодцами)
        if (showGrid) {
            drawDistanceGrid(widthPx, heightPx, camera, snapshot, showGravityGrid)
        }

        // 4. Обитаемая зона (Зона Златовласки) вокруг звёзд
        if (showHabitableZone) {
            drawHabitableZones(widthPx, heightPx, snapshot, camera)
        }

        // 5. Плавные затухающие следы орбит
        if (showTrails) {
            drawOrbitTrails(widthPx, heightPx, snapshot, camera)
        }

        // 6. Прогноз траектории при запуске тела
        if (predictedPointsWorld != null && predictedPointsWorld.size >= 4) {
            drawPredictedOrbit(widthPx, heightPx, camera, predictedPointsWorld)
        }

        // 7. Хвосты комет (изумрудно-бирюзовый ионный + пылевой)
        if (snapshot.hasStar) {
            drawCometTails(widthPx, heightPx, snapshot, camera)
        }

        // 8. Небесные тела
        val bodies = snapshot.bodies
        val animPhase = (snapshot.simTimeSeconds * 0.0002).toFloat()
        for (i in 0 until bodies.size) {
            val body = bodies[i]
            val sx = camera.worldToScreenX(body.x, widthPx)
            val sy = camera.worldToScreenY(body.y, widthPx, heightPx)
            val physPx = camera.metersToPx(body.radiusM, widthPx)
            val drawR = computeVisualRadius(body, physPx)

            val margin = drawR * 4.8f + 70f
            if (sx < -margin || sx > widthPx + margin || sy < -margin || sy > heightPx + margin) {
                continue
            }

            if (body.isLightweight) {
                drawLightweightParticle(body, sx, sy, drawR)
            } else {
                drawHeavyBody(
                    body = body,
                    sx = sx,
                    sy = sy,
                    drawR = drawR,
                    isSelected = body.id == selectedBodyId,
                    snapshot = snapshot,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    camera = camera,
                    showVelocityVector = showVelocityVectors,
                    animPhase = animPhase
                )
            }
        }

        // 9. Ударные вспышки, волны и сверхновые
        drawImpactFlashes(widthPx, heightPx, snapshot, camera)

        // 10. Подписи тел
        if (showLabels) {
            drawLabels(widthPx, heightPx, snapshot, camera, selectedBodyId)
        }

        // 11. Стрелка вектора начальной скорости
        if (launchStartScreenX != null && launchStartScreenY != null &&
            launchEndScreenX != null && launchEndScreenY != null
        ) {
            drawLaunchArrow(
                launchStartScreenX,
                launchStartScreenY,
                launchEndScreenX,
                launchEndScreenY,
                launchSpeedLabel
            )
        }
    }

    private fun DrawScope.drawNebulaBackdrop(
        bmp: ImageBitmap,
        widthPx: Float,
        heightPx: Float,
        camera: CameraState
    ) {
        val scale = max(widthPx / bmp.width.toFloat(), heightPx / bmp.height.toFloat()) * 1.12f
        val dstW = (bmp.width * scale).roundToInt()
        val dstH = (bmp.height * scale).roundToInt()
        val shiftX = ((-camera.centerX / PhysicsUnits.AU_METERS) * 0.6).toFloat().coerceIn(-45f, 45f)
        val shiftY = ((-camera.centerY / PhysicsUnits.AU_METERS) * 0.6).toFloat().coerceIn(-45f, 45f)
        val ox = ((widthPx - dstW) * 0.5f + shiftX).roundToInt()
        val oy = ((heightPx - dstH) * 0.5f + shiftY).roundToInt()
        drawImage(
            image = bmp,
            dstOffset = IntOffset(ox, oy),
            dstSize = IntSize(dstW, dstH),
            alpha = 0.36f
        )
    }

    private fun DrawScope.drawBackgroundStars(widthPx: Float, heightPx: Float, camera: CameraState) {
        val camAuX = camera.centerX / PhysicsUnits.AU_METERS
        val camAuY = camera.centerY / PhysicsUnits.AU_METERS
        for (i in 0 until starCount) {
            val pxShift = (camAuX * starParallax[i]).toFloat()
            val pyShift = (camAuY * starParallax[i]).toFloat()
            val x = ((starX[i] * widthPx - pxShift) % widthPx + widthPx) % widthPx
            val y = ((starY[i] * heightPx - pyShift) % heightPx + heightPx) % heightPx
            drawCircle(
                color = starColor[i],
                radius = starSize[i],
                center = Offset(x, y)
            )
        }
    }

    private fun DrawScope.drawDistanceGrid(
        widthPx: Float,
        heightPx: Float,
        camera: CameraState,
        snapshot: SimulationSnapshot,
        showGravityGrid: Boolean
    ) {
        val visibleSpanM = camera.halfWidthMeters * 2.0
        val rawStep = visibleSpanM / 4.2
        val exp = floor(log10(rawStep))
        val pow10 = 10.0.pow(exp)
        val mantissa = rawStep / pow10
        val stepM = when {
            mantissa < 1.5 -> 1.0 * pow10
            mantissa < 3.5 -> 2.0 * pow10
            mantissa < 7.5 -> 5.0 * pow10
            else -> 10.0 * pow10
        }

        val leftM = camera.screenToWorldX(0f, widthPx)
        val rightM = camera.screenToWorldX(widthPx, widthPx)
        val topM = camera.screenToWorldY(0f, widthPx, heightPx)
        val bottomM = camera.screenToWorldY(heightPx, widthPx, heightPx)

        // Собираем гравитационные колодцы для искривления сетки
        var wellCount = 0
        if (showGravityGrid) {
            val bodies = snapshot.bodies
            val minMass = PhysicsUnits.EARTH_MASS_KG * 15.0
            for (i in 0 until bodies.size) {
                val b = bodies[i]
                if (b.isLightweight || b.massKg < minMass) continue
                if (wellCount >= maxWellBodies) break
                val sx = camera.worldToScreenX(b.x, widthPx)
                val sy = camera.worldToScreenY(b.y, widthPx, heightPx)
                if (sx < -widthPx || sx > widthPx * 2f || sy < -heightPx || sy > heightPx * 2f) continue
                wellScreenX[wellCount] = sx
                wellScreenY[wellCount] = sy
                // Логарифмически-пропорциональная сила искривления
                val massFactor = (log10(b.massKg / minMass).toFloat() * 14f).coerceIn(8f, 68f)
                wellStrength[wellCount] = massFactor
                wellCount++
            }
        }

        val lineColor = Color(0x2E10B981)
        val axisColor = Color(0x5234D399)

        fun warpPoint(px: Float, py: Float): Offset {
            if (wellCount == 0) return Offset(px, py)
            var outX = px
            var outY = py
            for (w in 0 until wellCount) {
                val dx = wellScreenX[w] - px
                val dy = wellScreenY[w] - py
                val dSq = dx * dx + dy * dy + 2400f
                val d = sqrt(dSq)
                val pull = (wellStrength[w] * 950f / dSq).coerceAtMost(0.42f * d)
                outX += (dx / d) * pull
                outY += (dy / d) * pull
            }
            return Offset(outX, outY)
        }

        val segments = if (wellCount > 0) 22 else 1

        var wx = floor(leftM / stepM) * stepM
        val endX = ceil(rightM / stepM) * stepM
        var guard = 0
        while (wx <= endX && guard < 24) {
            val sx = camera.worldToScreenX(wx, widthPx)
            val isAxis = kotlin.math.abs(wx) < stepM * 0.05
            if (segments == 1) {
                drawLine(
                    color = if (isAxis) axisColor else lineColor,
                    start = Offset(sx, 0f),
                    end = Offset(sx, heightPx),
                    strokeWidth = if (isAxis) 1.6f else 1.1f
                )
            } else {
                gridPath.reset()
                for (s in 0..segments) {
                    val sy = (s.toFloat() / segments) * heightPx
                    val pt = warpPoint(sx, sy)
                    if (s == 0) gridPath.moveTo(pt.x, pt.y) else gridPath.lineTo(pt.x, pt.y)
                }
                drawPath(
                    path = gridPath,
                    color = if (isAxis) axisColor else lineColor,
                    style = Stroke(width = if (isAxis) 1.6f else 1.1f)
                )
            }
            wx += stepM
            guard++
        }

        var wy = floor(topM / stepM) * stepM
        val endY = ceil(bottomM / stepM) * stepM
        guard = 0
        while (wy <= endY && guard < 24) {
            val sy = camera.worldToScreenY(wy, widthPx, heightPx)
            val isAxis = kotlin.math.abs(wy) < stepM * 0.05
            if (segments == 1) {
                drawLine(
                    color = if (isAxis) axisColor else lineColor,
                    start = Offset(0f, sy),
                    end = Offset(widthPx, sy),
                    strokeWidth = if (isAxis) 1.6f else 1.1f
                )
            } else {
                gridPath.reset()
                for (s in 0..segments) {
                    val sx = (s.toFloat() / segments) * widthPx
                    val pt = warpPoint(sx, sy)
                    if (s == 0) gridPath.moveTo(pt.x, pt.y) else gridPath.lineTo(pt.x, pt.y)
                }
                drawPath(
                    path = gridPath,
                    color = if (isAxis) axisColor else lineColor,
                    style = Stroke(width = if (isAxis) 1.6f else 1.1f)
                )
            }
            wy += stepM
            guard++
        }

        val label = "Шаг сетки: ${formatGridStep(stepM)}"
        drawContext.canvas.nativeCanvas.drawText(label, 28f, heightPx * 0.16f, gridTextPaint)
    }

    private fun DrawScope.drawHabitableZones(
        widthPx: Float,
        heightPx: Float,
        snapshot: SimulationSnapshot,
        camera: CameraState
    ) {
        val bodies = snapshot.bodies
        for (i in 0 until bodies.size) {
            val b = bodies[i]
            if (b.type != BodyType.STAR || b.luminosityW <= 0.0) continue
            val lumRatio = b.luminosityW / PhysicsUnits.SUN_LUMINOSITY_W
            val sqrtLum = sqrt(lumRatio)
            val innerM = 0.95 * sqrtLum * PhysicsUnits.AU_METERS
            val outerM = 1.37 * sqrtLum * PhysicsUnits.AU_METERS

            val sx = camera.worldToScreenX(b.x, widthPx)
            val sy = camera.worldToScreenY(b.y, widthPx, heightPx)
            val rIn = camera.metersToPx(innerM, widthPx)
            val rOut = camera.metersToPx(outerM, widthPx)
            if (rOut < 8f || rIn > widthPx * 4f) continue

            val bandWidth = max(2f, rOut - rIn)
            val midR = (rIn + rOut) * 0.5f
            drawCircle(
                color = Color(0x2210B981),
                radius = midR,
                center = Offset(sx, sy),
                style = Stroke(width = bandWidth)
            )
            drawCircle(
                color = Color(0x6634D399),
                radius = rIn,
                center = Offset(sx, sy),
                style = Stroke(width = 1.3f, pathEffect = dashEffect)
            )
            drawCircle(
                color = Color(0x6634D399),
                radius = rOut,
                center = Offset(sx, sy),
                style = Stroke(width = 1.3f, pathEffect = dashEffect)
            )
        }
    }

    // Трёхступенчатый затухающий след орбиты без лишних аллокаций
    private fun DrawScope.drawOrbitTrails(
        widthPx: Float,
        heightPx: Float,
        snapshot: SimulationSnapshot,
        camera: CameraState
    ) {
        val bodies = snapshot.bodies
        for (i in 0 until bodies.size) {
            val b = bodies[i]
            val count = b.trailCount
            if (count < 2) continue
            val cap = b.trailCapacity
            val xs = b.trailXs
            val ys = b.trailYs
            val head = b.trailHead
            val startIdx = (head - count + cap) % cap

            val split1 = count / 3
            val split2 = (count * 2) / 3

            trailTailPath.reset()
            trailMidPath.reset()
            trailHeadPath.reset()

            var prevSx = 0f
            var prevSy = 0f
            for (k in 0 until count) {
                val idx = (startIdx + k) % cap
                val sx = camera.worldToScreenX(xs[idx], widthPx)
                val sy = camera.worldToScreenY(ys[idx], widthPx, heightPx)
                when {
                    k <= split1 -> {
                        if (k == 0) trailTailPath.moveTo(sx, sy) else trailTailPath.lineTo(sx, sy)
                        if (k == split1) trailMidPath.moveTo(sx, sy)
                    }
                    k <= split2 -> {
                        trailMidPath.lineTo(sx, sy)
                        if (k == split2) trailHeadPath.moveTo(sx, sy)
                    }
                    else -> {
                        trailHeadPath.lineTo(sx, sy)
                    }
                }
                prevSx = sx
                prevSy = sy
            }
            val curSx = camera.worldToScreenX(b.x, widthPx)
            val curSy = camera.worldToScreenY(b.y, widthPx, heightPx)
            if (split2 >= count - 1) {
                trailHeadPath.moveTo(prevSx, prevSy)
            }
            trailHeadPath.lineTo(curSx, curSy)

            val tint = Color(b.primaryColorArgb)
            if (split1 > 0) {
                drawPath(trailTailPath, tint.copy(alpha = 0.16f), style = Stroke(width = 1.5f, cap = StrokeCap.Round))
            }
            if (split2 > split1) {
                drawPath(trailMidPath, tint.copy(alpha = 0.36f), style = Stroke(width = 2.0f, cap = StrokeCap.Round))
            }
            drawPath(trailHeadPath, tint.copy(alpha = 0.65f), style = Stroke(width = 2.5f, cap = StrokeCap.Round))
        }
    }

    private fun DrawScope.drawCometTails(
        widthPx: Float,
        heightPx: Float,
        snapshot: SimulationSnapshot,
        camera: CameraState
    ) {
        val bodies = snapshot.bodies
        val starX = snapshot.nearestStarX
        val starY = snapshot.nearestStarY
        for (i in 0 until bodies.size) {
            val b = bodies[i]
            if (b.type != BodyType.COMET) continue
            val dx = b.x - starX
            val dy = b.y - starY
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < 1e6 || dist > PhysicsUnits.AU_METERS * 8.5) continue

            val sx = camera.worldToScreenX(b.x, widthPx)
            val sy = camera.worldToScreenY(b.y, widthPx, heightPx)
            val ux = (dx / dist).toFloat()
            val uy = (dy / dist).toFloat()
            val intensity = (1.0 - dist / (PhysicsUnits.AU_METERS * 8.5)).toFloat().coerceIn(0.18f, 1f)
            val tailLenPx = (58f + 105f * intensity).coerceAtMost(170f)
            val end = Offset(sx + ux * tailLenPx, sy + uy * tailLenPx)

            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xAA2DD4BF), Color.Transparent),
                    start = Offset(sx, sy),
                    end = end
                ),
                start = Offset(sx, sy),
                end = end,
                strokeWidth = 8.5f,
                cap = StrokeCap.Round
            )
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xEEA7F3D0), Color.Transparent),
                    start = Offset(sx, sy),
                    end = Offset(sx + ux * tailLenPx * 0.78f, sy + uy * tailLenPx * 0.78f)
                ),
                start = Offset(sx, sy),
                end = Offset(sx + ux * tailLenPx * 0.78f, sy + uy * tailLenPx * 0.78f),
                strokeWidth = 3.0f,
                cap = StrokeCap.Round
            )
        }
    }

    private fun DrawScope.drawLightweightParticle(
        b: RenderBody,
        sx: Float,
        sy: Float,
        drawR: Float
    ) {
        val c = if (b.surfaceTempK > 950.0) Color(0xFFFF8A3D) else Color(b.primaryColorArgb)
        if (b.surfaceTempK > 950.0 || b.type == BodyType.STAR) {
            drawCircle(color = c.copy(alpha = 0.28f), radius = drawR * 2.1f, center = Offset(sx, sy))
        }
        drawCircle(color = c, radius = drawR, center = Offset(sx, sy))
    }

    private fun DrawScope.drawHeavyBody(
        body: RenderBody,
        sx: Float,
        sy: Float,
        drawR: Float,
        isSelected: Boolean,
        snapshot: SimulationSnapshot,
        widthPx: Float,
        heightPx: Float,
        camera: CameraState,
        showVelocityVector: Boolean,
        animPhase: Float
    ) {
        val center = Offset(sx, sy)
        val primary = Color(body.primaryColorArgb)
        val secondary = Color(body.secondaryColorArgb)

        if (body.type == BodyType.STAR) {
            val pulse = 1.0f + 0.06f * sin(animPhase + body.id)
            val coronaR = drawR * 3.8f * pulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primary.copy(alpha = 0.72f),
                        secondary.copy(alpha = 0.28f),
                        Color(0x1610B981),
                        Color.Transparent
                    ),
                    center = center,
                    radius = coronaR
                ),
                radius = coronaR,
                center = center
            )

            // Если это нейтронная звезда / пульсар — вращающиеся релятивистские лучи
            if (body.textureStyle == TextureStyle.NEUTRON_STAR) {
                rotate(degrees = (animPhase * 42f) % 360f, pivot = center) {
                    val beamLen = drawR * 5.2f
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color(0xDD6EE7B7), Color.Transparent),
                            start = Offset(sx - beamLen, sy),
                            end = Offset(sx + beamLen, sy)
                        ),
                        start = Offset(sx - beamLen, sy),
                        end = Offset(sx + beamLen, sy),
                        strokeWidth = max(2.2f, drawR * 0.28f),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Основные и диагональные дифракционные лучи
            val rayLen = drawR * 2.75f * pulse
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, primary.copy(alpha = 0.58f), Color.Transparent),
                    start = Offset(sx - rayLen, sy),
                    end = Offset(sx + rayLen, sy)
                ),
                start = Offset(sx - rayLen, sy),
                end = Offset(sx + rayLen, sy),
                strokeWidth = max(1.6f, drawR * 0.14f)
            )
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, primary.copy(alpha = 0.58f), Color.Transparent),
                    start = Offset(sx, sy - rayLen),
                    end = Offset(sx, sy + rayLen)
                ),
                start = Offset(sx, sy - rayLen),
                end = Offset(sx, sy + rayLen),
                strokeWidth = max(1.6f, drawR * 0.14f)
            )
            val diag = rayLen * 0.58f
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, secondary.copy(alpha = 0.30f), Color.Transparent),
                    start = Offset(sx - diag, sy - diag),
                    end = Offset(sx + diag, sy + diag)
                ),
                start = Offset(sx - diag, sy - diag),
                end = Offset(sx + diag, sy + diag),
                strokeWidth = max(1.2f, drawR * 0.08f)
            )
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, secondary.copy(alpha = 0.30f), Color.Transparent),
                    start = Offset(sx - diag, sy + diag),
                    end = Offset(sx + diag, sy - diag)
                ),
                start = Offset(sx - diag, sy + diag),
                end = Offset(sx + diag, sy - diag),
                strokeWidth = max(1.2f, drawR * 0.08f)
            )

            // Текстура фотосферы + яркое ядро
            val img = ProceduralTextureCache.getImageBitmap(body.textureStyle)
            val dInt = (drawR * 2f).roundToInt().coerceAtLeast(2)
            drawImage(
                image = img,
                dstOffset = IntOffset((sx - drawR).roundToInt(), (sy - drawR).roundToInt()),
                dstSize = IntSize(dInt, dInt)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.52f),
                        primary.copy(alpha = 0.18f),
                        secondary.copy(alpha = 0.34f)
                    ),
                    center = center,
                    radius = drawR
                ),
                radius = drawR,
                center = center
            )
        } else if (body.type == BodyType.BLACK_HOLE) {
            // Гравитационное линзирование в тёмно-изумрудной и янтарной гамме
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x9910B981),
                        Color(0x442DD4BF),
                        Color(0x18064E3B),
                        Color.Transparent
                    ),
                    center = center,
                    radius = drawR * 3.8f
                ),
                radius = drawR * 3.8f,
                center = center
            )

            rotate(degrees = -22f, pivot = center) {
                val outerW = drawR * 5.0f
                val outerH = drawR * 1.32f
                drawOval(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xAA34D399),
                            Color(0x882DD4BF),
                            Color(0x66065F46)
                        ),
                        start = Offset(sx - outerW * 0.5f, sy),
                        end = Offset(sx + outerW * 0.5f, sy)
                    ),
                    topLeft = Offset(sx - outerW * 0.5f, sy - outerH * 0.5f),
                    size = Size(outerW, outerH),
                    style = Stroke(width = max(2.0f, drawR * 0.25f))
                )

                val diskW = drawR * 3.9f
                val diskH = drawR * 1.0f
                drawOval(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xEEA7F3D0),
                            Color(0xCCF59E0B),
                            Color(0x7710B981)
                        ),
                        start = Offset(sx - diskW * 0.5f, sy),
                        end = Offset(sx + diskW * 0.5f, sy)
                    ),
                    topLeft = Offset(sx - diskW * 0.5f, sy - diskH * 0.5f),
                    size = Size(diskW, diskH),
                    style = Stroke(width = max(2.4f, drawR * 0.34f))
                )
            }

            // Горизонт событий и яркое фотонное кольцо
            drawCircle(color = Color.Black, radius = drawR, center = center)
            drawCircle(
                color = Color(0xFFA7F3D0),
                radius = drawR,
                center = center,
                style = Stroke(width = max(1.8f, drawR * 0.12f))
            )
        } else {
            // Задняя дуга колец
            if (body.hasRings && drawR >= 5f) {
                drawPlanetRings(body, sx, sy, drawR, frontHalf = false)
            }

            // Атмосферное гало
            if (body.atmosphereFraction > 0.15 && drawR >= 5f && body.surfaceTempK < 1800.0) {
                val atmoR = drawR * 1.26f
                val atmoColor = when {
                    body.name == "Венера" || body.name == "Титан" -> Color(0xFFFBBF24)
                    body.name == "Изумрудия" || body.name == "Трисолярис" -> Color(0xFF34D399)
                    else -> Color(0xFF38BDF8)
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        0.66f to Color.Transparent,
                        0.84f to atmoColor.copy(alpha = (0.48f * body.atmosphereFraction.toFloat()).coerceIn(0.12f, 0.58f)),
                        1.0f to Color.Transparent,
                        center = center,
                        radius = atmoR
                    ),
                    radius = atmoR,
                    center = center
                )
            }

            // Процедурная текстура поверхности
            val img = ProceduralTextureCache.getImageBitmap(body.textureStyle)
            val dInt = (drawR * 2f).roundToInt().coerceAtLeast(2)
            drawImage(
                image = img,
                dstOffset = IntOffset((sx - drawR).roundToInt(), (sy - drawR).roundToInt()),
                dstSize = IntSize(dInt, dInt)
            )

            // Освещённость от ближайшей звезды (светлая сторона + ночной терминатор)
            if (snapshot.hasStar) {
                val lx = snapshot.nearestStarX - body.x
                val ly = snapshot.nearestStarY - body.y
                val lDist = sqrt(lx * lx + ly * ly)
                if (lDist > 1e3) {
                    val ux = (lx / lDist).toFloat()
                    val uy = (ly / lDist).toFloat()
                    val litFocus = Offset(sx + ux * drawR * 0.48f, sy + uy * drawR * 0.48f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to Color.White.copy(alpha = 0.24f),
                            0.55f to Color.Transparent,
                            1.0f to Color(0xC6000604),
                            center = litFocus,
                            radius = drawR * 1.38f
                        ),
                        radius = drawR,
                        center = center
                    )
                }
            }

            // Нагрев до раскалённого состояния
            if (body.surfaceTempK > 780.0) {
                val heatRatio = ((body.surfaceTempK - 780.0) / 3200.0).toFloat().coerceIn(0f, 1f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFF59D).copy(alpha = heatRatio * 0.92f),
                            Color(0xFFFF5722).copy(alpha = heatRatio * 0.74f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = drawR * 1.38f
                    ),
                    radius = drawR * 1.38f,
                    center = center
                )
            }

            // Передняя дуга колец
            if (body.hasRings && drawR >= 5f) {
                drawPlanetRings(body, sx, sy, drawR, frontHalf = true)
            }
        }

        // Вектор скорости
        if (showVelocityVector) {
            val speed = sqrt(body.vx * body.vx + body.vy * body.vy)
            if (speed > 100.0) {
                val ux = (body.vx / speed).toFloat()
                val uy = (body.vy / speed).toFloat()
                val len = (24f + (speed / 2500.0).toFloat()).coerceIn(22f, 92f)
                val end = Offset(sx + ux * len, sy + uy * len)
                drawLine(
                    color = Color(0xBB34D399),
                    start = center,
                    end = end,
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
                drawCircle(color = Color(0xFF34D399), radius = 3.4f, center = end)
            }
        }

        // Прицельная рамка выбранного тела (изумрудная)
        if (isSelected) {
            val selR = drawR + 11f
            val selColor = Color(0xFF34D399)
            drawCircle(
                color = selColor,
                radius = selR,
                center = center,
                style = Stroke(width = 3f)
            )
            val tick = 8f
            drawLine(selColor, Offset(sx - selR - tick, sy), Offset(sx - selR + 3f, sy), strokeWidth = 3f)
            drawLine(selColor, Offset(sx + selR - 3f, sy), Offset(sx + selR + tick, sy), strokeWidth = 3f)
            drawLine(selColor, Offset(sx, sy - selR - tick), Offset(sx, sy - selR + 3f), strokeWidth = 3f)
            drawLine(selColor, Offset(sx, sy + selR - 3f), Offset(sx, sy + selR + tick), strokeWidth = 3f)
        }
    }

    // Многополосные кольца планеты с щелью Кассини
    private fun DrawScope.drawPlanetRings(
        b: RenderBody,
        sx: Float,
        sy: Float,
        drawR: Float,
        frontHalf: Boolean
    ) {
        val innerRatio = b.ringInnerRatio
        val outerRatio = b.ringOuterRatio
        val span = outerRatio - innerRatio
        val rInnerBand = drawR * (innerRatio + span * 0.25f)
        val rOuterBand = drawR * (innerRatio + span * 0.78f)
        val thickInner = max(1.6f, drawR * span * 0.34f)
        val thickOuter = max(1.4f, drawR * span * 0.26f)
        val baseTint = Color(b.primaryColorArgb)
        val startAngle = if (frontHalf) 0f else 180f

        rotate(degrees = -18f, pivot = Offset(sx, sy)) {
            drawArc(
                color = baseTint.copy(alpha = 0.44f),
                startAngle = startAngle,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(sx - rOuterBand, sy - rOuterBand * 0.28f),
                size = Size(rOuterBand * 2f, rOuterBand * 0.56f),
                style = Stroke(width = thickOuter)
            )
            drawArc(
                color = baseTint.copy(alpha = 0.72f),
                startAngle = startAngle,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(sx - rInnerBand, sy - rInnerBand * 0.28f),
                size = Size(rInnerBand * 2f, rInnerBand * 0.56f),
                style = Stroke(width = thickInner)
            )
        }
    }

    private fun DrawScope.drawImpactFlashes(
        widthPx: Float,
        heightPx: Float,
        snapshot: SimulationSnapshot,
        camera: CameraState
    ) {
        val flashes = snapshot.flashes
        for (i in 0 until flashes.size) {
            val f = flashes[i]
            val p = (f.ageWallSec / f.durationWallSec).coerceIn(0f, 1f)
            val sx = camera.worldToScreenX(f.x, widthPx)
            val sy = camera.worldToScreenY(f.y, widthPx, heightPx)
            val maxPx = max(48f, camera.metersToPx(f.maxVisualRadiusM, widthPx)).coerceAtMost(360f)
            val curR = maxPx * (0.15f + 0.85f * p)
            val alpha = (1f - p).coerceIn(0f, 1f)
            val flashColor = Color(f.colorArgb).copy(alpha = alpha * 0.94f)

            if (f.isShockwave) {
                drawCircle(
                    color = flashColor,
                    radius = curR,
                    center = Offset(sx, sy),
                    style = Stroke(width = max(2.4f, 13f * (1f - p)))
                )
            }
            if (p < 0.58f) {
                val coreAlpha = ((1f - p / 0.58f) * 0.9f).coerceIn(0f, 1f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFFDE7).copy(alpha = coreAlpha),
                            flashColor.copy(alpha = coreAlpha * 0.55f),
                            Color.Transparent
                        ),
                        center = Offset(sx, sy),
                        radius = curR * 0.75f
                    ),
                    radius = curR * 0.75f,
                    center = Offset(sx, sy)
                )
            }
        }
    }

    private fun DrawScope.drawLabels(
        widthPx: Float,
        heightPx: Float,
        snapshot: SimulationSnapshot,
        camera: CameraState,
        selectedBodyId: Long?
    ) {
        val bodies = snapshot.bodies
        val zoomedIn = camera.halfWidthMeters < PhysicsUnits.AU_METERS * 0.25
        val native = drawContext.canvas.nativeCanvas
        for (i in 0 until bodies.size) {
            val b = bodies[i]
            if (b.isLightweight) continue
            if (b.type == BodyType.MOON && !zoomedIn && b.id != selectedBodyId) continue

            val sx = camera.worldToScreenX(b.x, widthPx)
            val sy = camera.worldToScreenY(b.y, widthPx, heightPx)
            if (sx < -40f || sx > widthPx + 40f || sy < -40f || sy > heightPx + 40f) continue

            val drawR = computeVisualRadius(b, camera.metersToPx(b.radiusM, widthPx))
            labelPaint.color = if (b.id == selectedBodyId) {
                android.graphics.Color.rgb(52, 211, 153)
            } else {
                android.graphics.Color.argb(228, 240, 253, 248)
            }
            native.drawText(b.name, sx + drawR + 9f, sy + 7f, labelPaint)
        }
    }

    private fun DrawScope.drawPredictedOrbit(
        widthPx: Float,
        heightPx: Float,
        camera: CameraState,
        ptsWorld: FloatArray
    ) {
        predictPath.reset()
        val count = ptsWorld.size / 2
        for (i in 0 until count) {
            val sx = camera.worldToScreenX(ptsWorld[i * 2].toDouble(), widthPx)
            val sy = camera.worldToScreenY(ptsWorld[i * 2 + 1].toDouble(), widthPx, heightPx)
            if (i == 0) predictPath.moveTo(sx, sy) else predictPath.lineTo(sx, sy)
        }
        drawPath(
            path = predictPath,
            color = Color(0xBB34D399),
            style = Stroke(width = 3f, pathEffect = dashEffect)
        )
    }

    private fun DrawScope.drawLaunchArrow(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        speedLabel: String?
    ) {
        val start = Offset(x0, y0)
        val end = Offset(x1, y1)
        drawCircle(color = Color(0x6610B981), radius = 22f, center = start)
        drawCircle(color = Color(0xFFF0FDF8), radius = 8f, center = start)

        val arrowColor = Color(0xFF34D399)
        drawLine(arrowColor, start, end, strokeWidth = 5f, cap = StrokeCap.Round)
        val dx = x1 - x0
        val dy = y1 - y0
        val len = sqrt(dx * dx + dy * dy)
        if (len > 20f) {
            val ux = dx / len
            val uy = dy / len
            val headLen = 22f
            val px = -uy
            val py = ux
            drawLine(arrowColor, end, Offset(x1 - ux * headLen + px * 11f, y1 - uy * headLen + py * 11f), strokeWidth = 5f, cap = StrokeCap.Round)
            drawLine(arrowColor, end, Offset(x1 - ux * headLen - px * 11f, y1 - uy * headLen - py * 11f), strokeWidth = 5f, cap = StrokeCap.Round)
        }
        if (speedLabel != null) {
            drawContext.canvas.nativeCanvas.drawText(speedLabel, x1 + 14f, y1 - 14f, arrowTextPaint)
        }
    }

    fun computeVisualRadius(b: RenderBody, physPx: Float): Float {
        if (b.isLightweight) {
            return max(physPx, if (b.type == BodyType.STAR) 2.9f else 2.2f).coerceAtMost(28f)
        }
        val minPx = when (b.type) {
            BodyType.STAR -> 13f
            BodyType.BLACK_HOLE -> 12.5f
            BodyType.PLANET -> 7.2f
            BodyType.COMET -> 4.8f
            BodyType.MOON -> 3.8f
            BodyType.ASTEROID -> 4.0f
        }
        return max(physPx, minPx)
    }

    private fun formatGridStep(stepM: Double): String {
        val au = stepM / PhysicsUnits.AU_METERS
        return when {
            au >= 0.05 -> "${String.format("%.2f", au).trimEnd('0').trimEnd('.')} а.е."
            stepM >= 1_000_000.0 -> "${String.format("%.0f", stepM / 1000.0)} км"
            else -> "${String.format("%.0f", stepM)} м"
        }
    }
}

@Composable
fun SimulationCanvas(
    snapshot: SimulationSnapshot,
    camera: CameraState,
    selectedBodyId: Long?,
    showGrid: Boolean,
    showGravityGrid: Boolean,
    showTrails: Boolean,
    showLabels: Boolean,
    showHabitableZone: Boolean,
    showVelocityVectors: Boolean,
    launchStartScreenX: Float?,
    launchStartScreenY: Float?,
    launchEndScreenX: Float?,
    launchEndScreenY: Float?,
    launchSpeedLabel: String?,
    predictedPointsWorld: FloatArray?,
    modifier: Modifier = Modifier
) {
    val renderer = remember { SimulationCanvas() }
    val nebulaBitmap = ImageBitmap.imageResource(id = R.drawable.img_emerald_nebula)
    Canvas(modifier = modifier.fillMaxSize()) {
        with(renderer) {
            render(
                snapshot = snapshot,
                camera = camera,
                selectedBodyId = selectedBodyId,
                showGrid = showGrid,
                showGravityGrid = showGravityGrid,
                showTrails = showTrails,
                showLabels = showLabels,
                showHabitableZone = showHabitableZone,
                showVelocityVectors = showVelocityVectors,
                launchStartScreenX = launchStartScreenX,
                launchStartScreenY = launchStartScreenY,
                launchEndScreenX = launchEndScreenX,
                launchEndScreenY = launchEndScreenY,
                launchSpeedLabel = launchSpeedLabel,
                predictedPointsWorld = predictedPointsWorld,
                backdropBitmap = nebulaBitmap
            )
        }
    }
}
