package com.anchor.launcher

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class DensityMode { QUIET, BALANCED, CONTROL }

data class Space(val id: String, val name: String, val apps: List<String>)
data class AppInfo(val label: String, val packageName: String)

class AnchorViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(application, AnchorDatabase::class.java, "anchor-db")
        .fallbackToDestructiveMigration()
        .build()
    private val dao = db.anchorDao()

    var currentSpaceIndex by mutableIntStateOf(0)
    var densityMode by mutableStateOf(DensityMode.BALANCED)
    var focusModeActive by mutableStateOf(false)
    var focusTimeRemaining by mutableIntStateOf(0)
    
    val spaces = listOf(
        Space("personal", "PERSONAL", listOf("com.android.chrome", "com.google.android.apps.maps")),
        Space("work", "WORK", listOf("com.google.android.gm", "com.google.android.calendar")),
        Space("evening", "EVENING", listOf("com.google.android.apps.photos", "com.spotify.music"))
    )

    val currentSpace get() = spaces[currentSpaceIndex]

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

    fun executeCommand(command: String, context: Context): Boolean {
        val parts = command.lowercase().trim().split(" ")
        if (parts.isEmpty()) return false
        
        return when (parts[0]) {
            "focus" -> {
                val minutes = parts.getOrNull(1)?.toIntOrNull() ?: 20
                startFocus(minutes)
                true
            }
            "space" -> {
                val spaceName = parts.getOrNull(1)
                val index = spaces.indexOfFirst { it.name.equals(spaceName, true) }
                if (index != -1) {
                    currentSpaceIndex = index
                    true
                } else false
            }
            "timer" -> {
                val minutes = parts.getOrNull(1)?.toIntOrNull() ?: 10
                val intent = Intent(android.provider.AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(android.provider.AlarmClock.EXTRA_LENGTH, minutes * 60)
                    putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
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
