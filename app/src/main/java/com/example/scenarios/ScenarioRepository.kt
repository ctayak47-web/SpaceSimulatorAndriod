package com.example.scenarios

import com.example.physics.BodyType
import com.example.physics.CelestialBody
import com.example.physics.PhysicsUnits
import com.example.physics.TextureStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// Готовые катастрофы, песочница и эксперименты одной кнопкой
enum class ScenarioType(
    val titleRu: String,
    val descriptionRu: String,
    val defaultTimeScale: Double,
    val defaultZoomMetersPerHalfScreen: Double
) {
    SOLAR_SYSTEM(
        titleRu = "Солнечная система",
        descriptionRu = "Реальные массы, орбиты, спутники, пояс астероидов и кометы",
        defaultTimeScale = 120_000.0,
        defaultZoomMetersPerHalfScreen = PhysicsUnits.AU_METERS * 2.4
    ),
    EMPTY_SANDBOX(
        titleRu = "Пустая песочница",
        descriptionRu = "Чистый космос: создавайте свои звёзды, планеты и чёрные дыры",
        defaultTimeScale = 100_000.0,
        defaultZoomMetersPerHalfScreen = PhysicsUnits.AU_METERS * 2.0
    ),
    MERCURY_HITS_EARTH(
        titleRu = "Меркурий врезается в Землю",
        descriptionRu = "Лобовое столкновение Меркурия с Землёй на космической скорости",
        defaultTimeScale = 8_000.0,
        defaultZoomMetersPerHalfScreen = PhysicsUnits.AU_METERS * 0.09
    ),
    MOON_FALLS_ON_EARTH(
        titleRu = "Луна падает на Землю",
        descriptionRu = "Луна теряет орбитальную скорость и обрушивается на Землю",
        defaultTimeScale = 6_000.0,
        defaultZoomMetersPerHalfScreen = 4.6e8
    ),
    SUN_DISAPPEARS(
        titleRu = "Солнце исчезает",
        descriptionRu = "Звезда мгновенно пропадает — планеты срываются с орбит и замерзают",
        defaultTimeScale = 450_000.0,
        defaultZoomMetersPerHalfScreen = PhysicsUnits.AU_METERS * 3.2
    ),
    SUN_RED_GIANT(
        titleRu = "Солнце — красный гигант",
        descriptionRu = "Раздувшаяся звезда поглощает внутренние планеты и сжигает Землю",
        defaultTimeScale = 180_000.0,
        defaultZoomMetersPerHalfScreen = PhysicsUnits.AU_METERS * 2.2
    ),
    SECOND_JUPITER(
        titleRu = "Второй Юпитер в системе",
        descriptionRu = "Второй газовый гигант на 1.85 а.е. разрушает стабильность орбит",
        defaultTimeScale = 600_000.0,
        defaultZoomMetersPerHalfScreen = PhysicsUnits.AU_METERS * 3.5
    ),
    BLACK_HOLE_FLYBY(
        titleRu = "Чёрная дыра пролетает рядом",
        descriptionRu = "Сверхплотный объект 4.5 M☉ проносится сквозь Солнечную систему",
        defaultTimeScale = 350_000.0,
        defaultZoomMetersPerHalfScreen = PhysicsUnits.AU_METERS * 3.2
    ),
    GALAXY_COLLISION(
        titleRu = "Столкновение двух галактик",
        descriptionRu = "Два звёздных диска сближаются и образуют приливные рукава",
        defaultTimeScale = 450_000.0,
        defaultZoomMetersPerHalfScreen = PhysicsUnits.AU_METERS * 18.0
    )
}

data class ScenarioSetup(
    val bodies: List<CelestialBody>,
    val timeScale: Double,
    val cameraCenterX: Double,
    val cameraCenterY: Double,
    val halfWidthMeters: Double,
    val focusBodyId: Long? = null
)

object ScenarioRepository {

    fun buildScenario(type: ScenarioType): ScenarioSetup {
        return when (type) {
            ScenarioType.SOLAR_SYSTEM -> ScenarioSetup(
                bodies = SolarSystemFactory.createSolarSystem(),
                timeScale = type.defaultTimeScale,
                cameraCenterX = 0.0,
                cameraCenterY = 0.0,
                halfWidthMeters = type.defaultZoomMetersPerHalfScreen
            )

            ScenarioType.EMPTY_SANDBOX -> ScenarioSetup(
                bodies = emptyList(),
                timeScale = type.defaultTimeScale,
                cameraCenterX = 0.0,
                cameraCenterY = 0.0,
                halfWidthMeters = type.defaultZoomMetersPerHalfScreen
            )

            ScenarioType.MERCURY_HITS_EARTH -> {
                val bodies = SolarSystemFactory.createSolarSystem().toMutableList()
                val earth = bodies.first { it.name == "Земля" }
                val mercury = bodies.first { it.name == "Меркурий" }
                val offset = PhysicsUnits.AU_METERS * 0.045
                mercury.x = earth.x + offset
                mercury.y = earth.y + offset * 0.15
                mercury.vx = earth.vx - 44_000.0
                mercury.vy = earth.vy - 6_600.0
                mercury.trail?.clear()
                ScenarioSetup(
                    bodies = bodies,
                    timeScale = type.defaultTimeScale,
                    cameraCenterX = earth.x + offset * 0.4,
                    cameraCenterY = earth.y,
                    halfWidthMeters = type.defaultZoomMetersPerHalfScreen,
                    focusBodyId = earth.id
                )
            }

            ScenarioType.MOON_FALLS_ON_EARTH -> {
                val bodies = SolarSystemFactory.createSolarSystem().toMutableList()
                val earth = bodies.first { it.name == "Земля" }
                val moon = bodies.first { it.name == "Луна" }
                moon.vx = earth.vx + (moon.vx - earth.vx) * 0.04
                moon.vy = earth.vy + (moon.vy - earth.vy) * 0.04
                moon.trail?.clear()
                ScenarioSetup(
                    bodies = bodies,
                    timeScale = type.defaultTimeScale,
                    cameraCenterX = earth.x,
                    cameraCenterY = earth.y,
                    halfWidthMeters = type.defaultZoomMetersPerHalfScreen,
                    focusBodyId = earth.id
                )
            }

            ScenarioType.SUN_DISAPPEARS -> {
                val bodies = SolarSystemFactory.createSolarSystem(includeSun = false)
                ScenarioSetup(
                    bodies = bodies,
                    timeScale = type.defaultTimeScale,
                    cameraCenterX = 0.0,
                    cameraCenterY = 0.0,
                    halfWidthMeters = type.defaultZoomMetersPerHalfScreen
                )
            }

            ScenarioType.SUN_RED_GIANT -> {
                val bodies = SolarSystemFactory.createSolarSystem(includeSun = true, redGiantSun = true)
                ScenarioSetup(
                    bodies = bodies,
                    timeScale = type.defaultTimeScale,
                    cameraCenterX = 0.0,
                    cameraCenterY = 0.0,
                    halfWidthMeters = type.defaultZoomMetersPerHalfScreen
                )
            }

            ScenarioType.SECOND_JUPITER -> {
                val bodies = SolarSystemFactory.createSolarSystem().toMutableList()
                val maxId = bodies.maxOf { it.id }
                val r = PhysicsUnits.AU_METERS * 1.85
                val angle = 0.65
                val v = sqrt(PhysicsUnits.G_SI * PhysicsUnits.SUN_MASS_KG / r) * 0.88
                bodies.add(
                    CelestialBody(
                        id = maxId + 1,
                        name = "Юпитер-2",
                        type = BodyType.PLANET,
                        textureStyle = TextureStyle.JUPITER,
                        massKg = 1.8982e27 * 1.25,
                        radiusM = 7.4e7,
                        x = r * cos(angle),
                        y = r * sin(angle),
                        vx = -v * sin(angle),
                        vy = v * cos(angle),
                        baseTempK = 240.0,
                        surfaceTempK = 240.0,
                        atmosphereFraction = 1.0,
                        primaryColorArgb = 0xFFF59E0B.toInt(),
                        secondaryColorArgb = 0xFFB45309.toInt()
                    )
                )
                ScenarioSetup(
                    bodies = bodies,
                    timeScale = type.defaultTimeScale,
                    cameraCenterX = 0.0,
                    cameraCenterY = 0.0,
                    halfWidthMeters = type.defaultZoomMetersPerHalfScreen
                )
            }

            ScenarioType.BLACK_HOLE_FLYBY -> {
                val bodies = SolarSystemFactory.createSolarSystem().toMutableList()
                val maxId = bodies.maxOf { it.id }
                bodies.add(
                    CelestialBody(
                        id = maxId + 1,
                        name = "Чёрная дыра X-1",
                        type = BodyType.BLACK_HOLE,
                        textureStyle = TextureStyle.BLACK_HOLE,
                        massKg = PhysicsUnits.SUN_MASS_KG * 4.5,
                        radiusM = 2.2e8,
                        x = -PhysicsUnits.AU_METERS * 2.4,
                        y = -PhysicsUnits.AU_METERS * 1.15,
                        vx = 82_000.0,
                        vy = 26_000.0,
                        baseTempK = 0.0,
                        surfaceTempK = 0.0,
                        primaryColorArgb = 0xFF000000.toInt(),
                        secondaryColorArgb = 0xFF60A5FA.toInt()
                    )
                )
                ScenarioSetup(
                    bodies = bodies,
                    timeScale = type.defaultTimeScale,
                    cameraCenterX = 0.0,
                    cameraCenterY = 0.0,
                    halfWidthMeters = type.defaultZoomMetersPerHalfScreen
                )
            }

            ScenarioType.GALAXY_COLLISION -> buildGalaxyCollisionScenario(type)
        }
    }

    private fun buildGalaxyCollisionScenario(type: ScenarioType): ScenarioSetup {
        val bodies = ArrayList<CelestialBody>(170)
        var nextId = 1L
        val rng = Random(2026)

        val core1Mass = PhysicsUnits.SUN_MASS_KG * 32.0
        val core2Mass = PhysicsUnits.SUN_MASS_KG * 24.0

        val c1x = -PhysicsUnits.AU_METERS * 9.5
        val c1y = -PhysicsUnits.AU_METERS * 3.5
        val c1vx = 36_000.0
        val c1vy = 11_500.0

        val c2x = PhysicsUnits.AU_METERS * 9.5
        val c2y = PhysicsUnits.AU_METERS * 3.5
        val c2vx = -42_000.0
        val c2vy = -14_000.0

        bodies.add(
            CelestialBody(
                id = nextId++,
                name = "Ядро Андромеды",
                type = BodyType.BLACK_HOLE,
                textureStyle = TextureStyle.BLACK_HOLE,
                massKg = core1Mass,
                radiusM = 6.0e9,
                x = c1x, y = c1y, vx = c1vx, vy = c1vy,
                baseTempK = 0.0, surfaceTempK = 0.0,
                primaryColorArgb = 0xFF000000.toInt(),
                secondaryColorArgb = 0xFF60A5FA.toInt()
            )
        )
        bodies.add(
            CelestialBody(
                id = nextId++,
                name = "Ядро Млечного Пути",
                type = BodyType.BLACK_HOLE,
                textureStyle = TextureStyle.BLACK_HOLE,
                massKg = core2Mass,
                radiusM = 5.0e9,
                x = c2x, y = c2y, vx = c2vx, vy = c2vy,
                baseTempK = 0.0, surfaceTempK = 0.0,
                primaryColorArgb = 0xFF000000.toInt(),
                secondaryColorArgb = 0xFFF59E0B.toInt()
            )
        )

        fun populateDisk(
            cx: Double, cy: Double, cvx: Double, cvy: Double,
            coreMass: Double, count: Int, maxRadiusAu: Double,
            clockwise: Boolean, warmPalette: Boolean
        ) {
            val dir = if (clockwise) -1.0 else 1.0
            for (i in 0 until count) {
                val rAu = 0.9 + (i.toDouble() / count) * maxRadiusAu + rng.nextDouble(-0.25, 0.25)
                val rM = rAu.coerceAtLeast(0.7) * PhysicsUnits.AU_METERS
                val armOffset = if (i % 2 == 0) 0.0 else PI
                val angle = armOffset + rAu * 0.65 + rng.nextDouble(-0.45, 0.45)
                val v = sqrt(PhysicsUnits.G_SI * coreMass / rM)
                val color = if (warmPalette) {
                    if (rng.nextFloat() < 0.6f) 0xFFFBBF24.toInt() else 0xFFF97316.toInt()
                } else {
                    if (rng.nextFloat() < 0.65f) 0xFF93C5FD.toInt() else 0xFFE0F2FE.toInt()
                }
                bodies.add(
                    CelestialBody(
                        id = nextId++,
                        name = "Звезда #$nextId",
                        type = BodyType.STAR,
                        textureStyle = if (warmPalette) TextureStyle.SUN else TextureStyle.BLUE_STAR,
                        massKg = PhysicsUnits.SUN_MASS_KG * 0.1,
                        radiusM = 2.0e8,
                        x = cx + rM * cos(angle),
                        y = cy + rM * sin(angle),
                        vx = cvx - dir * v * sin(angle),
                        vy = cvy + dir * v * cos(angle),
                        baseTempK = 5500.0,
                        surfaceTempK = 5500.0,
                        primaryColorArgb = color,
                        secondaryColorArgb = 0xFFFFFFFF.toInt(),
                        isLightweight = true,
                        trail = null
                    )
                )
            }
        }

        populateDisk(c1x, c1y, c1vx, c1vy, core1Mass, count = 80, maxRadiusAu = 7.5, clockwise = false, warmPalette = false)
        populateDisk(c2x, c2y, c2vx, c2vy, core2Mass, count = 75, maxRadiusAu = 6.5, clockwise = true, warmPalette = true)

        return ScenarioSetup(
            bodies = bodies,
            timeScale = type.defaultTimeScale,
            cameraCenterX = 0.0,
            cameraCenterY = 0.0,
            halfWidthMeters = type.defaultZoomMetersPerHalfScreen
        )
    }
}
