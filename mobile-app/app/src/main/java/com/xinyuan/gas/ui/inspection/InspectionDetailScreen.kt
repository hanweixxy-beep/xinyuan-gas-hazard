package com.xinyuan.gas.ui.inspection

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.xinyuan.gas.R
import com.xinyuan.gas.ui.components.*
import com.xinyuan.gas.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionDetailScreen(
    viewModel: InspectionViewModel,
    recordId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val record by viewModel.getById(recordId).collectAsState(initial = null)
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    // 全屏图片预览
    var previewPhoto by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            IOSTopBar(
                title = "检查详情",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { onEdit(recordId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑", tint = BluePrimary)
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = RedDanger)
                    }
                }
            )
        },
        containerColor = GrayBackground
    ) { padding ->
        val r = record
        if (r == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BluePrimary)
            }
            return@Scaffold
        }

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
                    .padding(top = 8.dp, bottom = 12.dp)
            )

            // 状态标签
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(r.customerName, style = MaterialTheme.typography.headlineMedium)
                val statusColor = if (r.isRectified == "已整改") GreenSuccess else OrangeAccent
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        r.isRectified,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        color = statusColor,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            // 用户信息
            IOSGroupCard(title = "用户信息") {
                DetailRow("用户名", r.customerName)
                DetailRow("电话", r.customerPhone)
                DetailRow("用户类型", r.customerType)
                DetailRow("地址", r.customerAddress, showDivider = false)
            }

            // 检查信息
            IOSGroupCard(title = "检查信息") {
                DetailRow("检查人", r.inspectorName)
                DetailRow("检查日期", dateFormat.format(Date(r.inspectionDate)))
                // 照片
                if (r.photoPath.isNotEmpty()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("现场照片", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            r.photoPath.split(",").filter { it.isNotEmpty() }.forEach { uri ->
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "照片",
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { previewPhoto = uri },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }

            // 隐患部位
            IOSGroupCard(title = "隐患部位") {
                DetailRow("报警器", r.hazardAlarm)
                DetailRow("灶具", r.hazardStove)
                DetailRow("软管", r.hazardHose)
                DetailRow("接头", r.hazardConnector)
                DetailRow("阀门", r.hazardValve, showDivider = false)
            }

            // 用气情况
            IOSGroupCard(title = "用气情况") {
                DetailRow("管道气", if (r.hazardPipeline == "有") "是" else "否")
                DetailRow("用油", if (r.useOil) "是" else "否")
                DetailRow("用电", if (r.useElectric) "是" else "否")
                DetailRow("用液化气", if (r.useLPG) "是" else "否")
                DetailRow("月均用气量", r.monthlyGasUsage, showDivider = false)
            }

            // 配送信息
            IOSGroupCard(title = "配送信息") {
                DetailRow("配送员", r.deliveryPerson, showDivider = false)
            }

            // 整改信息
            IOSGroupCard(title = "整改信息") {
                DetailRow("是否整改", r.isRectified)
                DetailRow(
                    "整改日期",
                    if (r.rectificationDate > 0) dateFormat.format(Date(r.rectificationDate)) else "未设置",
                    showDivider = false
                )
            }

            // 备注
            if (r.remark.isNotEmpty()) {
                IOSGroupCard(title = "备注") {
                    Text(
                        r.remark,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            IOSPrimaryButton("编辑记录", onClick = { onEdit(recordId) })

            Spacer(Modifier.height(32.dp))
        }
    }

    // 删除确认
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除该检查记录吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    record?.let { viewModel.deleteRecord(it) }
                    showDeleteDialog = false
                    Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                    onBack()
                }) {
                    Text("删除", color = RedDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 全屏图片预览
    if (previewPhoto != null) {
        AlertDialog(
            onDismissRequest = { previewPhoto = null },
            confirmButton = {
                TextButton(onClick = { previewPhoto = null }) { Text("关闭") }
            },
            text = {
                AsyncImage(
                    model = previewPhoto,
                    contentDescription = "照片预览",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        )
    }
}

@Composable
fun DetailRow(label: String, value: String, showDivider: Boolean = true) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = GraySecondaryText,
                modifier = Modifier.width(80.dp)
            )
            Text(
                value.ifEmpty { "-" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                color = GraySeparator.copy(alpha = 0.3f),
                thickness = 0.5.dp
            )
        }
    }
}
