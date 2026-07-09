package com.xinyuan.gas.data.repository

import com.xinyuan.gas.data.db.TerminalHazardDao
import com.xinyuan.gas.data.model.TerminalHazardRecord
import com.xinyuan.gas.data.sync.SyncStatus
import kotlinx.coroutines.flow.Flow

class TerminalHazardRepository(private val dao: TerminalHazardDao) {

    val allRecords: Flow<List<TerminalHazardRecord>> = dao.getAllRecords()

    fun totalCount(): Flow<Int> = dao.getTotalCount()
    fun pendingCount(): Flow<Int> = dao.getPendingCount()
    fun todayCount(startOfDay: Long): Flow<Int> = dao.getTodayCount(startOfDay)
    fun unsyncedCount(): Flow<Int> = dao.getUnsyncedCount()
    fun getByIdFlow(id: Long): Flow<TerminalHazardRecord?> = dao.getByIdFlow(id)
    fun search(keyword: String): Flow<List<TerminalHazardRecord>> = dao.search(keyword)
    fun filter(result: String, startDate: Long, endDate: Long): Flow<List<TerminalHazardRecord>> =
        dao.filter(result, startDate, endDate)

    suspend fun insert(record: TerminalHazardRecord): Long = dao.insert(record.ensureSyncIdentity())
    suspend fun update(record: TerminalHazardRecord) = dao.update(record.ensureSyncIdentity().copy(syncStatus = SyncStatus.PENDING))
    suspend fun delete(record: TerminalHazardRecord) = dao.delete(record)
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun getByUuid(uuid: String): TerminalHazardRecord? = dao.getByUuid(uuid)
    suspend fun getUnsyncedRecords(): List<TerminalHazardRecord> = dao.getUnsyncedRecords()
    suspend fun markSyncSuccess(record: TerminalHazardRecord, remoteId: String) =
        dao.updateSyncState(record.id, SyncStatus.SYNCED, System.currentTimeMillis(), remoteId)

    suspend fun markSyncFailed(record: TerminalHazardRecord) =
        dao.updateSyncState(record.id, SyncStatus.FAILED, record.lastSyncTime, record.remoteId)

    suspend fun restoreFromBackup(records: List<TerminalHazardRecord>): RestoreMergeResult {
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

    private fun TerminalHazardRecord.ensureSyncIdentity(): TerminalHazardRecord {
        return if (uuid.isNotBlank()) {
            this
        } else {
            copy(uuid = "terminal_${id}_${createdAt}_${System.currentTimeMillis()}")
        }
    }
}
