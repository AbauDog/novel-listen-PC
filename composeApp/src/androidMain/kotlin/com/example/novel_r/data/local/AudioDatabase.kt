package com.example.novel_r.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// 音訊檔案 Entity - 儲存檔案基本資訊
@Entity(tableName = "audio_files")
data class AudioFile(
    @PrimaryKey
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val duration: Long,
    val mimeType: String?,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val isValid: Boolean = true,  // 標記檔案是否仍存在
    
    // YouTube 串流擴充欄位
    val isStream: Boolean = false,
    val streamUrl: String? = null, // 快取的串流 URL (可能有時效性)
    val originalUrl: String? = null, // 原始 YouTube URL
    val thumbnailUrl: String? = null
)

// 播放進度 Entity - 儲存播放進度
@Entity(tableName = "audio_progress")
data class AudioProgress(
    @PrimaryKey
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val lastPosition: Long,  // 毫秒級播放位置
    val duration: Long,
    val lastPlayedTimestamp: Long = System.currentTimeMillis(),
    val playCount: Int = 0  // 播放次數
)

// 播放清單書籤 Entity
@Entity(tableName = "playlist_bookmarks")
data class PlaylistBookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String, // 播放清單 URL (Unique)
    val name: String,
    val thumbnailUrl: String?,
    val trackCount: Int,
    val addedTimestamp: Long = System.currentTimeMillis()
)

// 音訊檔案 DAO
@Dao
interface AudioFileDao {
    @Query("SELECT * FROM audio_files WHERE isValid = 1 ORDER BY filePath ASC")
    fun getAllFiles(): Flow<List<AudioFile>>

    @Query("SELECT * FROM audio_files WHERE filePath = :path")
    suspend fun getFile(path: String): AudioFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: AudioFile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<AudioFile>)

    @Update
    suspend fun updateFile(file: AudioFile)

    @Delete
    suspend fun deleteFile(file: AudioFile)

    @Query("DELETE FROM audio_files WHERE filePath = :path")
    suspend fun deleteFileByPath(path: String)

    @Query("UPDATE audio_files SET isValid = 0 WHERE filePath = :path")
    suspend fun markFileAsInvalid(path: String)

    @Query("DELETE FROM audio_files WHERE isValid = 0")
    suspend fun deleteInvalidFiles()

    @Query("DELETE FROM audio_files")
    suspend fun deleteAll()
}

// 播放進度 DAO
@Dao
interface AudioProgressDao {
    @Query("SELECT * FROM audio_progress WHERE filePath = :path")
    suspend fun getProgress(path: String): AudioProgress?

    @Query("SELECT * FROM audio_progress ORDER BY lastPlayedTimestamp DESC")
    fun getAllProgress(): Flow<List<AudioProgress>>

    @Query("SELECT * FROM audio_progress ORDER BY lastPlayedTimestamp DESC LIMIT 1")
    suspend fun getLastPlayed(): AudioProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: AudioProgress)

    @Update
    suspend fun updateProgress(progress: AudioProgress)

    @Delete
    suspend fun deleteProgress(progress: AudioProgress)

    @Query("DELETE FROM audio_progress WHERE filePath = :path")
    suspend fun deleteProgressByPath(path: String)

    @Query("DELETE FROM audio_progress WHERE filePath NOT IN (SELECT filePath FROM audio_files WHERE isValid = 1)")
    suspend fun cleanupOrphanedProgress()

    @Query("DELETE FROM audio_progress")
    suspend fun deleteAll()
}

// 播放清單書籤 DAO
@Dao
interface PlaylistBookmarkDao {
    @Query("SELECT * FROM playlist_bookmarks ORDER BY addedTimestamp DESC")
    fun getAllBookmarks(): Flow<List<PlaylistBookmark>>

    @Query("SELECT * FROM playlist_bookmarks WHERE url = :url")
    suspend fun getBookmarkByUrl(url: String): PlaylistBookmark?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: PlaylistBookmark)

    @Delete
    suspend fun deleteBookmark(bookmark: PlaylistBookmark)
}

@Database(
    entities = [AudioFile::class, AudioProgress::class, PlaylistBookmark::class],
    version = 2, // Increment version
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun audioFileDao(): AudioFileDao
    abstract fun audioProgressDao(): AudioProgressDao
    abstract fun playlistBookmarkDao(): PlaylistBookmarkDao
    
    companion object {
        // Migration from version 1 to 2
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // AudioFile: Add new columns
                database.execSQL("ALTER TABLE audio_files ADD COLUMN isStream INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE audio_files ADD COLUMN streamUrl TEXT")
                database.execSQL("ALTER TABLE audio_files ADD COLUMN originalUrl TEXT")
                database.execSQL("ALTER TABLE audio_files ADD COLUMN thumbnailUrl TEXT")

                // Create PlaylistBookmark table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS playlist_bookmarks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        url TEXT NOT NULL,
                        name TEXT NOT NULL,
                        thumbnailUrl TEXT,
                        trackCount INTEGER NOT NULL,
                        addedTimestamp INTEGER NOT NULL
                    )
                """)
            }
        }
    }
}
