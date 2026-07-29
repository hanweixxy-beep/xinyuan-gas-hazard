package com.xinyuan.gas.ui.terminal

import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xinyuan.gas.data.model.TerminalHazardCatalog
import com.xinyuan.gas.data.model.TerminalHazardRecord
import com.xinyuan.gas.ui.components.IOSTopBar
import com.xinyuan.gas.ui.inspection.InspectionViewModel
import com.xinyuan.gas.ui.theme.BrandGreen
import com.xinyuan.gas.ui.theme.GrayBackground
import com.xinyuan.gas.ui.theme.GraySeparator
import com.xinyuan.gas.ui.theme.GraySecondaryText
import com.xinyuan.gas.ui.theme.GrayTertiaryText
import com.xinyuan.gas.ui.theme.OrangeAccent
import com.xinyuan.gas.ui.theme.RedDanger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TerminalHazardRectificationListScreen(
    viewModel: InspectionViewModel,
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit
) {
    val allRecords by viewModel.allTerminalRecords.collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }

    val records = remember(allRecords, searchQuery) {
        val query = searchQuery.trim()
        allRecords
            .filter { it.checkResult == TerminalHazardCatalog.RESULT_NOT_CLEARED }
            .filter { record ->
                query.isEmpty() ||
                    record.customerName.contains(query, ignoreCase = true) ||
                    record.customerPhone.contains(query, ignoreCase = true) ||
                    record.customerAddress.contains(query, ignoreCase = true) ||
                    record.inspectorName.contains(query, ignoreCase = true) ||
                    record.deliveryPerson.contains(query, ignoreCase = true)
            }
    }

    Scaffold(
        topBar = {
            IOSTopBar(
                title = "隐患整改",
                onBack = onBack
            )
        },
        containerColor = GrayBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索用户名/地址/检查人/配送员/电话", color = GrayTertiaryText) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GraySecondaryText) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = GraySeparator,
                    focusedBorderColor = BrandGreen,
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                ),
                singleLine = true
            )

            Text(
                "待整改 ${records.size} 条",
                style = MaterialTheme.typography.labelMedium,
                color = GraySecondaryText,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)
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
                        Text("暂无待整改记录", color = GraySecondaryText)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(records, key = { it.id }) { record ->
                        PendingRectificationCard(
                            record = record,
                            onClick = { onOpenRecord(record.id) }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PendingRectificationCard(
    record: TerminalHazardRecord,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }
    val selectedItems = remember(record.hazardItems) { TerminalHazardCatalog.selectedItems(record) }
    val summary = selectedItems
        .groupBy { it.category }
        .entries
        .joinToString("；") { (category, items) -> "$category ${items.size}项" }
        .ifBlank { "未清零项目待确认" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GraySeparator, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                Surface(shape = RoundedCornerShape(12.dp), color = RedDanger.copy(alpha = 0.12f)) {
                    Text(
                        TerminalHazardCatalog.RESULT_NOT_CLEARED,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = RedDanger,
                        style = MaterialTheme.typography.bodySmall
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
            Spacer(Modifier.height(6.dp))
            Text("需整改：$summary", style = MaterialTheme.typography.bodySmall, color = OrangeAccent)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClick) {
                    Text("去整改", color = BrandGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
