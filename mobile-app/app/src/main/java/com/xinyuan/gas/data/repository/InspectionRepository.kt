package com.xinyuan.gas.data.repository

import com.xinyuan.gas.data.db.InspectionDao
import com.xinyuan.gas.data.model.InspectionRecord
import com.xinyuan.gas.data.sync.SyncStatus
import kotlinx.coroutines.flow.Flow

class InspectionRepository(private val dao: InspectionDao) {

    val allRecords: Flow<List<InspectionRecord>> = dao.getAllRecords()

    fun totalCount(): Flow<Int> = dao.getTotalCount()
    fun pendingCount(): Flow<Int> = dao.getPendingCount()
    fun rectifiedCount(): Flow<Int> = dao.getRectifiedCount()
    fun todayCount(startOfDay: Long): Flow<Int> = dao.getTodayCount(startOfDay)

    suspend fun insert(record: InspectionRecord): Long = dao.insert(record.ensureSyncIdentity())
    suspend fun update(record: InspectionRecord) = dao.update(record.ensureSyncIdentity().copy(syncStatus = SyncStatus.PENDING))
    suspend fun delete(record: InspectionRecord) = dao.delete(record)
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun getById(id: Long): InspectionRecord? = dao.getById(id)
    suspend fun getByUuid(uuid: String): InspectionRecord? = dao.getByUuid(uuid)

    suspend fun restoreFromBackup(records: List<InspectionRecord>): RestoreMergeResult {
        var inserted = 0
        var updated = 0
        records.forEach { record ->
            val normalized = record.ensureSyncIdentity().copy(syncStatus = SyncStatus.PENDING, lastSyncTime = 0L)
            val existing = dao.getByUuid(normalized.uuid)
            if (existing == null) {
                dao.insert(normalized.copy(id = 0L))
                inserted += 1
            } else {
                dao.update(normalized.copy(id = existing.id))
                updated += 1
            }
        }
        return RestoreMergeResult(inserted = inserted, updated = updated)
    }

    fun getByIdFlow(id: Long): Flow<InspectionRecord?> = dao.getByIdFlow(id)
    fun search(keyword: String): Flow<List<InspectionRecord>> = dao.search(keyword)
    fun filter(status: String, startDate: Long, endDate: Long): Flow<List<InspectionRecord>> =
        dao.filter(status, startDate, endDate)

    suspend fun getFilteredList(status: String, startDate: Long, endDate: Long): List<InspectionRecord> =
        dao.getFilteredList(status, startDate, endDate)

    private fun InspectionRecord.ensureSyncIdentity(): InspectionRecord {
        return if (uuid.isNotBlank()) {
            this
        } else {
            copy(uuid = "inspection_${id}_${createdAt}_${System.currentTimeMillis()}")
        }
    }
}
