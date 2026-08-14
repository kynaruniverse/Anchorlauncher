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

@Entity(tableName = "spaces")
data class SpaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val density: String
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
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

    @Query("SELECT * FROM spaces")
    fun getAllSpaces(): Flow<List<SpaceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpace(space: SpaceEntity)

    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetting(setting: SettingEntity)
}

@Database(entities = [Task::class, SpaceEntity::class, SettingEntity::class], version = 2)
abstract class AnchorDatabase : RoomDatabase() {
    abstract fun anchorDao(): AnchorDao
}
