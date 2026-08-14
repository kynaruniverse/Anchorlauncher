package com.anchor.launcher

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    var screenTimeMinutes by mutableIntStateOf(88)

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
        }
    }

    fun setFrictionLevel(packageName: String, level: String) {
        appFrictionLevels[packageName] = level
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

    fun getTasks() = dao.getTasksForSpace(currentSpace.id)

    fun addTask(text: String) {
        viewModelScope.launch { dao.insertTask(Task(text = text, spaceId = currentSpace.id)) }
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
        if (recentAppPackages.size > 5) {
            recentAppPackages.removeAt(5)
        }

        val friction = appFrictionLevels[app.packageName] ?: "OFF"
        
        when (friction) {
            "OFF" -> launchApp(app.packageName, context)
            "LIGHT", "INTENT", "TIMER" -> {
                pendingAppLaunch = app
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

    fun launchApp(packageName: String, context: Context) {
        pendingAppLaunch = null
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            context.startActivity(intent)
        }
    }

    fun executeCommand(command: String, context: Context): Boolean {
        val parts = command.lowercase().trim().split(" ", limit = 2)
        if (parts.isEmpty()) return false
        
        val action = parts[0]
        val arg = parts.getOrNull(1) ?: ""

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
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$arg")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }
            "message" -> {
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

    fun getInstalledApps(context: Context): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        return context.packageManager.queryIntentActivities(intent, 0).map {
            AppInfo(it.loadLabel(context.packageManager).toString(), it.activityInfo.packageName)
        }.sortedBy { it.label }
    }
}
