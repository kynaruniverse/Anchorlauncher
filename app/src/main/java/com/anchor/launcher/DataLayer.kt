package com.anchor.launcher

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,
    val isCompleted: Boolean = false,
    val spaceId: String = "personal"
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "hidden_apps")
data class HiddenAppEntity(
    @PrimaryKey val packageName: String
)

@Entity(tableName = "favorites")
data class FavoriteAppEntity(
    @PrimaryKey val packageName: String
)

@Entity(tableName = "spaces")
data class SpaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val density: String,
    // Comma-separated package names. Empty string means "unrestricted" (show every app),
    // which is the default and matches pre-existing behavior for spaces created before
    // this column existed. Stored as a flat string rather than a Room TypeConverter/List
    // to keep this simple for a single-column, order-independent set of package names.
    val allowedApps: String = ""
)

@Entity(tableName = "app_friction")
data class AppFrictionEntity(
    @PrimaryKey val packageName: String,
    val level: String // OFF, LIGHT, INTENT, TIMER, BLOCK, SCHEDULE
)

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val focusMinutes: Int,
    val spaceId: String = ""
)

@Dao
interface AnchorDao {
    @Query("SELECT * FROM tasks WHERE spaceId = :spaceId")
    fun getTasksForSpace(spaceId: String): Flow<List<Task>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetting(setting: SettingEntity)

    @Query("SELECT packageName FROM hidden_apps")
    suspend fun getHiddenApps(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hideApp(app: HiddenAppEntity)

    @Query("DELETE FROM hidden_apps WHERE packageName = :packageName")
    suspend fun unhideApp(packageName: String)

    @Query("SELECT packageName FROM favorites")
    suspend fun getFavorites(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(app: FavoriteAppEntity)

    @Query("DELETE FROM favorites WHERE packageName = :packageName")
    suspend fun removeFavorite(packageName: String)

    @Query("SELECT * FROM spaces")
    suspend fun getAllSpacesOnce(): List<SpaceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpace(space: SpaceEntity)

    @Query("DELETE FROM spaces WHERE id = :spaceId")
    suspend fun deleteSpace(spaceId: String)

    @Query("UPDATE spaces SET allowedApps = :allowedApps WHERE id = :spaceId")
    suspend fun updateSpaceAllowedApps(spaceId: String, allowedApps: String)

    @Query("SELECT * FROM app_friction")
    suspend fun getAllFrictionLevels(): List<AppFrictionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setFrictionLevel(friction: AppFrictionEntity)

    @Query("SELECT * FROM presets")
    suspend fun getAllPresetsOnce(): List<PresetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: PresetEntity)

    @Query("DELETE FROM presets WHERE id = :presetId")
    suspend fun deletePreset(presetId: String)

    // --- Backup/export support ---

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksOnce(): List<Task>

    @Query("SELECT * FROM settings")
    suspend fun getAllSettingsOnce(): List<SettingEntity>

    @Query("DELETE FROM tasks")
    suspend fun clearTasks()

    @Query("DELETE FROM hidden_apps")
    suspend fun clearHiddenApps()

    @Query("DELETE FROM favorites")
    suspend fun clearFavorites()

    @Query("DELETE FROM spaces")
    suspend fun clearSpaces()

    @Query("DELETE FROM app_friction")
    suspend fun clearFriction()

    @Query("DELETE FROM presets")
    suspend fun clearPresets()

    /**
     * Wipes and replaces every table with the contents of an imported backup, as a single
     * transaction (Room supports default-implemented @Dao methods that call the interface's
     * other @Insert/@Delete-annotated methods and wraps them atomically). Settings are
     * upserted rather than wiped first, so a partial/older backup doesn't blow away keys it
     * simply doesn't mention.
     */
    @Transaction
    suspend fun replaceAllData(
        tasks: List<Task>,
        hiddenApps: List<HiddenAppEntity>,
        favorites: List<FavoriteAppEntity>,
        spaces: List<SpaceEntity>,
        friction: List<AppFrictionEntity>,
        presets: List<PresetEntity>,
        settings: List<SettingEntity>
    ) {
        clearTasks()
        tasks.forEach { insertTask(it) }
        clearHiddenApps()
        hiddenApps.forEach { hideApp(it) }
        clearFavorites()
        favorites.forEach { addFavorite(it) }
        clearSpaces()
        spaces.forEach { insertSpace(it) }
        clearFriction()
        friction.forEach { setFrictionLevel(it) }
        clearPresets()
        presets.forEach { insertPreset(it) }
        settings.forEach { saveSetting(it) }
    }
}

// Schema version reset to 1 previously (pre-release, no installed base to protect); bumped
// to 2 to add SpaceEntity.allowedApps, and to 3 here to add the presets table. Still
// pre-release, so fallbackToDestructiveMigration() remains acceptable -- replace it with
// real Migration objects before shipping, since from that point on every schema bump would
// otherwise silently wipe tasks/favorites/hidden apps/friction rules/spaces/presets.
@Database(
    entities = [
        Task::class,
        SettingEntity::class,
        HiddenAppEntity::class,
        FavoriteAppEntity::class,
        SpaceEntity::class,
        AppFrictionEntity::class,
        PresetEntity::class
    ],
    version = 3
)
abstract class AnchorDatabase : RoomDatabase() {
    abstract fun anchorDao(): AnchorDao
}
