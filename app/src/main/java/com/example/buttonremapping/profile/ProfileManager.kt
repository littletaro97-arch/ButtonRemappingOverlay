package com.example.buttonremapping.profile

import android.content.Context
import android.view.Surface
import com.example.buttonremapping.ComponentRatio
import com.example.buttonremapping.LayoutConfig
import com.example.buttonremapping.LayoutPrefs
import com.example.buttonremapping.LongPressConfig
import com.example.buttonremapping.OperationLog
import com.example.buttonremapping.OverlayGeometry
import com.example.buttonremapping.highrisk.MappingConfig
import com.example.buttonremapping.highrisk.MappingPrefs
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ProfileMode(val wireName: String) {
    LOW("low"),
    LONG("long"),
    HIGH("high"),
}

data class ProfileSummary(
    val profileId: String,
    val name: String,
    val mode: ProfileMode,
    val createdTime: Long,
    val updatedTime: Long,
    val isActive: Boolean,
)

object ProfileManager {
    private const val PREFS_NAME = "profiles_config"
    private const val STORE_JSON = "profiles_json"
    private const val ACTIVE_LOW_ID = "active_low_id"
    private const val ACTIVE_LONG_ID = "active_long_id"
    private const val ACTIVE_HIGH_ID = "active_high_id"
    private const val DEFAULT_NAME = "默认方案"
    private const val LONG_PRESS_MIN_MS = 300
    private const val LONG_PRESS_MAX_MS = 3000

    private val lock = Any()

    fun list(context: Context, mode: ProfileMode): List<ProfileSummary> = synchronized(lock) {
        val store = ensureMode(context, loadStore(context), mode)
        store.profiles
            .asSequence()
            .filter { it.mode == mode }
            .sortedWith(compareBy<ProfileRecord> { it.createdTime }.thenBy { it.name })
            .map { it.toSummary(activeId(store, mode) == it.profileId) }
            .toList()
    }

    fun current(context: Context, mode: ProfileMode): ProfileSummary = synchronized(lock) {
        val store = ensureMode(context, loadStore(context), mode)
        val record = currentRecord(store, mode)
        record.toSummary(true)
    }

    fun currentName(context: Context, mode: ProfileMode): String = current(context, mode).name

    fun select(context: Context, mode: ProfileMode, profileId: String): Boolean = synchronized(lock) {
        val store = ensureMode(context, loadStore(context), mode)
        val selected = store.profiles.firstOrNull { it.mode == mode && it.profileId == profileId }
            ?: run {
                OperationLog.append(context, "方案切换失败", "mode=${mode.wireName}; profileId=$profileId")
                return false
            }
        setActiveId(store, mode, selected.profileId)
        persist(context, store)
        OperationLog.append(context, "切换方案", "mode=${mode.wireName}; name=${selected.name}")
        true
    }

    fun create(
        context: Context,
        mode: ProfileMode,
        requestedName: String,
        copyCurrent: Boolean,
    ): ProfileSummary = synchronized(lock) {
        val store = ensureMode(context, loadStore(context), mode)
        val now = System.currentTimeMillis()
        val name = uniqueName(
            store.profiles.filter { it.mode == mode }.map { it.name },
            requestedName,
        )
        val source = currentRecord(store, mode)
        val record = ProfileRecord(
            profileId = UUID.randomUUID().toString(),
            name = name,
            mode = mode,
            createdTime = now,
            updatedTime = now,
            lowConfig = if (mode == ProfileMode.LOW) {
                if (copyCurrent) source.lowConfig ?: LayoutPrefs.defaults else LayoutPrefs.defaults
            } else {
                null
            },
            longConfig = if (mode == ProfileMode.LONG) {
                if (copyCurrent) source.longConfig ?: LongPressConfig() else LongPressConfig()
            } else {
                null
            },
            highConfig = if (mode == ProfileMode.HIGH) {
                if (copyCurrent) source.highConfig ?: MappingConfig() else MappingConfig()
            } else {
                null
            },
        )
        store.profiles += record
        setActiveId(store, mode, record.profileId)
        persist(context, store)
        OperationLog.append(
            context,
            "创建方案",
            "mode=${mode.wireName}; name=$name; copyCurrent=$copyCurrent",
        )
        record.toSummary(true)
    }

    fun rename(
        context: Context,
        mode: ProfileMode,
        profileId: String,
        requestedName: String,
    ): Boolean = synchronized(lock) {
        val store = ensureMode(context, loadStore(context), mode)
        val index = store.profiles.indexOfFirst { it.mode == mode && it.profileId == profileId }
        if (index < 0) return false
        val current = store.profiles[index]
        val name = uniqueName(
            store.profiles.filter { it.mode == mode && it.profileId != profileId }.map { it.name },
            requestedName,
        )
        store.profiles[index] = current.copy(name = name, updatedTime = System.currentTimeMillis())
        persist(context, store)
        OperationLog.append(context, "重命名方案", "mode=${mode.wireName}; from=${current.name}; to=$name")
        true
    }

    fun delete(context: Context, mode: ProfileMode, profileId: String): Boolean = synchronized(lock) {
        val store = ensureMode(context, loadStore(context), mode)
        if (activeId(store, mode) == profileId) {
            OperationLog.append(context, "删除方案失败", "mode=${mode.wireName}; reason=active profile")
            return false
        }
        val removedName = store.profiles.firstOrNull { it.mode == mode && it.profileId == profileId }?.name
        val removed = store.profiles.removeAll { it.mode == mode && it.profileId == profileId }
        if (removed) {
            persist(context, store)
            OperationLog.append(context, "删除方案", "mode=${mode.wireName}; name=${removedName.orEmpty()}")
        }
        removed
    }

    fun loadLow(context: Context): LayoutConfig = synchronized(lock) {
        val store = ensureMode(context, loadStore(context), ProfileMode.LOW)
        currentRecord(store, ProfileMode.LOW).lowConfig ?: LayoutPrefs.defaults
    }

    fun saveLow(context: Context, config: LayoutConfig) = synchronized(lock) {
        val store = ensureMode(context, loadStore(context), ProfileMode.LOW)
        val id = activeId(store, ProfileMode.LOW)
        val index = store.profiles.indexOfFirst { it.profileId == id }
        if (index >= 0) {
            val record = store.profiles[index]
            store.profiles[index] = record.copy(
                lowConfig = config,
                updatedTime = System.currentTimeMillis(),
            )
            persist(context, store)
            OperationLog.append(
                context,
                "保存低风险布局",
                "profile=${record.name}; blocked=${config.blockedArea}; toggle=${config.toggleButton}; alpha=${config.blockedAreaAlpha}",
            )
        }
    }

    fun loadHigh(context: Context): MappingConfig = synchronized(lock) {
        val store = ensureMode(context, loadStore(context), ProfileMode.HIGH)
        currentRecord(store, ProfileMode.HIGH).highConfig ?: MappingConfig()
    }

    fun saveHigh(context: Context, config: MappingConfig) = synchronized(lock) {
        val store = ensureMode(context, loadStore(context), ProfileMode.HIGH)
        val id = activeId(store, ProfileMode.HIGH)
        val index = store.profiles.indexOfFirst { it.profileId == id }
        if (index >= 0) {
            val record = store.profiles[index]
            store.profiles[index] = record.copy(
                highConfig = config,
                updatedTime = System.currentTimeMillis(),
            )
            persist(context, store)
            OperationLog.append(
                context,
                "保存高风险布局",
                "profile=${record.name}; configured=${config.configured}; target=${config.targetBlock}; virtual=${config.virtualButton}",
            )
        }
    }

    fun loadLong(context: Context): LongPressConfig = synchronized(lock) {
        val store = ensureMode(context, loadStore(context), ProfileMode.LONG)
        currentRecord(store, ProfileMode.LONG).longConfig ?: LongPressConfig()
    }

    fun saveLong(context: Context, config: LongPressConfig) = synchronized(lock) {
        val store = ensureMode(context, loadStore(context), ProfileMode.LONG)
        val id = activeId(store, ProfileMode.LONG)
        val index = store.profiles.indexOfFirst { it.profileId == id }
        if (index >= 0) {
            val record = store.profiles[index]
            store.profiles[index] = record.copy(
                longConfig = config,
                updatedTime = System.currentTimeMillis(),
            )
            persist(context, store)
            OperationLog.append(
                context,
                "保存长按触发布局",
                "profile=${record.name}; area=${config.area}; alpha=${config.areaAlpha}; corner=${config.cornerRadius}; longPressMs=${config.longPressMs}",
            )
        }
    }

    private fun loadStore(context: Context): ProfileStore {
        val preferences = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = preferences.getString(STORE_JSON, null) ?: return ProfileStore()
        return try {
            val root = JSONObject(raw)
            val profiles = mutableListOf<ProfileRecord>()
            val array = root.optJSONArray("profiles") ?: JSONArray()
            for (index in 0 until array.length()) {
                parseProfile(array.optJSONObject(index))?.let(profiles::add)
            }
            ProfileStore(
                profiles = profiles,
                activeLowId = root.optString("activeLowId").takeIf { it.isNotBlank() },
                activeLongId = root.optString("activeLongId").takeIf { it.isNotBlank() },
                activeHighId = root.optString("activeHighId").takeIf { it.isNotBlank() },
            )
        } catch (_: Exception) {
            ProfileStore()
        }
    }

    private fun ensureMode(
        context: Context,
        store: ProfileStore,
        mode: ProfileMode,
    ): ProfileStore {
        val hasMode = store.profiles.any { it.mode == mode }
        if (!hasMode) {
            val now = System.currentTimeMillis()
            val record = ProfileRecord(
                profileId = UUID.randomUUID().toString(),
                name = DEFAULT_NAME,
                mode = mode,
                createdTime = now,
                updatedTime = now,
                lowConfig = if (mode == ProfileMode.LOW) LayoutPrefs.loadLegacy(context) else null,
                longConfig = if (mode == ProfileMode.LONG) LongPressConfig() else null,
                highConfig = if (mode == ProfileMode.HIGH) MappingPrefs.loadLegacy(context) else null,
            )
            store.profiles += record
            setActiveId(store, mode, record.profileId)
            persist(context, store)
            OperationLog.append(context, "创建默认方案", "mode=${mode.wireName}")
            return store
        }

        val active = activeId(store, mode)
        if (active == null || store.profiles.none { it.mode == mode && it.profileId == active }) {
            val first = store.profiles.first { it.mode == mode }
            setActiveId(store, mode, first.profileId)
            persist(context, store)
        }
        return store
    }

    private fun currentRecord(store: ProfileStore, mode: ProfileMode): ProfileRecord {
        val id = activeId(store, mode)
        return store.profiles.firstOrNull { it.mode == mode && it.profileId == id }
            ?: store.profiles.first { it.mode == mode }
    }

    private fun activeId(store: ProfileStore, mode: ProfileMode): String? = when (mode) {
        ProfileMode.LOW -> store.activeLowId
        ProfileMode.LONG -> store.activeLongId
        ProfileMode.HIGH -> store.activeHighId
    }

    private fun setActiveId(store: ProfileStore, mode: ProfileMode, id: String) {
        when (mode) {
            ProfileMode.LOW -> store.activeLowId = id
            ProfileMode.LONG -> store.activeLongId = id
            ProfileMode.HIGH -> store.activeHighId = id
        }
    }

    private fun persist(context: Context, store: ProfileStore) {
        val root = JSONObject().apply {
            put("activeLowId", store.activeLowId ?: JSONObject.NULL)
            put("activeLongId", store.activeLongId ?: JSONObject.NULL)
            put("activeHighId", store.activeHighId ?: JSONObject.NULL)
            put("profiles", JSONArray().apply {
                store.profiles.forEach { put(profileJson(it)) }
            })
        }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(STORE_JSON, root.toString())
            .apply()
    }

    private fun profileJson(profile: ProfileRecord): JSONObject = JSONObject().apply {
        put("profileId", profile.profileId)
        put("name", profile.name)
        put("mode", profile.mode.wireName)
        put("createdTime", profile.createdTime)
        put("updatedTime", profile.updatedTime)
        put("low", profile.lowConfig?.let(::lowJson) ?: JSONObject.NULL)
        put("long", profile.longConfig?.let(::longJson) ?: JSONObject.NULL)
        put("high", profile.highConfig?.let(::highJson) ?: JSONObject.NULL)
    }

    private fun parseProfile(json: JSONObject?): ProfileRecord? {
        if (json == null) return null
        val mode = when (json.optString("mode")) {
            ProfileMode.LOW.wireName -> ProfileMode.LOW
            ProfileMode.LONG.wireName -> ProfileMode.LONG
            ProfileMode.HIGH.wireName -> ProfileMode.HIGH
            else -> return null
        }
        val id = json.optString("profileId").takeIf { it.isNotBlank() } ?: return null
        val name = json.optString("name", DEFAULT_NAME).ifBlank { DEFAULT_NAME }
        val low = json.optJSONObject("low")?.let(::parseLow)
        val long = json.optJSONObject("long")?.let(::parseLong)
        val high = json.optJSONObject("high")?.let(::parseHigh)
        return ProfileRecord(
            profileId = id,
            name = name,
            mode = mode,
            createdTime = json.optLong("createdTime", 0L),
            updatedTime = json.optLong("updatedTime", 0L),
            lowConfig = if (mode == ProfileMode.LOW) low ?: LayoutPrefs.defaults else null,
            longConfig = if (mode == ProfileMode.LONG) long ?: LongPressConfig() else null,
            highConfig = if (mode == ProfileMode.HIGH) high ?: MappingConfig() else null,
        )
    }

    private fun lowJson(config: LayoutConfig): JSONObject = JSONObject().apply {
        put("blockedArea", componentJson(config.blockedArea))
        put("virtualButton", componentJson(config.virtualButton))
        put("virtualButtonAlpha", config.virtualButtonAlpha)
        put("blockedAreaAlpha", config.blockedAreaAlpha)
        put("blockedCornerRadius", config.blockedCornerRadius)
        put("toggleButton", componentJson(config.toggleButton))
        put("toggleAlpha", config.toggleAlpha)
        putOptionalString("screenshotUri", config.screenshotUri)
        put("screenshotWidth", config.screenshotWidth)
        put("screenshotHeight", config.screenshotHeight)
        put("coordinateSpaceVersion", config.coordinateSpaceVersion)
        put("coordinateRotation", config.coordinateRotation)
        put("triggerEnabled", config.triggerEnabled)
        put("triggerPackages", JSONArray(config.triggerPackages))
    }

    private fun parseLow(json: JSONObject): LayoutConfig {
        val defaults = LayoutPrefs.defaults
        return LayoutConfig(
            blockedArea = parseComponent(json.optJSONObject("blockedArea"), defaults.blockedArea),
            virtualButton = parseComponent(json.optJSONObject("virtualButton"), defaults.virtualButton),
            virtualButtonAlpha = json.float("virtualButtonAlpha", defaults.virtualButtonAlpha).coerceIn(0.25f, 1f),
            blockedAreaAlpha = json.float("blockedAreaAlpha", defaults.blockedAreaAlpha).coerceIn(0.05f, 1f),
            blockedCornerRadius = json.float("blockedCornerRadius", defaults.blockedCornerRadius).coerceIn(0f, 0.5f),
            toggleButton = parseComponent(json.optJSONObject("toggleButton"), defaults.toggleButton),
            toggleAlpha = json.float("toggleAlpha", defaults.toggleAlpha).coerceIn(0.2f, 1f),
            screenshotUri = json.optionalString("screenshotUri"),
            screenshotWidth = json.optInt("screenshotWidth", 0),
            screenshotHeight = json.optInt("screenshotHeight", 0),
            coordinateSpaceVersion = json.optInt(
                "coordinateSpaceVersion",
                OverlayGeometry.CURRENT_COORDINATE_SPACE_VERSION,
            ),
            coordinateRotation = json.optInt("coordinateRotation", Surface.ROTATION_90).coerceIn(0, 3),
            triggerEnabled = json.optBoolean("triggerEnabled", false),
            triggerPackages = parsePackages(json.optJSONArray("triggerPackages")),
        )
    }

    private fun longJson(config: LongPressConfig): JSONObject = JSONObject().apply {
        put("area", componentJson(config.area))
        put("areaAlpha", config.areaAlpha)
        put("cornerRadius", config.cornerRadius)
        put("longPressMs", config.longPressMs)
        putOptionalString("screenshotUri", config.screenshotUri)
        put("screenshotWidth", config.screenshotWidth)
        put("screenshotHeight", config.screenshotHeight)
        put("coordinateSpaceVersion", config.coordinateSpaceVersion)
        put("coordinateRotation", config.coordinateRotation)
        put("triggerEnabled", config.triggerEnabled)
        put("triggerPackages", JSONArray(config.triggerPackages))
    }

    private fun parseLong(json: JSONObject): LongPressConfig {
        val defaults = LongPressConfig()
        return LongPressConfig(
            area = parseComponent(json.optJSONObject("area"), defaults.area),
            areaAlpha = json.float("areaAlpha", defaults.areaAlpha).coerceIn(0.05f, 1f),
            cornerRadius = json.float("cornerRadius", defaults.cornerRadius).coerceIn(0f, 0.5f),
            longPressMs = json.optInt("longPressMs", defaults.longPressMs)
                .coerceIn(LONG_PRESS_MIN_MS, LONG_PRESS_MAX_MS),
            screenshotUri = json.optionalString("screenshotUri"),
            screenshotWidth = json.optInt("screenshotWidth", 0),
            screenshotHeight = json.optInt("screenshotHeight", 0),
            coordinateSpaceVersion = json.optInt(
                "coordinateSpaceVersion",
                OverlayGeometry.CURRENT_COORDINATE_SPACE_VERSION,
            ),
            coordinateRotation = json.optInt("coordinateRotation", Surface.ROTATION_90).coerceIn(0, 3),
            triggerEnabled = json.optBoolean("triggerEnabled", false),
            triggerPackages = parsePackages(json.optJSONArray("triggerPackages")),
        )
    }

    private fun highJson(config: MappingConfig): JSONObject = JSONObject().apply {
        put("targetBlock", componentJson(config.targetBlock))
        put("virtualButton", componentJson(config.virtualButton))
        put("virtualButtonAlpha", config.virtualButtonAlpha)
        put("virtualButtonCornerRadius", config.virtualButtonCornerRadius)
        put("targetCornerRadius", config.targetCornerRadius)
        put("targetBlockAlpha", config.targetBlockAlpha)
        putOptionalString("screenshotUri", config.screenshotUri)
        put("screenshotWidth", config.screenshotWidth)
        put("screenshotHeight", config.screenshotHeight)
        put("coordinateRotation", config.coordinateRotation)
        put("configured", config.configured)
        put("triggerEnabled", config.triggerEnabled)
        put("triggerPackages", JSONArray(config.triggerPackages))
    }

    private fun parseHigh(json: JSONObject): MappingConfig {
        val defaults = MappingConfig()
        val defaultBlock = defaults.targetBlock
        // 兼容旧配置：旧版只保存目标中心点（targetXRatio/targetYRatio），
        // 没有尺寸信息，用默认尺寸围绕该中心构造目标区域。
        val targetBlock = json.optJSONObject("targetBlock")?.let {
            parseComponent(it, defaultBlock)
        } ?: run {
            val centerX = json.float(
                "targetXRatio",
                defaultBlock.xRatio + defaultBlock.widthRatio / 2f,
            ).coerceIn(0f, 1f)
            val centerY = json.float(
                "targetYRatio",
                defaultBlock.yRatio + defaultBlock.heightRatio / 2f,
            ).coerceIn(0f, 1f)
            ComponentRatio(
                xRatio = (centerX - defaultBlock.widthRatio / 2f)
                    .coerceIn(0f, (1f - defaultBlock.widthRatio).coerceAtLeast(0f)),
                yRatio = (centerY - defaultBlock.heightRatio / 2f)
                    .coerceIn(0f, (1f - defaultBlock.heightRatio).coerceAtLeast(0f)),
                widthRatio = defaultBlock.widthRatio,
                heightRatio = defaultBlock.heightRatio,
            )
        }
        return MappingConfig(
            targetBlock = targetBlock,
            virtualButton = parseComponent(json.optJSONObject("virtualButton"), defaults.virtualButton),
            virtualButtonAlpha = json.float("virtualButtonAlpha", defaults.virtualButtonAlpha).coerceIn(0.25f, 1f),
            virtualButtonCornerRadius = json.float(
                "virtualButtonCornerRadius",
                defaults.virtualButtonCornerRadius,
            ).coerceIn(0f, 0.5f),
            targetCornerRadius = json.float(
                "targetCornerRadius",
                defaults.targetCornerRadius,
            ).coerceIn(0f, 0.5f),
            targetBlockAlpha = json.float("targetBlockAlpha", defaults.targetBlockAlpha)
                .coerceIn(0.05f, 1f),
            screenshotUri = json.optionalString("screenshotUri"),
            screenshotWidth = json.optInt("screenshotWidth", 0),
            screenshotHeight = json.optInt("screenshotHeight", 0),
            coordinateRotation = json.optInt("coordinateRotation", Surface.ROTATION_90).coerceIn(0, 3),
            configured = json.optBoolean("configured", false),
            triggerEnabled = json.optBoolean("triggerEnabled", false),
            triggerPackages = parsePackages(json.optJSONArray("triggerPackages")),
        )
    }

    private fun parsePackages(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val result = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isNotBlank() && value !in result) result += value
        }
        return result
    }

    private fun componentJson(component: ComponentRatio): JSONObject = JSONObject().apply {
        put("xRatio", component.xRatio)
        put("yRatio", component.yRatio)
        put("widthRatio", component.widthRatio)
        put("heightRatio", component.heightRatio)
    }

    private fun parseComponent(json: JSONObject?, fallback: ComponentRatio): ComponentRatio {
        if (json == null) return fallback
        val width = json.float("widthRatio", fallback.widthRatio)
            .coerceIn(OverlayGeometry.MIN_COMPONENT_RATIO, 1f)
        val height = json.float("heightRatio", fallback.heightRatio)
            .coerceIn(OverlayGeometry.MIN_COMPONENT_RATIO, 1f)
        return ComponentRatio(
            xRatio = json.float("xRatio", fallback.xRatio).coerceIn(0f, (1f - width).coerceAtLeast(0f)),
            yRatio = json.float("yRatio", fallback.yRatio).coerceIn(0f, (1f - height).coerceAtLeast(0f)),
            widthRatio = width,
            heightRatio = height,
        )
    }

    private fun uniqueName(existingNames: List<String>, requestedName: String): String {
        val base = requestedName.trim().ifBlank { DEFAULT_NAME }
        if (existingNames.none { it == base }) return base
        var index = 2
        while (existingNames.any { it == "$base $index" }) index++
        return "$base $index"
    }

    private fun JSONObject.putOptionalString(key: String, value: String?) {
        if (value.isNullOrBlank()) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private fun JSONObject.float(key: String, fallback: Float): Float =
        optDouble(key, fallback.toDouble()).toFloat()

    private data class ProfileRecord(
        val profileId: String,
        val name: String,
        val mode: ProfileMode,
        val createdTime: Long,
        val updatedTime: Long,
        val lowConfig: LayoutConfig?,
        val longConfig: LongPressConfig?,
        val highConfig: MappingConfig?,
    ) {
        fun toSummary(active: Boolean) = ProfileSummary(
            profileId = profileId,
            name = name,
            mode = mode,
            createdTime = createdTime,
            updatedTime = updatedTime,
            isActive = active,
        )
    }

    private data class ProfileStore(
        val profiles: MutableList<ProfileRecord> = mutableListOf(),
        var activeLowId: String? = null,
        var activeLongId: String? = null,
        var activeHighId: String? = null,
    )
}
