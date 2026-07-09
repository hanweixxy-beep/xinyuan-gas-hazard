package com.xinyuan.gas.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.xinyuan.gas.data.model.TerminalHazardCatalog
import com.xinyuan.gas.data.model.TerminalHazardRecord
import com.xinyuan.gas.ui.components.BrandLogo
import com.xinyuan.gas.ui.components.IOSGroupCard
import com.xinyuan.gas.ui.components.IOSTopBar
import com.xinyuan.gas.ui.inspection.InspectionViewModel
import com.xinyuan.gas.ui.theme.GrayBackground
import com.xinyuan.gas.ui.theme.GraySecondaryText
import com.xinyuan.gas.ui.theme.GreenSuccess
import com.xinyuan.gas.ui.theme.OrangeAccent
import com.xinyuan.gas.ui.theme.RedDanger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TerminalHazardDetailScreen(
    viewModel: InspectionViewModel,
    recordId: Long,
    onBack: () -> Unit
) {
    val recordFlow = remember(recordId) { viewModel.getTerminalById(recordId) }
    val record by recordFlow.collectAsState(initial = null)
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }
    var previewPhoto by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            IOSTopBar(
                title = "清单详情",
                onBack = onBack
            )
        },
        containerColor = GrayBackground
    ) { padding ->
        val current = record
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("记录不存在", color = GraySecondaryText)
            }
        } else {
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

                IOSGroupCard(title = "用户信息") {
                    ReadOnlyRow("用户名", current.customerName)
                    PhotoStrip("门头照片", listOf(current.storefrontPhoto), onPreviewPhoto = { previewPhoto = it })
                    ReadOnlyRow("电话", current.customerPhone)
                    ReadOnlyRow("用户类型", current.customerType)
                    ReadOnlyRow("地址", current.customerAddress.ifBlank { "地址未填写" }, showDivider = false)
                }

                IOSGroupCard(title = "检查信息") {
                    ReadOnlyRow("检查人", current.inspectorName)
                    ReadOnlyRow("配送员", current.deliveryPerson)
                    ReadOnlyRow("检查日期", dateFormat.format(Date(current.inspectionDate)))
                    StatusRow(TerminalHazardCatalog.displayResult(current), showDivider = false)
                }

                HazardInfo(current, onPreviewPhoto = { previewPhoto = it })
                GasSourceInfo(current, onPreviewPhoto = { previewPhoto = it })
                RectificationInfo(current, dateFormat, onPreviewPhoto = { previewPhoto = it })

                if (current.remark.isNotBlank()) {
                    IOSGroupCard(title = "备注") {
                        Text(
                            current.remark,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF1C1C1E),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    previewPhoto?.let { photo ->
        AlertDialog(
            onDismissRequest = { previewPhoto = null },
            confirmButton = {
                TextButton(onClick = { previewPhoto = null }) {
                    Text("关闭")
                }
            },
            text = {
                AsyncImage(
                    model = photo,
                    contentDescription = "照片预览",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        )
    }
}

@Composable
private fun HazardInfo(
    record: TerminalHazardRecord,
    onPreviewPhoto: (String) -> Unit
) {
    val items = TerminalHazardCatalog.selectedItems(record)
    val photoMap = TerminalHazardCatalog.photoListMap(record.hazardPhotos)

    IOSGroupCard(title = "隐患项目") {
        if (items.isEmpty()) {
            Text(
                "未记录隐患项目",
                style = MaterialTheme.typography.bodyMedium,
                color = GraySecondaryText,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            items.groupBy { it.category }.forEach { (category, categoryItems) ->
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        category,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = OrangeAccent
                    )
                    Spacer(Modifier.height(8.dp))
                    categoryItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            val photos = photoMap[item.code].orEmpty()
                            if (photos.isNotEmpty()) {
                                PhotoStripInline(photos, onPreviewPhoto)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GasSourceInfo(
    record: TerminalHazardRecord,
    onPreviewPhoto: (String) -> Unit
) {
    val issues = TerminalHazardCatalog.textSet(record.gasSourceIssues)
    val qrPhotos = TerminalHazardCatalog.photoList(record.cylinderQrPhotos)
    val cylinderPhotos = TerminalHazardCatalog.photoList(record.cylinderPhotos)

    if (issues.isEmpty() && qrPhotos.isEmpty() && cylinderPhotos.isEmpty()) return

    IOSGroupCard(title = "气源排查") {
        if (issues.isNotEmpty()) {
            Text(
                issues.joinToString("\n"),
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF1C1C1E),
                modifier = Modifier.padding(16.dp)
            )
        }
        PhotoStrip("钢瓶二维码照片", qrPhotos, onPreviewPhoto)
        PhotoStrip("钢瓶照片", cylinderPhotos, onPreviewPhoto)
    }
}

@Composable
private fun RectificationInfo(
    record: TerminalHazardRecord,
    dateFormat: SimpleDateFormat,
    onPreviewPhoto: (String) -> Unit
) {
    val photos = TerminalHazardCatalog.photoList(record.rectificationPhotos)
    val hasRectification = record.rectificationDate > 0L ||
        record.rectificationNote.isNotBlank() ||
        record.rectificationPerson.isNotBlank() ||
        photos.isNotEmpty()
    if (!hasRectification) return

    IOSGroupCard(title = "整改信息") {
        ReadOnlyRow("整改日期", if (record.rectificationDate > 0L) dateFormat.format(Date(record.rectificationDate)) else "未填写")
        if (record.rectificationPerson.isNotBlank()) {
            ReadOnlyRow("整改人", record.rectificationPerson)
        }
        if (record.rectificationNote.isNotBlank()) {
            ReadOnlyRow("整改情况", record.rectificationNote)
        }
        PhotoStrip("整改照片", photos, onPreviewPhoto)
    }
}

@Composable
private fun PhotoStrip(
    title: String,
    photos: List<String>,
    onPreviewPhoto: (String) -> Unit
) {
    val validPhotos = photos.filter { it.isNotBlank() }
    if (validPhotos.isEmpty()) return

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            validPhotos.forEach { photo ->
                PhotoThumb(photo, onPreviewPhoto)
            }
        }
    }
}

@Composable
private fun PhotoStripInline(
    photos: List<String>,
    onPreviewPhoto: (String) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        photos.filter { it.isNotBlank() }.forEach { photo ->
            PhotoThumb(photo, onPreviewPhoto)
        }
    }
}

@Composable
private fun PhotoThumb(
    photo: String,
    onPreviewPhoto: (String) -> Unit
) {
    AsyncImage(
        model = photo,
        contentDescription = "照片",
        modifier = Modifier
            .size(78.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(GrayBackground)
            .clickable { onPreviewPhoto(photo) },
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun ReadOnlyRow(
    label: String,
    value: String,
    showDivider: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(82.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF1C1C1E), modifier = Modifier.weight(1f))
        }
        if (showDivider) {
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                color = com.xinyuan.gas.ui.theme.GraySeparator.copy(alpha = 0.3f),
                thickness = 0.5.dp
            )
        }
    }
}

@Composable
private fun StatusRow(
    value: String,
    showDivider: Boolean = true
) {
    val color = when (value) {
        TerminalHazardCatalog.RESULT_NOT_CLEARED -> RedDanger
        TerminalHazardCatalog.RESULT_RECTIFIED_LATER -> OrangeAccent
        else -> GreenSuccess
    }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("状态", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(82.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.12f)) {
                Text(value, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = color, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (showDivider) {
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                color = com.xinyuan.gas.ui.theme.GraySeparator.copy(alpha = 0.3f),
                thickness = 0.5.dp
            )
        }
    }
}
