package com.example.storage

import android.content.Context
import com.example.physics.BodyType
import com.example.physics.CelestialBody
import com.example.physics.TextureStyle
import com.example.physics.TrailRingBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

// Метаданные сохранённой сцены для списка «Мои сцены»
data class SavedSceneMetadata(
    val id: String,
    val name: String,
    val savedAtMillis: Long,
    val bodyCount: Int
)

// Полное сохранённое состояние сцены
data class SavedSceneData(
    val id: String,
    val name: String,
    val savedAtMillis: Long,
    val bodies: List<CelestialBody>,
    val timeScale: Double,
    val cameraCenterX: Double,
    val cameraCenterY: Double,
    val halfWidthMeters: Double,
    val showGrid: Boolean,
    val showTrails: Boolean,
    val showLabels: Boolean
)

// Локальное хранилище сцен и настроек в памяти телефона
class SceneStorageRepository(context: Context) {

    private val appContext = context.applicationContext
    private val scenesDir = File(appContext.filesDir, "scenes").apply {
        if (!exists()) mkdirs()
    }
    private val prefs = appContext.getSharedPreferences("cosmos_sim_prefs", Context.MODE_PRIVATE)

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean("onboarding_completed_v1", false)
    }

    fun setOnboardingCompleted(completed: Boolean = true) {
        prefs.edit().putBoolean("onboarding_completed_v1", completed).apply()
    }

    suspend fun listScenes(): List<SavedSceneMetadata> = withContext(Dispatchers.IO) {
        val files = scenesDir.listFiles { f -> f.isFile && f.extension == "json" } ?: return@withContext emptyList()
        val list = ArrayList<SavedSceneMetadata>(files.size)
        for (file in files) {
            runCatching {
                val json = JSONObject(file.readText())
                val bodiesArr = json.optJSONArray("bodies")
                list.add(
                    SavedSceneMetadata(
                        id = json.getString("id"),
                        name = json.getString("name"),
                        savedAtMillis = json.getLong("savedAtMillis"),
                        bodyCount = bodiesArr?.length() ?: 0
                    )
                )
            }
        }
        list.sortByDescending { it.savedAtMillis }
        list
    }

    suspend fun saveScene(
        name: String,
        bodies: List<CelestialBody>,
        timeScale: Double,
        cameraCenterX: Double,
        cameraCenterY: Double,
        halfWidthMeters: Double,
        showGrid: Boolean,
        showTrails: Boolean,
        showLabels: Boolean
    ): SavedSceneMetadata = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val cleanName = name.trim().ifEmpty { "Моя сцена" }

        val root = JSONObject().apply {
            put("id", id)
            put("name", cleanName)
            put("savedAtMillis", now)
            put("timeScale", timeScale)
            put("cameraCenterX", cameraCenterX)
            put("cameraCenterY", cameraCenterY)
            put("halfWidthMeters", halfWidthMeters)
            put("showGrid", showGrid)
            put("showTrails", showTrails)
            put("showLabels", showLabels)

            val arr = JSONArray()
            for (b in bodies) {
                arr.put(
                    JSONObject().apply {
                        put("id", b.id)
                        put("name", b.name)
                        put("type", b.type.name)
                        put("textureStyle", b.textureStyle.name)
                        put("massKg", b.massKg)
                        put("radiusM", b.radiusM)
                        put("x", b.x)
                        put("y", b.y)
                        put("vx", b.vx)
                        put("vy", b.vy)
                        put("baseTempK", b.baseTempK)
                        put("surfaceTempK", b.surfaceTempK)
                        put("luminosityW", b.luminosityW)
                        put("atmosphereFraction", b.atmosphereFraction)
                        put("greenhouseOffsetK", b.greenhouseOffsetK)
                        put("albedo", b.albedo)
                        put("primaryColorArgb", b.primaryColorArgb)
                        put("secondaryColorArgb", b.secondaryColorArgb)
                        put("hasRings", b.hasRings)
                        put("ringInnerRatio", b.ringInnerRatio.toDouble())
                        put("ringOuterRatio", b.ringOuterRatio.toDouble())
                        put("isLightweight", b.isLightweight)
                    }
                )
            }
            put("bodies", arr)
        }

        File(scenesDir, "$id.json").writeText(root.toString())
        SavedSceneMetadata(id = id, name = cleanName, savedAtMillis = now, bodyCount = bodies.size)
    }

    suspend fun loadScene(sceneId: String): SavedSceneData? = withContext(Dispatchers.IO) {
        val file = File(scenesDir, "$sceneId.json")
        if (!file.exists()) return@withContext null
        runCatching {
            val root = JSONObject(file.readText())
            val arr = root.getJSONArray("bodies")
            val bodies = ArrayList<CelestialBody>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val isLight = o.optBoolean("isLightweight", false)
                bodies.add(
                    CelestialBody(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        type = BodyType.valueOf(o.getString("type")),
                        textureStyle = TextureStyle.valueOf(o.getString("textureStyle")),
                        massKg = o.getDouble("massKg"),
                        radiusM = o.getDouble("radiusM"),
                        x = o.getDouble("x"),
                        y = o.getDouble("y"),
                        vx = o.getDouble("vx"),
                        vy = o.getDouble("vy"),
                        baseTempK = o.optDouble("baseTempK", 250.0),
                        surfaceTempK = o.optDouble("surfaceTempK", 250.0),
                        luminosityW = o.optDouble("luminosityW", 0.0),
                        atmosphereFraction = o.optDouble("atmosphereFraction", 0.0),
                        greenhouseOffsetK = o.optDouble("greenhouseOffsetK", 0.0),
                        albedo = o.optDouble("albedo", 0.3),
                        primaryColorArgb = o.getInt("primaryColorArgb"),
                        secondaryColorArgb = o.getInt("secondaryColorArgb"),
                        hasRings = o.optBoolean("hasRings", false),
                        ringInnerRatio = o.optDouble("ringInnerRatio", 1.35).toFloat(),
                        ringOuterRatio = o.optDouble("ringOuterRatio", 2.25).toFloat(),
                        isLightweight = isLight,
                        trail = if (isLight) null else TrailRingBuffer(180)
                    )
                )
            }
            SavedSceneData(
                id = root.getString("id"),
                name = root.getString("name"),
                savedAtMillis = root.getLong("savedAtMillis"),
                bodies = bodies,
                timeScale = root.optDouble("timeScale", 100_000.0),
                cameraCenterX = root.optDouble("cameraCenterX", 0.0),
                cameraCenterY = root.optDouble("cameraCenterY", 0.0),
                halfWidthMeters = root.optDouble("halfWidthMeters", 1.495978707e11 * 2.4),
                showGrid = root.optBoolean("showGrid", true),
                showTrails = root.optBoolean("showTrails", true),
                showLabels = root.optBoolean("showLabels", true)
            )
        }.getOrNull()
    }

    suspend fun deleteScene(sceneId: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(scenesDir, "$sceneId.json")
        if (file.exists()) file.delete() else false
    }
}
