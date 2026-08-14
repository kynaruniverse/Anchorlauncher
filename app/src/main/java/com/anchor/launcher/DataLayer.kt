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
    val density: String
)

@Entity(tableName = "app_friction")
data class AppFrictionEntity(
    @PrimaryKey val packageName: String,
    val level: String // OFF, LIGHT, INTENT, TIMER, BLOCK
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

    @Query("SELECT * FROM app_friction")
    suspend fun getAllFrictionLevels(): List<AppFrictionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setFrictionLevel(friction: AppFrictionEntity)
}

@Database(
    entities = [
        Task::class,
        SettingEntity::class,
        HiddenAppEntity::class,
        FavoriteAppEntity::class,
        SpaceEntity::class,
        AppFrictionEntity::class
    ],
    version = 7
)
abstract class AnchorDatabase : RoomDatabase() {
    abstract fun anchorDao(): AnchorDao
}
