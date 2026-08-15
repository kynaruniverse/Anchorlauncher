package com.anchor.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.compose.runtime.*
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            dao.getSetting("density_mode")?.let { try { densityMode = DensityMode.valueOf(it) } catch(e: Exception) {} }
            dao.getSetting("font_size")?.let { fontSizeMultiplier = it.toFloatOrNull() ?: 1.0f }
            dao.getSetting("letter_spacing")?.let { letterSpacingExtra = it.toFloatOrNull() ?: 0.0f }
            dao.getSetting("bold_enabled")?.let { isBoldEnabled = it.toBoolean() }
            dao.getSetting("swipe_down_action")?.let { swipeDownAction = it }
            dao.getSetting("double_tap_action")?.let { doubleTapAction = it }
            dao.getSetting("one_thing")?.let { oneThingReflection = it }

            hiddenApps.addAll(dao.getHiddenApps())
            favoriteApps.addAll(dao.getFavorites())

            dao.getAllFrictionLevels().forEach {
                appFrictionLevels[it.packageName] = it.level
            }

            val savedSpaces = dao.getAllSpacesOnce()
            if (savedSpaces.isEmpty()) {
                val defaults = listOf(
                    SpaceEntity("personal", "PERSONAL", "BALANCED"),
                    SpaceEntity("work", "WORK", "BALANCED")
                )
                defaults.forEach { dao.insertSpace(it) }
                spaces.addAll(defaults.map { Space(it.id, it.name, emptyList()) })
            } else {
                spaces.addAll(savedSpaces.map { Space(it.id, it.name, emptyList()) })
            }

            // Load apps asynchronously on IO thread
            loadInstalledApps(application)
            registerPackageChangeReceiver(application)
        }
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
            dao.insertSpace(SpaceEntity(id, name.uppercase(), "BALANCED"))
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

    fun updateSetting(key: String, value: String) {
        viewModelScope.launch {
            dao.saveSetting(SettingEntity(key, value))
        }
    }

    fun setDensity(mode: DensityMode) {
        densityMode = mode
        updateSetting("density_mode", mode.name)
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
        viewModelScope.launch {
            while (focusTimeRemaining > 0 && focusModeActive) {
                delay(1000)
                focusTimeRemaining--
            }
            focusModeActive = false
        }
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
}
