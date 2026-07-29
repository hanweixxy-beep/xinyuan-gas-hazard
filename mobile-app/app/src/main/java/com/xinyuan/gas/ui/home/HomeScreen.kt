package com.xinyuan.gas.ui.home

import android.content.Context
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xinyuan.gas.BuildConfig
import com.xinyuan.gas.data.model.TerminalHazardCatalog
import com.xinyuan.gas.data.model.TerminalHazardRecord
import com.xinyuan.gas.ui.about.AppGuideDialog
import com.xinyuan.gas.ui.components.BrandLogo
import com.xinyuan.gas.ui.components.IOSTopBar
import com.xinyuan.gas.ui.inspection.InspectionViewModel
import com.xinyuan.gas.ui.theme.BlueLight
import com.xinyuan.gas.ui.theme.BluePrimary
import com.xinyuan.gas.ui.theme.BrandGreen
import com.xinyuan.gas.ui.theme.BrandGreenSoft
import com.xinyuan.gas.ui.theme.BrandGreenStrong
import com.xinyuan.gas.ui.theme.GrayBackground
import com.xinyuan.gas.ui.theme.GraySecondaryText
import com.xinyuan.gas.ui.theme.GraySeparator
import com.xinyuan.gas.ui.theme.GraySurface
import com.xinyuan.gas.ui.theme.GreenSuccess
import com.xinyuan.gas.ui.theme.InkPrimary
import com.xinyuan.gas.ui.theme.OrangeAccent
import com.xinyuan.gas.ui.theme.OrangeSoft
import com.xinyuan.gas.ui.theme.RedDanger
import com.xinyuan.gas.ui.theme.RedSoft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: InspectionViewModel,
    onNavigateToBottledModule: () -> Unit,
    onNavigateToTerminalModule: () -> Unit,
    onNavigateToNewTerminal: () -> Unit,
    onNavigateToTerminalList: () -> Unit,
    onNavigateToDataManagement: () -> Unit,
    onNavigateToAppGuide: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("xinyuan_app_settings", Context.MODE_PRIVATE)
    }
    val cloudSettings by viewModel.cloudSettings.collectAsState()
    val todayCount by viewModel.terminalTodayCount.collectAsState(initial = 0)
    val pendingCount by viewModel.terminalPendingCount.collectAsState(initial = 0)
    var showGuideDialog by remember { mutableStateOf(false) }

    fun dismissGuideDialog() {
        preferences.edit()
            .putInt("last_seen_guide_version_code", BuildConfig.VERSION_CODE)
            .apply()
        showGuideDialog = false
    }

    LaunchedEffect(Unit) {
        showGuideDialog = preferences.getInt("last_seen_guide_version_code", 0) < BuildConfig.VERSION_CODE
    }

    if (showGuideDialog) {
        AppGuideDialog(onDismiss = ::dismissGuideDialog)
    }

    Scaffold(
        containerColor = GrayBackground,
        bottomBar = {
            WorkbenchNavigation(
                onNewInspection = onNavigateToNewTerminal,
                onRecords = onNavigateToTerminalList,
                onData = onNavigateToDataManagement
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            Spacer(Modifier.height(18.dp))
            WorkbenchHeader(title = "工作台")
            Spacer(Modifier.height(14.dp))
            AccountStrip(
                account = cloudSettings.userName.ifBlank { "本机模式" },
                status = if (cloudSettings.isLoggedIn) "云端已连接" else "尚未登录云端",
                connected = cloudSettings.isLoggedIn
            )
            Spacer(Modifier.height(14.dp))
            TaskOverviewCard(
                eyebrow = "今日完成",
                title = "现场检查",
                value = todayCount,
                description = if (pendingCount > 0) {
                    "当前仍有 $pendingCount 条隐患记录等待整改。"
                } else {
                    "当前没有待整改记录，继续保持。"
                }
            )
            SectionTitle("业务入口")
            ActionGrid(
                actions = listOf(
                    WorkspaceAction(
                        title = "终端隐患核查",
                        subtitle = "现场登记、照片与整改",
                        icon = Icons.Default.AssignmentTurnedIn,
                        tint = BrandGreen,
                        container = BrandGreenSoft,
                        primary = true,
                        onClick = onNavigateToTerminalModule
                    ),
                    WorkspaceAction(
                        title = "瓶装气隐患排查",
                        subtitle = "入户检查与整改",
                        icon = Icons.Default.LocalFireDepartment,
                        tint = OrangeAccent,
                        container = OrangeSoft,
                        onClick = onNavigateToBottledModule
                    ),
                    WorkspaceAction(
                        title = "数据与云端",
                        subtitle = "同步、备份和恢复",
                        icon = Icons.Default.CloudDone,
                        tint = BluePrimary,
                        container = BlueLight,
                        onClick = onNavigateToDataManagement
                    ),
                    WorkspaceAction(
                        title = "版本与说明",
                        subtitle = "操作指南与版本信息",
                        icon = Icons.Default.Info,
                        tint = GraySecondaryText,
                        container = GraySurface,
                        onClick = onNavigateToAppGuide
                    )
                )
            )
            Spacer(Modifier.height(24.dp))
            FooterText()
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
fun BottledInspectionHomeScreen(
    viewModel: InspectionViewModel,
    onBack: () -> Unit,
    onNavigateToNew: () -> Unit,
    onNavigateToList: () -> Unit,
    onNavigateToExport: () -> Unit
) {
    val totalCount by viewModel.totalCount.collectAsState(initial = 0)
    val pendingCount by viewModel.pendingCount.collectAsState(initial = 0)
    val rectifiedCount by viewModel.rectifiedCount.collectAsState(initial = 0)
    val todayCount by viewModel.todayCount.collectAsState(initial = 0)

    ModuleDashboardScaffold(title = "瓶装气隐患排查", onBack = onBack) {
        ModuleIdentity("瓶装液化石油气", "入户检查、隐患登记与整改")
        TaskOverviewCard(
            eyebrow = "全部记录",
            title = "瓶装气检查",
            value = totalCount,
            description = "今日新增 $todayCount 条，待整改 $pendingCount 条，已整改 $rectifiedCount 条。"
        )
        SectionTitle("快捷操作")
        ActionGrid(
            actions = listOf(
                WorkspaceAction("新建检查", "开始现场登记", Icons.Default.Add, BrandGreen, BrandGreenSoft, true, onNavigateToNew),
                WorkspaceAction("记录查询", "查看历史记录", Icons.AutoMirrored.Filled.List, BluePrimary, BlueLight, onClick = onNavigateToList),
                WorkspaceAction("数据导出", "生成 Excel 文件", Icons.Default.FileDownload, OrangeAccent, OrangeSoft, onClick = onNavigateToExport)
            )
        )
    }
}

@Composable
fun TerminalHazardHomeScreen(
    viewModel: InspectionViewModel,
    onBack: () -> Unit,
    onNavigateToNew: () -> Unit,
    onNavigateToList: () -> Unit,
    onNavigateToRectification: () -> Unit,
    onNavigateToExport: () -> Unit
) {
    val totalCount by viewModel.terminalTotalCount.collectAsState(initial = 0)
    val pendingCount by viewModel.terminalPendingCount.collectAsState(initial = 0)
    val todayCount by viewModel.terminalTodayCount.collectAsState(initial = 0)
    val recentRecords by viewModel.allTerminalRecords.collectAsState(initial = emptyList())

    ModuleDashboardScaffold(title = "终端隐患核查", onBack = onBack) {
        ModuleIdentity("终端用户隐患核查", "现场检查、照片留证与整改闭环")
        TaskOverviewCard(
            eyebrow = "今日完成",
            title = "现场核查",
            value = todayCount,
            description = "累计 $totalCount 条记录，当前有 $pendingCount 条隐患等待整改。"
        )
        SectionTitle("快捷操作")
        ActionGrid(
            actions = listOf(
                WorkspaceAction("新增检查", "三步完成现场登记", Icons.Default.Add, BrandGreen, BrandGreenSoft, true, onNavigateToNew),
                WorkspaceAction("记录查询", "查找历史检查记录", Icons.Default.ManageSearch, BluePrimary, BlueLight, onClick = onNavigateToList),
                WorkspaceAction("待整改", "$pendingCount 条记录需要处理", Icons.Default.Warning, OrangeAccent, OrangeSoft, onClick = onNavigateToRectification),
                WorkspaceAction("数据导出", "生成 Excel 文件", Icons.Default.FileDownload, GraySecondaryText, GraySurface, onClick = onNavigateToExport)
            )
        )
        if (recentRecords.isNotEmpty()) {
            SectionTitle("最近记录")
            RecentRecordList(recentRecords.take(3), onNavigateToList)
        }
    }
}

@Composable
private fun ModuleDashboardScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = { IOSTopBar(title = title, onBack = onBack) },
        containerColor = GrayBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            content()
            Spacer(Modifier.height(28.dp))
            FooterText()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WorkbenchHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                "淮北鑫远燃气",
                style = MaterialTheme.typography.labelMedium,
                color = BrandGreen
            )
            Text(title, style = MaterialTheme.typography.titleLarge, color = InkPrimary)
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(BrandGreen, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("鑫", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ModuleIdentity(title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BrandLogo()
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = InkPrimary)
            Text(description, style = MaterialTheme.typography.bodySmall, color = GraySecondaryText)
        }
    }
}

@Composable
private fun AccountStrip(account: String, status: String, connected: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandGreenSoft, RoundedCornerShape(12.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("当前账号", style = MaterialTheme.typography.bodySmall, color = GraySecondaryText)
            Text(account, style = MaterialTheme.typography.labelMedium, color = BrandGreenStrong)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(
                if (connected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                contentDescription = null,
                tint = if (connected) BrandGreen else GraySecondaryText,
                modifier = Modifier.size(18.dp)
            )
            Text(status, style = MaterialTheme.typography.bodySmall, color = GraySecondaryText)
        }
    }
}

@Composable
private fun TaskOverviewCard(
    eyebrow: String,
    title: String,
    value: Int,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = BrandGreen),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(eyebrow, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.82f))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Text(
                    value.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = InkPrimary,
        modifier = Modifier.padding(top = 22.dp, bottom = 10.dp)
    )
}

@Composable
private fun ActionGrid(actions: List<WorkspaceAction>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        actions.chunked(2).forEach { rowActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowActions.forEach { action ->
                    WorkspaceActionCard(action = action, modifier = Modifier.weight(1f))
                }
                if (rowActions.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WorkspaceActionCard(action: WorkspaceAction, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Card(
        modifier = modifier
            .heightIn(min = 130.dp)
            .border(
                width = 1.dp,
                color = if (action.primary) BrandGreen.copy(alpha = 0.24f) else GraySeparator,
                shape = shape
            )
            .clip(shape)
            .clickable(onClick = action.onClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (action.primary) BrandGreenSoft else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(action.container, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(action.icon, contentDescription = null, tint = action.tint, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.padding(top = 15.dp)) {
                Text(action.title, style = MaterialTheme.typography.titleMedium, color = InkPrimary)
                Text(
                    action.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = GraySecondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun RecentRecordList(records: List<TerminalHazardRecord>, onOpenList: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MM-dd", Locale.CHINA) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GraySeparator, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        records.forEachIndexed { index, record ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenList)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(GraySurface, RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        dateFormat.format(Date(record.inspectionDate)),
                        style = MaterialTheme.typography.bodySmall,
                        color = GraySecondaryText,
                        textAlign = TextAlign.Center
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        record.customerName.ifBlank { "未命名用户" },
                        style = MaterialTheme.typography.labelMedium,
                        color = InkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        record.customerAddress.ifBlank { record.customerPhone },
                        style = MaterialTheme.typography.bodySmall,
                        color = GraySecondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusBadge(TerminalHazardCatalog.displayResult(record))
            }
            if (index < records.lastIndex) {
                androidx.compose.material3.HorizontalDivider(color = GraySeparator)
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val isPending = status == TerminalHazardCatalog.RESULT_NOT_CLEARED
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (isPending) OrangeSoft else BrandGreenSoft
    ) {
        Text(
            text = if (isPending) "待整改" else "已完成",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (isPending) OrangeAccent else BrandGreenStrong,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun WorkbenchNavigation(
    onNewInspection: () -> Unit,
    onRecords: () -> Unit,
    onData: () -> Unit
) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, GraySeparator)
    ) {
        val colors = NavigationBarItemDefaults.colors(
            selectedIconColor = BrandGreen,
            selectedTextColor = BrandGreen,
            indicatorColor = BrandGreenSoft,
            unselectedIconColor = GraySecondaryText,
            unselectedTextColor = GraySecondaryText
        )
        NavigationBarItem(
            selected = true,
            onClick = {},
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("首页") },
            colors = colors
        )
        NavigationBarItem(
            selected = false,
            onClick = onNewInspection,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            label = { Text("登记") },
            colors = colors
        )
        NavigationBarItem(
            selected = false,
            onClick = onRecords,
            icon = { Icon(Icons.Default.ManageSearch, contentDescription = null) },
            label = { Text("记录") },
            colors = colors
        )
        NavigationBarItem(
            selected = false,
            onClick = onData,
            icon = { Icon(Icons.Default.Person, contentDescription = null) },
            label = { Text("我的") },
            colors = colors
        )
    }
}

@Composable
private fun FooterText() {
    Text(
        "淮北鑫远燃气有限公司市场部  韩永龙",
        style = MaterialTheme.typography.bodySmall,
        color = GraySecondaryText,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

private data class WorkspaceAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val container: Color,
    val primary: Boolean = false,
    val onClick: () -> Unit
)
