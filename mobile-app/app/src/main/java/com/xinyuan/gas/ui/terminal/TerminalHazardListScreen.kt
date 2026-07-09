package com.xinyuan.gas.ui.terminal

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xinyuan.gas.data.model.TerminalHazardCatalog
import com.xinyuan.gas.data.model.TerminalHazardRecord
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TerminalHazardListScreen(
    viewModel: InspectionViewModel,
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
    onExport: () -> Unit
) {
    val allRecords by viewModel.allTerminalRecords.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }
    var status by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(0L) }
    var endDate by remember { mutableStateOf(0L) }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var inspectorName by remember { mutableStateOf("") }
    var deliveryPerson by remember { mutableStateOf("") }
    var filtersExpanded by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            IOSTopBar(
                title = "终端用户隐患核查清单",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onExport) {
                        Icon(Icons.Default.FileDownload, contentDescription = "数据导出", tint = GreenSuccess)
                    }
                }
            )
        },
        containerColor = GrayBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            BrandLogo(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 6.dp, bottom = 8.dp)
            )

            FilterPanel(
                status = status,
                onStatusChange = { status = it },
                startDate = startDate,
                endDate = endDate,
                dateFormat = dateFormat,
                onStartDateChange = { startDate = it },
                onEndDateChange = { endDate = it },
                customerName = customerName,
                onCustomerNameChange = { customerName = it },
                customerPhone = customerPhone,
                onCustomerPhoneChange = { customerPhone = it },
                inspectorName = inspectorName,
                onInspectorNameChange = { inspectorName = it },
                deliveryPerson = deliveryPerson,
                onDeliveryPersonChange = { deliveryPerson = it },
                expanded = filtersExpanded,
                onExpandedChange = { filtersExpanded = it },
                onReset = { resetFilters() }
            )

            Text(
                "共 ${records.size} 条记录",
                style = MaterialTheme.typography.labelMedium,
                color = GraySecondaryText,
                modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp)
            )

            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(64.dp), tint = GrayTertiaryText)
                        Spacer(Modifier.height(16.dp))
                        Text("暂无终端核查记录", color = GraySecondaryText)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(records, key = { it.id }) { record ->
                        TerminalHazardRecordCard(record = record, onClick = { onOpenRecord(record.id) })
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun FilterPanel(
    status: String,
    onStatusChange: (String) -> Unit,
    startDate: Long,
    endDate: Long,
    dateFormat: SimpleDateFormat,
    onStartDateChange: (Long) -> Unit,
    onEndDateChange: (Long) -> Unit,
    customerName: String,
    onCustomerNameChange: (String) -> Unit,
    customerPhone: String,
    onCustomerPhoneChange: (String) -> Unit,
    inspectorName: String,
    onInspectorNameChange: (String) -> Unit,
    deliveryPerson: String,
    onDeliveryPersonChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    val extraFilterCount = listOf(
        startDate > 0L,
        endDate > 0L,
        customerName.isNotBlank(),
        customerPhone.isNotBlank(),
        inspectorName.isNotBlank(),
        deliveryPerson.isNotBlank()
    ).count { it }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            listOf(
                listOf(
                    "" to "全部",
                    TerminalHazardCatalog.RESULT_CLEARED to "已清零"
                ),
                listOf(
                    TerminalHazardCatalog.RESULT_NOT_CLEARED to "未清零",
                    TerminalHazardCatalog.RESULT_RECTIFIED_LATER to "后期已整改"
                )
            ).forEachIndexed { index, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowItems.forEach { (value, label) ->
                        FilterChip(
                            selected = status == value,
                            onClick = { onStatusChange(value) },
                            label = {
                                Text(
                                    label,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            },
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onExpandedChange(!expanded) }) {
                    Text(
                        if (expanded) "收起筛选" else if (extraFilterCount > 0) "更多筛选($extraFilterCount)" else "更多筛选",
                        color = BluePrimary
                    )
                }
                if (extraFilterCount > 0 || status.isNotBlank()) {
                    TextButton(onClick = onReset) {
                        Text("一键重置", color = RedDanger)
                    }
                }
            }

            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            val cal = Calendar.getInstance()
                            DatePickerDialog(context, { _, y, m, d ->
                                val selected = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
                                onStartDateChange(selected.timeInMillis)
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
                                onEndDateChange(selected.timeInMillis)
                            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (endDate > 0) dateFormat.format(Date(endDate)) else "结束日期")
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallFilterField("用户名", customerName, onCustomerNameChange, Modifier.weight(1f))
                    SmallFilterField("电话", customerPhone, onCustomerPhoneChange, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallFilterField("检查人", inspectorName, onInspectorNameChange, Modifier.weight(1f))
                    SmallFilterField("配送员", deliveryPerson, onDeliveryPersonChange, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SmallFilterField(
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

@Composable
private fun TerminalHazardRecordCard(
    record: TerminalHazardRecord,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }
    val displayResult = TerminalHazardCatalog.displayResult(record)
    val isPending = record.checkResult == TerminalHazardCatalog.RESULT_NOT_CLEARED
    val statusColor = when (displayResult) {
        TerminalHazardCatalog.RESULT_NOT_CLEARED -> RedDanger
        TerminalHazardCatalog.RESULT_RECTIFIED_LATER -> OrangeAccent
        else -> GreenSuccess
    }
    val hazardCount = TerminalHazardCatalog.selectedItems(record).size
    val sourceIssueCount = TerminalHazardCatalog.textSet(record.gasSourceIssues).size

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    record.customerName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    modifier = Modifier.widthIn(min = 90.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        displayResult,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = statusColor,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                record.customerAddress.ifEmpty { "地址未填写" },
                style = MaterialTheme.typography.bodySmall,
                color = GraySecondaryText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("检查人：${record.inspectorName}", style = MaterialTheme.typography.bodySmall, color = GraySecondaryText)
                Text(dateFormat.format(Date(record.inspectionDate)), style = MaterialTheme.typography.bodySmall, color = GraySecondaryText)
            }
            if (isPending || displayResult == TerminalHazardCatalog.RESULT_RECTIFIED_LATER) {
                Spacer(Modifier.height(6.dp))
                Text("发现隐患：$hazardCount 项", style = MaterialTheme.typography.bodySmall, color = OrangeAccent)
            }
            if (sourceIssueCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text("气源异常：$sourceIssueCount 项", style = MaterialTheme.typography.bodySmall, color = RedDanger)
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClick) {
                    Text("查看详情", color = BluePrimary)
                }
            }
        }
    }
}

internal fun List<TerminalHazardRecord>.filterTerminalRecords(
    status: String,
    startDate: Long,
    endDate: Long,
    customerName: String,
    customerPhone: String,
    inspectorName: String,
    deliveryPerson: String
): List<TerminalHazardRecord> {
    fun String.matchesFilter(filter: String): Boolean =
        filter.isBlank() || contains(filter.trim(), ignoreCase = true)

    return filter { record ->
        (status.isBlank() || TerminalHazardCatalog.displayResult(record) == status) &&
            (startDate == 0L || record.inspectionDate >= startDate) &&
            (endDate == 0L || record.inspectionDate <= endDate) &&
            record.customerName.matchesFilter(customerName) &&
            record.customerPhone.matchesFilter(customerPhone) &&
            record.inspectorName.matchesFilter(inspectorName) &&
            record.deliveryPerson.matchesFilter(deliveryPerson)
    }
}
