package com.xinyuan.gas.ui.cloud

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.xinyuan.gas.reminder.RectificationReminder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.xinyuan.gas.BuildConfig
import com.xinyuan.gas.data.sync.CloudRecordDetail
import com.xinyuan.gas.data.sync.CloudRecordHazard
import com.xinyuan.gas.data.sync.CloudRecordPhoto
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class CloudTab(val label: String, val icon: ImageVector) {
    HOME("首页", Icons.Default.Home),
    REGISTER("登记", Icons.Default.Add),
    RECORDS("记录", Icons.Default.Description),
    PROFILE("我的", Icons.Default.Person)
}

@Composable
fun CloudAppRoot(viewModel: InspectionViewModel, openPendingRequest: Int = 0) {
    val context = LocalContext.current
    val settings by viewModel.cloudSettings.collectAsState()
    ReminderPermission(settings.isLoggedIn)
    val legacyCount by viewModel.terminalTotalCount.collectAsState(initial = 0)
    val legacyUnsynced by viewModel.terminalUnsyncedCount.collectAsState(initial = 0)
    val migrationRunning by viewModel.legacyMigrationRunning.collectAsState()
    val migrationPrefs = remember {
        context.getSharedPreferences("legacy_migration_ui", Context.MODE_PRIVATE)
    }
    var migrationAcknowledged by rememberSaveable {
        mutableStateOf(migrationPrefs.getBoolean("acknowledged", false))
    }
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
        else -> CloudShell(
            viewModel = viewModel,
            settings = settings,
            legacyCount = legacyCount,
            legacyUnsynced = legacyUnsynced,
            openPendingRequest = openPendingRequest,
            onShowMigration = {
                migrationAcknowledged = false
                migrationPrefs.edit().putBoolean("acknowledged", false).apply()
            }
        )
    }
}

@Composable
private fun ReminderPermission(isLoggedIn: Boolean) {
    val context = LocalContext.current
    val permissionPrefs = remember { context.getSharedPreferences("reminder_permission", Context.MODE_PRIVATE) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) RectificationReminder.checkAfterPermissionGranted(context)
    }
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn && Build.VERSION.SDK_INT >= 33 &&
            !RectificationReminder.notificationsEnabled(context) && !permissionPrefs.getBoolean("asked", false)) {
            permissionPrefs.edit().putBoolean("asked", true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
                    if (unsyncedCount == 0) {
                        "云端核对完成"
                    } else if (running) {
                        "$migrated / $totalCount 条记录正在自动上传"
                    } else {
                        "$migrated / $totalCount 条记录等待自动续传"
                    },
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
                        "登录后自动上传旧版记录；网络中断后会自动续传。核对成功前不会删除旧数据，UUID 会防止重复记录。",
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
                        Text("  立即重试")
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CloudShell(
    viewModel: InspectionViewModel,
    settings: CloudSettings,
    legacyCount: Int,
    legacyUnsynced: Int,
    openPendingRequest: Int,
    onShowMigration: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(CloudTab.HOME) }
    var recordsInitialStatus by rememberSaveable { mutableStateOf("") }
    var formSessionKey by rememberSaveable { mutableStateOf(0) }
    var recordOverlayActive by rememberSaveable { mutableStateOf(false) }
    var handledPendingRequest by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(openPendingRequest) {
        if (openPendingRequest > handledPendingRequest) {
            handledPendingRequest = openPendingRequest
            recordsInitialStatus = "pending"
            recordOverlayActive = false
            selectedTab = CloudTab.RECORDS
            viewModel.refreshCloudRecords()
        }
    }
    val keyboardVisible = WindowInsets.isImeVisible
    BackHandler(enabled = selectedTab != CloudTab.HOME && !recordOverlayActive) {
        if (selectedTab == CloudTab.REGISTER) {
            formSessionKey += 1
        }
        selectedTab = CloudTab.HOME
    }
    Scaffold(
        containerColor = GrayBackground,
        bottomBar = {
            if (!keyboardVisible && !recordOverlayActive) {
                NavigationBar(containerColor = Color.White) {
                    CloudTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = {
                                if (tab == CloudTab.RECORDS) recordsInitialStatus = ""
                                selectedTab = tab
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
        }
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = padding.calculateStartPadding(layoutDirection),
                    top = if (selectedTab == CloudTab.REGISTER) 0.dp else padding.calculateTopPadding(),
                    end = padding.calculateEndPadding(layoutDirection),
                    bottom = padding.calculateBottomPadding()
                )
        ) {
            when (selectedTab) {
                CloudTab.HOME -> CloudHomeScreen(
                    viewModel = viewModel,
                    settings = settings,
                    onNewRecord = { selectedTab = CloudTab.REGISTER },
                    onOpenRecords = {
                        recordsInitialStatus = ""
                        selectedTab = CloudTab.RECORDS
                    },
                    onOpenPending = {
                        recordsInitialStatus = "pending"
                        selectedTab = CloudTab.RECORDS
                    }
                )
                CloudTab.RECORDS -> key(openPendingRequest) {
                    CloudRecordsScreen(
                        viewModel = viewModel,
                        initialStatus = recordsInitialStatus,
                        onOverlayVisibilityChanged = { recordOverlayActive = it }
                    )
                }
                CloudTab.PROFILE -> CloudProfileScreen(
                    viewModel = viewModel,
                    settings = settings,
                    legacyCount = legacyCount,
                    legacyUnsynced = legacyUnsynced,
                    onShowMigration = onShowMigration
                )
                CloudTab.REGISTER -> Unit
            }
            key(formSessionKey) {
                TerminalHazardFormScreen(
                    viewModel = viewModel,
                    modifier = if (selectedTab == CloudTab.REGISTER) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.size(0.dp)
                    },
                    onBack = {
                        formSessionKey += 1
                        selectedTab = CloudTab.HOME
                    },
                    onSaved = {
                        formSessionKey += 1
                        viewModel.refreshCloudRecords()
                        selectedTab = CloudTab.RECORDS
                    }
                )
            }
        }
    }
}

@Composable
private fun CloudHomeScreen(
    viewModel: InspectionViewModel,
    settings: CloudSettings,
    onNewRecord: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenPending: () -> Unit
) {
    val records by viewModel.cloudRecords.collectAsState()
    val loading by viewModel.cloudRecordsLoading.collectAsState()
    val connection by viewModel.cloudConnectionState.collectAsState()
    val today = remember { SimpleDateFormat("yyyy-MM-dd EEEE", Locale.CHINA).format(Date()) }
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..10 -> "上午好"
            in 11..13 -> "中午好"
            in 14..17 -> "下午好"
            else -> "晚上好"
        }
    }
    val networkAvailable = viewModel.isNetworkAvailable()
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
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(today, color = GrayTertiaryText, style = MaterialTheme.typography.bodySmall)
                    Surface(shape = CircleShape, color = InkPrimary) {
                        Text(
                            settings.userName.take(1).ifBlank { "鑫" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
                Text(
                    "$greeting，${settings.userName}",
                    color = InkPrimary,
                    fontFamily = FontFamily.Serif,
                    fontSize = 26.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 8.dp, end = 8.dp)
                )
            }
        }
        item {
            val connected = networkAvailable && connection.verified == true
            val statusText = when {
                !networkAvailable -> "网络不可用，记录暂时不能上传"
                connection.verified == true -> "云端连接正常 · 数据已同步"
                connection.verified == false -> "云端连接异常 · 点击重新检查"
                else -> "正在验证云端连接"
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.refreshCloudRecords() },
                color = if (connected) BrandGreenSoft else Color.White,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (connected) BrandGreenSoft else GraySeparator)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (connected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = if (connected) GreenSuccess else RedDanger,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        statusText,
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
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
        if (pendingCount > 0) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenPending),
                    color = OrangeSoft,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, OrangeSoft)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = OrangeAccent)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 11.dp)
                        ) {
                            Text("处理待整改记录", color = InkPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                "当前 $pendingCount 条，完成整改后自动形成闭环",
                                color = InkSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = OrangeAccent)
                    }
                }
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
                CloudRecordCard(record, onClick = onOpenRecords)
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
private fun CloudRecordsScreen(
    viewModel: InspectionViewModel,
    initialStatus: String,
    onOverlayVisibilityChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.cloudSettings.collectAsState()
    val records by viewModel.cloudRecords.collectAsState()
    val loading by viewModel.cloudRecordsLoading.collectAsState()
    val rectificationSubmitting by viewModel.cloudRectificationInProgress.collectAsState()
    val recordsListState = rememberLazyListState()
    val recordsScope = rememberCoroutineScope()
    val showBackToTop by remember {
        derivedStateOf { recordsListState.firstVisibleItemIndex > 0 || recordsListState.firstVisibleItemScrollOffset > 0 }
    }
    var keyword by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf(initialStatus) }
    var detailRecordId by rememberSaveable { mutableStateOf<Long?>(null) }
    var rectificationDetail by remember { mutableStateOf<CloudRecordDetail?>(null) }
    val detail by viewModel.cloudRecordDetail.collectAsState()
    val detailLoading by viewModel.cloudRecordDetailLoading.collectAsState()
    val scopeDescription = when (settings.role) {
        "hq_admin" -> "显示当前账号可查看的全部公司记录"
        "company_admin" -> "显示当前账号可查看的本公司记录"
        else -> if (settings.canViewCompanyRecords) "显示当前账号可查看的本公司记录" else "仅显示我录入的数据"
    }
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
    LaunchedEffect(initialStatus) { status = initialStatus }
    LaunchedEffect(keyword, status) { recordsListState.scrollToItem(0) }
    LaunchedEffect(Unit) {
        viewModel.cloudRectificationResult.collectLatest { result ->
            result.fold(
                onSuccess = {
                    rectificationDetail = null
                    detailRecordId = null
                    onOverlayVisibilityChanged(false)
                    Toast.makeText(context, "整改已提交，记录已闭环", Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    Toast.makeText(
                        context,
                        "整改提交失败：${error.message ?: "未知错误"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = recordsListState,
        modifier = Modifier
            .fillMaxSize()
            .background(GrayBackground),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(scopeDescription, color = BrandGreenStrong, style = MaterialTheme.typography.labelMedium)
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
            items(filtered, key = { it.id }) { record ->
                CloudRecordCard(record) {
                    detailRecordId = record.id
                    onOverlayVisibilityChanged(true)
                    viewModel.loadCloudRecordDetail(record.id)
                }
            }
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

    AnimatedVisibility(
        visible = showBackToTop && detailRecordId == null && rectificationDetail == null,
        modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
    ) {
        ExtendedFloatingActionButton(
            onClick = { recordsScope.launch { recordsListState.animateScrollToItem(0) } },
            icon = { Icon(Icons.Default.VerticalAlignTop, contentDescription = null) },
            text = { Text("回到顶部") },
            containerColor = BrandGreen,
            contentColor = Color.White
        )
    }
    }

    AnimatedVisibility(
        visible = detailRecordId != null,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(animationSpec = tween(120)) +
            scaleIn(initialScale = 0.98f, animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(90)) +
            scaleOut(targetScale = 0.99f, animationSpec = tween(90))
    ) {
        CloudRecordDetailOverlay(
            detail = detail,
            loading = detailLoading,
            onDismiss = {
                detailRecordId = null
                onOverlayVisibilityChanged(false)
                viewModel.clearCloudRecordDetail()
            },
            onRectify = { record ->
                detailRecordId = null
                rectificationDetail = record
            }
        )
    }

    AnimatedVisibility(
        visible = rectificationDetail != null,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(animationSpec = tween(durationMillis = 140)),
        exit = fadeOut(animationSpec = tween(durationMillis = 90))
    ) {
        rectificationDetail?.let { record ->
            CloudRectificationDialog(
                detail = record,
                defaultPerson = settings.userName,
                submitting = rectificationSubmitting,
                onDismiss = {
                    rectificationDetail = null
                    detailRecordId = record.id
                },
                onSubmit = viewModel::rectifyCloudRecord
            )
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
private fun CloudRecordCard(record: CloudRecordSummary, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
private fun CloudRecordDetailOverlay(
    detail: CloudRecordDetail?,
    loading: Boolean,
    onDismiss: () -> Unit,
    onRectify: (CloudRecordDetail) -> Unit
) {
    var previewPhoto by remember(detail?.id) { mutableStateOf<CloudRecordPhoto?>(null) }
    BackHandler(onBack = onDismiss)
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = {}),
        color = Color.Black.copy(alpha = 0.42f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.82f),
                color = Color.White,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .padding(start = 20.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            detail?.customerName ?: "记录详情",
                            color = InkPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "关闭详情", tint = InkPrimary)
                        }
                    }
                    HorizontalDivider(color = GraySeparator)
                    if (loading || detail == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = BrandGreen)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 20.dp,
                                vertical = 14.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            item { DetailLine("电话", detail.customerPhone) }
                            item { DetailLine("用户类型", detail.customerType) }
                            item { DetailLine("地址", detail.customerAddress) }
                            item { DetailLine("检查日期", detail.inspectionDate) }
                            item { DetailLine("检查人", detail.inspectorName) }
                            item {
                                DetailLine(
                                    "检查归属",
                                    listOf(detail.regionName, detail.storeName)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" / ")
                                        .ifBlank { "公司级" }
                                )
                            }
                            item { DetailLine("配送员", detail.deliveryPerson) }
                            item { DetailLine("检查结果", detail.checkResult) }
                            if (detail.hazards.isNotEmpty()) {
                                item { DetailLine("隐患项目", detail.hazards.joinToString("；") { it.name }) }
                            }
                            if (detail.gasSourceIssues.isNotEmpty()) {
                                item { DetailLine("气源排查", detail.gasSourceIssues.joinToString("；")) }
                            }
                            if (detail.rectificationDate.isNotBlank()) {
                                item { DetailLine("整改日期", detail.rectificationDate) }
                            }
                            if (detail.rectificationPerson.isNotBlank()) {
                                item { DetailLine("整改人", detail.rectificationPerson) }
                            }
                            if (detail.rectificationNote.isNotBlank()) {
                                item { DetailLine("整改情况", detail.rectificationNote) }
                            }
                            item { DetailLine("照片", "${detail.photos.size} 张") }
                            cloudPhotoGroups(detail).forEach { group ->
                                item {
                                    CloudPhotoGroup(
                                        title = group.title,
                                        photos = group.photos,
                                        onPreview = { previewPhoto = it }
                                    )
                                }
                            }
                            if (detail.remark.isNotBlank()) item { DetailLine("备注", detail.remark) }
                        }
                    }
                    HorizontalDivider(color = GraySeparator)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (detail?.status == "pending") {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(0.42f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, GraySeparator)
                            ) {
                                Text("关闭", color = InkSecondary)
                            }
                            Button(
                                onClick = { onRectify(detail) },
                                modifier = Modifier
                                    .weight(0.58f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
                            ) {
                                Text("开始整改")
                            }
                        } else {
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
                            ) {
                                Text("关闭")
                            }
                        }
                    }
                }
            }
        }
    }

    previewPhoto?.let { photo ->
        CloudPhotoPreviewDialog(
            photo = photo,
            title = cloudPhotoTitle(photo, detail?.hazards.orEmpty()),
            onDismiss = { previewPhoto = null }
        )
    }
}

private data class CloudPhotoGroupUi(
    val title: String,
    val photos: List<CloudRecordPhoto>
)

private fun cloudPhotoGroups(detail: CloudRecordDetail): List<CloudPhotoGroupUi> =
    detail.photos
        .groupBy { cloudPhotoTitle(it, detail.hazards) }
        .map { (title, photos) -> CloudPhotoGroupUi(title, photos) }

private fun cloudPhotoTitle(
    photo: CloudRecordPhoto,
    hazards: List<CloudRecordHazard>
): String = when (photo.type) {
    "storefront" -> "门头照片"
    "cylinder_qr" -> "钢瓶二维码照片"
    "cylinder" -> "钢瓶照片"
    "rectification" -> "整改照片"
    "alarm" -> "可燃气体报警器照片"
    "hose" -> "软管照片"
    "environment" -> "用气环境照片"
    "valve" -> "减压阀照片"
    "guide" -> "安全用气指南照片"
    "hazard" -> {
        val hazard = hazards.firstOrNull { it.code == photo.hazardCode }
        if (hazard == null) {
            "隐患照片"
        } else {
            listOf(hazard.category, hazard.name)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" · ")
                .plus("照片")
        }
    }
    else -> "其他照片"
}

@Composable
private fun CloudPhotoGroup(
    title: String,
    photos: List<CloudRecordPhoto>,
    onPreview: (CloudRecordPhoto) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$title（${photos.size}）",
            color = InkPrimary,
            style = MaterialTheme.typography.labelMedium
        )
        photos.chunked(2).forEach { rowPhotos ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowPhotos.forEach { photo ->
                    CloudPhotoThumbnail(
                        photo = photo,
                        onClick = { onPreview(photo) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowPhotos.size == 1) {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudPhotoThumbnail(
    photo: CloudRecordPhoto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SubcomposeAsyncImage(
        model = photo.url,
        contentDescription = "查看照片",
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(GraySurface)
            .clickable(onClick = onClick),
        contentScale = ContentScale.Crop,
        loading = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = BrandGreen,
                    strokeWidth = 2.dp
                )
            }
        },
        error = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("照片加载失败", color = GrayTertiaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
    )
}

@Composable
private fun CloudPhotoPreviewDialog(
    photo: CloudRecordPhoto,
    title: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xF2181818)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭照片", tint = Color.White)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SubcomposeAsyncImage(
                        model = photo.url,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        loading = {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        },
                        error = {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("照片加载失败，请检查网络后重试", color = Color.White)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, color = GrayTertiaryText, style = MaterialTheme.typography.bodySmall)
        Text(value.ifBlank { "-" }, color = InkPrimary, style = MaterialTheme.typography.bodyMedium)
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
    val context = LocalContext.current
    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }
    val changingPassword by viewModel.cloudPasswordChangeInProgress.collectAsState()
    val connection by viewModel.cloudConnectionState.collectAsState()
    val dataScope = when (settings.role) {
        "hq_admin" -> "全部公司数据"
        "company_admin" -> "本公司数据"
        else -> if (settings.canViewCompanyRecords) "本公司数据" else "仅本人录入"
    }

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
                        Text("APP 账号 · $dataScope", color = BrandGreenSoft, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            CloudCard {
                AccountInfoRow("手机号", maskPhone(settings.userPhone))
                AccountInfoRow("数据权限", dataScope)
                AccountInfoRow(
                    "云端状态",
                    when {
                        !viewModel.isNetworkAvailable() -> "网络不可用"
                        connection.verified == true -> "连接正常"
                        connection.verified == false -> "连接异常"
                        else -> "正在检查"
                    },
                    if (connection.verified == true) GreenSuccess else InkSecondary
                )
            }
        }
        item {
            CloudCard {
                ProfileAction(
                    Icons.Default.Key,
                    "修改密码",
                    "在手机端更新",
                    onClick = { showPasswordDialog = true }
                )
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
                ProfileAction(Icons.Default.Warning, "整改提醒", "每3天 · 通知设置", onClick = {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                })
                Text(
                    "未整改记录自检查日期满3天后提醒，之后每隔3天重复，整改完成后停止。请开启应用通知；断网或手机省电限制可能延后提醒。",
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                HorizontalDivider(color = GraySeparator)
                ProfileAction(Icons.Default.Shield, "关于应用", "云端版 v${BuildConfig.VERSION_NAME}")
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

    if (showPasswordDialog) {
        ChangePasswordDialog(
            changing = changingPassword,
            onDismiss = { if (!changingPassword) showPasswordDialog = false },
            onConfirm = viewModel::changeCloudPassword
        )
    }
}

@Composable
private fun ChangePasswordDialog(
    changing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var currentPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PasswordField("当前密码", currentPassword) { currentPassword = it }
                PasswordField("新密码（至少 8 位）", newPassword) { newPassword = it }
                PasswordField("再次输入新密码", confirmPassword) { confirmPassword = it }
                if (error.isNotBlank()) {
                    Text(error, color = RedDanger, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "修改成功后需要使用新密码重新登录。",
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !changing) {
                Text("取消", color = InkSecondary)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    error = when {
                        currentPassword.isBlank() -> "请输入当前密码"
                        newPassword.length < 8 -> "新密码至少 8 位"
                        newPassword != confirmPassword -> "两次输入的新密码不一致"
                        newPassword == currentPassword -> "新密码不能与当前密码相同"
                        else -> ""
                    }
                    if (error.isBlank()) onConfirm(currentPassword, newPassword)
                },
                enabled = !changing,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
            ) {
                if (changing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("确认修改")
                }
            }
        },
        shape = RoundedCornerShape(8.dp),
        containerColor = Color.White
    )
}

@Composable
private fun PasswordField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandGreen,
            unfocusedBorderColor = GraySeparator
        )
    )
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
