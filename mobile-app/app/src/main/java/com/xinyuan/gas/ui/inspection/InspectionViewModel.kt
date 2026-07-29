package com.xinyuan.gas.ui.inspection

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xinyuan.gas.data.db.AppDatabase
import com.xinyuan.gas.data.model.InspectionRecord
import com.xinyuan.gas.data.model.TerminalHazardCatalog
import com.xinyuan.gas.data.model.TerminalHazardRecord
import com.xinyuan.gas.data.repository.InspectionRepository
import com.xinyuan.gas.data.repository.TerminalHazardRepository
import com.xinyuan.gas.data.sync.CloudApiException
import com.xinyuan.gas.data.sync.CloudSessionStore
import com.xinyuan.gas.data.sync.CloudRecordSummary
import com.xinyuan.gas.data.sync.CloudSettings
import com.xinyuan.gas.data.sync.HttpCloudSyncClient
import com.xinyuan.gas.util.BackupUtil
import com.xinyuan.gas.util.PhotoUtil
import com.xinyuan.gas.util.TerminalHazardExcelImportUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

enum class CloudSaveStatus {
    IDLE,
    SAVING,
    SAVED,
    ERROR
}

data class CloudSaveUiState(
    val status: CloudSaveStatus = CloudSaveStatus.IDLE,
    val message: String = "",
    val savedAt: Long = 0L
)

class InspectionViewModel(application: Application) : AndroidViewModel(application) {

    private val repo: InspectionRepository
    private val terminalRepo: TerminalHazardRepository
    private val cloudStore = CloudSessionStore(application)
    private val terminalSyncMutex = Mutex()
    private val cloudDraftMutex = Mutex()
    private var activeCloudClient: HttpCloudSyncClient? = null
    private var activeCloudClientKey = ""
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    val allRecords: Flow<List<InspectionRecord>>
    val totalCount: Flow<Int>
    val pendingCount: Flow<Int>
    val rectifiedCount: Flow<Int>
    val todayCount: Flow<Int>
    val allTerminalRecords: Flow<List<TerminalHazardRecord>>
    val terminalTotalCount: Flow<Int>
    val terminalPendingCount: Flow<Int>
    val terminalTodayCount: Flow<Int>
    val terminalUnsyncedCount: Flow<Int>

    private val _cloudSettings = MutableStateFlow(cloudStore.load())
    val cloudSettings: StateFlow<CloudSettings> = _cloudSettings.asStateFlow()

    private val _cloudRecords = MutableStateFlow<List<CloudRecordSummary>>(emptyList())
    val cloudRecords: StateFlow<List<CloudRecordSummary>> = _cloudRecords.asStateFlow()

    private val _cloudRecordsLoading = MutableStateFlow(false)
    val cloudRecordsLoading: StateFlow<Boolean> = _cloudRecordsLoading.asStateFlow()

    private val _cloudLoginInProgress = MutableStateFlow(false)
    val cloudLoginInProgress: StateFlow<Boolean> = _cloudLoginInProgress.asStateFlow()

    private val _cloudSaveState = MutableStateFlow(CloudSaveUiState())
    val cloudSaveState: StateFlow<CloudSaveUiState> = _cloudSaveState.asStateFlow()

    private val _legacyMigrationRunning = MutableStateFlow(false)
    val legacyMigrationRunning: StateFlow<Boolean> = _legacyMigrationRunning.asStateFlow()

    // 搜索
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // 筛选
    private val _filterStatus = MutableStateFlow("")
    val filterStatus: StateFlow<String> = _filterStatus

    private val _filterStartDate = MutableStateFlow(0L)
    private val _filterEndDate = MutableStateFlow(0L)

    private val _terminalSearchQuery = MutableStateFlow("")
    val terminalSearchQuery: StateFlow<String> = _terminalSearchQuery

    private val _terminalFilterResult = MutableStateFlow("")
    val terminalFilterResult: StateFlow<String> = _terminalFilterResult

    private val _terminalFilterStartDate = MutableStateFlow(0L)
    private val _terminalFilterEndDate = MutableStateFlow(0L)

    // 保存结果
    private val _saveResult = MutableSharedFlow<Result<Long>>()
    val saveResult: SharedFlow<Result<Long>> = _saveResult

    private val _terminalSaveResult = MutableSharedFlow<Result<Long>>()
    val terminalSaveResult: SharedFlow<Result<Long>> = _terminalSaveResult

    private val _dataActionMessage = MutableSharedFlow<String>()
    val dataActionMessage: SharedFlow<String> = _dataActionMessage

    // 搜索+筛选结果
    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredRecords: Flow<List<InspectionRecord>> = combine(
        _searchQuery, _filterStatus, _filterStartDate, _filterEndDate
    ) { query, status, start, end ->
        FilterParams(query, status, start, end)
    }.flatMapLatest { params ->
        if (params.query.isNotEmpty()) {
            repo.search(params.query)
        } else {
            repo.filter(params.status, params.startDate, params.endDate)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredTerminalRecords: Flow<List<TerminalHazardRecord>> = combine(
        _terminalSearchQuery, _terminalFilterResult, _terminalFilterStartDate, _terminalFilterEndDate
    ) { query, result, start, end ->
        TerminalFilterParams(query, result, start, end)
    }.flatMapLatest { params ->
        val baseFlow = if (params.query.isNotEmpty()) {
            terminalRepo.search(params.query)
        } else {
            terminalRepo.filter(params.result, params.startDate, params.endDate)
        }
        baseFlow.map { records ->
            records.filter { record ->
                (params.result.isEmpty() || record.checkResult == params.result) &&
                    (params.startDate == 0L || record.inspectionDate >= params.startDate) &&
                    (params.endDate == 0L || record.inspectionDate <= params.endDate)
            }
        }
    }

    init {
        val db = AppDatabase.getInstance(application)
        repo = InspectionRepository(db.inspectionDao())
        terminalRepo = TerminalHazardRepository(db.terminalHazardDao())
        allRecords = repo.allRecords
        allTerminalRecords = terminalRepo.allRecords
        totalCount = repo.totalCount()
        pendingCount = repo.pendingCount()
        rectifiedCount = repo.rectifiedCount()
        terminalTotalCount = terminalRepo.totalCount()
        terminalPendingCount = terminalRepo.pendingCount()
        terminalUnsyncedCount = terminalRepo.unsyncedCount()

        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        todayCount = repo.todayCount(startOfDay)
        terminalTodayCount = terminalRepo.todayCount(startOfDay)

        registerAutoSyncOnNetworkAvailable()
        if (_cloudSettings.value.isLoggedIn) refreshCloudSession()
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setFilterStatus(status: String) { _filterStatus.value = status }
    fun setFilterDates(start: Long, end: Long) {
        _filterStartDate.value = start
        _filterEndDate.value = end
    }

    fun setTerminalSearchQuery(query: String) { _terminalSearchQuery.value = query }
    fun setTerminalFilterResult(result: String) { _terminalFilterResult.value = result }
    fun setTerminalFilterDates(start: Long, end: Long) {
        _terminalFilterStartDate.value = start
        _terminalFilterEndDate.value = end
    }

    fun saveRecord(record: InspectionRecord) {
        viewModelScope.launch {
            try {
                val id = if (record.id == 0L) {
                    repo.insert(record)
                } else {
                    repo.update(record.copy(updatedAt = System.currentTimeMillis()))
                    record.id
                }
                _saveResult.emit(Result.success(id))
            } catch (e: Exception) {
                _saveResult.emit(Result.failure(e))
            }
        }
    }

    fun deleteRecord(record: InspectionRecord) {
        viewModelScope.launch {
            PhotoUtil.deletePhotos(getApplication(), record.photoPath)
            repo.delete(record)
        }
    }

    fun saveTerminalRecord(record: TerminalHazardRecord) {
        viewModelScope.launch {
            val settings = _cloudSettings.value
            if (!settings.isLoggedIn) {
                _terminalSaveResult.emit(Result.failure(IllegalStateException("请先登录 APP 账号")))
                return@launch
            }
            if (!isNetworkAvailable()) {
                _cloudSaveState.value = CloudSaveUiState(
                    status = CloudSaveStatus.ERROR,
                    message = "网络不可用，尚未提交"
                )
                _terminalSaveResult.emit(Result.failure(IllegalStateException("当前网络不可用，记录尚未提交")))
                return@launch
            }

            _cloudSaveState.value = CloudSaveUiState(CloudSaveStatus.SAVING, "正在提交到云端")
            val result = withContext(Dispatchers.IO) {
                cloudClient(settings).submitTerminalDraft(record)
            }
            result.fold(
                onSuccess = { syncResult ->
                    _cloudSaveState.value = CloudSaveUiState(
                        status = CloudSaveStatus.SAVED,
                        message = "已提交到云端",
                        savedAt = System.currentTimeMillis()
                    )
                    TerminalHazardCatalog.photoUris(record).forEach { uri ->
                        PhotoUtil.deletePhoto(getApplication(), uri)
                    }
                    refreshCloudRecords()
                    _terminalSaveResult.emit(Result.success(syncResult.remoteId.toLongOrNull() ?: 0L))
                },
                onFailure = { error ->
                    _cloudSaveState.value = CloudSaveUiState(
                        status = CloudSaveStatus.ERROR,
                        message = error.message ?: "提交失败"
                    )
                    _terminalSaveResult.emit(Result.failure(error))
                }
            )
        }
    }

    fun saveTerminalDraft(record: TerminalHazardRecord) {
        viewModelScope.launch {
            val settings = _cloudSettings.value
            if (!settings.isLoggedIn) {
                _cloudSaveState.value = CloudSaveUiState(CloudSaveStatus.ERROR, "请先登录")
                return@launch
            }
            if (!isNetworkAvailable()) {
                _cloudSaveState.value = CloudSaveUiState(CloudSaveStatus.ERROR, "网络不可用，尚未保存")
                return@launch
            }

            _cloudSaveState.value = CloudSaveUiState(CloudSaveStatus.SAVING, "正在保存到云端")
            val result = withContext(Dispatchers.IO) {
                cloudDraftMutex.withLock {
                    cloudClient(settings).saveTerminalDraft(record)
                }
            }
            result.fold(
                onSuccess = {
                    _cloudSaveState.value = CloudSaveUiState(
                        status = CloudSaveStatus.SAVED,
                        message = "草稿已保存到云端",
                        savedAt = System.currentTimeMillis()
                    )
                },
                onFailure = { error ->
                    _cloudSaveState.value = CloudSaveUiState(
                        status = CloudSaveStatus.ERROR,
                        message = error.message ?: "云端保存失败"
                    )
                }
            )
        }
    }

    fun deleteTerminalRecord(record: TerminalHazardRecord) {
        viewModelScope.launch {
            TerminalHazardCatalog.photoUris(record)
                .filter { it.isNotEmpty() }
                .forEach { PhotoUtil.deletePhoto(getApplication(), it) }
            terminalRepo.delete(record)
        }
    }

    fun getById(id: Long): Flow<InspectionRecord?> = repo.getByIdFlow(id)

    fun getTerminalById(id: Long): Flow<TerminalHazardRecord?> = terminalRepo.getByIdFlow(id)

    suspend fun getFilteredList(status: String, startDate: Long, endDate: Long): List<InspectionRecord> =
        repo.getFilteredList(status, startDate, endDate)

    fun restoreBackup(backupUri: Uri) {
        viewModelScope.launch {
            try {
                val message = withContext(Dispatchers.IO) {
                    val payload = BackupUtil.restoreBackup(getApplication(), backupUri)
                    val inspection = repo.restoreFromBackup(payload.inspectionRecords)
                    val terminal = terminalRepo.restoreFromBackup(payload.terminalRecords)
                    "恢复完成：瓶装新增 ${inspection.inserted} 条、更新 ${inspection.updated} 条；终端新增 ${terminal.inserted} 条、更新 ${terminal.updated} 条；照片 ${payload.restoredPhotoCount} 张"
                }
                _dataActionMessage.emit(message)
            } catch (e: Exception) {
                _dataActionMessage.emit("恢复失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun restoreLocalBackup(backupFile: File) {
        viewModelScope.launch {
            try {
                val message = withContext(Dispatchers.IO) {
                    val payload = BackupUtil.restoreBackup(getApplication(), backupFile)
                    val inspection = repo.restoreFromBackup(payload.inspectionRecords)
                    val terminal = terminalRepo.restoreFromBackup(payload.terminalRecords)
                    "恢复完成：瓶装新增 ${inspection.inserted} 条、更新 ${inspection.updated} 条；终端新增 ${terminal.inserted} 条、更新 ${terminal.updated} 条；照片 ${payload.restoredPhotoCount} 张"
                }
                _dataActionMessage.emit(message)
            } catch (e: Exception) {
                _dataActionMessage.emit("恢复失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun importTerminalHazardExcel(workbookUri: Uri) {
        viewModelScope.launch {
            try {
                val message = withContext(Dispatchers.IO) {
                    val inspections = allRecords.first()
                    val terminals = allTerminalRecords.first()
                    val backup = BackupUtil.createBackup(getApplication(), inspections, terminals)
                    val result = TerminalHazardExcelImportUtil.importWorkbook(
                        context = getApplication(),
                        workbookUri = workbookUri,
                        existingRecords = terminals
                    )
                    result.records.forEach { record ->
                        terminalRepo.insert(record)
                    }
                    "Excel导入完成：新增 ${result.records.size} 条，跳过重复 ${result.skippedDuplicateCount} 条，恢复照片 ${result.importedPhotoCount} 张，提示 ${result.warningCount} 条；导入前备份 ${backup.file.name}"
                }
                _dataActionMessage.emit(message)
            } catch (e: Exception) {
                _dataActionMessage.emit("Excel导入失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun loginCloud(baseUrl: String, phone: String, password: String) {
        viewModelScope.launch {
            if (_cloudLoginInProgress.value) return@launch
            _cloudLoginInProgress.value = true
            try {
                val settings = withContext(Dispatchers.IO) {
                    val session = HttpCloudSyncClient.login(getApplication(), baseUrl, phone, password).getOrThrow()
                    cloudStore.save(session)
                }
                _cloudSettings.value = settings
                _dataActionMessage.emit("云端登录成功：${settings.userName.ifBlank { "已登录" }}")
                activeCloudClient = null
                activeCloudClientKey = ""
                refreshCloudRecords()
            } catch (e: Exception) {
                _dataActionMessage.emit("云端登录失败：${e.message ?: "未知错误"}")
            } finally {
                _cloudLoginInProgress.value = false
            }
        }
    }

    fun logoutCloud() {
        viewModelScope.launch {
            val settings = withContext(Dispatchers.IO) { cloudStore.clear() }
            _cloudSettings.value = settings
            _cloudRecords.value = emptyList()
            _cloudSaveState.value = CloudSaveUiState()
            activeCloudClient = null
            activeCloudClientKey = ""
            _dataActionMessage.emit("已退出云端账号")
        }
    }

    fun refreshCloudRecords() {
        viewModelScope.launch {
            val settings = _cloudSettings.value
            if (!settings.isLoggedIn || _cloudRecordsLoading.value) return@launch
            _cloudRecordsLoading.value = true
            val result = withContext(Dispatchers.IO) {
                cloudClient(settings).fetchTerminalRecords()
            }
            result.fold(
                onSuccess = { _cloudRecords.value = it },
                onFailure = { error ->
                    handleCloudFailure("云端记录加载失败", error)
                }
            )
            _cloudRecordsLoading.value = false
        }
    }

    private fun refreshCloudSession() {
        viewModelScope.launch {
            val settings = _cloudSettings.value
            if (!settings.isLoggedIn) return@launch
            val result = withContext(Dispatchers.IO) {
                cloudClient(settings).fetchCurrentSession()
            }
            result.fold(
                onSuccess = { session ->
                    val refreshed = withContext(Dispatchers.IO) { cloudStore.save(session) }
                    _cloudSettings.value = refreshed
                    activeCloudClient = null
                    activeCloudClientKey = ""
                    refreshCloudRecords()
                },
                onFailure = { error ->
                    handleCloudFailure("账号资料刷新失败", error)
                }
            )
        }
    }

    private suspend fun handleCloudFailure(prefix: String, error: Throwable) {
        if (error is CloudApiException && error.statusCode == 401) {
            val cleared = withContext(Dispatchers.IO) { cloudStore.clear() }
            _cloudSettings.value = cleared
            _cloudRecords.value = emptyList()
            activeCloudClient = null
            activeCloudClientKey = ""
            _dataActionMessage.emit("登录已过期，请重新登录")
            return
        }
        _dataActionMessage.emit("$prefix：${error.message ?: "未知错误"}")
    }

    fun migrateLegacyRecords() {
        viewModelScope.launch {
            if (_legacyMigrationRunning.value) return@launch
            _legacyMigrationRunning.value = true
            val message = syncTerminalRecordsToCloudInternal(showStatusMessage = true)
            _legacyMigrationRunning.value = false
            _dataActionMessage.emit(message)
            refreshCloudRecords()
        }
    }

    fun clearMigratedLegacyData() {
        viewModelScope.launch {
            val unsynced = withContext(Dispatchers.IO) { terminalRepo.getUnsyncedRecords() }
            if (unsynced.isNotEmpty()) {
                _dataActionMessage.emit("仍有 ${unsynced.size} 条旧记录未迁移，暂不清理本机数据")
                return@launch
            }
            val records = allTerminalRecords.first()
            withContext(Dispatchers.IO) {
                terminalRepo.deleteAll()
                records.flatMap { TerminalHazardCatalog.photoUris(it) }
                    .distinct()
                    .forEach { PhotoUtil.deletePhoto(getApplication(), it) }
            }
            _dataActionMessage.emit("旧版数据已完成核对并从本机清理")
        }
    }

    fun syncTerminalRecordsToCloud() {
        viewModelScope.launch {
            _dataActionMessage.emit(syncTerminalRecordsToCloudInternal(showStatusMessage = true))
        }
    }

    private suspend fun syncTerminalRecordsToCloudInternal(showStatusMessage: Boolean): String {
        val settings = _cloudSettings.value
        if (!settings.isLoggedIn) {
            return if (showStatusMessage) "请先登录云端账号" else ""
        }
        if (!isNetworkAvailable()) {
            return if (showStatusMessage) "当前网络不可用，旧记录仍安全保留；联网后请重新点击开始迁移" else ""
        }

        return runCatching {
            withContext(Dispatchers.IO) {
                terminalSyncMutex.withLock {
                    val records = terminalRepo.getUnsyncedRecords()
                    if (records.isEmpty()) {
                        return@withLock "没有待上传的终端核查记录"
                    }

                    val client = HttpCloudSyncClient(getApplication(), settings.baseUrl, settings.token)
                    var successCount = 0
                    var failedCount = 0
                    val errors = mutableListOf<String>()

                    records.forEach { record ->
                        val result = client.uploadTerminalHazardRecord(record)
                        result.onSuccess { syncResult ->
                            terminalRepo.markSyncSuccess(record, syncResult.remoteId)
                            successCount += 1
                        }.onFailure { error ->
                            terminalRepo.markSyncFailed(record)
                            failedCount += 1
                            if (errors.size < 3) {
                                errors += "${record.customerName.ifBlank { record.customerPhone }}：${error.message ?: "上传失败"}"
                            }
                        }
                    }

                    buildString {
                        append("云同步完成：成功 $successCount 条，失败 $failedCount 条")
                        if (errors.isNotEmpty()) {
                            append("；")
                            append(errors.joinToString("；"))
                        }
                    }
                }
            }
        }.getOrElse { error ->
            "云同步失败：${error.message ?: "未知错误"}"
        }
    }

    private fun cloudClient(settings: CloudSettings): HttpCloudSyncClient {
        val key = "${settings.baseUrl}|${settings.token}"
        if (activeCloudClient == null || activeCloudClientKey != key) {
            activeCloudClient = HttpCloudSyncClient(getApplication(), settings.baseUrl, settings.token)
            activeCloudClientKey = key
        }
        return requireNotNull(activeCloudClient)
    }

    fun isNetworkAvailable(): Boolean {
        val connectivityManager = getApplication<Application>()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerAutoSyncOnNetworkAvailable() {
        val connectivityManager = getApplication<Application>()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (_cloudSettings.value.isLoggedIn) {
                    refreshCloudRecords()
                }
            }
        }
        runCatching {
            connectivityManager.registerNetworkCallback(request, callback)
            networkCallback = callback
        }
    }

    override fun onCleared() {
        val connectivityManager = getApplication<Application>()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback?.let { callback ->
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
        networkCallback = null
        super.onCleared()
    }

    fun clearLocalBusinessData() {
        viewModelScope.launch {
            try {
                val message = withContext(Dispatchers.IO) {
                    val inspectionCount = allRecords.first().size
                    val terminalCount = allTerminalRecords.first().size
                    terminalRepo.deleteAll()
                    repo.deleteAll()
                    val photos = BackupUtil.deleteAllPhotos(getApplication())
                    "本机业务数据已清空：瓶装 $inspectionCount 条，终端 $terminalCount 条，删除照片 ${photos.deletedCount} 张，释放 ${BackupUtil.formatBytes(photos.freedBytes)}"
                }
                _dataActionMessage.emit(message)
            } catch (e: Exception) {
                _dataActionMessage.emit("清空失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun clearLocalBackupFiles() {
        viewModelScope.launch {
            try {
                val message = withContext(Dispatchers.IO) {
                    val result = BackupUtil.deleteLocalBackupFiles(getApplication())
                    "本机备份包已清理：删除 ${result.deletedCount} 个，释放 ${BackupUtil.formatBytes(result.freedBytes)}"
                }
                _dataActionMessage.emit(message)
            } catch (e: Exception) {
                _dataActionMessage.emit("清理备份包失败：${e.message ?: "未知错误"}")
            }
        }
    }

    private data class FilterParams(
        val query: String,
        val status: String,
        val startDate: Long,
        val endDate: Long
    )

    private data class TerminalFilterParams(
        val query: String,
        val result: String,
        val startDate: Long,
        val endDate: Long
    )
}
