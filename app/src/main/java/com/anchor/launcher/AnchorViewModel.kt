package com.anchor.launcher

import android.app.Application
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.view.View
import androidx.compose.runtime.*
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AnchorViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(application, AnchorDatabase::class.java, "anchor-db")
        .fallbackToDestructiveMigration()
        .build()
    private val dao = db.anchorDao()

    var currentSpaceIndex by mutableIntStateOf(0)
    var densityMode by mutableStateOf(DensityMode.BALANCED)
    var focusModeActive by mutableStateOf(false)
    var focusTimeRemaining by mutableIntStateOf(0)

    var pendingAppLaunch by mutableStateOf<AppInfo?>(null)
    var selectedAppForMenu by mutableStateOf<AppInfo?>(null)

    var hiddenApps = mutableStateListOf<String>()
    var favoriteApps = mutableStateListOf<String>()
    var recentAppPackages = mutableStateListOf<String>()

    // Async loaded apps
    var installedApps = mutableStateListOf<AppInfo>()
        private set

    // Whether the initial app-list load has completed at least once. Lets the UI show a
    // loading state instead of a blank drawer/pager on first cold-start frame.
    var appsLoaded by mutableStateOf(false)
        private set

    var fontSizeMultiplier by mutableFloatStateOf(1.0f)
    var letterSpacingExtra by mutableFloatStateOf(0.0f)
    var isBoldEnabled by mutableStateOf(false)

    var swipeDownAction by mutableStateOf("NOTIFICATIONS")
    var doubleTapAction by mutableStateOf("NONE")

    // Haptics on/off. Previously ~9 raw view.performHapticFeedback(...) call sites across
    // MainActivity/Widgets had no way to be disabled -- see the View.hapticFeedback()
    // extension below, which every one of them now goes through.
    var hapticsEnabled by mutableStateOf(true)

    // Curated base/accent color ids from AnchorPalette (see ColorPalette.kt) -- picked in
    // Settings > Colors. Both draw from the same 15-color muted palette; base drives the
    // background/text scheme (see anchorColorScheme() in Theme.kt), accent stays reserved
    // for the three "active state" moments described there.
    var baseColorId by mutableStateOf("black_olive")
    var accentColorId by mutableStateOf("sage")

    // Whether starting a Focus session should also enable Android's Do Not Disturb.
    // Requires a separate, non-runtime-dialog permission grant -- see hasDndAccess().
    var dndOnFocusEnabled by mutableStateOf(false)

    // Global schedule window during which SCHEDULE-friction apps are gated (e.g. 22-7 for
    // "bedtime"). One shared window rather than a per-app schedule matrix, matching the
    // app's preference for one simple rule over a complex rules engine.
    var scheduleEnabled by mutableStateOf(false)
    var scheduleStartHour by mutableIntStateOf(22)
    var scheduleEndHour by mutableIntStateOf(7)

    // Named shortcuts bundling a Space switch + a Focus duration (see Preset in Models.kt).
    var presets = mutableStateListOf<Preset>()

    // Dynamic Spaces
    var spaces = mutableStateListOf<Space>()

    // Friction Levels & Reflection
    var appFrictionLevels = mutableStateMapOf<String, String>()
    var oneThingReflection by mutableStateOf("")
    var screenTimeMinutes by mutableIntStateOf(0)

    // Package -> epoch millis until which a TIMER-friction app can be relaunched without
    // going through IntentGate again. Populated when the user picks a duration in the gate.
    private val timedUnlocks = mutableMapOf<String, Long>()

    private var packageChangeReceiver: BroadcastReceiver? = null

    val currentSpace get() = spaces.getOrElse(currentSpaceIndex) {
        if (spaces.isNotEmpty()) spaces[0] else Space("default", "HOME", emptyList())
    }

    init {
        viewModelScope.launch {
            reloadPersistedState()
            // Load apps asynchronously on IO thread
            loadInstalledApps(application)
            registerPackageChangeReceiver(application)
        }
    }

    /**
     * Loads all persisted state from the database into the in-memory/Compose-observable
     * properties above. Extracted out of init{} so importBackupJson() can call the exact
     * same logic to refresh the UI after a restore, instead of duplicating it.
     */
    private suspend fun reloadPersistedState() {
        dao.getSetting("density_mode")?.let { try { densityMode = DensityMode.valueOf(it) } catch (e: Exception) {} }
        dao.getSetting("font_size")?.let { fontSizeMultiplier = it.toFloatOrNull() ?: 1.0f }
        dao.getSetting("letter_spacing")?.let { letterSpacingExtra = it.toFloatOrNull() ?: 0.0f }
        dao.getSetting("bold_enabled")?.let { isBoldEnabled = it.toBoolean() }
        dao.getSetting("swipe_down_action")?.let { swipeDownAction = it }
        dao.getSetting("double_tap_action")?.let { doubleTapAction = it }
        dao.getSetting("one_thing")?.let { oneThingReflection = it }
        dao.getSetting("haptics_enabled")?.let { hapticsEnabled = it.toBoolean() }
        dao.getSetting("base_color_id")?.let { baseColorId = it }
        dao.getSetting("accent_color_id")?.let { accentColorId = it }
        dao.getSetting("dnd_on_focus")?.let { dndOnFocusEnabled = it.toBoolean() }
        dao.getSetting("schedule_enabled")?.let { scheduleEnabled = it.toBoolean() }
        dao.getSetting("schedule_start_hour")?.let { scheduleStartHour = it.toIntOrNull() ?: 22 }
        dao.getSetting("schedule_end_hour")?.let { scheduleEndHour = it.toIntOrNull() ?: 7 }

        hiddenApps.clear()
        hiddenApps.addAll(dao.getHiddenApps())
        favoriteApps.clear()
        favoriteApps.addAll(dao.getFavorites())

        appFrictionLevels.clear()
        dao.getAllFrictionLevels().forEach { appFrictionLevels[it.packageName] = it.level }

        presets.clear()
        presets.addAll(dao.getAllPresetsOnce().map { Preset(it.id, it.name, it.focusMinutes, it.spaceId) })

        val savedSpaces = dao.getAllSpacesOnce()
        spaces.clear()
        if (savedSpaces.isEmpty()) {
            val defaults = listOf(
                SpaceEntity("personal", "PERSONAL", "BALANCED"),
                SpaceEntity("work", "WORK", "BALANCED")
            )
            defaults.forEach { dao.insertSpace(it) }
            spaces.addAll(defaults.map { Space(it.id, it.name, emptyList()) })
        } else {
            spaces.addAll(savedSpaces.map { Space(it.id, it.name, it.allowedApps.split(",").filter { pkg -> pkg.isNotBlank() }) })
        }
        if (currentSpaceIndex >= spaces.size) currentSpaceIndex = 0
    }

    private suspend fun loadInstalledApps(context: Context) {
        val apps = withContext(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            context.packageManager.queryIntentActivities(intent, 0).map { resolveInfo ->
                val icon = try {
                    resolveInfo.loadIcon(context.packageManager).toBitmap(width = 128, height = 128)
                } catch (e: Exception) {
                    null
                }
                AppInfo(
                    label = resolveInfo.loadLabel(context.packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    icon = icon
                )
            }.sortedBy { it.label }
        }
        installedApps.clear()
        installedApps.addAll(apps)
        appsLoaded = true
    }

    /**
     * A launcher's app list goes stale the moment the user installs/uninstalls anything
     * while Anchor is backgrounded (there was previously no mechanism to refresh it at all).
     * This keeps the drawer in sync without requiring a process restart.
     */
    private fun registerPackageChangeReceiver(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                viewModelScope.launch { loadInstalledApps(context) }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        packageChangeReceiver = receiver
    }

    override fun onCleared() {
        super.onCleared()
        packageChangeReceiver?.let {
            try { getApplication<Application>().unregisterReceiver(it) } catch (e: Exception) {}
        }
    }

    fun setFrictionLevel(packageName: String, level: String) {
        appFrictionLevels[packageName] = level
        timedUnlocks.remove(packageName)
        viewModelScope.launch {
            dao.setFrictionLevel(AppFrictionEntity(packageName, level))
        }
    }

    fun setOneThing(text: String) {
        oneThingReflection = text
        updateSetting("one_thing", text)
    }

    fun addSpace(name: String) {
        val id = UUID.randomUUID().toString()
        val newSpace = Space(id, name.uppercase(), emptyList())
        spaces.add(newSpace)
        viewModelScope.launch {
            dao.insertSpace(SpaceEntity(id, name.uppercase(), "BALANCED", ""))
        }
    }

    /**
     * Restricts (or un-restricts, if [packages] is empty) which apps appear in the drawer
     * while this space is active. Previously Space.allowedApps existed as a field but was
     * never populated or read anywhere -- the App Drawer showed every installed app
     * regardless of which Space was current.
     */
    fun setSpaceAllowedApps(spaceId: String, packages: List<String>) {
        val index = spaces.indexOfFirst { it.id == spaceId }
        if (index != -1) {
            spaces[index] = spaces[index].copy(allowedApps = packages)
        }
        viewModelScope.launch {
            dao.updateSpaceAllowedApps(spaceId, packages.joinToString(","))
        }
    }

    fun deleteSpace(spaceId: String) {
        if (spaces.size <= 1) return
        val index = spaces.indexOfFirst { it.id == spaceId }
        if (index != -1) {
            spaces.removeAt(index)
            if (currentSpaceIndex >= spaces.size) currentSpaceIndex = spaces.size - 1
            viewModelScope.launch {
                dao.deleteSpace(spaceId)
            }
        }
    }

    fun addPreset(name: String, focusMinutes: Int, spaceId: String) {
        val id = UUID.randomUUID().toString()
        presets.add(Preset(id, name, focusMinutes, spaceId))
        viewModelScope.launch {
            dao.insertPreset(PresetEntity(id, name, focusMinutes, spaceId))
        }
    }

    fun deletePreset(presetId: String) {
        presets.removeAll { it.id == presetId }
        viewModelScope.launch {
            dao.deletePreset(presetId)
        }
    }

    /** Switches to the preset's Space (if it names one) and starts its Focus duration --
     * one tap combining two things that already existed separately. */
    fun activatePreset(preset: Preset) {
        if (preset.spaceId.isNotBlank()) {
            val index = spaces.indexOfFirst { it.id == preset.spaceId }
            if (index != -1) currentSpaceIndex = index
        }
        startFocus(preset.focusMinutes)
    }

    fun updateSetting(key: String, value: String) {
        viewModelScope.launch {
            dao.saveSetting(SettingEntity(key, value))
        }
    }

    fun setDensity(mode: DensityMode) {
        densityMode = mode
        updateSetting("density_mode", mode.name)
    }

    fun setSwipeDownAction(action: String) {
        swipeDownAction = action
        updateSetting("swipe_down_action", action)
    }

    fun setDoubleTapAction(action: String) {
        doubleTapAction = action
        updateSetting("double_tap_action", action)
    }

    fun setHapticsEnabled(enabled: Boolean) {
        hapticsEnabled = enabled
        updateSetting("haptics_enabled", enabled.toString())
    }

    fun setBaseColor(colorId: String) {
        baseColorId = colorId
        updateSetting("base_color_id", colorId)
    }

    fun setAccentColor(colorId: String) {
        accentColorId = colorId
        updateSetting("accent_color_id", colorId)
    }

    fun setDndOnFocus(enabled: Boolean) {
        dndOnFocusEnabled = enabled
        updateSetting("dnd_on_focus", enabled.toString())
    }

    fun setScheduleEnabled(enabled: Boolean) {
        scheduleEnabled = enabled
        updateSetting("schedule_enabled", enabled.toString())
    }

    fun setScheduleStartHour(hour: Int) {
        scheduleStartHour = hour
        updateSetting("schedule_start_hour", hour.toString())
    }

    fun setScheduleEndHour(hour: Int) {
        scheduleEndHour = hour
        updateSetting("schedule_end_hour", hour.toString())
    }

    /** Whether the current hour falls inside the configured schedule window. Handles the
     * window wrapping past midnight (e.g. start=22, end=7 means "10pm through 7am"). */
    private fun isWithinScheduleWindow(): Boolean {
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return if (scheduleStartHour <= scheduleEndHour) {
            currentHour in scheduleStartHour until scheduleEndHour
        } else {
            currentHour >= scheduleStartHour || currentHour < scheduleEndHour
        }
    }

    /** Whether the user has granted the separate "Do Not Disturb access" app-op. This is
     * NOT a normal runtime permission dialog -- it can only be granted from a system
     * Settings screen (see Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS). */
    fun hasDndAccess(): Boolean {
        val nm = getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    private fun setDnd(enabled: Boolean) {
        val nm = getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // No-ops safely if access was never granted, rather than crashing -- matches how
        // dndOnFocusEnabled can be "on" in settings while the actual OS-level grant is
        // still pending.
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }

    fun toggleHideApp(packageName: String) {
        viewModelScope.launch {
            if (hiddenApps.contains(packageName)) {
                hiddenApps.remove(packageName)
                dao.unhideApp(packageName)
            } else {
                hiddenApps.add(packageName)
                dao.hideApp(HiddenAppEntity(packageName))
            }
        }
        selectedAppForMenu = null
    }

    fun toggleFavorite(packageName: String) {
        viewModelScope.launch {
            if (favoriteApps.contains(packageName)) {
                favoriteApps.remove(packageName)
                dao.removeFavorite(packageName)
            } else {
                favoriteApps.add(packageName)
                dao.addFavorite(FavoriteAppEntity(packageName))
            }
        }
        selectedAppForMenu = null
    }

    fun uninstallApp(packageName: String, context: Context) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
        }
        context.startActivity(intent)
        selectedAppForMenu = null
    }

    fun getTasks(spaceId: String) = dao.getTasksForSpace(spaceId)

    fun addTask(text: String, spaceId: String) {
        viewModelScope.launch { dao.insertTask(Task(text = text, spaceId = spaceId)) }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch { dao.updateTask(task.copy(isCompleted = !task.isCompleted)) }
    }

    fun startFocus(minutes: Int) {
        focusModeActive = true
        focusTimeRemaining = minutes * 60
        if (dndOnFocusEnabled) setDnd(true)
        viewModelScope.launch {
            while (focusTimeRemaining > 0 && focusModeActive) {
                delay(1000)
                focusTimeRemaining--
            }
            focusModeActive = false
            if (dndOnFocusEnabled) setDnd(false)
        }
    }

    /** Manually ends an in-progress Focus session (as opposed to the countdown finishing
     * naturally). Previously the "END SESSION" button set focusModeActive = false directly
     * from the widget, which skipped turning DND back off. */
    fun endFocus() {
        focusModeActive = false
        if (dndOnFocusEnabled) setDnd(false)
    }

    fun handleAppClick(app: AppInfo, context: Context) {
        recentAppPackages.remove(app.packageName)
        recentAppPackages.add(0, app.packageName)
        if (recentAppPackages.size > AnchorDefaults.MAX_RECENT_APPS) {
            recentAppPackages.removeAt(AnchorDefaults.MAX_RECENT_APPS)
        }

        val friction = appFrictionLevels[app.packageName] ?: "OFF"

        when (friction) {
            "OFF" -> launchApp(app.packageName, context)
            "LIGHT", "INTENT" -> {
                pendingAppLaunch = app
            }
            "TIMER" -> {
                val unlockUntil = timedUnlocks[app.packageName]
                if (unlockUntil != null && System.currentTimeMillis() < unlockUntil) {
                    // Still inside a previously granted timed window -- skip the gate.
                    launchApp(app.packageName, context)
                } else {
                    timedUnlocks.remove(app.packageName)
                    pendingAppLaunch = app
                }
            }
            "BLOCK" -> {
                if (focusModeActive) {
                    pendingAppLaunch = app
                } else {
                    launchApp(app.packageName, context)
                }
            }
            "SCHEDULE" -> {
                if (scheduleEnabled && isWithinScheduleWindow()) {
                    pendingAppLaunch = app
                } else {
                    launchApp(app.packageName, context)
                }
            }
            else -> launchApp(app.packageName, context)
        }
    }

    /**
     * @param timerMinutes when set, grants a timed unlock window for this package (used by
     * the TIMER friction level) so the user isn't re-gated on every open for the duration
     * they explicitly chose. Null means "just this once."
     */
    fun launchApp(packageName: String, context: Context, timerMinutes: Int? = null) {
        pendingAppLaunch = null
        if (timerMinutes != null && timerMinutes > 0) {
            timedUnlocks[packageName] = System.currentTimeMillis() + timerMinutes * 60_000L
        }
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            context.startActivity(intent)
        }
    }

    fun executeCommand(command: String, context: Context): Boolean {
        val parts = command.lowercase().trim().split(" ", limit = 2)
        if (parts.isEmpty()) return false

        val action = parts[0]
        val arg = parts.getOrNull(1)?.trim() ?: ""

        return when (action) {
            "focus" -> {
                val minutes = arg.toIntOrNull() ?: 20
                startFocus(minutes)
                true
            }
            "space" -> {
                val index = spaces.indexOfFirst { it.name.equals(arg, true) }
                if (index != -1) {
                    currentSpaceIndex = index
                    true
                } else false
            }
            "timer" -> {
                val minutes = arg.toIntOrNull() ?: 10
                val intent = Intent(android.provider.AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(android.provider.AlarmClock.EXTRA_LENGTH, minutes * 60)
                    putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }
            "call" -> {
                if (arg.isBlank()) return false
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$arg")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }
            "message" -> {
                if (arg.isBlank()) return false
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$arg")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }
            else -> false
        }
    }

    /** Serializes all local data (settings, hidden apps, favorites, spaces, friction rules,
     * tasks) to a JSON string the user can save via Storage Access Framework. Plain
     * org.json (already used elsewhere, e.g. WeatherWidget) rather than adding a
     * serialization dependency for this one feature. */
    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val json = JSONObject()
        json.put("version", 1)

        val settingsObj = JSONObject()
        dao.getAllSettingsOnce().forEach { settingsObj.put(it.key, it.value) }
        json.put("settings", settingsObj)

        json.put("hiddenApps", JSONArray(hiddenApps.toList()))
        json.put("favorites", JSONArray(favoriteApps.toList()))

        val spacesArr = JSONArray()
        spaces.forEach { s ->
            spacesArr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("allowedApps", s.allowedApps.joinToString(","))
            })
        }
        json.put("spaces", spacesArr)

        val frictionArr = JSONArray()
        appFrictionLevels.forEach { (pkg, level) ->
            frictionArr.put(JSONObject().apply { put("packageName", pkg); put("level", level) })
        }
        json.put("friction", frictionArr)

        val presetsArr = JSONArray()
        presets.forEach { p ->
            presetsArr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("focusMinutes", p.focusMinutes)
                put("spaceId", p.spaceId)
            })
        }
        json.put("presets", presetsArr)

        val tasksArr = JSONArray()
        dao.getAllTasksOnce().forEach { t ->
            tasksArr.put(JSONObject().apply {
                put("text", t.text)
                put("isCompleted", t.isCompleted)
                put("spaceId", t.spaceId)
            })
        }
        json.put("tasks", tasksArr)

        json.toString(2)
    }

    /**
     * Replaces ALL local data with the contents of a previously exported backup, then
     * reloads in-memory state so the UI reflects it immediately. Task ids are dropped and
     * re-autogenerated on import to avoid colliding with any existing rows. Throws if the
     * JSON is malformed -- callers should catch and show the user an error rather than
     * silently losing their current data partway through.
     */
    suspend fun importBackupJson(jsonStr: String) {
        val json = JSONObject(jsonStr)

        val tasks = mutableListOf<Task>()
        json.optJSONArray("tasks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                tasks.add(Task(text = o.getString("text"), isCompleted = o.optBoolean("isCompleted", false), spaceId = o.optString("spaceId", "personal")))
            }
        }
        val hidden = mutableListOf<HiddenAppEntity>()
        json.optJSONArray("hiddenApps")?.let { arr -> for (i in 0 until arr.length()) hidden.add(HiddenAppEntity(arr.getString(i))) }
        val favs = mutableListOf<FavoriteAppEntity>()
        json.optJSONArray("favorites")?.let { arr -> for (i in 0 until arr.length()) favs.add(FavoriteAppEntity(arr.getString(i))) }
        val spacesList = mutableListOf<SpaceEntity>()
        json.optJSONArray("spaces")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                spacesList.add(SpaceEntity(o.getString("id"), o.getString("name"), "BALANCED", o.optString("allowedApps", "")))
            }
        }
        val frictionList = mutableListOf<AppFrictionEntity>()
        json.optJSONArray("friction")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                frictionList.add(AppFrictionEntity(o.getString("packageName"), o.getString("level")))
            }
        }
        val presetsList = mutableListOf<PresetEntity>()
        json.optJSONArray("presets")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                presetsList.add(PresetEntity(o.getString("id"), o.getString("name"), o.getInt("focusMinutes"), o.optString("spaceId", "")))
            }
        }
        val settingsList = mutableListOf<SettingEntity>()
        json.optJSONObject("settings")?.let { obj ->
            obj.keys().asSequence().forEach { key -> settingsList.add(SettingEntity(key, obj.getString(key))) }
        }

        withContext(Dispatchers.IO) {
            dao.replaceAllData(tasks, hidden, favs, spacesList, frictionList, presetsList, settingsList)
        }

        reloadPersistedState()
    }
}

/** Fires haptic feedback only if the user has haptics enabled (Settings > Haptics).
 * Centralizes what were previously ~9 raw view.performHapticFeedback(...) call sites
 * across MainActivity/Widgets with no way to disable them. */
fun View.hapticFeedback(type: Int, viewModel: AnchorViewModel) {
    if (viewModel.hapticsEnabled) performHapticFeedback(type)
}
