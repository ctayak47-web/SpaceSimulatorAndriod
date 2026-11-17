package com.example.physics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// Движок гравитационной симуляции N тел (Ньютон + интегратор Верле)
class PhysicsEngine {

    companion object {
        const val MAX_HEAVY_BODIES = 95
        const val MAX_LIGHTWEIGHT_PARTICLES = 200
        private const val SOFTENING_M_SQ = 1.0e8 // (10 км)^2
        private const val TARGET_FRAME_MS = 16L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()

    private val heavyBodies = ArrayList<CelestialBody>(64)
    private val lightParticles = ArrayList<CelestialBody>(220)
    private val flashes = ArrayList<ImpactFlash>(24)

    private var nextBodyId = 1L
    private var simTimeSeconds = 0.0
    private var timeScale = 100_000.0 // 0.0 = пауза, 1..1_000_000x
    private var lastActiveTimeScale = 100_000.0
    private var isReversed = false
    private var selectedBodyId: Long? = null

    private var loopJob: Job? = null

    private val _snapshot = MutableStateFlow(
        SimulationSnapshot(
            bodies = emptyList(),
            flashes = emptyList(),
            simTimeSeconds = 0.0,
            heavyBodyCount = 0,
            particleCount = 0,
            nearestStarX = 0.0,
            nearestStarY = 0.0,
            hasStar = false,
            selectedTelemetry = null
        )
    )
    val snapshot: StateFlow<SimulationSnapshot> = _snapshot.asStateFlow()

    private val _lastCollision = MutableStateFlow<CollisionEvent?>(null)
    val lastCollision: StateFlow<CollisionEvent?> = _lastCollision.asStateFlow()

    private val _timeState = MutableStateFlow(TimeControlState(timeScale, false, isReversed))
    val timeState: StateFlow<TimeControlState> = _timeState.asStateFlow()

    private var accX = DoubleArray(128)
    private var accY = DoubleArray(128)

    data class TimeControlState(
        val timeScale: Double,
        val isPaused: Boolean,
        val isReversed: Boolean
    )

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            var lastWallNs = System.nanoTime()
            while (isActive) {
                val frameStartNs = System.nanoTime()
                val wallDt = ((frameStartNs - lastWallNs) / 1_000_000_000.0).coerceIn(0.001, 0.05)
                lastWallNs = frameStartNs

                val snap = synchronized(lock) {
                    stepSimulationLocked(wallDt.toFloat(), wallDt)
                    buildSnapshotLocked()
                }
                _snapshot.value = snap

                val elapsedMs = (System.nanoTime() - frameStartNs) / 1_000_000L
                val sleepMs = TARGET_FRAME_MS - elapsedMs
                if (sleepMs > 1L) delay(sleepMs) else delay(1L)
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    fun loadBodies(
        newBodies: List<CelestialBody>,
        initialTimeScale: Double = 100_000.0,
        startPaused: Boolean = false,
        initialSelectedId: Long? = null
    ) {
        synchronized(lock) {
            heavyBodies.clear()
            lightParticles.clear()
            flashes.clear()
            var maxId = 0L
            for (body in newBodies) {
                if (body.id > maxId) maxId = body.id
                body.densityKgM3 = PhysicsUnits.computeDensityKgM3(body.massKg, body.radiusM)
                if (body.isLightweight) {
                    if (lightParticles.size < MAX_LIGHTWEIGHT_PARTICLES) lightParticles.add(body)
                } else {
                    if (heavyBodies.size < MAX_HEAVY_BODIES) heavyBodies.add(body)
                }
            }
            nextBodyId = maxId + 1L
            simTimeSeconds = 0.0
            val clamped = initialTimeScale.coerceIn(0.0, PhysicsUnits.MAX_TIME_SCALE)
            if (clamped > 0.0) lastActiveTimeScale = clamped
            timeScale = if (startPaused) 0.0 else clamped
            isReversed = false
            selectedBodyId = initialSelectedId
            computeAccelerationsLocked()
            _lastCollision.value = null
            emitTimeStateLocked()
            _snapshot.value = buildSnapshotLocked()
        }
    }

    fun getCurrentBodiesCopy(): List<CelestialBody> {
        synchronized(lock) {
            val result = ArrayList<CelestialBody>(heavyBodies.size + lightParticles.size)
            for (b in heavyBodies) {
                val trailCopy = b.trail?.let { src ->
                    TrailRingBuffer(src.capacity).apply { copyPointsFrom(src) }
                }
                result.add(b.copy(trail = trailCopy))
            }
            for (p in lightParticles) {
                result.add(p.copy(trail = null))
            }
            return result
        }
    }

    fun getTimeScale(): Double = synchronized(lock) { timeScale }
    fun getSimTimeSeconds(): Double = synchronized(lock) { simTimeSeconds }

    // timeScale поддерживает 0.0 (пауза) до 1_000_000.0x
    fun setTimeScale(newScale: Double) {
        synchronized(lock) {
            val clamped = newScale.coerceIn(0.0, PhysicsUnits.MAX_TIME_SCALE)
            if (clamped > 0.0) {
                lastActiveTimeScale = clamped
            }
            timeScale = clamped
            emitTimeStateLocked()
        }
    }

    fun setPaused(paused: Boolean) {
        synchronized(lock) {
            timeScale = if (paused) 0.0 else lastActiveTimeScale.coerceAtLeast(1.0)
            emitTimeStateLocked()
        }
    }

    fun togglePause() {
        synchronized(lock) {
            timeScale = if (timeScale <= 0.0) {
                lastActiveTimeScale.coerceAtLeast(1.0)
            } else {
                lastActiveTimeScale = timeScale
                0.0
            }
            emitTimeStateLocked()
        }
    }

    fun setReversed(reversed: Boolean) {
        synchronized(lock) {
            isReversed = reversed
            emitTimeStateLocked()
        }
    }

    fun toggleReverse() {
        synchronized(lock) {
            isReversed = !isReversed
            emitTimeStateLocked()
        }
    }

    private fun emitTimeStateLocked() {
        _timeState.value = TimeControlState(
            timeScale = timeScale,
            isPaused = timeScale <= 0.0,
            isReversed = isReversed
        )
    }

    fun selectBody(bodyId: Long?) {
        synchronized(lock) {
            selectedBodyId = bodyId
            _snapshot.value = buildSnapshotLocked()
        }
    }

    fun clearCollisionBanner() {
        _lastCollision.value = null
    }

    fun allocateId(): Long = synchronized(lock) { nextBodyId++ }

    fun addBody(body: CelestialBody) {
        synchronized(lock) {
            val assigned = if (body.id <= 0L) body.copy(id = nextBodyId++) else body
            if (assigned.id >= nextBodyId) nextBodyId = assigned.id + 1L
            assigned.densityKgM3 = PhysicsUnits.computeDensityKgM3(assigned.massKg, assigned.radiusM)
            if (assigned.isLightweight) {
                if (lightParticles.size >= MAX_LIGHTWEIGHT_PARTICLES) lightParticles.removeAt(0)
                lightParticles.add(assigned)
            } else {
                if (heavyBodies.size >= MAX_HEAVY_BODIES) {
                    val victimIdx = heavyBodies.indices
                        .filter { heavyBodies[it].id != selectedBodyId && heavyBodies[it].type != BodyType.STAR }
                        .minByOrNull { heavyBodies[it].massKg }
                    if (victimIdx != null) heavyBodies.removeAt(victimIdx)
                }
                heavyBodies.add(assigned)
            }
            computeAccelerationsLocked()
            _snapshot.value = buildSnapshotLocked()
        }
    }

    fun updateBodyMass(bodyId: Long, newMassKg: Double) {
        synchronized(lock) {
            val body = heavyBodies.firstOrNull { it.id == bodyId } ?: return
            body.massKg = newMassKg.coerceIn(1e12, 1e34)
            body.densityKgM3 = PhysicsUnits.computeDensityKgM3(body.massKg, body.radiusM)
            computeAccelerationsLocked()
            _snapshot.value = buildSnapshotLocked()
        }
    }

    fun updateBodyRadius(bodyId: Long, newRadiusM: Double) {
        synchronized(lock) {
            val body = heavyBodies.firstOrNull { it.id == bodyId } ?: return
            body.radiusM = newRadiusM.coerceIn(500.0, 2.0e11)
            body.densityKgM3 = PhysicsUnits.computeDensityKgM3(body.massKg, body.radiusM)
            _snapshot.value = buildSnapshotLocked()
        }
    }

    fun toggleBodyRings(bodyId: Long) {
        synchronized(lock) {
            val body = heavyBodies.firstOrNull { it.id == bodyId } ?: return
            if (body.type == BodyType.PLANET || body.type == BodyType.BLACK_HOLE) {
                body.hasRings = !body.hasRings
                _snapshot.value = buildSnapshotLocked()
            }
        }
    }

    fun scaleBodyVelocity(bodyId: Long, factor: Double) {
        synchronized(lock) {
            val body = heavyBodies.firstOrNull { it.id == bodyId } ?: return
            body.vx *= factor
            body.vy *= factor
            _snapshot.value = buildSnapshotLocked()
        }
    }

    fun deleteBody(bodyId: Long) {
        synchronized(lock) {
            heavyBodies.removeAll { it.id == bodyId }
            lightParticles.removeAll { it.id == bodyId }
            if (selectedBodyId == bodyId) selectedBodyId = null
            computeAccelerationsLocked()
            _snapshot.value = buildSnapshotLocked()
        }
    }

    // Принудительная детонация тела / Сверхновая звезды (Universe Sandbox)
    fun detonateBody(bodyId: Long) {
        synchronized(lock) {
            val idx = heavyBodies.indexOfFirst { it.id == bodyId }
            if (idx < 0) return
            val target = heavyBodies[idx]
            val cx = target.x
            val cy = target.y
            val cvx = target.vx
            val cvy = target.vy
            val r = target.radiusM
            val m = target.massKg

            when (target.type) {
                BodyType.STAR -> {
                    val isCompact = target.textureStyle == TextureStyle.WHITE_DWARF ||
                        target.textureStyle == TextureStyle.NEUTRON_STAR
                    val ejectSpeed = if (isCompact) 420_000.0 else 240_000.0
                    spawnDebrisLocked(
                        x = cx,
                        y = cy,
                        baseVx = cvx,
                        baseVy = cvy,
                        parentRadiusM = max(r * 1.4, 3.0e8),
                        ejectSpeed = ejectSpeed,
                        count = 28,
                        hotColor = 0xFF34D399.toInt()
                    )
                    spawnFlashLocked(cx, cy, max(r * 12.0, PhysicsUnits.AU_METERS * 1.4), 0xFF34D399.toInt(), true)
                    spawnFlashLocked(cx, cy, max(r * 7.5, PhysicsUnits.AU_METERS * 0.7), 0xFFFFF59D.toInt(), false)

                    // Нагрев и ударная волна по ближайшим телам
                    for (other in heavyBodies) {
                        if (other.id == target.id || other.type == BodyType.BLACK_HOLE) continue
                        val dx = other.x - cx
                        val dy = other.y - cy
                        val dist = sqrt(max(1.0e12, dx * dx + dy * dy))
                        val heatBoost = (6500.0 * (PhysicsUnits.AU_METERS / dist).pow(1.4)).coerceIn(40.0, 8200.0)
                        other.surfaceTempK = (other.surfaceTempK + heatBoost).coerceAtMost(12000.0)
                        val blastDv = (85_000.0 * (PhysicsUnits.AU_METERS / dist)).coerceIn(500.0, 95_000.0)
                        other.vx += (dx / dist) * blastDv
                        other.vy += (dy / dist) * blastDv
                        if (dist < PhysicsUnits.AU_METERS * 2.5) {
                            other.atmosphereFraction *= 0.45
                        }
                    }

                    val energyMt = 1.5e29 // Условная энергия вспышки сверхновой
                    if (isCompact) {
                        heavyBodies.removeAt(idx)
                        if (selectedBodyId == bodyId) selectedBodyId = null
                    } else {
                        target.name = "${target.name} (Пульсар)"
                        target.textureStyle = TextureStyle.NEUTRON_STAR
                        target.massKg = (m * 0.55).coerceAtLeast(PhysicsUnits.SUN_MASS_KG * 0.45)
                        target.radiusM = 2.4e7
                        target.luminosityW = PhysicsUnits.SUN_LUMINOSITY_W * 0.15
                        target.surfaceTempK = 28_000.0
                        target.primaryColorArgb = 0xFF6EE7B7.toInt()
                        target.secondaryColorArgb = 0xFF2DD4BF.toInt()
                        target.densityKgM3 = PhysicsUnits.computeDensityKgM3(target.massKg, target.radiusM)
                    }

                    _lastCollision.value = CollisionEvent(
                        title = "Сверхновая: ${target.name}",
                        isMerge = false,
                        energyMt = energyMt,
                        massLossPercent = if (isCompact) 100.0 else 45.0,
                        newMassKg = if (isCompact) 0.0 else target.massKg,
                        newDensityKgM3 = if (isCompact) 0.0 else target.densityKgM3,
                        newTempK = 28_000.0,
                        tempDeltaK = 22_000.0,
                        atmosphereLossPercent = 55.0,
                        timestampMs = System.currentTimeMillis()
                    )
                }

                BodyType.BLACK_HOLE -> {
                    spawnDebrisLocked(cx, cy, cvx, cvy, r * 2.0, 320_000.0, count = 20, hotColor = 0xFF2DD4BF.toInt())
                    spawnFlashLocked(cx, cy, r * 14.0, 0xFF10B981.toInt(), true)
                    spawnFlashLocked(cx, cy, r * 8.0, 0xFFE0F2FE.toInt(), false)
                    _lastCollision.value = CollisionEvent(
                        title = "Релятивистский выброс: ${target.name}",
                        isMerge = false,
                        energyMt = 5.0e26,
                        massLossPercent = 0.0,
                        newMassKg = m,
                        newDensityKgM3 = target.densityKgM3,
                        newTempK = 0.0,
                        tempDeltaK = 0.0,
                        atmosphereLossPercent = 0.0,
                        timestampMs = System.currentTimeMillis()
                    )
                }

                else -> {
                    val esc = PhysicsUnits.computeEscapeVelocity(m, r)
                    val ejectSpeed = max(esc * 1.45, 18_000.0)
                    spawnDebrisLocked(
                        x = cx,
                        y = cy,
                        baseVx = cvx,
                        baseVy = cvy,
                        parentRadiusM = max(r, 2.0e6),
                        ejectSpeed = ejectSpeed,
                        count = 22,
                        hotColor = 0xFFFF7043.toInt()
                    )
                    spawnFlashLocked(cx, cy, max(r * 9.0, 4.5e7), 0xFFFF6D00.toInt(), true)
                    spawnFlashLocked(cx, cy, max(r * 5.0, 2.5e7), 0xFFFFF59D.toInt(), false)

                    heavyBodies.removeAt(idx)
                    if (selectedBodyId == bodyId) selectedBodyId = null
                    _lastCollision.value = CollisionEvent(
                        title = "Детонация: ${target.name}",
                        isMerge = false,
                        energyMt = (0.6 * PhysicsUnits.G_SI * m * m / max(1.0, r)) / PhysicsUnits.MEGATON_JOULES,
                        massLossPercent = 100.0,
                        newMassKg = 0.0,
                        newDensityKgM3 = 0.0,
                        newTempK = 3600.0,
                        tempDeltaK = 3200.0,
                        atmosphereLossPercent = if (target.atmosphereFraction > 0.0) 100.0 else 0.0,
                        timestampMs = System.currentTimeMillis()
                    )
                }
            }
            computeAccelerationsLocked()
            _snapshot.value = buildSnapshotLocked()
        }
    }

    // Прогноз орбиты для запуска нового тела
    fun predictTrajectory(
        startX: Double,
        startY: Double,
        startVx: Double,
        startVy: Double,
        steps: Int = 60,
        stepDtSeconds: Double = 45_000.0
    ): FloatArray {
        val result = FloatArray(steps * 2)
        synchronized(lock) {
            var px = startX
            var py = startY
            var vx = startVx
            var vy = startVy

            fun accelAt(x: Double, y: Double): Pair<Double, Double> {
                var ax = 0.0
                var ay = 0.0
                for (i in 0 until heavyBodies.size) {
                    val b = heavyBodies[i]
                    val dx = b.x - x
                    val dy = b.y - y
                    val distSq = dx * dx + dy * dy + SOFTENING_M_SQ
                    val dist = sqrt(distSq)
                    val invDist3 = 1.0 / (distSq * dist)
                    val f = PhysicsUnits.G_SI * b.massKg * invDist3
                    ax += dx * f
                    ay += dy * f
                }
                return ax to ay
            }

            var (ax, ay) = accelAt(px, py)
            for (i in 0 until steps) {
                px += vx * stepDtSeconds + 0.5 * ax * stepDtSeconds * stepDtSeconds
                py += vy * stepDtSeconds + 0.5 * ay * stepDtSeconds * stepDtSeconds
                val (nax, nay) = accelAt(px, py)
                vx += 0.5 * (ax + nax) * stepDtSeconds
                vy += 0.5 * (ay + nay) * stepDtSeconds
                ax = nax
                ay = nay
                result[i * 2] = px.toFloat()
                result[i * 2 + 1] = py.toFloat()
            }
        }
        return result
    }

    private fun stepSimulationLocked(wallDtSec: Float, wallDtDouble: Double) {
        var fIdx = flashes.size - 1
        while (fIdx >= 0) {
            val flash = flashes[fIdx]
            flash.ageWallSec += wallDtSec
            if (flash.ageWallSec >= flash.durationWallSec) flashes.removeAt(fIdx)
            fIdx--
        }

        if (timeScale <= 0.0) return

        val signedScale = if (isReversed) -timeScale else timeScale
        val totalSimDt = wallDtDouble * signedScale
        simTimeSeconds += totalSimDt

        val absSimDt = abs(totalSimDt)
        val subSteps = when {
            absSimDt < 500.0 -> 1
            absSimDt < 10_000.0 -> 4
            absSimDt < 60_000.0 -> 10
            else -> 22
        }
        val subDt = totalSimDt / subSteps

        for (step in 0 until subSteps) {
            integrateVerletSubstepLocked(subDt)
            if (!isReversed) {
                resolveCollisionsLocked()
            }
        }

        for (i in 0 until heavyBodies.size) {
            val b = heavyBodies[i]
            b.trail?.record(b.x, b.y, minStepInterval = 2)
        }

        updateThermodynamicsLocked(absSimDt)
    }

    private fun integrateVerletSubstepLocked(dt: Double) {
        val halfDtSq = 0.5 * dt * dt
        val halfDt = 0.5 * dt

        for (i in 0 until heavyBodies.size) {
            val b = heavyBodies[i]
            b.x += b.vx * dt + b.ax * halfDtSq
            b.y += b.vy * dt + b.ay * halfDtSq
        }
        for (i in 0 until lightParticles.size) {
            val p = lightParticles[i]
            p.x += p.vx * dt + p.ax * halfDtSq
            p.y += p.vy * dt + p.ay * halfDtSq
        }

        val hCount = heavyBodies.size
        if (accX.size < hCount) {
            accX = DoubleArray(hCount * 2)
            accY = DoubleArray(hCount * 2)
        }
        for (i in 0 until hCount) {
            accX[i] = 0.0
            accY[i] = 0.0
        }

        for (i in 0 until hCount) {
            val b1 = heavyBodies[i]
            for (j in i + 1 until hCount) {
                val b2 = heavyBodies[j]
                val dx = b2.x - b1.x
                val dy = b2.y - b1.y
                val distSq = dx * dx + dy * dy + SOFTENING_M_SQ
                val dist = sqrt(distSq)
                val factor = PhysicsUnits.G_SI / (distSq * dist)
                val fx = dx * factor
                val fy = dy * factor

                accX[i] += fx * b2.massKg
                accY[i] += fy * b2.massKg
                accX[j] -= fx * b1.massKg
                accY[j] -= fy * b1.massKg
            }
        }

        for (i in 0 until hCount) {
            val b = heavyBodies[i]
            val nax = accX[i]
            val nay = accY[i]
            b.vx += (b.ax + nax) * halfDt
            b.vy += (b.ay + nay) * halfDt
            b.ax = nax
            b.ay = nay
        }

        for (i in 0 until lightParticles.size) {
            val p = lightParticles[i]
            var nax = 0.0
            var nay = 0.0
            for (j in 0 until hCount) {
                val b = heavyBodies[j]
                val dx = b.x - p.x
                val dy = b.y - p.y
                val distSq = dx * dx + dy * dy + SOFTENING_M_SQ
                val dist = sqrt(distSq)
                val factor = PhysicsUnits.G_SI * b.massKg / (distSq * dist)
                nax += dx * factor
                nay += dy * factor
            }
            p.vx += (p.ax + nax) * halfDt
            p.vy += (p.ay + nay) * halfDt
            p.ax = nax
            p.ay = nay
        }
    }

    private fun computeAccelerationsLocked() {
        val hCount = heavyBodies.size
        for (i in 0 until hCount) {
            heavyBodies[i].ax = 0.0
            heavyBodies[i].ay = 0.0
        }
        for (i in 0 until hCount) {
            val b1 = heavyBodies[i]
            for (j in i + 1 until hCount) {
                val b2 = heavyBodies[j]
                val dx = b2.x - b1.x
                val dy = b2.y - b1.y
                val distSq = dx * dx + dy * dy + SOFTENING_M_SQ
                val dist = sqrt(distSq)
                val factor = PhysicsUnits.G_SI / (distSq * dist)
                b1.ax += dx * factor * b2.massKg
                b1.ay += dy * factor * b2.massKg
                b2.ax -= dx * factor * b1.massKg
                b2.ay -= dy * factor * b1.massKg
            }
        }
        for (i in 0 until lightParticles.size) {
            val p = lightParticles[i]
            var ax = 0.0
            var ay = 0.0
            for (j in 0 until hCount) {
                val b = heavyBodies[j]
                val dx = b.x - p.x
                val dy = b.y - p.y
                val distSq = dx * dx + dy * dy + SOFTENING_M_SQ
                val dist = sqrt(distSq)
                val factor = PhysicsUnits.G_SI * b.massKg / (distSq * dist)
                ax += dx * factor
                ay += dy * factor
            }
            p.ax = ax
            p.ay = ay
        }
    }

    // Алгоритм обработки столкновений: проверка перекрытия радиусов, слияние или разрушение на осколки
    private fun resolveCollisionsLocked() {
        var i = 0
        while (i < heavyBodies.size) {
            val b1 = heavyBodies[i]
            var j = i + 1
            var mergedOrRemovedI = false
            while (j < heavyBodies.size) {
                val b2 = heavyBodies[j]
                val dx = b2.x - b1.x
                val dy = b2.y - b1.y
                val sumRadius = b1.radiusM + b2.radiusM
                val distSq = dx * dx + dy * dy
                // Проверяем перекрытие радиусов тел: d <= R1 + R2
                if (distSq <= sumRadius * sumRadius) {
                    val primaryWins = b1.massKg >= b2.massKg
                    val survivor = if (primaryWins) b1 else b2
                    val victim = if (primaryWins) b2 else b1
                    handleHeavyCollisionLocked(survivor, victim)
                    if (primaryWins) {
                        heavyBodies.removeAt(j)
                        if (selectedBodyId == victim.id) selectedBodyId = survivor.id
                        continue
                    } else {
                        heavyBodies.removeAt(i)
                        if (selectedBodyId == victim.id) selectedBodyId = survivor.id
                        mergedOrRemovedI = true
                        break
                    }
                } else {
                    // Проверка предела Роша (приливное разрушение в гравитационном поле гиганта/звезды/ЧД)
                    val primaryWins = b1.massKg >= b2.massKg
                    val primary = if (primaryWins) b1 else b2
                    val victim = if (primaryWins) b2 else b1
                    if (shouldTidallyDisruptLocked(primary, victim, distSq)) {
                        handleTidalDisruptionLocked(primary, victim)
                        if (primaryWins) {
                            heavyBodies.removeAt(j)
                            if (selectedBodyId == victim.id) selectedBodyId = primary.id
                            continue
                        } else {
                            heavyBodies.removeAt(i)
                            if (selectedBodyId == victim.id) selectedBodyId = primary.id
                            mergedOrRemovedI = true
                            break
                        }
                    }
                }
                j++
            }
            if (!mergedOrRemovedI) i++
        }

        // Столкновения осколков и астероидов с крупными телами
        var pIdx = lightParticles.size - 1
        while (pIdx >= 0) {
            val p = lightParticles[pIdx]
            var hit = false
            for (hIdx in 0 until heavyBodies.size) {
                val b = heavyBodies[hIdx]
                val dx = b.x - p.x
                val dy = b.y - p.y
                val minDist = b.radiusM + p.radiusM
                if (dx * dx + dy * dy <= minDist * minDist) {
                    val rvx = p.vx - b.vx
                    val rvy = p.vy - b.vy
                    val energyJ = 0.5 * p.massKg * (rvx * rvx + rvy * rvy)
                    val totalM = b.massKg + p.massKg
                    b.vx = (b.vx * b.massKg + p.vx * p.massKg) / totalM
                    b.vy = (b.vy * b.massKg + p.vy * p.massKg) / totalM
                    b.massKg = totalM
                    b.densityKgM3 = PhysicsUnits.computeDensityKgM3(b.massKg, b.radiusM)
                    if (b.type != BodyType.BLACK_HOLE) {
                        val deltaT = (energyJ * 0.3 / max(1.0, b.massKg * 900.0)).coerceAtMost(900.0)
                        b.surfaceTempK = (b.surfaceTempK + deltaT).coerceAtMost(9000.0)
                    }
                    spawnFlashLocked(p.x, p.y, max(b.radiusM * 0.5, p.radiusM * 6.0), 0xFFFFB74D.toInt(), false)
                    hit = true
                    break
                }
            }
            if (hit) lightParticles.removeAt(pIdx)
            pIdx--
        }
    }

    private fun handleHeavyCollisionLocked(survivor: CelestialBody, victim: CelestialBody) {
        val m1 = survivor.massKg
        val m2 = victim.massKg
        val totalMass = m1 + m2
        val reducedMass = (m1 * m2) / max(1.0, totalMass)

        val rvx = victim.vx - survivor.vx
        val rvy = victim.vy - survivor.vy
        val relSpeedSq = rvx * rvx + rvy * rvy
        val relSpeed = sqrt(relSpeedSq)

        val impactEnergyJ = 0.5 * reducedMass * relSpeedSq
        val energyMt = impactEnergyJ / PhysicsUnits.MEGATON_JOULES
        val sumRadius = max(1000.0, survivor.radiusM + victim.radiusM)
        val escapeSpeed = sqrt(2.0 * PhysicsUnits.G_SI * totalMass / sumRadius)

        val impactX = (survivor.x * m1 + victim.x * m2) / totalMass
        val impactY = (survivor.y * m1 + victim.y * m2) / totalMass
        val newVx = (survivor.vx * m1 + victim.vx * m2) / totalMass
        val newVy = (survivor.vy * m1 + victim.vy * m2) / totalMass

        // Особый случай: Чёрная дыра
        if (survivor.type == BodyType.BLACK_HOLE) {
            survivor.vx = newVx
            survivor.vy = newVy
            survivor.massKg = totalMass
            survivor.radiusM = max(survivor.radiusM, 2.0 * PhysicsUnits.G_SI * totalMass / 8.98755e16 * 5000.0)
            survivor.densityKgM3 = PhysicsUnits.computeDensityKgM3(survivor.massKg, survivor.radiusM)
            spawnFlashLocked(impactX, impactY, max(survivor.radiusM, victim.radiusM) * 5.0, 0xFF60A5FA.toInt(), true)
            _lastCollision.value = CollisionEvent(
                title = "${survivor.name} поглотила ${victim.name}",
                isMerge = true,
                energyMt = energyMt,
                massLossPercent = 0.0,
                newMassKg = survivor.massKg,
                newDensityKgM3 = survivor.densityKgM3,
                newTempK = survivor.surfaceTempK,
                tempDeltaK = 0.0,
                atmosphereLossPercent = if (victim.atmosphereFraction > 0.0) 100.0 else 0.0,
                timestampMs = System.currentTimeMillis()
            )
            return
        }

        // Особый случай: Звезда поглощает меньшее тело
        if (survivor.type == BodyType.STAR && victim.type != BodyType.STAR) {
            survivor.vx = newVx
            survivor.vy = newVy
            survivor.massKg = totalMass
            survivor.densityKgM3 = PhysicsUnits.computeDensityKgM3(survivor.massKg, survivor.radiusM)
            val oldTemp = survivor.surfaceTempK
            val deltaT = min(2200.0, impactEnergyJ * 0.2 / max(1.0, survivor.massKg * 1200.0))
            survivor.surfaceTempK += deltaT
            spawnFlashLocked(impactX, impactY, max(survivor.radiusM * 1.5, victim.radiusM * 6.5), 0xFFFFD54F.toInt(), true)
            _lastCollision.value = CollisionEvent(
                title = "${victim.name} упала на ${survivor.name}",
                isMerge = true,
                energyMt = energyMt,
                massLossPercent = 0.0,
                newMassKg = survivor.massKg,
                newDensityKgM3 = survivor.densityKgM3,
                newTempK = survivor.surfaceTempK,
                tempDeltaK = survivor.surfaceTempK - oldTemp,
                atmosphereLossPercent = if (victim.atmosphereFraction > 0.0) 100.0 else 0.0,
                timestampMs = System.currentTimeMillis()
            )
            return
        }

        // Исходные плотности сталкивающихся тел
        val rho1 = PhysicsUnits.computeDensityKgM3(m1, survivor.radiusM)
        val rho2 = PhysicsUnits.computeDensityKgM3(m2, victim.radiusM)

        // Порог скорости: при низкой скорости — слияние, при высокой — разрушение на осколки
        val isMerge = relSpeed < 1.15 * escapeSpeed
        val oldTemp = survivor.surfaceTempK
        val oldAtm = survivor.atmosphereFraction

        survivor.x = impactX
        survivor.y = impactY
        survivor.vx = newVx
        survivor.vy = newVy

        if (isMerge) {
            // Слияние: сохраняем суммарный объём по плотностям с лёгким гравитационным уплотнением (~2%)
            val ejectaFraction = (0.04 * (relSpeed / max(1.0, 1.15 * escapeSpeed)).pow(2)).coerceIn(0.0, 0.04)
            val retainedMass = totalMass * (1.0 - ejectaFraction)
            val combinedVolume = (m1 / rho1 + m2 / rho2) * (1.0 - ejectaFraction) * 0.985
            val newRadius = ((3.0 * combinedVolume) / (4.0 * PI)).pow(1.0 / 3.0).coerceAtLeast(1000.0)

            survivor.massKg = retainedMass
            survivor.radiusM = newRadius
            survivor.densityKgM3 = PhysicsUnits.computeDensityKgM3(retainedMass, newRadius)

            val heatGain = (impactEnergyJ * 0.35 / max(1.0, retainedMass * 850.0)).coerceIn(50.0, 5200.0)
            survivor.surfaceTempK = (survivor.surfaceTempK + heatGain).coerceAtMost(8500.0)

            val atmLossRatio = (relSpeed / max(1.0, 1.8 * escapeSpeed)).pow(2).coerceIn(0.05, 0.65)
            val combinedAtm = max(survivor.atmosphereFraction, victim.atmosphereFraction * 0.45)
            survivor.atmosphereFraction = (combinedAtm * (1.0 - atmLossRatio)).coerceIn(0.0, 1.0)
            survivor.greenhouseOffsetK *= (1.0 - atmLossRatio)

            spawnDebrisLocked(impactX, impactY, newVx, newVy, newRadius, relSpeed, count = 6, hotColor = 0xFFFF8A3D.toInt())
            spawnFlashLocked(impactX, impactY, max(newRadius, victim.radiusM) * 4.0, 0xFFFFAA44.toInt(), true)

            val lostAtmPct = if (oldAtm > 0.01) ((oldAtm - survivor.atmosphereFraction) / oldAtm * 100.0).coerceIn(0.0, 100.0) else 0.0
            _lastCollision.value = CollisionEvent(
                title = "Слияние: ${survivor.name} + ${victim.name}",
                isMerge = true,
                energyMt = energyMt,
                massLossPercent = ejectaFraction * 100.0,
                newMassKg = survivor.massKg,
                newDensityKgM3 = survivor.densityKgM3,
                newTempK = survivor.surfaceTempK,
                tempDeltaK = survivor.surfaceTempK - oldTemp,
                atmosphereLossPercent = lostAtmPct,
                timestampMs = System.currentTimeMillis()
            )
        } else {
            // Высокоскоростное столкновение -> срыв оболочки и разрушение на осколки со вспышкой
            val bindingEnergyJ = 0.6 * PhysicsUnits.G_SI * totalMass * totalMass / sumRadius
            val ratioQ = impactEnergyJ / max(1.0, bindingEnergyJ)
            val massLossFraction = (0.10 + 0.50 * (ratioQ / (1.0 + ratioQ))).coerceIn(0.08, 0.72)
            val retainedMass = totalMass * (1.0 - massLossFraction)

            // При срыве лёгкой мантии средняя плотность ядра немного возрастает (+4%..+12%)
            val compactionBoost = 1.0 + 0.12 * massLossFraction
            val newDensity = ((rho1 * m1 + rho2 * m2) / totalMass) * compactionBoost
            val newRadius = ((3.0 * retainedMass) / (4.0 * PI * newDensity)).pow(1.0 / 3.0).coerceAtLeast(1000.0)

            survivor.massKg = retainedMass
            survivor.radiusM = newRadius
            survivor.densityKgM3 = PhysicsUnits.computeDensityKgM3(retainedMass, newRadius)

            val heatGain = (impactEnergyJ * 0.44 / max(1.0, retainedMass * 850.0)).coerceIn(750.0, 7200.0)
            survivor.surfaceTempK = (survivor.surfaceTempK + heatGain).coerceAtMost(9800.0)

            val atmLossRatio = (0.40 + 0.60 * (ratioQ / (0.6 + ratioQ))).coerceIn(0.30, 1.0)
            survivor.atmosphereFraction = (survivor.atmosphereFraction * (1.0 - atmLossRatio)).coerceIn(0.0, 1.0)
            survivor.greenhouseOffsetK *= (1.0 - atmLossRatio)

            spawnDebrisLocked(
                impactX,
                impactY,
                newVx,
                newVy,
                newRadius,
                max(relSpeed * 0.48, escapeSpeed * 1.3),
                count = 16,
                hotColor = 0xFFFF5722.toInt()
            )
            // Двойная вспышка: ядро взрыва + ударная волна
            spawnFlashLocked(impactX, impactY, max(newRadius, victim.radiusM) * 6.5, 0xFFFF6D00.toInt(), true)
            spawnFlashLocked(impactX, impactY, max(newRadius, victim.radiusM) * 3.8, 0xFFFFF59D.toInt(), false)

            val lostAtmPct = if (oldAtm > 0.01) ((oldAtm - survivor.atmosphereFraction) / oldAtm * 100.0).coerceIn(0.0, 100.0) else 0.0
            _lastCollision.value = CollisionEvent(
                title = "Разрушение: ${survivor.name} × ${victim.name}",
                isMerge = false,
                energyMt = energyMt,
                massLossPercent = massLossFraction * 100.0,
                newMassKg = survivor.massKg,
                newDensityKgM3 = survivor.densityKgM3,
                newTempK = survivor.surfaceTempK,
                tempDeltaK = survivor.surfaceTempK - oldTemp,
                atmosphereLossPercent = lostAtmPct,
                timestampMs = System.currentTimeMillis()
            )
        }
    }

    // Проверка предела Роша: приливное разрывание планет и спутников при опасном сближении
    private fun shouldTidallyDisruptLocked(
        primary: CelestialBody,
        victim: CelestialBody,
        distSq: Double
    ): Boolean {
        if (victim.type == BodyType.STAR || victim.type == BodyType.BLACK_HOLE) return false
        if (victim.massKg < 1e20 || primary.massKg < victim.massKg * 50.0) return false
        val rocheM = 1.45 * victim.radiusM * (2.0 * primary.massKg / victim.massKg).pow(1.0 / 3.0)
        return distSq < rocheM * rocheM
    }

    private fun handleTidalDisruptionLocked(primary: CelestialBody, victim: CelestialBody) {
        val rvx = victim.vx - primary.vx
        val rvy = victim.vy - primary.vy
        val relSpeed = sqrt(rvx * rvx + rvy * rvy)
        val esc = PhysicsUnits.computeEscapeVelocity(victim.massKg, victim.radiusM)

        spawnDebrisLocked(
            x = victim.x,
            y = victim.y,
            baseVx = victim.vx,
            baseVy = victim.vy,
            parentRadiusM = max(victim.radiusM * 1.4, 3.0e6),
            ejectSpeed = max(esc * 1.2, relSpeed * 0.25),
            count = 20,
            hotColor = 0xFFFF8A3D.toInt()
        )
        spawnFlashLocked(victim.x, victim.y, max(victim.radiusM * 6.5, primary.radiusM * 0.9), 0xFF34D399.toInt(), true)

        val energyMt = (0.5 * victim.massKg * relSpeed * relSpeed) / PhysicsUnits.MEGATON_JOULES
        _lastCollision.value = CollisionEvent(
            title = "Предел Роша: ${primary.name} разорвала ${victim.name}",
            isMerge = false,
            energyMt = energyMt,
            massLossPercent = 100.0,
            newMassKg = primary.massKg,
            newDensityKgM3 = primary.densityKgM3,
            newTempK = 2400.0,
            tempDeltaK = 2000.0,
            atmosphereLossPercent = if (victim.atmosphereFraction > 0.0) 100.0 else 0.0,
            timestampMs = System.currentTimeMillis()
        )
    }

    private fun spawnFlashLocked(x: Double, y: Double, radiusM: Double, colorArgb: Int, shockwave: Boolean) {
        if (flashes.size >= 24) flashes.removeAt(0)
        flashes.add(
            ImpactFlash(
                x = x,
                y = y,
                maxVisualRadiusM = radiusM,
                durationWallSec = if (shockwave) 1.55f else 0.75f,
                colorArgb = colorArgb,
                isShockwave = shockwave
            )
        )
    }

    private fun spawnDebrisLocked(
        x: Double,
        y: Double,
        baseVx: Double,
        baseVy: Double,
        parentRadiusM: Double,
        ejectSpeed: Double,
        count: Int,
        hotColor: Int
    ) {
        for (k in 0 until count) {
            if (lightParticles.size >= MAX_LIGHTWEIGHT_PARTICLES) {
                lightParticles.removeAt(0)
            }
            val angle = (2.0 * PI * k / count) + Random.nextDouble(-0.28, 0.28)
            val speed = ejectSpeed * Random.nextDouble(0.6, 1.55)
            val dist = parentRadiusM * Random.nextDouble(1.15, 1.65)
            val pRadius = (parentRadiusM * Random.nextDouble(0.07, 0.16)).coerceAtLeast(150_000.0)
            lightParticles.add(
                CelestialBody(
                    id = nextBodyId++,
                    name = "Осколок",
                    type = BodyType.ASTEROID,
                    textureStyle = TextureStyle.LAVA_WORLD,
                    massKg = 1e18,
                    radiusM = pRadius,
                    x = x + cos(angle) * dist,
                    y = y + sin(angle) * dist,
                    vx = baseVx + cos(angle) * speed,
                    vy = baseVy + sin(angle) * speed,
                    baseTempK = 2800.0,
                    surfaceTempK = 3200.0,
                    primaryColorArgb = hotColor,
                    secondaryColorArgb = 0xFFFFF176.toInt(),
                    isLightweight = true,
                    trail = null
                )
            )
        }
    }

    private fun updateThermodynamicsLocked(absSimDt: Double) {
        if (absSimDt <= 0.0) return
        val coolingFactor = (absSimDt / (PhysicsUnits.SECONDS_PER_DAY * 35.0)).coerceIn(0.0, 0.12)
        for (i in 0 until heavyBodies.size) {
            val body = heavyBodies[i]
            if (body.type == BodyType.STAR || body.type == BodyType.BLACK_HOLE) continue

            var totalFlux = 0.0
            for (j in 0 until heavyBodies.size) {
                val star = heavyBodies[j]
                if (star.luminosityW <= 0.0) continue
                val dx = star.x - body.x
                val dy = star.y - body.y
                val distSq = max(star.radiusM * star.radiusM, dx * dx + dy * dy)
                totalFlux += star.luminosityW / (4.0 * PI * distSq)
            }

            val eqTemp = if (totalFlux > 0.0) {
                val raw = (totalFlux * (1.0 - body.albedo) / (4.0 * PhysicsUnits.STEFAN_BOLTZMANN)).pow(0.25)
                max(2.7, raw + body.greenhouseOffsetK * body.atmosphereFraction)
            } else {
                2.7
            }

            body.surfaceTempK += (eqTemp - body.surfaceTempK) * coolingFactor
        }

        for (i in 0 until lightParticles.size) {
            val p = lightParticles[i]
            if (p.surfaceTempK > 300.0) {
                p.surfaceTempK -= (p.surfaceTempK - 220.0) * (coolingFactor * 1.8)
            }
        }
    }

    private fun buildSnapshotLocked(): SimulationSnapshot {
        var starX = 0.0
        var starY = 0.0
        var hasStar = false
        var bestStarMass = -1.0
        for (i in 0 until heavyBodies.size) {
            val b = heavyBodies[i]
            if (b.type == BodyType.STAR && b.massKg > bestStarMass) {
                bestStarMass = b.massKg
                starX = b.x
                starY = b.y
                hasStar = true
            }
        }

        val renderList = ArrayList<RenderBody>(heavyBodies.size + lightParticles.size)
        for (i in 0 until heavyBodies.size) renderList.add(toRenderBody(heavyBodies[i]))
        for (i in 0 until lightParticles.size) renderList.add(toRenderBody(lightParticles[i]))

        val selectedTelemetry = selectedBodyId?.let { selId ->
            heavyBodies.firstOrNull { it.id == selId }?.let { b ->
                computeTelemetryLocked(b, starX, starY, hasStar)
            }
        }

        val flashCopies = ArrayList<ImpactFlash>(flashes.size)
        for (f in flashes) flashCopies.add(f.copy())

        return SimulationSnapshot(
            bodies = renderList,
            flashes = flashCopies,
            simTimeSeconds = simTimeSeconds,
            heavyBodyCount = heavyBodies.size,
            particleCount = lightParticles.size,
            nearestStarX = starX,
            nearestStarY = starY,
            hasStar = hasStar,
            selectedTelemetry = selectedTelemetry
        )
    }

    private fun toRenderBody(b: CelestialBody): RenderBody {
        val trail = b.trail
        return if (trail != null && trail.count > 0) {
            RenderBody(
                id = b.id,
                name = b.name,
                type = b.type,
                textureStyle = b.textureStyle,
                massKg = b.massKg,
                radiusM = b.radiusM,
                x = b.x,
                y = b.y,
                vx = b.vx,
                vy = b.vy,
                surfaceTempK = b.surfaceTempK,
                luminosityW = b.luminosityW,
                atmosphereFraction = b.atmosphereFraction,
                primaryColorArgb = b.primaryColorArgb,
                secondaryColorArgb = b.secondaryColorArgb,
                hasRings = b.hasRings,
                ringInnerRatio = b.ringInnerRatio,
                ringOuterRatio = b.ringOuterRatio,
                isLightweight = b.isLightweight,
                trailCount = trail.count,
                trailXs = trail.xs.copyOf(),
                trailYs = trail.ys.copyOf(),
                trailHead = trail.head,
                trailCapacity = trail.capacity
            )
        } else {
            RenderBody(
                id = b.id,
                name = b.name,
                type = b.type,
                textureStyle = b.textureStyle,
                massKg = b.massKg,
                radiusM = b.radiusM,
                x = b.x,
                y = b.y,
                vx = b.vx,
                vy = b.vy,
                surfaceTempK = b.surfaceTempK,
                luminosityW = b.luminosityW,
                atmosphereFraction = b.atmosphereFraction,
                primaryColorArgb = b.primaryColorArgb,
                secondaryColorArgb = b.secondaryColorArgb,
                hasRings = b.hasRings,
                ringInnerRatio = b.ringInnerRatio,
                ringOuterRatio = b.ringOuterRatio,
                isLightweight = b.isLightweight,
                trailCount = 0,
                trailXs = DOUBLE_EMPTY,
                trailYs = DOUBLE_EMPTY,
                trailHead = 0,
                trailCapacity = 0
            )
        }
    }

    private fun computeTelemetryLocked(
        body: CelestialBody,
        starX: Double,
        starY: Double,
        hasStar: Boolean
    ): SelectedBodyTelemetry {
        val speed = sqrt(body.vx * body.vx + body.vy * body.vy)
        val distToSun = if (hasStar && body.type != BodyType.STAR) {
            val dx = body.x - starX
            val dy = body.y - starY
            sqrt(dx * dx + dy * dy)
        } else null

        var dominant: CelestialBody? = null
        var bestPull = 0.0
        for (other in heavyBodies) {
            if (other.id == body.id || other.massKg <= body.massKg * 0.5) continue
            val dx = other.x - body.x
            val dy = other.y - body.y
            val distSq = max(1.0, dx * dx + dy * dy)
            val pull = other.massKg / distSq
            if (pull > bestPull) {
                bestPull = pull
                dominant = other
            }
        }

        val periodSec = dominant?.let { parent ->
            val dx = body.x - parent.x
            val dy = body.y - parent.y
            val r = sqrt(dx * dx + dy * dy)
            val rvx = body.vx - parent.vx
            val rvy = body.vy - parent.vy
            val vSq = rvx * rvx + rvy * rvy
            val mu = PhysicsUnits.G_SI * (parent.massKg + body.massKg)
            val invA = (2.0 / r) - (vSq / mu)
            if (invA > 0.0) {
                val a = 1.0 / invA
                2.0 * PI * sqrt((a * a * a) / mu)
            } else null
        }

        val density = PhysicsUnits.computeDensityKgM3(body.massKg, body.radiusM)
        return SelectedBodyTelemetry(
            id = body.id,
            name = body.name,
            type = body.type,
            massKg = body.massKg,
            radiusM = body.radiusM,
            densityKgM3 = density,
            surfaceGravityMs2 = PhysicsUnits.computeSurfaceGravity(body.massKg, body.radiusM),
            escapeVelocityMs = PhysicsUnits.computeEscapeVelocity(body.massKg, body.radiusM),
            speedMs = speed,
            distanceToSunM = distToSun,
            surfaceTempK = body.surfaceTempK,
            orbitalPeriodSec = periodSec,
            atmosphereFraction = body.atmosphereFraction,
            hasRings = body.hasRings,
            primaryColorArgb = body.primaryColorArgb
        )
    }

    private val DOUBLE_EMPTY = DoubleArray(0)
}
