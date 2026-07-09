package com.xinyuan.gas.ui.home

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.xinyuan.gas.BuildConfig
import com.xinyuan.gas.ui.about.AppGuideDialog
import com.xinyuan.gas.ui.components.BrandLogo
import com.xinyuan.gas.ui.components.IOSTopBar
import com.xinyuan.gas.ui.inspection.InspectionViewModel
import com.xinyuan.gas.ui.theme.BluePrimary
import com.xinyuan.gas.ui.theme.GrayBackground
import com.xinyuan.gas.ui.theme.GraySecondaryText
import com.xinyuan.gas.ui.theme.GrayTertiaryText
import com.xinyuan.gas.ui.theme.GreenSuccess
import com.xinyuan.gas.ui.theme.OrangeAccent
import com.xinyuan.gas.ui.theme.RedDanger
import com.xinyuan.gas.util.ExportUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onNavigateToBottledModule: () -> Unit,
    onNavigateToTerminalModule: () -> Unit,
    onNavigateToDataManagement: () -> Unit,
    onNavigateToAppGuide: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("xinyuan_app_settings", Context.MODE_PRIVATE)
    }
    var showGuideDialog by remember { mutableStateOf(false) }

    fun dismissGuideDialog() {
        preferences.edit()
            .putInt("last_seen_guide_version_code", BuildConfig.VERSION_CODE)
            .apply()
        showGuideDialog = false
    }

    LaunchedEffect(Unit) {
        val lastSeenVersion = preferences.getInt("last_seen_guide_version_code", 0)
        showGuideDialog = lastSeenVersion < BuildConfig.VERSION_CODE
    }

    if (showGuideDialog) {
        AppGuideDialog(onDismiss = ::dismissGuideDialog)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GrayBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(28.dp))
        BrandLogo(large = true, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(20.dp))
        Text(
            "请选择业务功能",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
            color = Color(0xFF1C1C1E),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModuleCard(
                title = "瓶装液化石油气隐患排查登记",
                subtitle = "入户检查、整改、导出",
                icon = Icons.Default.LocalFireDepartment,
                color = OrangeAccent,
                onClick = onNavigateToBottledModule,
                modifier = Modifier.weight(1f)
            )
            ModuleCard(
                title = "终端隐患核查清单登记",
                subtitle = "清零核查、项目拍照",
                icon = Icons.Default.AssignmentTurnedIn,
                color = BluePrimary,
                onClick = onNavigateToTerminalModule,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))
        DataManagementEntry(onClick = onNavigateToDataManagement)
        Spacer(Modifier.height(10.dp))
        AppGuideEntry(onClick = onNavigateToAppGuide)

        Spacer(Modifier.height(28.dp))
        FooterText()
        Spacer(Modifier.height(24.dp))
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

    ModuleDashboardScaffold(title = "瓶装液化石油气隐患排查登记", onBack = onBack) {
        BrandHeader("瓶装液化石油气隐患排查登记")
        StatsRow(
            values = listOf(
                StatSpec("总记录", "$totalCount", BluePrimary, Icons.Default.Description),
                StatSpec("待整改", "$pendingCount", OrangeAccent, Icons.Default.Warning),
                StatSpec("已整改", "$rectifiedCount", GreenSuccess, Icons.Default.CheckCircle),
                StatSpec("今日新增", "$todayCount", BluePrimary, Icons.Default.Today)
            )
        )
        QuickActions(
            actions = listOf(
                ActionSpec("新建检查", Icons.Default.AddCircle, BluePrimary, onNavigateToNew),
                ActionSpec("记录列表", Icons.AutoMirrored.Filled.List, OrangeAccent, onNavigateToList),
                ActionSpec("数据导出", Icons.Default.FileDownload, GreenSuccess, onNavigateToExport)
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
    val clearedCount = (totalCount - pendingCount).coerceAtLeast(0)

    ModuleDashboardScaffold(title = "终端隐患核查清单登记", onBack = onBack) {
        BrandHeader("终端隐患核查清单登记")
        StatsRow(
            values = listOf(
                StatSpec("总记录", "$totalCount", BluePrimary, Icons.Default.Description),
                StatSpec("未清零", "$pendingCount", RedDanger, Icons.Default.Warning),
                StatSpec("已清零", "$clearedCount", GreenSuccess, Icons.Default.CheckCircle),
                StatSpec("今日新增", "$todayCount", BluePrimary, Icons.Default.Today)
            )
        )
        QuickActions(
            actions = listOf(
                ActionSpec("新增核查", Icons.Default.AddCircle, BluePrimary, onNavigateToNew),
                ActionSpec("清单列表", Icons.AutoMirrored.Filled.List, OrangeAccent, onNavigateToList),
                ActionSpec("隐患整改", Icons.Default.CheckCircle, RedDanger, onNavigateToRectification),
                ActionSpec("数据导出", Icons.Default.FileDownload, GreenSuccess, onNavigateToExport)
            )
        )
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
                .padding(horizontal = 16.dp)
        ) {
            content()
            Spacer(Modifier.height(34.dp))
            FooterText()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BrandHeader(title: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BrandLogo(
            large = true,
            modifier = Modifier.padding(top = 8.dp, bottom = 14.dp)
        )
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 24.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = Color(0xFF2C2C2E),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun StatsRow(values: List<StatSpec>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        values.forEach { spec ->
            DashboardStatCard(
                title = spec.title,
                value = spec.value,
                color = spec.color,
                icon = spec.icon,
                modifier = Modifier.weight(1f)
            )
        }
    }
    Spacer(Modifier.height(28.dp))
}

@Composable
private fun QuickActions(actions: List<ActionSpec>) {
    Text(
        "快捷操作",
        style = MaterialTheme.typography.labelMedium,
        color = GraySecondaryText,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        actions.forEach { spec ->
            DashboardActionCard(
                title = spec.title,
                icon = spec.icon,
                color = spec.color,
                onClick = spec.onClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ModuleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(206.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
                color = Color(0xFF1C1C1E),
                textAlign = TextAlign.Center,
                minLines = 2
            )
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = GraySecondaryText,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun DashboardStatCard(
    title: String,
    value: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(124.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 31.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
                color = color,
                maxLines = 1
            )
            Text(
                title,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                color = GraySecondaryText,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DashboardActionCard(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(122.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .aspectRatio(1f)
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(27.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                color = Color(0xFF1C1C1E),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DataManagementEntry(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(94.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(GreenSuccess.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(30.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "数据管理与备份",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF1C1C1E)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "备份、恢复、空间优化",
                    style = MaterialTheme.typography.bodySmall,
                    color = GraySecondaryText
                )
            }
        }
    }
}

@Composable
private fun AppGuideEntry(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(94.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(BluePrimary.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(30.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "版本与使用说明",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF1C1C1E)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "当前版本 ${BuildConfig.VERSION_NAME}，查看操作说明",
                    style = MaterialTheme.typography.bodySmall,
                    color = GraySecondaryText
                )
            }
        }
    }
}

@Composable
private fun FooterText() {
    Text(
        "淮北鑫远燃气市场部  韩永龙",
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
        color = Color(0xFF6B6B70),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

private data class StatSpec(
    val title: String,
    val value: String,
    val color: Color,
    val icon: ImageVector
)

private data class ActionSpec(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)
