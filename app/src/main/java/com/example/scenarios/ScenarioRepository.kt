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

enum class ScenarioCategory(val titleRu: String) {
    ALL("Все"),
    SYSTEMS("Системы"),
    CATASTROPHES("Катастрофы"),
    EXPERIMENTS("Эксперименты")
}

// Готовые катастрофы, песочница и эксперименты одной кнопкой
enum class ScenarioType(
    val titleRu: String,
    val descriptionRu: String,
    val category: ScenarioCategory,
    val defaultTimeScale: Double,
    val defaultZoomMetersPerHalfScreen: Double
) {
    SOLAR_SYSTEM(
        titleRu = "Солнечная система",
        descriptionRu = "Реальные массы, орбиты, спутники, пояс астероидов и кометы",
        category = ScenarioCategory.SYSTEMS,
        defaultTimeScale = 120_000.0,
        defaultZoomMetersPerHalfScreen = PhysicsUnits.AU_METERS * 2.4
    ),
    EMPTY_SANDBOX(
        titleRu = "Пустая песочница",
        descriptionRu = "Чистый космос: создавайте свои звёзды, планеты и чёрные дыры",
        category = ScenarioCategory.EXPERIMENTS,
        defaultTimeScale = 100_000.0,
        defaultZoomMetersPerHalfScreen = PhysicsUnits.AU_METERS * 2.0
    ),
    MERCURY_HITS_EARTH(
        titleRu = "Меркурий врезается в Землю",
        descriptionRu = "Лобовое столкновение Меркурия с Землёй на космической скорости",
        category = ScenarioCategory.CATASTROPHES,
        defaultTimeScale = 8_000.0,
        defaultZoomMetersPerHalfScreen = PhysicsUnits.AU_METERS * 0.09
    ),
    MOON_FALLS_ON_EARTH(
        titleRu = "Луна падает на Землю",
        descriptionRu = "Луна теряет орбитальную скорость и обрушивается на Землю",
        category = ScenarioCategory.CATASTROPHES,
        defaultTimeScale = 6_000.0,
        defaultZoomMetersPerHalfScreen = 4.6e8
    ),
    EARTH_RINGS(
        titleRu = "Земля с кольцами Сатурна",
        descriptionRu = "Вокруг Земли вращается кольцо из 90 ледяных обломков и Луна",
        category = ScenarioCategory.EXPERIMENTS,
        defaultTimeScale = 4_500.0,
        defaultZoomMetersPerHalfScreen = 5.2e8
    ),
    BINARY_STAR_TATOOINE(
        titleRu = "Двойная звезда Татуин",
        descriptionRu = "Две орбитальные звезды и планеты в общей обитаемой зоне",
        category = ScenarioCategory.SYSTEMS,
        defaultTimeScale = 160_000.0,
        defaultZoomMetersPerHalfScreen = PhysicsUnits.AU_METERS * 2.6
    ),
    THREE_BODY_CHAOS(
        titleRu = "Задача трёх тел",
        descriptionRu = "Три звезды в хаотическом гравитационном танце и обречённая планета",
        category = ScenarioCategory.EXPERIMENTS,
        defaultTimeScale = 180_000.0,
        defaultZoomMetersPerHalfScreen = PhysicsUnits.AU_METERS * 2.8
    ),
    BLUE_SUPERGIANT_SYSTEM(
        titleRu = "Система голубого сверхгиганта",
        descriptionRu = "Сверхгорячая звезда 8 M☉ с раскалёнными лавовыми и газовыми мирами",
        category = ScenarioCategory.SYSTEMS,
        defaultTimeScale = 90_000.0,
        defaultZoomMetersPerHalfScreen = PhysicsUnits.AU_METERS * 3.8
    ),
    ROGUE_PLANET_NIBIRU(
        titleRu = "Планета-странник в системе",
        descriptionRu = "Ледяной супергигант 12 M⊕ пересекает внутреннюю Солнечную систему",
        category = ScenarioCategory.CATASTROPHES,
        defaultTimeScale = 220_000.0,
        defaultZoomMetersPerHalfScreen = PhysicsUnits.AU_METERS * 2.5
    ),
    SUN_DISAPPEARS(
        titleRu = "Солнце исчезает",
        descriptionRu = "Звезда мгновенно пропадает — планеты срываются с орбит и замерзают",
        category = ScenarioCategory.CATASTROPHES,
        defaultTimeScale = 450_000.0,
        defaultZoomMetersPerHalfScreen = PhysicsUnits.AU_METERS * 3.2
    ),
    SUN_RED_GIANT(
        titleRu = "Солнце — красный гигант",
        descriptionRu = "Раздувшаяся звезда поглощает внутренние планеты и сжигает Землю",
        category = ScenarioCategory.CATASTROPHES,
        defaultTimeScale = 180_000.0,
        defaultZoomMetersPerHalfScreen = PhysicsUnits.AU_METERS * 2.2
    ),
    SECOND_JUPITER(
        titleRu = "Второй Юпитер в системе",
        descriptionRu = "Второй газовый гигант на 1.85 а.е. разрушает стабильность орбит",
        category = ScenarioCategory.EXPERIMENTS,
        defaultTimeScale = 600_000.0,
        defaultZoomMetersPerHalfScreen = PhysicsUnits.AU_METERS * 3.5
    ),
    BLACK_HOLE_FLYBY(
        titleRu = "Чёрная дыра пролетает рядом",
        descriptionRu = "Сверхплотный объект 4.5 M☉ проносится сквозь Солнечную систему",
        category = ScenarioCategory.CATASTROPHES,
        defaultTimeScale = 350_000.0,
        defaultZoomMetersPerHalfScreen = PhysicsUnits.AU_METERS * 3.2
    ),
    GALAXY_COLLISION(
        titleRu = "Столкновение двух галактик",
        descriptionRu = "Два звёздных диска сближаются и образуют приливные рукава",
        category = ScenarioCategory.CATASTROPHES,
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

            ScenarioType.EARTH_RINGS -> buildEarthRingsScenario(type)
            ScenarioType.BINARY_STAR_TATOOINE -> buildBinaryStarScenario(type)
            ScenarioType.THREE_BODY_CHAOS -> buildThreeBodyScenario(type)
            ScenarioType.BLUE_SUPERGIANT_SYSTEM -> buildBlueSupergiantScenario(type)
            ScenarioType.ROGUE_PLANET_NIBIRU -> buildRoguePlanetScenario(type)

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
                        secondaryColorArgb = 0xFF34D399.toInt()
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

    private fun buildEarthRingsScenario(type: ScenarioType): ScenarioSetup {
        val list = ArrayList<CelestialBody>(105)
        var nextId = 1L
        val earth = CelestialBody(
            id = nextId++,
            name = "Земля",
            type = BodyType.PLANET,
            textureStyle = TextureStyle.EARTH,
            massKg = PhysicsUnits.EARTH_MASS_KG,
            radiusM = PhysicsUnits.EARTH_RADIUS_M,
            x = 0.0, y = 0.0, vx = 0.0, vy = 0.0,
            baseTempK = 285.0,
            surfaceTempK = 285.0,
            atmosphereFraction = 1.0,
            greenhouseOffsetK = 33.0,
            primaryColorArgb = 0xFF2563EB.toInt(),
            secondaryColorArgb = 0xFF10B981.toInt()
        )
        list.add(earth)

        val moonDist = 3.844e8
        val moonV = sqrt(PhysicsUnits.G_SI * (earth.massKg + 7.342e22) / moonDist)
        list.add(
            CelestialBody(
                id = nextId++,
                name = "Луна",
                type = BodyType.MOON,
                textureStyle = TextureStyle.MOON,
                massKg = 7.342e22,
                radiusM = 1.7374e6,
                x = moonDist, y = 0.0, vx = 0.0, vy = moonV,
                baseTempK = 250.0, surfaceTempK = 250.0,
                primaryColorArgb = 0xFFD1D5DB.toInt(),
                secondaryColorArgb = 0xFF9CA3AF.toInt()
            )
        )

        val rng = Random(77)
        for (i in 0 until 90) {
            val r = rng.nextDouble(2.4e7, 7.6e7)
            val ang = rng.nextDouble(0.0, 2.0 * PI)
            val v = sqrt(PhysicsUnits.G_SI * earth.massKg / r)
            val isIce = rng.nextFloat() < 0.55f
            list.add(
                CelestialBody(
                    id = nextId++,
                    name = "Обломок кольца #${i + 1}",
                    type = BodyType.ASTEROID,
                    textureStyle = if (isIce) TextureStyle.COMET else TextureStyle.ASTEROID,
                    massKg = 2.0e14,
                    radiusM = rng.nextDouble(1.8e5, 4.5e5),
                    x = r * cos(ang), y = r * sin(ang),
                    vx = -v * sin(ang), vy = v * cos(ang),
                    baseTempK = 195.0, surfaceTempK = 195.0,
                    primaryColorArgb = if (isIce) 0xFFA7F3D0.toInt() else 0xFFD1D5DB.toInt(),
                    secondaryColorArgb = 0xFF6B7280.toInt(),
                    isLightweight = true,
                    trail = null
                )
            )
        }

        return ScenarioSetup(
            bodies = list,
            timeScale = type.defaultTimeScale,
            cameraCenterX = 0.0,
            cameraCenterY = 0.0,
            halfWidthMeters = type.defaultZoomMetersPerHalfScreen,
            focusBodyId = earth.id
        )
    }

    private fun buildBinaryStarScenario(type: ScenarioType): ScenarioSetup {
        val list = ArrayList<CelestialBody>(12)
        var nextId = 1L

        val m1 = PhysicsUnits.SUN_MASS_KG * 1.15
        val m2 = PhysicsUnits.SUN_MASS_KG * 0.85
        val totalM = m1 + m2
        val sep = PhysicsUnits.AU_METERS * 0.34
        val r1 = sep * (m2 / totalM)
        val r2 = sep * (m1 / totalM)
        val omega = sqrt(PhysicsUnits.G_SI * totalM / (sep * sep * sep))

        list.add(
            CelestialBody(
                id = nextId++, name = "Тату I", type = BodyType.STAR, textureStyle = TextureStyle.SUN,
                massKg = m1, radiusM = PhysicsUnits.SUN_RADIUS_M * 1.05,
                x = -r1, y = 0.0, vx = 0.0, vy = -omega * r1,
                baseTempK = 6050.0, surfaceTempK = 6050.0, luminosityW = PhysicsUnits.SUN_LUMINOSITY_W * 1.35,
                primaryColorArgb = 0xFFFFD54F.toInt(), secondaryColorArgb = 0xFFFF8F00.toInt()
            )
        )
        list.add(
            CelestialBody(
                id = nextId++, name = "Тату II", type = BodyType.STAR, textureStyle = TextureStyle.RED_GIANT,
                massKg = m2, radiusM = PhysicsUnits.SUN_RADIUS_M * 0.85,
                x = r2, y = 0.0, vx = 0.0, vy = omega * r2,
                baseTempK = 4350.0, surfaceTempK = 4350.0, luminosityW = PhysicsUnits.SUN_LUMINOSITY_W * 0.45,
                primaryColorArgb = 0xFFFF7043.toInt(), secondaryColorArgb = 0xFFD84315.toInt()
            )
        )

        fun addCircumbinary(
            name: String, style: TextureStyle, massKg: Double, radiusM: Double,
            distAu: Double, angle: Double, tempK: Double, atmo: Double,
            c1: Int, c2: Int, rings: Boolean = false
        ) {
            val r = distAu * PhysicsUnits.AU_METERS
            val v = sqrt(PhysicsUnits.G_SI * totalM / r)
            list.add(
                CelestialBody(
                    id = nextId++, name = name, type = BodyType.PLANET, textureStyle = style,
                    massKg = massKg, radiusM = radiusM,
                    x = r * cos(angle), y = r * sin(angle),
                    vx = -v * sin(angle), vy = v * cos(angle),
                    baseTempK = tempK, surfaceTempK = tempK, atmosphereFraction = atmo,
                    primaryColorArgb = c1, secondaryColorArgb = c2, hasRings = rings
                )
            )
        }

        addCircumbinary("Татуин", TextureStyle.MARS, PhysicsUnits.EARTH_MASS_KG * 1.1, PhysicsUnits.EARTH_RADIUS_M * 1.04, 1.35, 0.4, 318.0, 0.85, 0xFFE6C27A.toInt(), 0xFFB45309.toInt())
        addCircumbinary("Изумрудия", TextureStyle.EARTH, PhysicsUnits.EARTH_MASS_KG * 1.8, PhysicsUnits.EARTH_RADIUS_M * 1.2, 2.05, 2.4, 275.0, 1.0, 0xFF10B981.toInt(), 0xFF0284C7.toInt())
        addCircumbinary("Хот", TextureStyle.URANUS, PhysicsUnits.EARTH_MASS_KG * 6.5, PhysicsUnits.EARTH_RADIUS_M * 2.3, 3.4, 4.7, 130.0, 0.9, 0xFF67E8F9.toInt(), 0xFF0E7490.toInt(), rings = true)

        return ScenarioSetup(
            bodies = list,
            timeScale = type.defaultTimeScale,
            cameraCenterX = 0.0, cameraCenterY = 0.0,
            halfWidthMeters = type.defaultZoomMetersPerHalfScreen
        )
    }

    private fun buildThreeBodyScenario(type: ScenarioType): ScenarioSetup {
        val list = ArrayList<CelestialBody>(6)
        var nextId = 1L
        val au = PhysicsUnits.AU_METERS
        val starMass = PhysicsUnits.SUN_MASS_KG * 1.1

        val angles = listOf(0.0, 2.0 * PI / 3.0, 4.0 * PI / 3.0)
        val radius = au * 1.05
        val orbitalV = sqrt(PhysicsUnits.G_SI * starMass / (sqrt(3.0) * radius)) * 0.92
        val styles = listOf(TextureStyle.SUN, TextureStyle.BLUE_STAR, TextureStyle.RED_GIANT)
        val names = listOf("Альфа", "Бета", "Проксима")
        val colors = listOf(0xFFFFD54F.toInt(), 0xFF60A5FA.toInt(), 0xFFFF7043.toInt())

        for (k in 0..2) {
            val a = angles[k]
            val jitter = if (k == 1) 1.08 else 1.0
            list.add(
                CelestialBody(
                    id = nextId++,
                    name = names[k],
                    type = BodyType.STAR,
                    textureStyle = styles[k],
                    massKg = starMass * (if (k == 2) 0.85 else 1.0),
                    radiusM = PhysicsUnits.SUN_RADIUS_M,
                    x = radius * cos(a) * jitter,
                    y = radius * sin(a),
                    vx = -orbitalV * sin(a) * (if (k == 0) 0.95 else 1.03),
                    vy = orbitalV * cos(a),
                    baseTempK = 5800.0,
                    surfaceTempK = 5800.0,
                    luminosityW = PhysicsUnits.SUN_LUMINOSITY_W,
                    primaryColorArgb = colors[k],
                    secondaryColorArgb = 0xFFFFFFFF.toInt()
                )
            )
        }

        list.add(
            CelestialBody(
                id = nextId++,
                name = "Трисолярис",
                type = BodyType.PLANET,
                textureStyle = TextureStyle.EARTH,
                massKg = PhysicsUnits.EARTH_MASS_KG * 1.4,
                radiusM = PhysicsUnits.EARTH_RADIUS_M * 1.1,
                x = 0.0, y = au * 0.25,
                vx = 28_000.0, vy = 8_000.0,
                baseTempK = 310.0, surfaceTempK = 310.0, atmosphereFraction = 1.0,
                primaryColorArgb = 0xFF34D399.toInt(), secondaryColorArgb = 0xFF047857.toInt()
            )
        )

        return ScenarioSetup(
            bodies = list,
            timeScale = type.defaultTimeScale,
            cameraCenterX = 0.0, cameraCenterY = 0.0,
            halfWidthMeters = type.defaultZoomMetersPerHalfScreen
        )
    }

    private fun buildBlueSupergiantScenario(type: ScenarioType): ScenarioSetup {
        val list = ArrayList<CelestialBody>(8)
        var nextId = 1L
        val starMass = PhysicsUnits.SUN_MASS_KG * 8.0
        list.add(
            CelestialBody(
                id = nextId++,
                name = "Ригель-А",
                type = BodyType.STAR,
                textureStyle = TextureStyle.BLUE_STAR,
                massKg = starMass,
                radiusM = PhysicsUnits.SUN_RADIUS_M * 3.8,
                x = 0.0, y = 0.0, vx = 0.0, vy = 0.0,
                baseTempK = 18_500.0, surfaceTempK = 18_500.0,
                luminosityW = PhysicsUnits.SUN_LUMINOSITY_W * 1200.0,
                primaryColorArgb = 0xFF60A5FA.toInt(),
                secondaryColorArgb = 0xFF2DD4BF.toInt()
            )
        )

        fun addExo(name: String, style: TextureStyle, mE: Double, rE: Double, distAu: Double, ang: Double, tempK: Double, c1: Int, c2: Int, rings: Boolean = false) {
            val r = distAu * PhysicsUnits.AU_METERS
            val v = sqrt(PhysicsUnits.G_SI * starMass / r)
            list.add(
                CelestialBody(
                    id = nextId++, name = name, type = BodyType.PLANET, textureStyle = style,
                    massKg = PhysicsUnits.EARTH_MASS_KG * mE,
                    radiusM = PhysicsUnits.EARTH_RADIUS_M * rE,
                    x = r * cos(ang), y = r * sin(ang),
                    vx = -v * sin(ang), vy = v * cos(ang),
                    baseTempK = tempK, surfaceTempK = tempK, atmosphereFraction = 0.8,
                    primaryColorArgb = c1, secondaryColorArgb = c2, hasRings = rings
                )
            )
        }

        addExo("Мустафар", TextureStyle.LAVA_WORLD, 2.2, 1.3, 0.75, 0.3, 2400.0, 0xFFFF5722.toInt(), 0xFF7F1D1D.toInt())
        addExo("Горячий Юпитер", TextureStyle.JUPITER, 420.0, 12.0, 1.45, 2.1, 1450.0, 0xFFF59E0B.toInt(), 0xFFDC2626.toInt())
        addExo("Синий Гигант-2", TextureStyle.NEPTUNE, 45.0, 4.5, 2.6, 4.1, 680.0, 0xFF38BDF8.toInt(), 0xFF1D4ED8.toInt(), rings = true)
        addExo("Элизиум", TextureStyle.EARTH, 1.6, 1.15, 4.1, 5.4, 340.0, 0xFF10B981.toInt(), 0xFF0284C7.toInt())

        return ScenarioSetup(
            bodies = list,
            timeScale = type.defaultTimeScale,
            cameraCenterX = 0.0, cameraCenterY = 0.0,
            halfWidthMeters = type.defaultZoomMetersPerHalfScreen
        )
    }

    private fun buildRoguePlanetScenario(type: ScenarioType): ScenarioSetup {
        val bodies = SolarSystemFactory.createSolarSystem().toMutableList()
        val maxId = bodies.maxOf { it.id }
        bodies.add(
            CelestialBody(
                id = maxId + 1,
                name = "Нибиру (Странник)",
                type = BodyType.PLANET,
                textureStyle = TextureStyle.NEPTUNE,
                massKg = PhysicsUnits.EARTH_MASS_KG * 12.0,
                radiusM = PhysicsUnits.EARTH_RADIUS_M * 3.2,
                x = -PhysicsUnits.AU_METERS * 1.9,
                y = PhysicsUnits.AU_METERS * 1.35,
                vx = 48_000.0,
                vy = -31_000.0,
                baseTempK = 140.0,
                surfaceTempK = 140.0,
                atmosphereFraction = 1.0,
                primaryColorArgb = 0xFF2DD4BF.toInt(),
                secondaryColorArgb = 0xFF065F46.toInt(),
                hasRings = true,
                ringInnerRatio = 1.4f,
                ringOuterRatio = 2.1f
            )
        )
        return ScenarioSetup(
            bodies = bodies,
            timeScale = type.defaultTimeScale,
            cameraCenterX = 0.0, cameraCenterY = 0.0,
            halfWidthMeters = type.defaultZoomMetersPerHalfScreen
        )
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
                secondaryColorArgb = 0xFF34D399.toInt()
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
                    if (rng.nextFloat() < 0.65f) 0xFF34D399.toInt() else 0xFFA7F3D0.toInt()
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
