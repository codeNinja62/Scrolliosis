package com.saltatoryimpulse.braingate

import android.content.Context
import androidx.annotation.Keep
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- ENTITIES: Hardened against Obfuscation ---

@Keep // Ensures R8 never renames this class
@Entity(tableName = "knowledge_vault")
data class KnowledgeEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "summary") val summary: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    // BUG-04: distinguishes user-authored knowledge prompts from auto-saved reflections
    @ColumnInfo(name = "is_custom_prompt", defaultValue = "0") val isCustomPrompt: Boolean = false
)

@Keep
@Entity(tableName = "blocked_apps")
data class BlockedApp(
    @PrimaryKey @ColumnInfo(name = "packageName") val packageName: String,
    @ColumnInfo(name = "appName") val appName: String
)

@Dao
interface KnowledgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: KnowledgeEntry)

    @Query("SELECT * FROM knowledge_vault ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<KnowledgeEntry>>

    @Delete
    suspend fun deleteEntry(entry: KnowledgeEntry)

    // BUG-04: returns only user-authored knowledge prompts, ordered randomly for variety
    @Query("SELECT * FROM knowledge_vault WHERE is_custom_prompt = 1 ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomCustomPrompt(): KnowledgeEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockApp(app: BlockedApp)

    @Delete
    suspend fun unblockApp(app: BlockedApp)

    @Query("SELECT * FROM blocked_apps ORDER BY appName ASC")
    fun getBlockedApps(): Flow<List<BlockedApp>>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_apps WHERE packageName = :pkg)")
    suspend fun isAppBlocked(pkg: String): Boolean
}

@Database(entities = [KnowledgeEntry::class, BlockedApp::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun knowledgeDao(): KnowledgeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Use the context as-is so callers can pass device-protected storage context
                // for Direct Boot support. Do NOT call context.applicationContext here because
                // on some OEM implementations that strips the device-protected flag.
                val instance = Room.databaseBuilder(
                    context,
                    AppDatabase::class.java,
                    "braingate_db"
                )

                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}