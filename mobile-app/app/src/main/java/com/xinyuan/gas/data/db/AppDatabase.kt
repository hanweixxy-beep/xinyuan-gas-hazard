package com.xinyuan.gas.data.db

import android.content.Context
import androidx.room.*
import com.xinyuan.gas.data.model.InspectionRecord
import com.xinyuan.gas.data.model.TerminalHazardRecord
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Dao
interface InspectionDao {

    @Insert
    suspend fun insert(record: InspectionRecord): Long

    @Update
    suspend fun update(record: InspectionRecord)

    @Delete
    suspend fun delete(record: InspectionRecord)

    @Query("DELETE FROM inspection_records")
    suspend fun deleteAll()

    @Query("SELECT * FROM inspection_records ORDER BY createdAt DESC")
    fun getAllRecords(): Flow<List<InspectionRecord>>

    @Query("SELECT * FROM inspection_records WHERE id = :id")
    suspend fun getById(id: Long): InspectionRecord?

    @Query("SELECT * FROM inspection_records WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<InspectionRecord?>

    @Query("SELECT * FROM inspection_records WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): InspectionRecord?

    @Query("SELECT COUNT(*) FROM inspection_records")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM inspection_records WHERE isRectified = '未整改'")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM inspection_records WHERE isRectified = '已整改'")
    fun getRectifiedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM inspection_records WHERE createdAt >= :startOfDay")
    fun getTodayCount(startOfDay: Long): Flow<Int>

    @Query("""
        SELECT * FROM inspection_records
        WHERE customerName LIKE '%' || :keyword || '%'
           OR customerAddress LIKE '%' || :keyword || '%'
           OR inspectorName LIKE '%' || :keyword || '%'
           OR deliveryPerson LIKE '%' || :keyword || '%'
           OR customerType LIKE '%' || :keyword || '%'
        ORDER BY createdAt DESC
    """)
    fun search(keyword: String): Flow<List<InspectionRecord>>

    @Query("""
        SELECT * FROM inspection_records
        WHERE (:status = '' OR isRectified = :status)
          AND (:startDate = 0 OR inspectionDate >= :startDate)
          AND (:endDate = 0 OR inspectionDate <= :endDate)
        ORDER BY createdAt DESC
    """)
    fun filter(status: String, startDate: Long, endDate: Long): Flow<List<InspectionRecord>>

    @Query("""
        SELECT * FROM inspection_records
        WHERE (:status = '' OR isRectified = :status)
          AND (:startDate = 0 OR inspectionDate >= :startDate)
          AND (:endDate = 0 OR inspectionDate <= :endDate)
        ORDER BY createdAt DESC
    """)
    suspend fun getFilteredList(status: String, startDate: Long, endDate: Long): List<InspectionRecord>
}

@Dao
interface TerminalHazardDao {

    @Insert
    suspend fun insert(record: TerminalHazardRecord): Long

    @Update
    suspend fun update(record: TerminalHazardRecord)

    @Delete
    suspend fun delete(record: TerminalHazardRecord)

    @Query("DELETE FROM terminal_hazard_records")
    suspend fun deleteAll()

    @Query("SELECT * FROM terminal_hazard_records ORDER BY createdAt DESC")
    fun getAllRecords(): Flow<List<TerminalHazardRecord>>

    @Query("SELECT * FROM terminal_hazard_records WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<TerminalHazardRecord?>

    @Query("SELECT * FROM terminal_hazard_records WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): TerminalHazardRecord?

    @Query("SELECT * FROM terminal_hazard_records WHERE syncStatus != 'SYNCED' ORDER BY createdAt ASC")
    suspend fun getUnsyncedRecords(): List<TerminalHazardRecord>

    @Query("SELECT COUNT(*) FROM terminal_hazard_records WHERE syncStatus != 'SYNCED'")
    fun getUnsyncedCount(): Flow<Int>

    @Query("""
        UPDATE terminal_hazard_records
        SET syncStatus = :syncStatus,
            lastSyncTime = :lastSyncTime,
            remoteId = :remoteId
        WHERE id = :id
    """)
    suspend fun updateSyncState(id: Long, syncStatus: String, lastSyncTime: Long, remoteId: String)

    @Query("SELECT COUNT(*) FROM terminal_hazard_records")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM terminal_hazard_records WHERE checkResult = '隐患未清零'")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM terminal_hazard_records WHERE createdAt >= :startOfDay")
    fun getTodayCount(startOfDay: Long): Flow<Int>

    @Query("""
        SELECT * FROM terminal_hazard_records
        WHERE customerName LIKE '%' || :keyword || '%'
           OR customerAddress LIKE '%' || :keyword || '%'
           OR inspectorName LIKE '%' || :keyword || '%'
           OR deliveryPerson LIKE '%' || :keyword || '%'
           OR customerType LIKE '%' || :keyword || '%'
           OR customerPhone LIKE '%' || :keyword || '%'
        ORDER BY createdAt DESC
    """)
    fun search(keyword: String): Flow<List<TerminalHazardRecord>>

    @Query("""
        SELECT * FROM terminal_hazard_records
        WHERE (:result = '' OR checkResult = :result)
          AND (:startDate = 0 OR inspectionDate >= :startDate)
          AND (:endDate = 0 OR inspectionDate <= :endDate)
        ORDER BY createdAt DESC
    """)
    fun filter(result: String, startDate: Long, endDate: Long): Flow<List<TerminalHazardRecord>>
}

@Database(
    entities = [InspectionRecord::class, TerminalHazardRecord::class],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun inspectionDao(): InspectionDao
    abstract fun terminalHazardDao(): TerminalHazardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS terminal_hazard_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        customerName TEXT NOT NULL,
                        storefrontPhoto TEXT NOT NULL,
                        customerPhone TEXT NOT NULL,
                        customerType TEXT NOT NULL,
                        customerAddress TEXT NOT NULL,
                        inspectorName TEXT NOT NULL,
                        inspectionDate INTEGER NOT NULL,
                        checkResult TEXT NOT NULL,
                        hazardItems TEXT NOT NULL,
                        hazardPhotos TEXT NOT NULL,
                        deliveryPerson TEXT NOT NULL,
                        remark TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE terminal_hazard_records ADD COLUMN gasSourceIssues TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE terminal_hazard_records ADD COLUMN cylinderQrPhotos TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE terminal_hazard_records ADD COLUMN cylinderPhotos TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE terminal_hazard_records ADD COLUMN rectificationPhotos TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE terminal_hazard_records ADD COLUMN rectificationDate INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE terminal_hazard_records ADD COLUMN rectificationNote TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE terminal_hazard_records ADD COLUMN hadUnclearedHazard INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                listOf("inspection_records", "terminal_hazard_records").forEach { table ->
                    database.execSQL("ALTER TABLE $table ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                    database.execSQL("ALTER TABLE $table ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'PENDING'")
                    database.execSQL("ALTER TABLE $table ADD COLUMN lastSyncTime INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE $table ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
                }
                database.execSQL(
                    "UPDATE inspection_records SET uuid = 'inspection_' || id || '_' || createdAt WHERE uuid = ''"
                )
                database.execSQL(
                    "UPDATE terminal_hazard_records SET uuid = 'terminal_' || id || '_' || createdAt WHERE uuid = ''"
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE terminal_hazard_records ADD COLUMN rectificationPerson TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE terminal_hazard_records ADD COLUMN locationLatitude REAL")
                database.execSQL("ALTER TABLE terminal_hazard_records ADD COLUMN locationLongitude REAL")
                database.execSQL("ALTER TABLE terminal_hazard_records ADD COLUMN locationAccuracyMeters REAL")
                database.execSQL("ALTER TABLE terminal_hazard_records ADD COLUMN locationCapturedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE terminal_hazard_records ADD COLUMN regionId INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE terminal_hazard_records ADD COLUMN regionName TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE terminal_hazard_records ADD COLUMN storeId INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE terminal_hazard_records ADD COLUMN storeName TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gas_hazard_db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
