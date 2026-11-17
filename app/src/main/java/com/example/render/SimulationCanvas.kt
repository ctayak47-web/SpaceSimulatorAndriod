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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.physics.BodyType
import com.example.physics.PhysicsUnits
import com.example.physics.RenderBody
import com.example.physics.SimulationSnapshot
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

// Класс отрисовки космоса через Jetpack Compose Canvas с процедурными градиентами атмосферы и освещённости
class SimulationCanvas {

    private val trailPath = Path()
    private val predictPath = Path()
    private val dashEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)

    private val gridTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(145, 148, 163, 184)
        textSize = 24f
        typeface = Typeface.MONOSPACE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(230, 248, 250, 252)
        textSize = 28f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        setShadowLayer(4f, 0f, 1f, android.graphics.Color.BLACK)
    }
    private val arrowTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(191, 219, 254)
        textSize = 30f
        typeface = Typeface.MONOSPACE
        setShadowLayer(5f, 0f, 1f, android.graphics.Color.BLACK)
    }

    // Преаллоцированные звёзды фона для стабильных 60 FPS
    private val starCount = 160
    private val starX = FloatArray(starCount)
    private val starY = FloatArray(starCount)
    private val starSize = FloatArray(starCount)
    private val starColor = Array(starCount) { Color.White }

    init {
        val rng = Random(1337)
        for (i in 0 until starCount) {
            starX[i] = rng.nextFloat()
            starY[i] = rng.nextFloat()
            starSize[i] = 0.8f + rng.nextFloat() * 1.9f
            val alpha = 75 + rng.nextInt(155)
            starColor[i] = when (rng.nextInt(5)) {
                0 -> Color(191, 219, 254, alpha)
                1 -> Color(254, 240, 138, alpha)
                else -> Color(226, 232, 240, alpha)
            }
        }
    }

    fun DrawScope.render(
        snapshot: SimulationSnapshot,
        camera: CameraState,
        selectedBodyId: Long?,
        showGrid: Boolean,
        showTrails: Boolean,
        showLabels: Boolean,
        showHabitableZone: Boolean,
        showVelocityVectors: Boolean,
        launchStartScreenX: Float?,
        launchStartScreenY: Float?,
        launchEndScreenX: Float?,
        launchEndScreenY: Float?,
        launchSpeedLabel: String?,
        predictedPointsWorld: FloatArray?
    ) {
        val widthPx = size.width
        val heightPx = size.height

        // 1. Чистый чёрный фон глубокого космоса
        drawRect(color = Color.Black)

        // 2. Звёздное небо с мягким параллаксом
        drawBackgroundStars(widthPx, heightPx, camera)

        // 3. Сетка расстояний
        if (showGrid) {
            drawDistanceGrid(widthPx, heightPx, camera)
        }

        // 4. Обитаемая зона (Зона Златовласки) вокруг звёзд
        if (showHabitableZone) {
            drawHabitableZones(widthPx, heightPx, snapshot, camera)
        }

        // 5. Следы орбит
        if (showTrails) {
            drawOrbitTrails(widthPx, heightPx, snapshot, camera)
        }

        // 6. Прогноз траектории при запуске тела
        if (predictedPointsWorld != null && predictedPointsWorld.size >= 4) {
            drawPredictedOrbit(widthPx, heightPx, camera, predictedPointsWorld)
        }

        // 7. Хвосты комет
        if (snapshot.hasStar) {
            drawCometTails(widthPx, heightPx, snapshot, camera)
        }

        // 8. Небесные тела (окружности с процедурными текстурами и градиентами атмосферы/освещённости)
        val bodies = snapshot.bodies
        for (i in 0 until bodies.size) {
            val body = bodies[i]
            val sx = camera.worldToScreenX(body.x, widthPx)
            val sy = camera.worldToScreenY(body.y, widthPx, heightPx)
            val physPx = camera.metersToPx(body.radiusM, widthPx)
            val drawR = computeVisualRadius(body, physPx)

            val margin = drawR * 4.5f + 60f
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
                    showVelocityVector = showVelocityVectors
                )
            }
        }

        // 9. Ударные вспышки и волны
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

    private fun DrawScope.drawBackgroundStars(widthPx: Float, heightPx: Float, camera: CameraState) {
        val pxShift = ((camera.centerX / PhysicsUnits.AU_METERS) * 2.5).toFloat()
        val pyShift = ((camera.centerY / PhysicsUnits.AU_METERS) * 2.5).toFloat()
        for (i in 0 until starCount) {
            val x = ((starX[i] * widthPx - pxShift) % widthPx + widthPx) % widthPx
            val y = ((starY[i] * heightPx - pyShift) % heightPx + heightPx) % heightPx
            drawCircle(
                color = starColor[i],
                radius = starSize[i],
                center = Offset(x, y)
            )
        }
    }

    private fun DrawScope.drawDistanceGrid(widthPx: Float, heightPx: Float, camera: CameraState) {
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

        val lineColor = Color(0x2E3B82F6)
        val axisColor = Color(0x4A60A5FA)

        var wx = floor(leftM / stepM) * stepM
        val endX = ceil(rightM / stepM) * stepM
        var guard = 0
        while (wx <= endX && guard < 24) {
            val sx = camera.worldToScreenX(wx, widthPx)
            val isAxis = kotlin.math.abs(wx) < stepM * 0.05
            drawLine(
                color = if (isAxis) axisColor else lineColor,
                start = Offset(sx, 0f),
                end = Offset(sx, heightPx),
                strokeWidth = if (isAxis) 1.6f else 1.1f
            )
            wx += stepM
            guard++
        }

        var wy = floor(topM / stepM) * stepM
        val endY = ceil(bottomM / stepM) * stepM
        guard = 0
        while (wy <= endY && guard < 24) {
            val sy = camera.worldToScreenY(wy, widthPx, heightPx)
            val isAxis = kotlin.math.abs(wy) < stepM * 0.05
            drawLine(
                color = if (isAxis) axisColor else lineColor,
                start = Offset(0f, sy),
                end = Offset(widthPx, sy),
                strokeWidth = if (isAxis) 1.6f else 1.1f
            )
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
                color = Color(0x1C10B981),
                radius = midR,
                center = Offset(sx, sy),
                style = Stroke(width = bandWidth)
            )
            drawCircle(
                color = Color(0x5534D399),
                radius = rIn,
                center = Offset(sx, sy),
                style = Stroke(width = 1.2f, pathEffect = dashEffect)
            )
            drawCircle(
                color = Color(0x5534D399),
                radius = rOut,
                center = Offset(sx, sy),
                style = Stroke(width = 1.2f, pathEffect = dashEffect)
            )
        }
    }

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

            trailPath.reset()
            for (k in 0 until count) {
                val idx = (startIdx + k) % cap
                val sx = camera.worldToScreenX(xs[idx], widthPx)
                val sy = camera.worldToScreenY(ys[idx], widthPx, heightPx)
                if (k == 0) trailPath.moveTo(sx, sy) else trailPath.lineTo(sx, sy)
            }
            trailPath.lineTo(
                camera.worldToScreenX(b.x, widthPx),
                camera.worldToScreenY(b.y, widthPx, heightPx)
            )

            val tint = Color(b.primaryColorArgb).copy(alpha = 0.42f)
            drawPath(
                path = trailPath,
                color = tint,
                style = Stroke(width = 2.1f, cap = StrokeCap.Round)
            )
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
            if (dist < 1e6 || dist > PhysicsUnits.AU_METERS * 8.0) continue

            val sx = camera.worldToScreenX(b.x, widthPx)
            val sy = camera.worldToScreenY(b.y, widthPx, heightPx)
            val ux = (dx / dist).toFloat()
            val uy = (dy / dist).toFloat()
            val intensity = (1.0 - dist / (PhysicsUnits.AU_METERS * 8.0)).toFloat().coerceIn(0.15f, 1f)
            val tailLenPx = (52f + 95f * intensity).coerceAtMost(155f)
            val end = Offset(sx + ux * tailLenPx, sy + uy * tailLenPx)

            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0x997DD3FC), Color.Transparent),
                    start = Offset(sx, sy),
                    end = end
                ),
                start = Offset(sx, sy),
                end = end,
                strokeWidth = 7.5f,
                cap = StrokeCap.Round
            )
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xDDE0F2FE), Color.Transparent),
                    start = Offset(sx, sy),
                    end = end
                ),
                start = Offset(sx, sy),
                end = Offset(sx + ux * tailLenPx * 0.75f, sy + uy * tailLenPx * 0.75f),
                strokeWidth = 2.8f,
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
        showVelocityVector: Boolean
    ) {
        val center = Offset(sx, sy)
        val primary = Color(body.primaryColorArgb)
        val secondary = Color(body.secondaryColorArgb)

        // 1. Звёзды: многослойный процедурный градиент короны + лучи + фотосфера
        if (body.type == BodyType.STAR) {
            val coronaR = drawR * 3.4f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primary.copy(alpha = 0.65f),
                        secondary.copy(alpha = 0.25f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = coronaR
                ),
                radius = coronaR,
                center = center
            )

            // Лучи дифракционной короны для звёзд
            val rayLen = drawR * 2.5f
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, primary.copy(alpha = 0.55f), Color.Transparent),
                    start = Offset(sx - rayLen, sy),
                    end = Offset(sx + rayLen, sy)
                ),
                start = Offset(sx - rayLen, sy),
                end = Offset(sx + rayLen, sy),
                strokeWidth = max(1.6f, drawR * 0.14f)
            )
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, primary.copy(alpha = 0.55f), Color.Transparent),
                    start = Offset(sx, sy - rayLen),
                    end = Offset(sx, sy + rayLen)
                ),
                start = Offset(sx, sy - rayLen),
                end = Offset(sx, sy + rayLen),
                strokeWidth = max(1.6f, drawR * 0.14f)
            )

            // Текстура фотосферы + яркое ядро градиентом
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
                        Color.White.copy(alpha = 0.55f),
                        primary.copy(alpha = 0.18f),
                        secondary.copy(alpha = 0.35f)
                    ),
                    center = center,
                    radius = drawR
                ),
                radius = drawR,
                center = center
            )
        } else if (body.type == BodyType.BLACK_HOLE) {
            // Чёрная дыра: гравитационное линзирование + аккреционный диск + горизонт событий
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x883B82F6),
                        Color(0x3360A5FA),
                        Color.Transparent
                    ),
                    center = center,
                    radius = drawR * 3.2f
                ),
                radius = drawR * 3.2f,
                center = center
            )

            rotate(degrees = -22f, pivot = center) {
                val diskW = drawR * 4.4f
                val diskH = drawR * 1.15f
                drawOval(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xDD93C5FD),
                            Color(0xAAF59E0B),
                            Color(0x553B82F6)
                        ),
                        start = Offset(sx - diskW * 0.5f, sy),
                        end = Offset(sx + diskW * 0.5f, sy)
                    ),
                    topLeft = Offset(sx - diskW * 0.5f, sy - diskH * 0.5f),
                    size = Size(diskW, diskH),
                    style = Stroke(width = max(2.4f, drawR * 0.35f))
                )
            }

            // Горизонт событий и фотонное кольцо
            drawCircle(color = Color.Black, radius = drawR, center = center)
            drawCircle(
                color = Color(0xFFE0F2FE),
                radius = drawR,
                center = center,
                style = Stroke(width = max(1.8f, drawR * 0.12f))
            )
        } else {
            // 2. Планеты, спутники и кометы
            if (body.hasRings && drawR >= 5f) {
                drawPlanetRings(body, sx, sy, drawR, frontHalf = false)
            }

            // Атмосферное гало (процедурный радиальный градиент)
            if (body.atmosphereFraction > 0.15 && drawR >= 5f && body.surfaceTempK < 1800.0) {
                val atmoR = drawR * 1.24f
                val atmoColor = if (body.name == "Венера" || body.name == "Титан") {
                    Color(0xFFFBBF24)
                } else {
                    Color(0xFF60A5FA)
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        0.68f to Color.Transparent,
                        0.84f to atmoColor.copy(alpha = (0.45f * body.atmosphereFraction.toFloat()).coerceIn(0.1f, 0.55f)),
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

            // Процедурная освещённость от ближайшей звезды (светлая сторона + ночной терминатор)
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
                            0.0f to Color.White.copy(alpha = 0.22f),
                            0.55f to Color.Transparent,
                            1.0f to Color(0xC200030A),
                            center = litFocus,
                            radius = drawR * 1.38f
                        ),
                        radius = drawR,
                        center = center
                    )
                }
            }

            // Нагрев поверхности до раскалённого состояния (после столкновений или у звезды)
            if (body.surfaceTempK > 780.0) {
                val heatRatio = ((body.surfaceTempK - 780.0) / 3200.0).toFloat().coerceIn(0f, 1f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFF59D).copy(alpha = heatRatio * 0.9f),
                            Color(0xFFFF5722).copy(alpha = heatRatio * 0.72f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = drawR * 1.35f
                    ),
                    radius = drawR * 1.35f,
                    center = center
                )
            }

            if (body.hasRings && drawR >= 5f) {
                drawPlanetRings(body, sx, sy, drawR, frontHalf = true)
            }
        }

        // 3. Вектор скорости тела (опциональный слой)
        if (showVelocityVector) {
            val speed = sqrt(body.vx * body.vx + body.vy * body.vy)
            if (speed > 100.0) {
                val ux = (body.vx / speed).toFloat()
                val uy = (body.vy / speed).toFloat()
                val len = (24f + (speed / 2500.0).toFloat()).coerceIn(22f, 88f)
                val end = Offset(sx + ux * len, sy + uy * len)
                drawLine(
                    color = Color(0xAA22D3EE),
                    start = center,
                    end = end,
                    strokeWidth = 2.4f,
                    cap = StrokeCap.Round
                )
                drawCircle(color = Color(0xFF22D3EE), radius = 3.2f, center = end)
            }
        }

        // 4. Прицельная рамка выбранного тела
        if (isSelected) {
            val selR = drawR + 11f
            val selColor = Color(0xFF3B82F6)
            drawCircle(
                color = selColor,
                radius = selR,
                center = center,
                style = Stroke(width = 3f)
            )
            val tick = 7f
            drawLine(selColor, Offset(sx - selR - tick, sy), Offset(sx - selR + 3f, sy), strokeWidth = 3f)
            drawLine(selColor, Offset(sx + selR - 3f, sy), Offset(sx + selR + tick, sy), strokeWidth = 3f)
            drawLine(selColor, Offset(sx, sy - selR - tick), Offset(sx, sy - selR + 3f), strokeWidth = 3f)
            drawLine(selColor, Offset(sx, sy + selR - 3f), Offset(sx, sy + selR + tick), strokeWidth = 3f)
        }
    }

    private fun DrawScope.drawPlanetRings(
        b: RenderBody,
        sx: Float,
        sy: Float,
        drawR: Float,
        frontHalf: Boolean
    ) {
        val midRatio = (b.ringInnerRatio + b.ringOuterRatio) * 0.5f
        val rx = drawR * midRatio
        val ry = rx * 0.28f
        val ringThickness = max(2.2f, drawR * (b.ringOuterRatio - b.ringInnerRatio) * 0.55f)
        val tint = Color(b.primaryColorArgb).copy(alpha = 0.62f)
        rotate(degrees = -18f, pivot = Offset(sx, sy)) {
            drawArc(
                color = tint,
                startAngle = if (frontHalf) 0f else 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(sx - rx, sy - ry),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(width = ringThickness)
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
            val maxPx = max(42f, camera.metersToPx(f.maxVisualRadiusM, widthPx)).coerceAtMost(310f)
            val curR = maxPx * (0.16f + 0.84f * p)
            val alpha = (1f - p).coerceIn(0f, 1f)
            val flashColor = Color(f.colorArgb).copy(alpha = alpha * 0.92f)

            if (f.isShockwave) {
                drawCircle(
                    color = flashColor,
                    radius = curR,
                    center = Offset(sx, sy),
                    style = Stroke(width = max(2.2f, 12f * (1f - p)))
                )
            }
            if (p < 0.55f) {
                val coreAlpha = ((1f - p / 0.55f) * 0.88f).coerceIn(0f, 1f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFF9C4).copy(alpha = coreAlpha),
                            flashColor.copy(alpha = coreAlpha * 0.5f),
                            Color.Transparent
                        ),
                        center = Offset(sx, sy),
                        radius = curR * 0.72f
                    ),
                    radius = curR * 0.72f,
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
                android.graphics.Color.rgb(96, 165, 250)
            } else {
                android.graphics.Color.argb(225, 241, 245, 249)
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
            color = Color(0xAA3B82F6),
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
        drawCircle(color = Color(0x663B82F6), radius = 20f, center = start)
        drawCircle(color = Color.White, radius = 8f, center = start)

        val arrowColor = Color(0xFF60A5FA)
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
            return max(physPx, if (b.type == BodyType.STAR) 2.8f else 2.2f).coerceAtMost(28f)
        }
        val minPx = when (b.type) {
            BodyType.STAR -> 13f
            BodyType.BLACK_HOLE -> 12f
            BodyType.PLANET -> 7.0f
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
    Canvas(modifier = modifier.fillMaxSize()) {
        with(renderer) {
            render(
                snapshot = snapshot,
                camera = camera,
                selectedBodyId = selectedBodyId,
                showGrid = showGrid,
                showTrails = showTrails,
                showLabels = showLabels,
                showHabitableZone = showHabitableZone,
                showVelocityVectors = showVelocityVectors,
                launchStartScreenX = launchStartScreenX,
                launchStartScreenY = launchStartScreenY,
                launchEndScreenX = launchEndScreenX,
                launchEndScreenY = launchEndScreenY,
                launchSpeedLabel = launchSpeedLabel,
                predictedPointsWorld = predictedPointsWorld
            )
        }
    }
}
