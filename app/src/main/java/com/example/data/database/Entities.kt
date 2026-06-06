package com.example.data.database

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scan_logs")
data class ScanLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val targetSdk: Int,
    val isLocalApk: Boolean,
    val riskScore: Int,
    val threatLevel: String, // "CLEAN", "SUSPICIOUS", "HIGH_RISK"
    val scanTime: Long = System.currentTimeMillis(),
    val sensitivePermissions: String, // Comma separated list of high/medium permissions
    val cleartextTrafficEnabled: Boolean,
    val aiSummaryReport: String // Markdown report from Gemini
)

@Dao
interface ScanLogDao {
    @Query("SELECT * FROM scan_logs ORDER BY scanTime DESC")
    fun getAllScanLogs(): Flow<List<ScanLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanLog(log: ScanLog)

    @Query("DELETE FROM scan_logs WHERE id = :id")
    suspend fun deleteScanLogById(id: Int)

    @Query("DELETE FROM scan_logs")
    suspend fun deleteAllScanLogs()
}

@Database(entities = [ScanLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanLogDao(): ScanLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "apk_scanner_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
