package com.example.scenarios

import com.example.physics.BodyType
import com.example.physics.CelestialBody
import com.example.physics.PhysicsUnits
import com.example.physics.TextureStyle
import com.example.physics.TrailRingBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// Шаблоны тел для конструктора Песочницы
enum class SandboxPreset(
    val titleRu: String,
    val subtitleRu: String,
    val bodyType: BodyType,
    val textureStyle: TextureStyle,
    val massKg: Double,
    val radiusM: Double,
    val tempK: Double,
    val luminosityW: Double,
    val atmosphere: Double,
    val primaryColor: Int,
    val secondaryColor: Int,
    val hasRings: Boolean = false
) {
    YELLOW_DWARF(
        "Жёлтый карлик", "1.0 M☉ · как Солнце",
        BodyType.STAR, TextureStyle.SUN,
        PhysicsUnits.SUN_MASS_KG, PhysicsUnits.SUN_RADIUS_M, 5778.0, PhysicsUnits.SUN_LUMINOSITY_W, 0.0,
        0xFFFFD54F.toInt(), 0xFFFF8F00.toInt()
    ),
    BLUE_SUPERGIANT(
        "Голубой гигант", "8.0 M☉ · яркая горячая звезда",
        BodyType.STAR, TextureStyle.BLUE_STAR,
        PhysicsUnits.SUN_MASS_KG * 8.0, PhysicsUnits.SUN_RADIUS_M * 3.5, 18500.0, PhysicsUnits.SUN_LUMINOSITY_W * 4500.0, 0.0,
        0xFF60A5FA.toInt(), 0xFF1D4ED8.toInt()
    ),
    RED_GIANT(
        "Красный гигант", "1.2 M☉ · огромная оболочка",
        BodyType.STAR, TextureStyle.RED_GIANT,
        PhysicsUnits.SUN_MASS_KG * 1.2, PhysicsUnits.AU_METERS * 0.45, 3200.0, PhysicsUnits.SUN_LUMINOSITY_W * 1600.0, 0.0,
        0xFFFF5722.toInt(), 0xFFB71C1C.toInt()
    ),
    WHITE_DWARF(
        "Белый карлик", "1.1 M☉ · сверхплотное ядро",
        BodyType.STAR, TextureStyle.WHITE_DWARF,
        PhysicsUnits.SUN_MASS_KG * 1.1, PhysicsUnits.EARTH_RADIUS_M * 1.1, 14000.0, PhysicsUnits.SUN_LUMINOSITY_W * 0.02, 0.0,
        0xFFE0F2FE.toInt(), 0xFF93C5FD.toInt()
    ),
    ROCKY_PLANET(
        "Каменистая планета", "1.5 M⊕ · с атмосферой",
        BodyType.PLANET, TextureStyle.EARTH,
        PhysicsUnits.EARTH_MASS_KG * 1.5, PhysicsUnits.EARTH_RADIUS_M * 1.15, 290.0, 0.0, 0.9,
        0xFF2563EB.toInt(), 0xFF22C55E.toInt()
    ),
    GAS_GIANT(
        "Газовый гигант", "1.0 M♃ · плотная атмосфера",
        BodyType.PLANET, TextureStyle.JUPITER,
        1.8982e27, 6.9911e7, 165.0, 0.0, 1.0,
        0xFFD9A066.toInt(), 0xFFB06A3B.toInt()
    ),
    RINGED_GIANT(
        "Кольцевая планета", "0.3 M♃ · с системой колец",
        BodyType.PLANET, TextureStyle.SATURN,
        5.6834e26, 5.8232e7, 140.0, 0.0, 1.0,
        0xFFE6CFA1.toInt(), 0xFFC2A36B.toInt(), hasRings = true
    ),
    LAVA_PLANET(
        "Лавовая планета", "2.0 M⊕ · раскалённая кора",
        BodyType.PLANET, TextureStyle.LAVA_WORLD,
        PhysicsUnits.EARTH_MASS_KG * 2.0, PhysicsUnits.EARTH_RADIUS_M * 1.25, 1850.0, 0.0, 0.15,
        0xFFFF5722.toInt(), 0xFF7F1D1D.toInt()
    ),
    ICE_PLANET(
        "Ледяной гигант", "15 M⊕ · холодный метан",
        BodyType.PLANET, TextureStyle.NEPTUNE,
        PhysicsUnits.EARTH_MASS_KG * 15.0, PhysicsUnits.EARTH_RADIUS_M * 3.8, 75.0, 0.0, 1.0,
        0xFF3B82F6.toInt(), 0xFF1E3A8A.toInt()
    ),
    STELLAR_BLACK_HOLE(
        "Чёрная дыра", "5.0 M☉ · сильное искривление",
        BodyType.BLACK_HOLE, TextureStyle.BLACK_HOLE,
        PhysicsUnits.SUN_MASS_KG * 5.0, 1.6e8, 0.0, 0.0, 0.0,
        0xFF000000.toInt(), 0xFF60A5FA.toInt()
    ),
    SUPERMASSIVE_BH(
        "Сверхмассивная ЧД", "45 M☉ · галактическое ядро",
        BodyType.BLACK_HOLE, TextureStyle.BLACK_HOLE,
        PhysicsUnits.SUN_MASS_KG * 45.0, 1.2e9, 0.0, 0.0, 0.0,
        0xFF000000.toInt(), 0xFFF59E0B.toInt()
    ),
    ASTEROID_BODY(
        "Астероид", "Каменный болид",
        BodyType.ASTEROID, TextureStyle.ASTEROID,
        8.0e20, 8.5e5, 190.0, 0.0, 0.0,
        0xFF9CA3AF.toInt(), 0xFF4B5563.toInt()
    ),
    COMET_BODY(
        "Комета", "Ледяное ядро с хвостом",
        BodyType.COMET, TextureStyle.COMET,
        5.0e15, 6.0e5, 160.0, 0.0, 0.0,
        0xFF67E8F9.toInt(), 0xFF0284C7.toInt()
    )
}

// Пользовательская конфигурация нового тела в песочнице
data class CustomBodyDraft(
    val preset: SandboxPreset = SandboxPreset.ROCKY_PLANET,
    val name: String = preset.titleRu,
    val massKg: Double = preset.massKg,
    val radiusM: Double = preset.radiusM,
    val hasRings: Boolean = preset.hasRings
)

// Фабрика реальной Солнечной системы и пресетов новых тел
object SolarSystemFactory {

    fun createSolarSystem(includeSun: Boolean = true, redGiantSun: Boolean = false): List<CelestialBody> {
        val list = ArrayList<CelestialBody>(128)
        var nextId = 1L

        val sunMass = if (redGiantSun) PhysicsUnits.SUN_MASS_KG * 0.88 else PhysicsUnits.SUN_MASS_KG
        val sunRadius = if (redGiantSun) PhysicsUnits.AU_METERS * 0.92 else PhysicsUnits.SUN_RADIUS_M
        val sunTemp = if (redGiantSun) 3150.0 else 5778.0
        val sunLum = if (redGiantSun) PhysicsUnits.SUN_LUMINOSITY_W * 2200.0 else PhysicsUnits.SUN_LUMINOSITY_W

        if (includeSun) {
            list.add(
                CelestialBody(
                    id = nextId++,
                    name = if (redGiantSun) "Солнце (Гигант)" else "Солнце",
                    type = BodyType.STAR,
                    textureStyle = if (redGiantSun) TextureStyle.RED_GIANT else TextureStyle.SUN,
                    massKg = sunMass,
                    radiusM = sunRadius,
                    x = 0.0, y = 0.0, vx = 0.0, vy = 0.0,
                    baseTempK = sunTemp,
                    surfaceTempK = sunTemp,
                    luminosityW = sunLum,
                    primaryColorArgb = if (redGiantSun) 0xFFFF5722.toInt() else 0xFFFFD54F.toInt(),
                    secondaryColorArgb = if (redGiantSun) 0xFFB71C1C.toInt() else 0xFFFF8F00.toInt()
                )
            )
        }

        val centralMass = PhysicsUnits.SUN_MASS_KG

        fun addOrbitingPlanet(
            name: String,
            style: TextureStyle,
            massKg: Double,
            radiusM: Double,
            semiMajorAu: Double,
            eccentricity: Double,
            angleRad: Double,
            baseTempK: Double,
            atmosphere: Double,
            greenhouseK: Double,
            albedo: Double,
            primaryColor: Int,
            secondaryColor: Int,
            hasRings: Boolean = false,
            ringInner: Float = 1.35f,
            ringOuter: Float = 2.25f,
            type: BodyType = BodyType.PLANET
        ): CelestialBody {
            val a = semiMajorAu * PhysicsUnits.AU_METERS
            val r = a * (1.0 - eccentricity)
            val speed = sqrt(PhysicsUnits.G_SI * centralMass * (1.0 + eccentricity) / r)
            val px = r * cos(angleRad)
            val py = r * sin(angleRad)
            val vx = -speed * sin(angleRad)
            val vy = speed * cos(angleRad)
            val body = CelestialBody(
                id = nextId++,
                name = name,
                type = type,
                textureStyle = style,
                massKg = massKg,
                radiusM = radiusM,
                x = px, y = py, vx = vx, vy = vy,
                baseTempK = baseTempK,
                surfaceTempK = baseTempK,
                atmosphereFraction = atmosphere,
                greenhouseOffsetK = greenhouseK,
                albedo = albedo,
                primaryColorArgb = primaryColor,
                secondaryColorArgb = secondaryColor,
                hasRings = hasRings,
                ringInnerRatio = ringInner,
                ringOuterRatio = ringOuter
            )
            list.add(body)
            return body
        }

        fun addMoon(
            parent: CelestialBody,
            name: String,
            massKg: Double,
            radiusM: Double,
            orbitDistM: Double,
            angleRad: Double,
            baseTempK: Double,
            atmosphere: Double,
            primaryColor: Int,
            secondaryColor: Int
        ) {
            val v = sqrt(PhysicsUnits.G_SI * (parent.massKg + massKg) / orbitDistM)
            list.add(
                CelestialBody(
                    id = nextId++,
                    name = name,
                    type = BodyType.MOON,
                    textureStyle = TextureStyle.MOON,
                    massKg = massKg,
                    radiusM = radiusM,
                    x = parent.x + orbitDistM * cos(angleRad),
                    y = parent.y + orbitDistM * sin(angleRad),
                    vx = parent.vx - v * sin(angleRad),
                    vy = parent.vy + v * cos(angleRad),
                    baseTempK = baseTempK,
                    surfaceTempK = baseTempK,
                    atmosphereFraction = atmosphere,
                    albedo = 0.12,
                    primaryColorArgb = primaryColor,
                    secondaryColorArgb = secondaryColor
                )
            )
        }

        if (!redGiantSun) {
            addOrbitingPlanet(
                name = "Меркурий", style = TextureStyle.MERCURY,
                massKg = 3.3011e23, radiusM = 2.4397e6,
                semiMajorAu = 0.3871, eccentricity = 0.2056, angleRad = 0.4,
                baseTempK = 440.0, atmosphere = 0.0, greenhouseK = 0.0, albedo = 0.088,
                primaryColor = 0xFF9E9E9E.toInt(), secondaryColor = 0xFF616161.toInt()
            )
            addOrbitingPlanet(
                name = "Венера", style = TextureStyle.VENUS,
                massKg = 4.8675e24, radiusM = 6.0518e6,
                semiMajorAu = 0.7233, eccentricity = 0.0067, angleRad = 2.1,
                baseTempK = 737.0, atmosphere = 1.0, greenhouseK = 505.0, albedo = 0.76,
                primaryColor = 0xFFE6C27A.toInt(), secondaryColor = 0xFFC89B4F.toInt()
            )
        }

        val earth = addOrbitingPlanet(
            name = "Земля", style = TextureStyle.EARTH,
            massKg = 5.9722e24, radiusM = 6.371e6,
            semiMajorAu = 1.0000, eccentricity = 0.0167, angleRad = 0.0,
            baseTempK = 288.0, atmosphere = 1.0, greenhouseK = 33.0, albedo = 0.30,
            primaryColor = 0xFF2563EB.toInt(), secondaryColor = 0xFF22C55E.toInt()
        )
        addMoon(
            parent = earth, name = "Луна",
            massKg = 7.342e22, radiusM = 1.7374e6, orbitDistM = 3.844e8,
            angleRad = 0.9, baseTempK = 250.0, atmosphere = 0.0,
            primaryColor = 0xFFD1D5DB.toInt(), secondaryColor = 0xFF9CA3AF.toInt()
        )

        val mars = addOrbitingPlanet(
            name = "Марс", style = TextureStyle.MARS,
            massKg = 6.4171e23, radiusM = 3.3895e6,
            semiMajorAu = 1.5237, eccentricity = 0.0934, angleRad = 3.7,
            baseTempK = 210.0, atmosphere = 0.08, greenhouseK = 4.0, albedo = 0.25,
            primaryColor = 0xFFD95B38.toInt(), secondaryColor = 0xFF8C331A.toInt()
        )
        addMoon(
            parent = mars, name = "Фобос",
            massKg = 1.0659e16, radiusM = 1.1267e4, orbitDistM = 9.376e6,
            angleRad = 1.5, baseTempK = 210.0, atmosphere = 0.0,
            primaryColor = 0xFF8D7B71.toInt(), secondaryColor = 0xFF5D4E46.toInt()
        )

        val rng = Random(42)
        for (i in 0 until 95) {
            val rAu = 2.15 + rng.nextDouble() * 1.10
            val rM = rAu * PhysicsUnits.AU_METERS
            val angle = rng.nextDouble() * 2.0 * PI
            val speed = sqrt(PhysicsUnits.G_SI * centralMass / rM) * rng.nextDouble(0.96, 1.04)
            list.add(
                CelestialBody(
                    id = nextId++,
                    name = "Астероид #${i + 1}",
                    type = BodyType.ASTEROID,
                    textureStyle = TextureStyle.ASTEROID,
                    massKg = 1.0e19,
                    radiusM = rng.nextDouble(1.0e5, 4.5e5),
                    x = rM * cos(angle),
                    y = rM * sin(angle),
                    vx = -speed * sin(angle),
                    vy = speed * cos(angle),
                    baseTempK = 170.0,
                    surfaceTempK = 170.0,
                    albedo = 0.15,
                    primaryColorArgb = 0xFF78716C.toInt(),
                    secondaryColorArgb = 0xFF57534E.toInt(),
                    isLightweight = true,
                    trail = null
                )
            )
        }

        val jupiter = addOrbitingPlanet(
            name = "Юпитер", style = TextureStyle.JUPITER,
            massKg = 1.8982e27, radiusM = 6.9911e7,
            semiMajorAu = 5.2044, eccentricity = 0.0489, angleRad = 1.25,
            baseTempK = 165.0, atmosphere = 1.0, greenhouseK = 45.0, albedo = 0.50,
            primaryColor = 0xFFD9A066.toInt(), secondaryColor = 0xFFB06A3B.toInt()
        )
        addMoon(
            parent = jupiter, name = "Ио",
            massKg = 8.9319e22, radiusM = 1.8216e6, orbitDistM = 4.217e8,
            angleRad = 0.3, baseTempK = 130.0, atmosphere = 0.01,
            primaryColor = 0xFFEAB308.toInt(), secondaryColor = 0xFFCA8A04.toInt()
        )
        addMoon(
            parent = jupiter, name = "Европа",
            massKg = 4.7998e22, radiusM = 1.5608e6, orbitDistM = 6.709e8,
            angleRad = 2.6, baseTempK = 102.0, atmosphere = 0.0,
            primaryColor = 0xFFE2E8F0.toInt(), secondaryColor = 0xFF94A3B8.toInt()
        )

        val saturn = addOrbitingPlanet(
            name = "Сатурн", style = TextureStyle.SATURN,
            massKg = 5.6834e26, radiusM = 5.8232e7,
            semiMajorAu = 9.5826, eccentricity = 0.0565, angleRad = 4.4,
            baseTempK = 134.0, atmosphere = 1.0, greenhouseK = 40.0, albedo = 0.47,
            primaryColor = 0xFFE6CFA1.toInt(), secondaryColor = 0xFFC2A36B.toInt(),
            hasRings = true, ringInner = 1.32f, ringOuter = 2.35f
        )
        addMoon(
            parent = saturn, name = "Титан",
            massKg = 1.3452e23, radiusM = 2.5747e6, orbitDistM = 1.2219e9,
            angleRad = 1.8, baseTempK = 94.0, atmosphere = 1.0,
            primaryColor = 0xFFF59E0B.toInt(), secondaryColor = 0xFFB45309.toInt()
        )

        addOrbitingPlanet(
            name = "Уран", style = TextureStyle.URANUS,
            massKg = 8.6810e25, radiusM = 2.5362e7,
            semiMajorAu = 19.218, eccentricity = 0.0457, angleRad = 2.9,
            baseTempK = 76.0, atmosphere = 1.0, greenhouseK = 15.0, albedo = 0.48,
            primaryColor = 0xFF67E8F9.toInt(), secondaryColor = 0xFF22D3EE.toInt(),
            hasRings = true, ringInner = 1.45f, ringOuter = 1.75f
        )

        addOrbitingPlanet(
            name = "Нептун", style = TextureStyle.NEPTUNE,
            massKg = 1.0241e26, radiusM = 2.4622e7,
            semiMajorAu = 30.110, eccentricity = 0.0113, angleRad = 5.3,
            baseTempK = 72.0, atmosphere = 1.0, greenhouseK = 22.0, albedo = 0.44,
            primaryColor = 0xFF3B82F6.toInt(), secondaryColor = 0xFF1D4ED8.toInt()
        )

        addOrbitingPlanet(
            name = "Плутон", style = TextureStyle.PLUTO,
            massKg = 1.303e22, radiusM = 1.1883e6,
            semiMajorAu = 39.482, eccentricity = 0.2488, angleRad = 0.85,
            baseTempK = 44.0, atmosphere = 0.02, greenhouseK = 0.0, albedo = 0.55,
            primaryColor = 0xFFD6C7B2.toInt(), secondaryColor = 0xFF8C735B.toInt()
        )

        run {
            val a = 17.83 * PhysicsUnits.AU_METERS
            val r = 1.75 * PhysicsUnits.AU_METERS
            val speed = sqrt(PhysicsUnits.G_SI * centralMass * (2.0 / r - 1.0 / a))
            val angle = 2.5
            val flightPathAngle = 0.65
            val vx = speed * (-sin(angle) * cos(flightPathAngle) - cos(angle) * sin(flightPathAngle))
            val vy = speed * (cos(angle) * cos(flightPathAngle) - sin(angle) * sin(flightPathAngle))
            list.add(
                CelestialBody(
                    id = nextId++,
                    name = "Комета Галлея",
                    type = BodyType.COMET,
                    textureStyle = TextureStyle.COMET,
                    massKg = 2.2e14,
                    radiusM = 5.5e5,
                    x = r * cos(angle), y = r * sin(angle), vx = vx, vy = vy,
                    baseTempK = 190.0, surfaceTempK = 210.0, albedo = 0.04,
                    primaryColorArgb = 0xFFBAE6FD.toInt(),
                    secondaryColorArgb = 0xFF38BDF8.toInt()
                )
            )
        }

        run {
            val r = 2.6 * PhysicsUnits.AU_METERS
            val a = 25.0 * PhysicsUnits.AU_METERS
            val speed = sqrt(PhysicsUnits.G_SI * centralMass * (2.0 / r - 1.0 / a))
            val angle = -0.95
            val flightPathAngle = 0.52
            val vx = speed * (-sin(angle) * cos(flightPathAngle) - cos(angle) * sin(flightPathAngle))
            val vy = speed * (cos(angle) * cos(flightPathAngle) - sin(angle) * sin(flightPathAngle))
            list.add(
                CelestialBody(
                    id = nextId++,
                    name = "Комета Хейла–Боппа",
                    type = BodyType.COMET,
                    textureStyle = TextureStyle.COMET,
                    massKg = 1.3e16,
                    radiusM = 7.0e5,
                    x = r * cos(angle), y = r * sin(angle), vx = vx, vy = vy,
                    baseTempK = 175.0, surfaceTempK = 190.0, albedo = 0.05,
                    primaryColorArgb = 0xFFA5F3FC.toInt(),
                    secondaryColorArgb = 0xFF06B6D4.toInt()
                )
            )
        }

        return list
    }

    fun createFromDraft(
        id: Long,
        draft: CustomBodyDraft,
        x: Double,
        y: Double,
        vx: Double,
        vy: Double
    ): CelestialBody {
        val p = draft.preset
        val cleanName = draft.name.trim().ifEmpty { p.titleRu }
        return CelestialBody(
            id = id,
            name = cleanName,
            type = p.bodyType,
            textureStyle = p.textureStyle,
            massKg = draft.massKg,
            radiusM = draft.radiusM,
            x = x,
            y = y,
            vx = vx,
            vy = vy,
            densityKgM3 = PhysicsUnits.computeDensityKgM3(draft.massKg, draft.radiusM),
            baseTempK = p.tempK,
            surfaceTempK = p.tempK,
            luminosityW = p.luminosityW,
            atmosphereFraction = p.atmosphere,
            greenhouseOffsetK = if (p.atmosphere > 0.4) 22.0 else 0.0,
            primaryColorArgb = p.primaryColor,
            secondaryColorArgb = p.secondaryColor,
            hasRings = draft.hasRings,
            ringInnerRatio = 1.35f,
            ringOuterRatio = 2.25f,
            trail = TrailRingBuffer(180)
        )
    }
}
