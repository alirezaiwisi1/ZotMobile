package com.zot.mobile.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,          // directory name
    val name: String,
    val remoteUrl: String?,              // GitHub URL or null
    val createdAt: Long,
    val lastOpenedAt: Long,
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY lastOpenedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: String): ProjectEntity?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)
}

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String,
    val role: String,       // user | assistant | system
    val content: String,
    val timestamp: Long,
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE projectId = :projectId ORDER BY timestamp ASC")
    fun observeForProject(projectId: String): Flow<List<ChatMessageEntity>>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages WHERE projectId = :projectId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recentForProject(projectId: String, limit: Int): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages WHERE projectId = :projectId")
    suspend fun clearForProject(projectId: String)
}

@Entity(tableName = "command_log")
data class CommandLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String?,
    val command: String,
    val exitCode: Int,
    val output: String,
    val durationMs: Long,
    val timestamp: Long,
)

@Dao
interface CommandLogDao {
    @Query("SELECT * FROM command_log ORDER BY timestamp DESC LIMIT 200")
    fun observeRecent(): Flow<List<CommandLogEntity>>

    @Insert
    suspend fun insert(entry: CommandLogEntity)

    @Query("DELETE FROM command_log")
    suspend fun clear()
}

@Database(
    entities = [ProjectEntity::class, ChatMessageEntity::class, CommandLogEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ZotDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun chatDao(): ChatDao
    abstract fun commandLogDao(): CommandLogDao

    companion object {
        fun build(context: Context): ZotDatabase =
            Room.databaseBuilder(context, ZotDatabase::class.java, "zot.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
