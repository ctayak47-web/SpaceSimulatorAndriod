package com.example

import com.example.physics.BodyType
import com.example.physics.CelestialBody
import com.example.physics.PhysicsEngine
import com.example.physics.PhysicsUnits
import com.example.physics.TextureStyle
import com.example.scenarios.ScenarioRepository
import com.example.scenarios.ScenarioType
import com.example.scenarios.SolarSystemFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsEngineTest {

    @Test
    fun solarSystemContainsAllExpectedBodies() {
        val bodies = SolarSystemFactory.createSolarSystem()
        val names = bodies.map { it.name }.toSet()
        listOf(
            "Солнце", "Меркурий", "Венера", "Земля", "Луна",
            "Марс", "Фобос", "Юпитер", "Ио", "Европа",
            "Сатурн", "Титан", "Уран", "Нептун", "Плутон",
            "Комета Галлея", "Комета Хейла–Боппа"
        ).forEach { expected ->
            assertTrue("Missing $expected", names.contains(expected))
        }
        val beltCount = bodies.count { it.type == BodyType.ASTEROID && it.isLightweight }
        assertTrue("Expected asteroid belt particles, got $beltCount", beltCount >= 90)
    }

    @Test
    fun allScenariosBuildWithoutNaN() {
        for (scenario in ScenarioType.entries) {
            val setup = ScenarioRepository.buildScenario(scenario)
            if (scenario == ScenarioType.EMPTY_SANDBOX) {
                assertTrue(setup.bodies.isEmpty())
                continue
            }
            assertTrue("Scenario $scenario must have bodies", setup.bodies.isNotEmpty())
            for (b in setup.bodies) {
                assertTrue("${b.name} x is NaN", !b.x.isNaN() && !b.y.isNaN())
                assertTrue("${b.name} v is NaN", !b.vx.isNaN() && !b.vy.isNaN())
                assertTrue("${b.name} mass > 0", b.massKg > 0.0)
            }
        }
    }

    @Test
    fun logarithmicTimeScaleSupportsPauseToOneMillionX() {
        assertEquals(0.0, PhysicsUnits.sliderToTimeScale(0f), 0.0001)
        assertEquals(1_000_000.0, PhysicsUnits.sliderToTimeScale(1f), 1.0)
        val mid = PhysicsUnits.sliderToTimeScale(0.51f)
        assertTrue(mid in 100.0..10_000.0)
        val roundTrip = PhysicsUnits.timeScaleToSlider(mid)
        assertEquals(0.51f, roundTrip, 0.01f)
    }

    @Test
    fun lowSpeedCollisionMergesWithNewMassAndDensity() {
        val engine = PhysicsEngine()
        val b1 = CelestialBody(
            id = 1L, name = "Планета А", type = BodyType.PLANET, textureStyle = TextureStyle.EARTH,
            massKg = 5.97e24, radiusM = 6.37e6, x = 0.0, y = 0.0, vx = 0.0, vy = 0.0,
            atmosphereFraction = 1.0, primaryColorArgb = 0xFF2563EB.toInt(), secondaryColorArgb = 0xFF22C55E.toInt()
        )
        val b2 = CelestialBody(
            id = 2L, name = "Планета Б", type = BodyType.PLANET, textureStyle = TextureStyle.MERCURY,
            massKg = 3.3e23, radiusM = 2.44e6, x = 8.5e6, y = 0.0, vx = -2_000.0, vy = 0.0,
            primaryColorArgb = 0xFF9E9E9E.toInt(), secondaryColorArgb = 0xFF616161.toInt()
        )
        val initialMass1 = b1.massKg
        engine.loadBodies(listOf(b1, b2), initialTimeScale = 50.0, startPaused = false)
        engine.start()
        Thread.sleep(250)
        engine.stop()

        val snap = engine.snapshot.value
        assertEquals(1, snap.heavyBodyCount)
        val event = engine.lastCollision.value
        assertNotNull("Expected collision event", event)
        assertTrue("Expected low-speed merge", event!!.isMerge)
        assertTrue("Expected combined mass > initialMass1", event.newMassKg > initialMass1)
        assertTrue("Expected valid density", event.newDensityKgM3 > 1000.0)
    }

    @Test
    fun highSpeedCollisionShattersIntoDebrisFragments() {
        val engine = PhysicsEngine()
        val b1 = CelestialBody(
            id = 1L, name = "Земля", type = BodyType.PLANET, textureStyle = TextureStyle.EARTH,
            massKg = 5.97e24, radiusM = 6.37e6, x = 0.0, y = 0.0, vx = 0.0, vy = 0.0,
            atmosphereFraction = 1.0, primaryColorArgb = 0xFF2563EB.toInt(), secondaryColorArgb = 0xFF22C55E.toInt()
        )
        val b2 = CelestialBody(
            id = 2L, name = "Болид", type = BodyType.PLANET, textureStyle = TextureStyle.MERCURY,
            massKg = 3.3e23, radiusM = 2.44e6, x = 8.5e6, y = 0.0, vx = -45_000.0, vy = 0.0,
            primaryColorArgb = 0xFF9E9E9E.toInt(), secondaryColorArgb = 0xFF616161.toInt()
        )
        engine.loadBodies(listOf(b1, b2), initialTimeScale = 10.0, startPaused = false)
        engine.start()
        Thread.sleep(250)
        engine.stop()

        val snap = engine.snapshot.value
        assertEquals(1, snap.heavyBodyCount)
        assertTrue("Expected debris fragments", snap.particleCount > 0)
        val event = engine.lastCollision.value
        assertNotNull("Expected collision event", event)
        assertFalse("Expected high-speed fragmentation", event!!.isMerge)
        assertTrue("Expected mass loss > 5%", event.massLossPercent > 5.0)
    }

    @Test
    fun detonateBodyTriggersSupernovaOrFragments() {
        val engine = PhysicsEngine()
        val star = CelestialBody(
            id = 1L, name = "Звезда-А", type = BodyType.STAR, textureStyle = TextureStyle.SUN,
            massKg = PhysicsUnits.SUN_MASS_KG, radiusM = PhysicsUnits.SUN_RADIUS_M,
            x = 0.0, y = 0.0, vx = 0.0, vy = 0.0,
            luminosityW = PhysicsUnits.SUN_LUMINOSITY_W,
            primaryColorArgb = 0xFFFFD54F.toInt(), secondaryColorArgb = 0xFFFF8F00.toInt()
        )
        val planet = CelestialBody(
            id = 2L, name = "Мир", type = BodyType.PLANET, textureStyle = TextureStyle.EARTH,
            massKg = PhysicsUnits.EARTH_MASS_KG, radiusM = PhysicsUnits.EARTH_RADIUS_M,
            x = PhysicsUnits.AU_METERS, y = 0.0, vx = 0.0, vy = 29_780.0,
            primaryColorArgb = 0xFF2563EB.toInt(), secondaryColorArgb = 0xFF10B981.toInt()
        )
        engine.loadBodies(listOf(star, planet), initialTimeScale = 1.0, startPaused = false)
        engine.detonateBody(1L)

        val afterSupernova = engine.snapshot.value
        assertTrue("Expected supernova debris particles", afterSupernova.particleCount >= 20)
        val remnant = afterSupernova.bodies.first { it.id == 1L }
        assertEquals(TextureStyle.NEUTRON_STAR, remnant.textureStyle)
        assertNotNull(engine.lastCollision.value)
        assertTrue(engine.lastCollision.value!!.title.contains("Сверхновая"))

        engine.detonateBody(2L)
        val afterPlanetBomb = engine.snapshot.value
        assertEquals(1, afterPlanetBomb.heavyBodyCount)
    }

    @Test
    fun rocheLimitDisruptsPlanetNearBlackHole() {
        val engine = PhysicsEngine()
        val bh = CelestialBody(
            id = 1L, name = "ЧД", type = BodyType.BLACK_HOLE, textureStyle = TextureStyle.BLACK_HOLE,
            massKg = PhysicsUnits.SUN_MASS_KG * 5.0, radiusM = 2.0e8,
            x = 0.0, y = 0.0, vx = 0.0, vy = 0.0,
            primaryColorArgb = 0xFF000000.toInt(), secondaryColorArgb = 0xFF34D399.toInt()
        )
        val planet = CelestialBody(
            id = 2L, name = "Обречённая", type = BodyType.PLANET, textureStyle = TextureStyle.EARTH,
            massKg = PhysicsUnits.EARTH_MASS_KG, radiusM = PhysicsUnits.EARTH_RADIUS_M,
            x = 6.0e8, y = 0.0, vx = 0.0, vy = 100_000.0,
            primaryColorArgb = 0xFF2563EB.toInt(), secondaryColorArgb = 0xFF10B981.toInt()
        )
        engine.loadBodies(listOf(bh, planet), initialTimeScale = 10.0, startPaused = false)
        engine.start()
        Thread.sleep(200)
        engine.stop()

        val snap = engine.snapshot.value
        assertEquals(1, snap.heavyBodyCount)
        assertTrue("Expected tidal debris fragments", snap.particleCount > 0)
        val event = engine.lastCollision.value
        assertNotNull(event)
        assertTrue(event!!.title.contains("Предел Роша"))
    }
}
