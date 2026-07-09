package com.xinyuan.gas.ui.inspection

import android.app.DatePickerDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.xinyuan.gas.R
import com.xinyuan.gas.ui.components.*
import com.xinyuan.gas.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionListScreen(
    viewModel: InspectionViewModel,
    onBack: () -> Unit,
    onRecordClick: (Long) -> Unit
) {
    val records by viewModel.filteredRecords.collectAsState(initial = emptyList())
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterStatus by viewModel.filterStatus.collectAsState()
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }

    var showFilter by remember { mutableStateOf(false) }
    var startDateLabel by remember { mutableStateOf("") }
    var endDateLabel by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            IOSTopBar(
                title = "检查记录",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showFilter = !showFilter }) {
                        Icon(Icons.Default.FilterList, contentDescription = "筛选", tint = BluePrimary)
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

            // 搜索栏
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索用户名/地址/检查人/配送员", color = GrayTertiaryText) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GraySecondaryText) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = BluePrimary,
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                ),
                singleLine = true
            )

            // 筛选面板
            if (showFilter) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("整改状态", style = MaterialTheme.typography.labelMedium, color = GraySecondaryText)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("" to "全部", "未整改" to "未整改", "已整改" to "已整改").forEach { (value, label) ->
                                FilterChip(
                                    selected = filterStatus == value,
                                    onClick = { viewModel.setFilterStatus(value) },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BluePrimary.copy(alpha = 0.12f),
                                        selectedLabelColor = BluePrimary
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text("日期范围", style = MaterialTheme.typography.labelMedium, color = GraySecondaryText)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val cal = Calendar.getInstance()
                                    DatePickerDialog(context, { _, y, m, d ->
                                        val selected = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
                                        viewModel.setFilterDates(selected.timeInMillis, 0)
                                        startDateLabel = dateFormat.format(selected.time)
                                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(startDateLabel.ifEmpty { "开始日期" })
                            }
                            Text("至", color = GraySecondaryText)
                            OutlinedButton(
                                onClick = {
                                    val cal = Calendar.getInstance()
                                    DatePickerDialog(context, { _, y, m, d ->
                                        val selected = Calendar.getInstance().apply { set(y, m, d, 23, 59, 59) }
                                        viewModel.setFilterDates(0, selected.timeInMillis)
                                        endDateLabel = dateFormat.format(selected.time)
                                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(endDateLabel.ifEmpty { "结束日期" })
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            viewModel.setSearchQuery("")
                            viewModel.setFilterStatus("")
                            viewModel.setFilterDates(0, 0)
                            startDateLabel = ""
                            endDateLabel = ""
                        }) {
                            Text("重置筛选", color = RedDanger)
                        }
                    }
                }
            }

            // 记录数量
            Text(
                "共 ${records.size} 条记录",
                style = MaterialTheme.typography.labelMedium,
                color = GraySecondaryText,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)
            )

            // 记录列表
            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Inbox,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = GrayTertiaryText
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("暂无记录", color = GraySecondaryText)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(records, key = { it.id }) { record ->
                        RecordCard(
                            record = record,
                            onClick = { onRecordClick(record.id) }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}
