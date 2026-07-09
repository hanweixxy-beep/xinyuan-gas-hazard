package com.xinyuan.gas.ui.data

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.xinyuan.gas.data.sync.HttpCloudSyncClient
import com.xinyuan.gas.data.sync.SyncStatus
import com.xinyuan.gas.ui.components.BrandLogo
import com.xinyuan.gas.ui.components.IOSGroupCard
import com.xinyuan.gas.ui.components.IOSTopBar
import com.xinyuan.gas.ui.inspection.InspectionViewModel
import com.xinyuan.gas.ui.theme.BluePrimary
import com.xinyuan.gas.ui.theme.GrayBackground
import com.xinyuan.gas.ui.theme.GraySecondaryText
import com.xinyuan.gas.ui.theme.GreenSuccess
import com.xinyuan.gas.ui.theme.OrangeAccent
import com.xinyuan.gas.ui.theme.RedDanger
import com.xinyuan.gas.util.BackupUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DataManagementScreen(
    viewModel: InspectionViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA) }
    val dayFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }
    val inspectionRecords by viewModel.allRecords.collectAsState(initial = emptyList())
    val terminalRecords by viewModel.allTerminalRecords.collectAsState(initial = emptyList())
    val terminalUnsyncedCount by viewModel.terminalUnsyncedCount.collectAsState(initial = 0)
    val cloudSettings by viewModel.cloudSettings.collectAsState()

    var stats by remember { mutableStateOf<BackupUtil.StorageStats?>(null) }
    var localBackups by remember { mutableStateOf<List<BackupUtil.LocalBackupInfo>>(emptyList()) }
    var workingText by remember { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var pendingClearBackup by remember { mutableStateOf<File?>(null) }
    var pendingDeleteBackup by remember { mutableStateOf<File?>(null) }
    var showClearBackupsDialog by remember { mutableStateOf(false) }
    var showCloudLoginDialog by remember { mutableStateOf(false) }

    fun refreshData() {
        refreshToken += 1
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun restoreLocalBackup(file: File) {
        if (!file.exists() || !file.isFile) {
            toast("备份包不存在或已被删除")
            refreshData()
            return
        }
        workingText = "正在恢复备份..."
        viewModel.restoreLocalBackup(file)
        showRestoreDialog = false
    }

    fun openRestoreDialog() {
        val backups = BackupUtil.listLocalBackups(context)
        localBackups = backups
        if (backups.isEmpty()) {
            toast("未找到本机备份包，请先生成备份包")
        } else {
            showRestoreDialog = true
        }
    }

    val externalRestoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            workingText = "正在恢复外部备份..."
            viewModel.restoreBackup(uri)
        }
    }

    val terminalExcelImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            workingText = "正在导入终端核查 Excel..."
            viewModel.importTerminalHazardExcel(uri)
        }
    }

    LaunchedEffect(inspectionRecords, terminalRecords, refreshToken) {
        val result = withContext(Dispatchers.IO) {
            BackupUtil.calculateStorageStats(context, inspectionRecords, terminalRecords) to
                BackupUtil.listLocalBackups(context)
        }
        stats = result.first
        localBackups = result.second
    }

    LaunchedEffect(Unit) {
        viewModel.dataActionMessage.collectLatest { message ->
            workingText = null
            toast(message)
            refreshData()
        }
    }

    Scaffold(
        topBar = { IOSTopBar(title = "数据管理与备份", onBack = onBack) },
        containerColor = GrayBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            BrandLogo(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp, bottom = 10.dp)
            )

            Text(
                "备份包保存在 APP 专用备份文件夹内，可在下方列表查看、恢复、分享或删除。",
                color = GraySecondaryText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            workingText?.let { text ->
                Text(text, color = BluePrimary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = BluePrimary)
                Spacer(Modifier.height(12.dp))
            }

            IOSGroupCard(title = "本机数据概览") {
                val current = stats
                MetricRow("业务记录", "瓶装 ${current?.inspectionCount ?: inspectionRecords.size} 条 / 终端 ${current?.terminalCount ?: terminalRecords.size} 条")
                MetricRow("照片文件", "${current?.photoCount ?: 0} 张，其中无效 ${current?.orphanPhotoCount ?: 0} 张")
                MetricRow("照片占用", current?.photoBytes?.let(BackupUtil::formatBytes) ?: "计算中")
                MetricRow("数据库占用", current?.databaseBytes?.let(BackupUtil::formatBytes) ?: "计算中")
                MetricRow("本机备份包", "${localBackups.size} 个，占用 ${current?.backupBytes?.let(BackupUtil::formatBytes) ?: "计算中"}", showDivider = false)
            }

            IOSGroupCard(title = "备份与恢复") {
                DataActionButton(
                    title = "生成本机备份包",
                    icon = Icons.Default.FileDownload,
                    color = BluePrimary,
                    enabled = workingText == null,
                    onClick = {
                        scope.launch {
                            workingText = "正在生成备份..."
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    BackupUtil.createBackup(context, inspectionRecords, terminalRecords)
                                }
                            }.onSuccess { result ->
                                workingText = null
                                refreshData()
                                toast("备份完成：${result.file.name}，照片 ${result.photoCount} 张")
                            }.onFailure { error ->
                                workingText = null
                                toast("备份失败：${error.message ?: "未知错误"}")
                            }
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
                DataActionButton(
                    title = "从备份包恢复",
                    icon = Icons.Default.Restore,
                    color = GreenSuccess,
                    enabled = workingText == null,
                    outlined = true,
                    onClick = { openRestoreDialog() }
                )
                Spacer(Modifier.height(10.dp))
                DataActionButton(
                    title = "从手机文件导入备份",
                    icon = Icons.Default.Restore,
                    color = OrangeAccent,
                    enabled = workingText == null,
                    outlined = true,
                    onClick = { externalRestoreLauncher.launch("application/zip") }
                )
                Spacer(Modifier.height(10.dp))
                DataActionButton(
                    title = "从Excel导入终端核查表",
                    icon = Icons.Default.FileDownload,
                    color = BluePrimary,
                    enabled = workingText == null,
                    outlined = true,
                    onClick = { terminalExcelImportLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") }
                )
            }

            IOSGroupCard(title = "本机备份包列表") {
                Text(
                    "备份目录：${BackupUtil.backupFolderPath(context)}",
                    color = GraySecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )

                if (localBackups.isEmpty()) {
                    EmptyBackupRow()
                } else {
                    localBackups.forEachIndexed { index, backup ->
                        BackupPackageRow(
                            backup = backup,
                            isLatest = index == 0,
                            dateText = dateFormat.format(Date(backup.createdAt)),
                            dayText = dayFormat.format(Date(backup.createdAt)),
                            enabled = workingText == null,
                            onRestore = { restoreLocalBackup(backup.file) },
                            onShare = { shareBackup(context, backup.file, ::toast) },
                            onDelete = { pendingDeleteBackup = backup.file },
                            showDivider = index != localBackups.lastIndex
                        )
                    }
                }
            }

            IOSGroupCard(title = "空间与清理") {
                DataActionButton(
                    title = "生成备份并准备清空本机业务数据",
                    icon = Icons.Default.Delete,
                    color = RedDanger,
                    enabled = workingText == null && (inspectionRecords.isNotEmpty() || terminalRecords.isNotEmpty()),
                    outlined = true,
                    onClick = {
                        scope.launch {
                            workingText = "正在生成清空前备份..."
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    BackupUtil.createBackup(context, inspectionRecords, terminalRecords)
                                }
                            }.onSuccess { result ->
                                workingText = null
                                refreshData()
                                pendingClearBackup = result.file
                            }.onFailure { error ->
                                workingText = null
                                toast("备份失败，未清空数据：${error.message ?: "未知错误"}")
                            }
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
                DataActionButton(
                    title = "压缩现有照片",
                    icon = Icons.Default.Refresh,
                    color = OrangeAccent,
                    enabled = workingText == null,
                    outlined = true,
                    onClick = {
                        scope.launch {
                            workingText = "正在优化照片..."
                            val result = withContext(Dispatchers.IO) {
                                BackupUtil.optimizeReferencedPhotos(context, inspectionRecords, terminalRecords)
                            }
                            workingText = null
                            refreshData()
                            toast("优化完成：处理 ${result.optimizedCount} 张，节省 ${BackupUtil.formatBytes(result.freedBytes)}")
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
                DataActionButton(
                    title = "清理无效照片",
                    icon = Icons.Default.Delete,
                    color = RedDanger,
                    enabled = workingText == null,
                    outlined = true,
                    onClick = {
                        scope.launch {
                            workingText = "正在清理照片..."
                            val result = withContext(Dispatchers.IO) {
                                BackupUtil.cleanupOrphanPhotos(context, inspectionRecords, terminalRecords)
                            }
                            workingText = null
                            refreshData()
                            toast("清理完成：删除 ${result.deletedCount} 张，释放 ${BackupUtil.formatBytes(result.freedBytes)}")
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
                DataActionButton(
                    title = "清理全部本机备份包",
                    icon = Icons.Default.Delete,
                    color = RedDanger,
                    enabled = workingText == null && localBackups.isNotEmpty(),
                    outlined = true,
                    onClick = { showClearBackupsDialog = true }
                )
            }

            IOSGroupCard(title = "云端同步") {
                val failedCount = terminalRecords.count { it.syncStatus == SyncStatus.FAILED }
                MetricRow("登录状态", if (cloudSettings.isLoggedIn) "已登录：${cloudSettings.userName.ifBlank { "云端账号" }}" else "未登录")
                MetricRow("服务器", cloudSettings.baseUrl)
                MetricRow("待上传终端记录", "${terminalUnsyncedCount} 条，失败待重试 ${failedCount} 条", showDivider = false)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .background(Color(0xFFEAF4FF), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "保存终端核查记录后会自动上传；网络异常或服务器暂不可用时会保留为待上传，APP 打开、登录云端账号或网络恢复后会自动重试。云端会按本机 UUID 去重，重复上传不会新增重复记录。",
                        color = GraySecondaryText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                DataActionButton(
                    title = if (cloudSettings.isLoggedIn) "更换云端账号" else "登录云端账号",
                    icon = Icons.Default.CloudUpload,
                    color = BluePrimary,
                    enabled = workingText == null,
                    onClick = { showCloudLoginDialog = true }
                )
                Spacer(Modifier.height(10.dp))
                DataActionButton(
                    title = "立即重试上传待同步记录",
                    icon = Icons.Default.Refresh,
                    color = GreenSuccess,
                    enabled = workingText == null && cloudSettings.isLoggedIn && terminalUnsyncedCount > 0,
                    outlined = true,
                    onClick = {
                        workingText = "正在上传终端核查记录和照片..."
                        viewModel.syncTerminalRecordsToCloud()
                    }
                )
                if (cloudSettings.isLoggedIn) {
                    Spacer(Modifier.height(10.dp))
                    DataActionButton(
                        title = "退出云端账号",
                        icon = Icons.Default.Delete,
                        color = RedDanger,
                        enabled = workingText == null,
                        outlined = true,
                        onClick = { viewModel.logoutCloud() }
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (showRestoreDialog) {
        RestoreBackupDialog(
            backups = localBackups,
            dateFormat = dateFormat,
            dayFormat = dayFormat,
            onDismiss = { showRestoreDialog = false },
            onRestore = { restoreLocalBackup(it.file) },
            onShare = { shareBackup(context, it.file, ::toast) }
        )
    }

    pendingDeleteBackup?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingDeleteBackup = null },
            title = { Text("删除备份包") },
            text = {
                Text(
                    "确认删除这个本机备份包？\n\n${file.name}\n\n删除后列表会自动刷新；如果文件已经不存在，不会影响 APP 数据。",
                    color = GraySecondaryText,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteBackup = null
                        val deleted = runCatching { file.exists() && file.delete() }.getOrDefault(false)
                        toast(if (deleted) "备份包已删除" else "备份包不存在或删除失败")
                        refreshData()
                    }
                ) {
                    Text("确认删除", color = RedDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteBackup = null }) {
                    Text("取消")
                }
            }
        )
    }

    pendingClearBackup?.let { backupFile ->
        AlertDialog(
            onDismissRequest = { pendingClearBackup = null },
            title = { Text("确认清空本机业务数据") },
            text = {
                Column {
                    Text(
                        "已生成备份包：${backupFile.name}\n\n清空前请先分享到电脑、微信、网盘或另一台手机，确认备份已经离开本机后再继续。",
                        color = GraySecondaryText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    DataActionButton(
                        title = "分享这个备份包",
                        icon = Icons.Default.Share,
                        color = BluePrimary,
                        enabled = true,
                        onClick = { shareBackup(context, backupFile, ::toast) }
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "清空会删除本机业务记录和照片，不会删除已生成的备份包。",
                        color = RedDanger,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingClearBackup = null
                        workingText = "正在清空本机业务数据..."
                        viewModel.clearLocalBusinessData()
                    }
                ) {
                    Text("确认清空", color = RedDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingClearBackup = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showClearBackupsDialog) {
        AlertDialog(
            onDismissRequest = { showClearBackupsDialog = false },
            title = { Text("清理全部本机备份包") },
            text = {
                Text(
                    "请确认这些备份包已经保存到电脑、微信、网盘或另一台手机。本操作只删除备份包，不删除业务记录和照片。",
                    color = GraySecondaryText,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearBackupsDialog = false
                        workingText = "正在清理本机备份包..."
                        viewModel.clearLocalBackupFiles()
                    }
                ) {
                    Text("确认清理", color = RedDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearBackupsDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showCloudLoginDialog) {
        CloudLoginDialog(
            initialBaseUrl = cloudSettings.baseUrl.ifBlank { HttpCloudSyncClient.DEFAULT_BASE_URL },
            onDismiss = { showCloudLoginDialog = false },
            onLogin = { baseUrl, phone, password ->
                showCloudLoginDialog = false
                workingText = "正在登录云端账号..."
                viewModel.loginCloud(baseUrl, phone, password)
            }
        )
    }
}

@Composable
private fun CloudLoginDialog(
    initialBaseUrl: String,
    onDismiss: () -> Unit,
    onLogin: (String, String, String) -> Unit
) {
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("登录云端账号") },
        text = {
            Column {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("服务器地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("手机号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "登录后可手动上传本机终端核查记录和照片。",
                    color = GraySecondaryText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = baseUrl.isNotBlank() && phone.isNotBlank() && password.isNotBlank(),
                onClick = { onLogin(baseUrl, phone, password) }
            ) {
                Text("登录")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    showDivider: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(label, color = GraySecondaryText, style = MaterialTheme.typography.bodyMedium)
            Text(
                value,
                color = Color(0xFF1C1C1E),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        if (showDivider) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .padding(start = 16.dp)
                    .background(Color(0xFFE5E5EA))
            )
        }
    }
}

@Composable
private fun BackupPackageRow(
    backup: BackupUtil.LocalBackupInfo,
    isLatest: Boolean,
    dateText: String,
    dayText: String,
    enabled: Boolean,
    onRestore: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    showDivider: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                dayText,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF1C1C1E),
                modifier = Modifier.weight(1f)
            )
            if (isLatest) {
                Surface(shape = RoundedCornerShape(12.dp), color = GreenSuccess.copy(alpha = 0.12f)) {
                    Text(
                        "最新",
                        color = GreenSuccess,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(dateText, color = BluePrimary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(3.dp))
        Text(
            "${backup.name} · ${BackupUtil.formatBytes(backup.bytes)}",
            color = GraySecondaryText,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Text(
            backup.path,
            color = GraySecondaryText,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = enabled && backup.exists, onClick = onRestore) {
                Text("恢复", color = GreenSuccess)
            }
            TextButton(enabled = enabled && backup.exists, onClick = onShare) {
                Text("分享", color = BluePrimary)
            }
            TextButton(enabled = enabled, onClick = onDelete) {
                Text("删除", color = RedDanger)
            }
        }
    }
    if (showDivider) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .padding(start = 16.dp)
                .background(Color(0xFFE5E5EA))
        )
    }
}

@Composable
private fun EmptyBackupRow() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "暂无本机备份包",
            color = Color(0xFF1C1C1E),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "点击“生成本机备份包”后，这里会显示备份日期、路径和最新标记。",
            color = GraySecondaryText,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun RestoreBackupDialog(
    backups: List<BackupUtil.LocalBackupInfo>,
    dateFormat: SimpleDateFormat,
    dayFormat: SimpleDateFormat,
    onDismiss: () -> Unit,
    onRestore: (BackupUtil.LocalBackupInfo) -> Unit,
    onShare: (BackupUtil.LocalBackupInfo) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择备份包恢复") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (backups.isEmpty()) {
                    Text("没有可恢复的本机备份包", color = GraySecondaryText)
                } else {
                    backups.forEachIndexed { index, backup ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = backup.exists) { onRestore(backup) }
                                .padding(vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    dayFormat.format(Date(backup.createdAt)),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.weight(1f)
                                )
                                if (index == 0) {
                                    Text("最新", color = GreenSuccess, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Text(dateFormat.format(Date(backup.createdAt)), color = BluePrimary, style = MaterialTheme.typography.bodySmall)
                            Text(backup.name, color = GraySecondaryText, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TextButton(enabled = backup.exists, onClick = { onRestore(backup) }) {
                                    Text("一键恢复", color = GreenSuccess)
                                }
                                TextButton(enabled = backup.exists, onClick = { onShare(backup) }) {
                                    Text("分享", color = BluePrimary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun DataActionButton(
    title: String,
    icon: ImageVector,
    color: Color,
    enabled: Boolean,
    outlined: Boolean = false,
    onClick: () -> Unit
) {
    val content: @Composable () -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(title)
    }
    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            content()
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = color)
        ) {
            content()
        }
    }
}

private fun shareBackup(
    context: android.content.Context,
    file: File,
    showMessage: (String) -> Unit
) {
    if (!file.exists() || !file.isFile) {
        showMessage("备份包不存在或已被删除")
        return
    }
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "分享备份包"))
    }.onFailure {
        showMessage("无法分享备份包：${it.message ?: "未知错误"}")
    }
}
