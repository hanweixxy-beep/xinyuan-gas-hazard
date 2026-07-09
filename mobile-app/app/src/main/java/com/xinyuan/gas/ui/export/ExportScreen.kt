package com.xinyuan.gas.ui.export

import android.app.DatePickerDialog
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xinyuan.gas.R
import com.xinyuan.gas.ui.components.*
import com.xinyuan.gas.ui.inspection.InspectionViewModel
import com.xinyuan.gas.ui.theme.*
import com.xinyuan.gas.util.ExportUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    viewModel: InspectionViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }

    var exportFormat by remember { mutableStateOf("Excel") }
    var filterStatus by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(0L) }
    var endDate by remember { mutableStateOf(0L) }
    var isExporting by remember { mutableStateOf(false) }
    var pendingShareFile by remember { mutableStateOf<File?>(null) }
    var shareStartedAt by remember { mutableStateOf(0L) }

    fun cleanupPendingExportFile() {
        val file = pendingShareFile ?: return
        pendingShareFile = null
        shareStartedAt = 0L
        scope.launch(Dispatchers.IO) {
            ExportUtil.deleteTemporaryExport(context, file)
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            ExportUtil.cleanupTemporaryExports(context)
        }
    }

    DisposableEffect(lifecycleOwner, pendingShareFile, shareStartedAt) {
        val observer = LifecycleEventObserver { _, event ->
            if (
                event == Lifecycle.Event.ON_RESUME &&
                pendingShareFile != null &&
                shareStartedAt > 0L &&
                System.currentTimeMillis() - shareStartedAt > 500L
            ) {
                cleanupPendingExportFile()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            IOSTopBar(title = "数据导出", onBack = onBack)
        },
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
                    .padding(top = 8.dp, bottom = 12.dp)
            )

            // 导出格式
            IOSGroupCard(title = "导出格式") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf("Excel" to Icons.Default.TableChart, "CSV" to Icons.AutoMirrored.Filled.TextSnippet).forEach { (format, icon) ->
                        FilterChip(
                            selected = exportFormat == format,
                            onClick = { exportFormat = format },
                            label = { Text(format) },
                            leadingIcon = {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BluePrimary.copy(alpha = 0.12f),
                                selectedLabelColor = BluePrimary
                            )
                        )
                    }
                }
            }

            // 筛选条件
            IOSGroupCard(title = "筛选条件（可选）") {
                IOSDropdownRow(
                    "整改状态",
                    filterStatus.ifEmpty { "全部" },
                    listOf("全部", "未整改", "已整改"),
                    { filterStatus = if (it == "全部") "" else it }
                )
                IOSClickableRow(
                    label = "开始日期",
                    value = if (startDate > 0) dateFormat.format(Date(startDate)) else "不限",
                    onClick = {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(context, { _, y, m, d ->
                            val selected = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
                            startDate = selected.timeInMillis
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                    }
                )
                IOSClickableRow(
                    label = "结束日期",
                    value = if (endDate > 0) dateFormat.format(Date(endDate)) else "不限",
                    onClick = {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(context, { _, y, m, d ->
                            val selected = Calendar.getInstance().apply { set(y, m, d, 23, 59, 59) }
                            endDate = selected.timeInMillis
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                    },
                    showDivider = false
                )
            }

            Spacer(Modifier.height(8.dp))

            // 重置
            TextButton(
                onClick = {
                    filterStatus = ""
                    startDate = 0L
                    endDate = 0L
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("重置筛选条件", color = RedDanger)
            }

            Spacer(Modifier.height(16.dp))

            // 导出按钮
            IOSPrimaryButton(
                text = if (isExporting) "导出中..." else "导出数据",
                enabled = !isExporting,
                onClick = {
                    scope.launch {
                        isExporting = true
                        try {
                            val records = withContext(Dispatchers.IO) {
                                viewModel.getFilteredList(filterStatus, startDate, endDate)
                            }
                            if (records.isEmpty()) {
                                Toast.makeText(context, "没有符合条件的记录", Toast.LENGTH_SHORT).show()
                                isExporting = false
                                return@launch
                            }
                            val file = withContext(Dispatchers.IO) {
                                ExportUtil.cleanupTemporaryExports(context)
                                if (exportFormat == "Excel") {
                                    ExportUtil.exportToExcel(context, records)
                                } else {
                                    ExportUtil.exportToCsv(context, records)
                                }
                            }
                            Toast.makeText(context, "导出成功: ${file.name}", Toast.LENGTH_LONG).show()

                            // 分享文件
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = if (exportFormat == "Excel")
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                else "text/csv"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            pendingShareFile = file
                            shareStartedAt = System.currentTimeMillis()
                            runCatching {
                                context.startActivity(Intent.createChooser(shareIntent, "分享导出文件"))
                            }.onFailure {
                                cleanupPendingExportFile()
                                throw it
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isExporting = false
                        }
                    }
                },
                color = GreenSuccess
            )

            Spacer(Modifier.height(16.dp))

            // 提示
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BlueLight)
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "导出文件为临时文件，分享或取消后返回 APP 会自动删除；下一次导出会重新生成。Excel格式包含完整格式，CSV格式兼容性更好。",
                        style = MaterialTheme.typography.bodySmall,
                        color = BluePrimary
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
