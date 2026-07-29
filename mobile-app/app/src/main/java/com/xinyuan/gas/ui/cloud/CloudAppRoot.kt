package com.xinyuan.gas.ui.cloud

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xinyuan.gas.data.sync.CloudRecordSummary
import com.xinyuan.gas.data.sync.CloudSettings
import com.xinyuan.gas.data.sync.HttpCloudSyncClient
import com.xinyuan.gas.ui.components.BrandLogo
import com.xinyuan.gas.ui.inspection.InspectionViewModel
import com.xinyuan.gas.ui.terminal.TerminalHazardFormScreen
import com.xinyuan.gas.ui.theme.BrandGreen
import com.xinyuan.gas.ui.theme.BrandGreenSoft
import com.xinyuan.gas.ui.theme.BrandGreenStrong
import com.xinyuan.gas.ui.theme.GrayBackground
import com.xinyuan.gas.ui.theme.GraySeparator
import com.xinyuan.gas.ui.theme.GraySurface
import com.xinyuan.gas.ui.theme.GrayTertiaryText
import com.xinyuan.gas.ui.theme.GreenSuccess
import com.xinyuan.gas.ui.theme.InkPrimary
import com.xinyuan.gas.ui.theme.InkSecondary
import com.xinyuan.gas.ui.theme.OrangeAccent
import com.xinyuan.gas.ui.theme.OrangeSoft
import com.xinyuan.gas.ui.theme.RedDanger
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class CloudTab(val label: String, val icon: ImageVector) {
    HOME("首页", Icons.Default.Home),
    REGISTER("登记", Icons.Default.Add),
    RECORDS("记录", Icons.Default.Description),
    PROFILE("我的", Icons.Default.Person)
}

@Composable
fun CloudAppRoot(viewModel: InspectionViewModel) {
    val context = LocalContext.current
    val settings by viewModel.cloudSettings.collectAsState()
    val legacyCount by viewModel.terminalTotalCount.collectAsState(initial = 0)
    val legacyUnsynced by viewModel.terminalUnsyncedCount.collectAsState(initial = 0)
    val migrationRunning by viewModel.legacyMigrationRunning.collectAsState()
    val migrationPrefs = remember {
        context.getSharedPreferences("legacy_migration_ui", Context.MODE_PRIVATE)
    }
    var migrationAcknowledged by rememberSaveable {
        mutableStateOf(migrationPrefs.getBoolean("acknowledged", false))
    }
    var showForm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.dataActionMessage.collectLatest { message ->
            if (message.isNotBlank()) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    when {
        !settings.isLoggedIn -> LoginScreen(viewModel)
        legacyCount > 0 && !migrationAcknowledged -> LegacyMigrationScreen(
            settings = settings,
            totalCount = legacyCount,
            unsyncedCount = legacyUnsynced,
            running = migrationRunning,
            onMigrate = viewModel::migrateLegacyRecords,
            onClear = {
                viewModel.clearMigratedLegacyData()
                migrationAcknowledged = true
                migrationPrefs.edit().putBoolean("acknowledged", true).apply()
            },
            onContinue = {
                migrationAcknowledged = true
                migrationPrefs.edit().putBoolean("acknowledged", true).apply()
            }
        )
        showForm -> TerminalHazardFormScreen(
            viewModel = viewModel,
            onBack = {
                showForm = false
                viewModel.refreshCloudRecords()
            }
        )
        else -> CloudShell(
            viewModel = viewModel,
            settings = settings,
            legacyCount = legacyCount,
            legacyUnsynced = legacyUnsynced,
            onNewRecord = { showForm = true },
            onShowMigration = {
                migrationAcknowledged = false
                migrationPrefs.edit().putBoolean("acknowledged", false).apply()
            }
        )
    }
}

@Composable
private fun LoginScreen(viewModel: InspectionViewModel) {
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val loggingIn by viewModel.cloudLoginInProgress.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GrayBackground)
            .safeDrawingPadding()
            .padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.Center
    ) {
        BrandLogo(large = true)
        Spacer(Modifier.height(42.dp))
        Text(
            text = "欢迎回来",
            color = InkPrimary,
            fontFamily = FontFamily.Serif,
            fontSize = 34.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "登录后开始今天的现场检查。",
            color = InkSecondary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 28.dp)
        )
        LoginField(
            label = "手机号",
            value = phone,
            onValueChange = { phone = it },
            keyboardType = KeyboardType.Phone
        )
        Spacer(Modifier.height(14.dp))
        LoginField(
            label = "密码",
            value = password,
            onValueChange = { password = it },
            isPassword = true
        )
        Button(
            onClick = {
                viewModel.loginCloud(
                    HttpCloudSyncClient.DEFAULT_BASE_URL,
                    phone,
                    password
                )
            },
            enabled = phone.isNotBlank() && password.isNotBlank() && !loggingIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(top = 2.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
        ) {
            if (loggingIn) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("登录", style = MaterialTheme.typography.labelLarge)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(16.dp))
            Text(
                text = " 账号由管理员在后台分配",
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            text = "安全连接 · www.gashbxy.com",
            color = GrayTertiaryText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 10.dp)
        )
    }
}

@Composable
private fun LoginField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false
) {
    Column {
        Text(label, color = InkPrimary, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(7.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandGreen,
                unfocusedBorderColor = GraySeparator,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                cursorColor = BrandGreen
            )
        )
    }
}

@Composable
private fun LegacyMigrationScreen(
    settings: CloudSettings,
    totalCount: Int,
    unsyncedCount: Int,
    running: Boolean,
    onMigrate: () -> Unit,
    onClear: () -> Unit,
    onContinue: () -> Unit
) {
    val migrated = (totalCount - unsyncedCount).coerceAtLeast(0)
    val progress = if (totalCount == 0) 1f else migrated.toFloat() / totalCount.toFloat()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GrayBackground)
            .safeDrawingPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("一次性迁移", color = BrandGreenStrong, style = MaterialTheme.typography.labelMedium)
            Text(
                "发现旧版数据",
                color = InkPrimary,
                fontFamily = FontFamily.Serif,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "登录账号：${settings.userName}\n归属公司：${settings.companyName}",
                color = InkSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        item {
            CloudCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MigrationNumber(totalCount.toString(), "旧版记录")
                    MigrationNumber(migrated.toString(), "已上传")
                    MigrationNumber(unsyncedCount.toString(), "待迁移")
                }
            }
        }
        item {
            CloudCard {
                Text("迁移进度", color = InkPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(14.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = GreenSuccess,
                    trackColor = GraySurface
                )
                Text(
                    if (unsyncedCount == 0) "云端核对完成" else "$migrated / $totalCount 条记录已迁移",
                    color = if (unsyncedCount == 0) GreenSuccess else InkSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
        item {
            Surface(
                color = BrandGreenSoft,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = GreenSuccess)
                    Text(
                        "迁移完成并核对成功前不会删除旧数据。中断后可继续，UUID 会防止重复记录。",
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }
        }
        item {
            if (unsyncedCount > 0) {
                Button(
                    onClick = onMigrate,
                    enabled = !running,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
                ) {
                    if (running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Text("  正在迁移")
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Text("  开始迁移")
                    }
                }
            } else {
                Button(
                    onClick = onClear,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Text("  核对完成并清理本机旧数据")
                }
            }
            TextButton(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text(if (unsyncedCount > 0) "稍后处理，先进入 APP" else "暂不清理，进入 APP")
            }
        }
    }
}

@Composable
private fun MigrationNumber(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = InkPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(label, color = InkSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CloudShell(
    viewModel: InspectionViewModel,
    settings: CloudSettings,
    legacyCount: Int,
    legacyUnsynced: Int,
    onNewRecord: () -> Unit,
    onShowMigration: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(CloudTab.HOME) }
    Scaffold(
        containerColor = GrayBackground,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                CloudTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = {
                            if (tab == CloudTab.REGISTER) onNewRecord() else selectedTab = tab
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandGreenStrong,
                            selectedTextColor = BrandGreenStrong,
                            indicatorColor = BrandGreenSoft,
                            unselectedIconColor = GrayTertiaryText,
                            unselectedTextColor = GrayTertiaryText
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                CloudTab.HOME -> CloudHomeScreen(viewModel, settings, onNewRecord) {
                    selectedTab = CloudTab.RECORDS
                }
                CloudTab.RECORDS -> CloudRecordsScreen(viewModel)
                CloudTab.PROFILE -> CloudProfileScreen(
                    viewModel = viewModel,
                    settings = settings,
                    legacyCount = legacyCount,
                    legacyUnsynced = legacyUnsynced,
                    onShowMigration = onShowMigration
                )
                CloudTab.REGISTER -> Unit
            }
        }
    }
}

@Composable
private fun CloudHomeScreen(
    viewModel: InspectionViewModel,
    settings: CloudSettings,
    onNewRecord: () -> Unit,
    onOpenRecords: () -> Unit
) {
    val records by viewModel.cloudRecords.collectAsState()
    val loading by viewModel.cloudRecordsLoading.collectAsState()
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date()) }
    val todayCount = records.count { it.inspectionDate == today }
    val pendingCount = records.count { it.status == "pending" }

    LaunchedEffect(Unit) { viewModel.refreshCloudRecords() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GrayBackground),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(today, color = GrayTertiaryText, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "上午好，${settings.userName}",
                        color = InkPrimary,
                        fontFamily = FontFamily.Serif,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Surface(shape = CircleShape, color = InkPrimary) {
                    Text(
                        settings.userName.take(1).ifBlank { "鑫" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(13.dp)
                    )
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (viewModel.isNetworkAvailable()) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = if (viewModel.isNetworkAvailable()) GreenSuccess else RedDanger,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    if (viewModel.isNetworkAvailable()) "  已连接云端 · 仅显示我的数据" else "  网络不可用 · 新记录不能提交",
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = InkPrimary)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("终端用户隐患核查", color = BrandGreenSoft, style = MaterialTheme.typography.labelMedium)
                    Text(
                        "开始新的现场检查",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        "信息、定位和照片会实时保存到云端。",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
                    )
                    Button(
                        onClick = onNewRecord,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("  新增检查")
                    }
                }
            }
        }
        item {
            Text("今日进度", color = InkPrimary, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniStat(todayCount.toString(), "今日完成", Modifier.weight(1f))
                MiniStat(pendingCount.toString(), "待整改", Modifier.weight(1f), OrangeAccent)
                MiniStat(records.size.toString(), "我的记录", Modifier.weight(1f))
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("最近记录", color = InkPrimary, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onOpenRecords, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text("查看全部", color = BrandGreenStrong)
                }
            }
        }
        if (loading && records.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandGreen)
                }
            }
        } else {
            items(records.take(3), key = { it.id }) { record ->
                CloudRecordCard(record)
            }
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String, modifier: Modifier, color: Color = InkPrimary) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, GraySeparator)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(label, color = InkSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CloudRecordsScreen(viewModel: InspectionViewModel) {
    val records by viewModel.cloudRecords.collectAsState()
    val loading by viewModel.cloudRecordsLoading.collectAsState()
    var keyword by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    val filtered = records.filter { record ->
        val matchesKeyword = keyword.isBlank() ||
            record.customerName.contains(keyword, true) ||
            record.customerPhone.contains(keyword, true) ||
            record.customerAddress.contains(keyword, true)
        val matchesStatus = when (status) {
            "pending" -> record.status == "pending"
            "done" -> record.status != "pending"
            else -> true
        }
        matchesKeyword && matchesStatus
    }

    LaunchedEffect(Unit) { viewModel.refreshCloudRecords() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GrayBackground),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("仅显示我录入的数据", color = BrandGreenStrong, style = MaterialTheme.typography.labelMedium)
            Text(
                "我的记录",
                color = InkPrimary,
                fontFamily = FontFamily.Serif,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        item {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索用户、电话或地址") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandGreen,
                    unfocusedBorderColor = GraySeparator,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusFilter("全部 ${records.size}", status.isBlank()) { status = "" }
                StatusFilter("待整改 ${records.count { it.status == "pending" }}", status == "pending") { status = "pending" }
                StatusFilter("已完成 ${records.count { it.status != "pending" }}", status == "done") { status = "done" }
            }
        }
        if (loading && records.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandGreen)
                }
            }
        } else if (filtered.isEmpty()) {
            item {
                Text(
                    "没有符合条件的记录",
                    color = InkSecondary,
                    modifier = Modifier.padding(vertical = 36.dp)
                )
            }
        } else {
            items(filtered, key = { it.id }) { CloudRecordCard(it) }
        }
        item {
            Surface(color = GraySurface, shape = RoundedCornerShape(8.dp)) {
                Text(
                    "数据下载请登录 www.gashbxy.com/admin/",
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusFilter(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) BrandGreenSoft else Color.White,
        border = BorderStroke(1.dp, if (selected) BrandGreenSoft else GraySeparator)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(
                text,
                color = if (selected) BrandGreenStrong else InkSecondary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun CloudRecordCard(record: CloudRecordSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, GraySeparator)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    record.customerName,
                    color = InkPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusBadge(record.status)
            }
            Text(
                record.customerAddress,
                color = InkSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 7.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${record.inspectionDate} · ${record.checkResult}",
                color = GrayTertiaryText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val pending = status == "pending"
    Surface(
        color = if (pending) OrangeSoft else BrandGreenSoft,
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            if (pending) "待整改" else "已完成",
            color = if (pending) OrangeAccent else GreenSuccess,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun CloudProfileScreen(
    viewModel: InspectionViewModel,
    settings: CloudSettings,
    legacyCount: Int,
    legacyUnsynced: Int,
    onShowMigration: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GrayBackground),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("ACCOUNT", color = BrandGreenStrong, style = MaterialTheme.typography.labelMedium)
            Text(
                "我的账号",
                color = InkPrimary,
                fontFamily = FontFamily.Serif,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        item {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = InkPrimary)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = CircleShape, color = BrandGreen) {
                        Text(
                            settings.userName.take(1).ifBlank { "鑫" },
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(15.dp)
                        )
                    }
                    Column(modifier = Modifier.padding(start = 14.dp)) {
                        Text(settings.userName, color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Text(settings.companyName, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyMedium)
                        Text("APP 账号 · 仅本人数据", color = BrandGreenSoft, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            CloudCard {
                AccountInfoRow("手机号", maskPhone(settings.userPhone))
                AccountInfoRow("数据权限", "仅本人录入")
                AccountInfoRow("云端状态", if (viewModel.isNetworkAvailable()) "已连接" else "未连接", GreenSuccess)
            }
        }
        item {
            CloudCard {
                ProfileAction(Icons.Default.Key, "修改密码", "请在电脑后台操作")
                if (legacyCount > 0) {
                    HorizontalDivider(color = GraySeparator)
                    ProfileAction(
                        Icons.Default.Sync,
                        "历史数据迁移",
                        if (legacyUnsynced == 0) "已完成" else "待迁移 $legacyUnsynced 条",
                        onClick = onShowMigration
                    )
                }
                HorizontalDivider(color = GraySeparator)
                ProfileAction(Icons.Default.Shield, "关于应用", "云端版 v2.0")
            }
        }
        item {
            OutlinedButton(
                onClick = viewModel::logoutCloud,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, GraySeparator),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RedDanger)
            ) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Text("  退出登录")
            }
        }
        item {
            Text(
                "手机端不提供数据导出与本地业务备份。",
                color = GrayTertiaryText,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun CloudCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, GraySeparator)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun AccountInfoRow(label: String, value: String, valueColor: Color = InkPrimary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = InkSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProfileAction(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = BrandGreenStrong, modifier = Modifier.size(21.dp))
        Text(title, color = InkPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 11.dp).weight(1f))
        Text(detail, color = GrayTertiaryText, style = MaterialTheme.typography.bodySmall)
        if (onClick != null) {
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = GrayTertiaryText, modifier = Modifier.padding(start = 6.dp).size(18.dp))
        }
    }
}

private fun maskPhone(phone: String): String =
    if (phone.length >= 7) phone.take(3) + " **** " + phone.takeLast(4) else phone.ifBlank { "-" }
