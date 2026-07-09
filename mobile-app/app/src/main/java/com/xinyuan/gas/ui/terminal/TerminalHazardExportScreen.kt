package com.xinyuan.gas.ui.terminal

import android.app.DatePickerDialog
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xinyuan.gas.data.model.TerminalHazardCatalog
import com.xinyuan.gas.ui.components.BrandLogo
import com.xinyuan.gas.ui.components.IOSGroupCard
import com.xinyuan.gas.ui.components.IOSPrimaryButton
import com.xinyuan.gas.ui.components.IOSTopBar
import com.xinyuan.gas.ui.inspection.InspectionViewModel
import com.xinyuan.gas.ui.theme.BluePrimary
import com.xinyuan.gas.ui.theme.GrayBackground
import com.xinyuan.gas.ui.theme.GraySecondaryText
import com.xinyuan.gas.ui.theme.GrayTertiaryText
import com.xinyuan.gas.ui.theme.GreenSuccess
import com.xinyuan.gas.ui.theme.RedDanger
import com.xinyuan.gas.util.ExportUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TerminalHazardExportScreen(
    viewModel: InspectionViewModel,
    onBack: () -> Unit
) {
    val allRecords by viewModel.allTerminalRecords.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }
    var format by remember { mutableStateOf(ExportFormat.Excel) }
    var status by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(0L) }
    var endDate by remember { mutableStateOf(0L) }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var inspectorName by remember { mutableStateOf("") }
    var deliveryPerson by remember { mutableStateOf("") }
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

    val records = remember(allRecords, status, startDate, endDate, customerName, customerPhone, inspectorName, deliveryPerson) {
        allRecords.filterTerminalRecords(
            status = status,
            startDate = startDate,
            endDate = endDate,
            customerName = customerName,
            customerPhone = customerPhone,
            inspectorName = inspectorName,
            deliveryPerson = deliveryPerson
        )
    }

    fun resetFilters() {
        status = ""
        startDate = 0L
        endDate = 0L
        customerName = ""
        customerPhone = ""
        inspectorName = ""
        deliveryPerson = ""
    }

    fun exportRecords() {
        if (records.isEmpty()) {
            Toast.makeText(context, "没有符合条件的数据", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            isExporting = true
            try {
                val file = withContext(Dispatchers.IO) {
                    ExportUtil.cleanupTemporaryExports(context)
                    when (format) {
                        ExportFormat.Excel -> ExportUtil.exportTerminalHazardList(context, records)
                        ExportFormat.Csv -> ExportUtil.exportTerminalHazardListToCsv(context, records)
                    }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = format.mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                Toast.makeText(context, "导出成功: ${file.name}", Toast.LENGTH_LONG).show()
                pendingShareFile = file
                shareStartedAt = System.currentTimeMillis()
                runCatching {
                    context.startActivity(Intent.createChooser(shareIntent, "分享终端用户隐患核查清单"))
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
    }

    Scaffold(
        topBar = {
            IOSTopBar(
                title = "数据导出",
                onBack = onBack
            )
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

            IOSGroupCard(title = "导出格式") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExportFormat.values().forEach { item ->
                        FilterChip(
                            selected = format == item,
                            onClick = { format = item },
                            label = { Text(item.label) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BluePrimary.copy(alpha = 0.12f),
                                selectedLabelColor = BluePrimary
                            )
                        )
                    }
                }
            }

            IOSGroupCard(title = "筛选条件") {
                Column(modifier = Modifier.padding(12.dp)) {
                    listOf(
                        listOf("" to "全部", TerminalHazardCatalog.RESULT_CLEARED to "已清零"),
                        listOf(TerminalHazardCatalog.RESULT_NOT_CLEARED to "未清零", TerminalHazardCatalog.RESULT_RECTIFIED_LATER to "后期已整改")
                    ).forEachIndexed { index, rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { (value, label) ->
                                FilterChip(
                                    selected = status == value,
                                    onClick = { status = value },
                                    label = { Text(label) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BluePrimary.copy(alpha = 0.12f),
                                        selectedLabelColor = BluePrimary
                                    )
                                )
                            }
                        }
                        if (index == 0) Spacer(Modifier.height(6.dp))
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                val cal = Calendar.getInstance()
                                DatePickerDialog(context, { _, y, m, d ->
                                    val selected = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
                                    startDate = selected.timeInMillis
                                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (startDate > 0) dateFormat.format(Date(startDate)) else "开始日期")
                        }
                        OutlinedButton(
                            onClick = {
                                val cal = Calendar.getInstance()
                                DatePickerDialog(context, { _, y, m, d ->
                                    val selected = Calendar.getInstance().apply { set(y, m, d, 23, 59, 59) }
                                    endDate = selected.timeInMillis
                                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (endDate > 0) dateFormat.format(Date(endDate)) else "结束日期")
                        }
                        TextButton(onClick = { resetFilters() }) {
                            Text("一键重置", color = RedDanger)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallExportField("用户名", customerName, { customerName = it }, Modifier.weight(1f))
                        SmallExportField("电话", customerPhone, { customerPhone = it }, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallExportField("检查人", inspectorName, { inspectorName = it }, Modifier.weight(1f))
                        SmallExportField("配送员", deliveryPerson, { deliveryPerson = it }, Modifier.weight(1f))
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "当前筛选结果 ${records.size} 条",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF1C1C1E),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (format == ExportFormat.Excel) {
                            "Excel 格式包含照片，分享返回后临时文件会自动删除"
                        } else {
                            "CSV 格式为轻量文本清单，分享返回后临时文件会自动删除"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = GraySecondaryText,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            IOSPrimaryButton(
                text = if (isExporting) "正在导出..." else "导出数据",
                onClick = { exportRecords() },
                enabled = !isExporting,
                color = GreenSuccess
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SmallExportField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(label, color = GrayTertiaryText) },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = BluePrimary,
            unfocusedContainerColor = GrayBackground.copy(alpha = 0.75f),
            focusedContainerColor = GrayBackground.copy(alpha = 0.75f)
        )
    )
}

private enum class ExportFormat(
    val label: String,
    val mimeType: String
) {
    Excel("EXCEL", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    Csv("CSV", "text/csv")
}
