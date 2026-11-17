package com.example.physics

// Тип небесного тела
enum class BodyType(val titleRu: String) {
    STAR("Звезда"),
    PLANET("Планета"),
    MOON("Спутник"),
    ASTEROID("Астероид"),
    COMET("Комета"),
    BLACK_HOLE("Чёрная дыра")
}

// Стиль программно генерируемой текстуры
enum class TextureStyle {
    SUN,
    RED_GIANT,
    BLUE_STAR,
    WHITE_DWARF,
    NEUTRON_STAR,
    MERCURY,
    VENUS,
    EARTH,
    MOON,
    MARS,
    JUPITER,
    SATURN,
    URANUS,
    NEPTUNE,
    PLUTO,
    LAVA_WORLD,
    ASTEROID,
    COMET,
    BLACK_HOLE
}

// Преаллоцированный кольцевой буфер для орбитального следа (без аллокаций в игровом цикле)
class TrailRingBuffer(val capacity: Int = 180) {
    val xs = DoubleArray(capacity)
    val ys = DoubleArray(capacity)
    var head = 0
    var count = 0
    private var stepCounter = 0

    fun record(x: Double, y: Double, minStepInterval: Int = 2) {
        stepCounter++
        if (count > 0 && stepCounter < minStepInterval) return
        stepCounter = 0
        xs[head] = x
        ys[head] = y
        head = (head + 1) % capacity
        if (count < capacity) count++
    }

    fun clear() {
        head = 0
        count = 0
        stepCounter = 0
    }

    fun copyPointsFrom(other: TrailRingBuffer) {
        val len = minOf(capacity, other.capacity)
        count = minOf(other.count, len)
        head = other.head % capacity
        System.arraycopy(other.xs, 0, xs, 0, count.coerceAtMost(other.xs.size))
        System.arraycopy(other.ys, 0, ys, 0, count.coerceAtMost(other.ys.size))
    }
}

// Основная физическая модель небесного тела
data class CelestialBody(
    val id: Long,
    var name: String,
    var type: BodyType,
    var textureStyle: TextureStyle,
    var massKg: Double,
    var radiusM: Double,
    var x: Double,
    var y: Double,
    var vx: Double,
    var vy: Double,
    var ax: Double = 0.0,
    var ay: Double = 0.0,
    var densityKgM3: Double = PhysicsUnits.computeDensityKgM3(massKg, radiusM),
    var baseTempK: Double = 280.0,
    var surfaceTempK: Double = 280.0,
    var luminosityW: Double = 0.0,
    var atmosphereFraction: Double = 0.0,
    var greenhouseOffsetK: Double = 0.0,
    var albedo: Double = 0.3,
    var primaryColorArgb: Int,
    var secondaryColorArgb: Int,
    var hasRings: Boolean = false,
    var ringInnerRatio: Float = 1.35f,
    var ringOuterRatio: Float = 2.25f,
    var isLightweight: Boolean = false,
    var trail: TrailRingBuffer? = if (isLightweight) null else TrailRingBuffer(180)
)

// Эффект ударной вспышки и волны при столкновении
data class ImpactFlash(
    val x: Double,
    val y: Double,
    val maxVisualRadiusM: Double,
    var ageWallSec: Float = 0f,
    val durationWallSec: Float = 1.6f,
    val colorArgb: Int = 0xFFFF9E3D.toInt(),
    val isShockwave: Boolean = true
)

// Событие столкновения для отображения телеметрии удара в HUD
data class CollisionEvent(
    val title: String,
    val isMerge: Boolean,
    val energyMt: Double,
    val massLossPercent: Double,
    val newMassKg: Double,
    val newDensityKgM3: Double,
    val newTempK: Double,
    val tempDeltaK: Double,
    val atmosphereLossPercent: Double,
    val timestampMs: Long
)

// Лёгкий снимок тела для безопасной и быстрой отрисовки в Compose Canvas
data class RenderBody(
    val id: Long,
    val name: String,
    val type: BodyType,
    val textureStyle: TextureStyle,
    val massKg: Double,
    val radiusM: Double,
    val x: Double,
    val y: Double,
    val vx: Double,
    val vy: Double,
    val surfaceTempK: Double,
    val luminosityW: Double,
    val atmosphereFraction: Double,
    val primaryColorArgb: Int,
    val secondaryColorArgb: Int,
    val hasRings: Boolean,
    val ringInnerRatio: Float,
    val ringOuterRatio: Float,
    val isLightweight: Boolean,
    val trailCount: Int,
    val trailXs: DoubleArray,
    val trailYs: DoubleArray,
    val trailHead: Int,
    val trailCapacity: Int
)

// Снимок телеметрии выбранного тела для панели инспектора
data class SelectedBodyTelemetry(
    val id: Long,
    val name: String,
    val type: BodyType,
    val massKg: Double,
    val radiusM: Double,
    val densityKgM3: Double,
    val surfaceGravityMs2: Double,
    val escapeVelocityMs: Double,
    val speedMs: Double,
    val distanceToSunM: Double?,
    val surfaceTempK: Double,
    val orbitalPeriodSec: Double?,
    val atmosphereFraction: Double,
    val hasRings: Boolean,
    val primaryColorArgb: Int
)

// Снимок кадра симуляции (обновляется на ~60 FPS из фоновой корутины)
data class SimulationSnapshot(
    val bodies: List<RenderBody>,
    val flashes: List<ImpactFlash>,
    val simTimeSeconds: Double,
    val heavyBodyCount: Int,
    val particleCount: Int,
    val nearestStarX: Double,
    val nearestStarY: Double,
    val hasStar: Boolean,
    val selectedTelemetry: SelectedBodyTelemetry?
)
