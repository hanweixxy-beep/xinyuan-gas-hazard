package com.xinyuan.gas.ui.terminal

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.xinyuan.gas.data.model.TerminalHazardCatalog
import com.xinyuan.gas.data.model.TerminalHazardItem
import com.xinyuan.gas.data.model.TerminalHazardRecord
import com.xinyuan.gas.ui.components.IOSGroupCard
import com.xinyuan.gas.ui.components.IOSPrimaryButton
import com.xinyuan.gas.ui.components.IOSTopBar
import com.xinyuan.gas.ui.inspection.InspectionViewModel
import com.xinyuan.gas.ui.theme.BrandGreen
import com.xinyuan.gas.ui.theme.GrayBackground
import com.xinyuan.gas.ui.theme.GraySeparator
import com.xinyuan.gas.ui.theme.GraySecondaryText
import com.xinyuan.gas.ui.theme.GrayTertiaryText
import com.xinyuan.gas.ui.theme.GreenSuccess
import com.xinyuan.gas.ui.theme.OrangeAccent
import com.xinyuan.gas.ui.theme.RedDanger
import com.xinyuan.gas.util.PhotoUtil
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TerminalHazardRectificationDetailScreen(
    viewModel: InspectionViewModel,
    recordId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val recordFlow = remember(recordId) { viewModel.getTerminalById(recordId) }
    val record by recordFlow.collectAsState(initial = null)
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }

    var loadedRecordId by remember { mutableStateOf(0L) }
    var rectificationPhotos by remember { mutableStateOf(listOf<String>()) }
    var rectificationNote by remember { mutableStateOf("") }
    var rectificationPerson by remember { mutableStateOf("") }
    var previewPhoto by remember { mutableStateOf<String?>(null) }
    var currentPhotoUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(record?.id) {
        val current = record
        if (current != null && loadedRecordId != current.id) {
            rectificationPhotos = TerminalHazardCatalog.photoList(current.rectificationPhotos)
            rectificationNote = current.rectificationNote
            rectificationPerson = current.rectificationPerson.ifBlank { current.inspectorName }
            loadedRecordId = current.id
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = currentPhotoUri
        val current = record
        if (success && uri != null && current != null) {
            coroutineScope.launch {
                val locationText = PhotoUtil.getLocationWatermark(context)
                PhotoUtil.addWatermark(
                    context = context,
                    photoUri = uri,
                    address = current.customerAddress,
                    inspector = current.inspectorName,
                    customerName = current.customerName,
                    customerPhone = current.customerPhone,
                    locationText = locationText
                )
                rectificationPhotos = rectificationPhotos + uri.toString()
            }
        } else {
            uri?.let { PhotoUtil.deletePhoto(context, it.toString()) }
        }
        currentPhotoUri = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[android.Manifest.permission.CAMERA] == true) {
            val uri = PhotoUtil.createImageUri(context)
            currentPhotoUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show()
        }
    }

    fun takeRectificationPhoto() {
        cameraPermissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    fun confirmRectification(current: TerminalHazardRecord) {
        if (current.checkResult != TerminalHazardCatalog.RESULT_NOT_CLEARED) {
            Toast.makeText(context, "该记录已清零", Toast.LENGTH_SHORT).show()
            return
        }
        if (rectificationNote.isBlank()) {
            Toast.makeText(context, "请填写整改情况", Toast.LENGTH_SHORT).show()
            return
        }
        if (rectificationPerson.isBlank()) {
            Toast.makeText(context, "请填写整改人", Toast.LENGTH_SHORT).show()
            return
        }
        if (rectificationPhotos.isEmpty()) {
            Toast.makeText(context, "请上传整改照片", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.saveTerminalRecord(
            current.copy(
                checkResult = TerminalHazardCatalog.RESULT_CLEARED,
                rectificationPhotos = TerminalHazardCatalog.serializePhotoList(rectificationPhotos),
                rectificationDate = System.currentTimeMillis(),
                rectificationNote = rectificationNote.trim(),
                rectificationPerson = rectificationPerson.trim(),
                hadUnclearedHazard = true
            )
        )
    }

    LaunchedEffect(Unit) {
        viewModel.terminalSaveResult.collectLatest { result ->
            result.fold(
                onSuccess = {
                    Toast.makeText(context, "整改记录已保存", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                onFailure = {
                    Toast.makeText(context, "保存失败: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    Scaffold(
        topBar = {
            IOSTopBar(
                title = "整改处理",
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
                Spacer(Modifier.height(12.dp))

                IOSGroupCard(title = "用户信息") {
                    ReadOnlyRow("用户名", current.customerName)
                    ReadOnlyRow("电话", current.customerPhone)
                    ReadOnlyRow("用户类型", current.customerType)
                    ReadOnlyRow("地址", current.customerAddress.ifBlank { "地址未填写" }, showDivider = false)
                }

                IOSGroupCard(title = "检查信息") {
                    ReadOnlyRow("检查人", current.inspectorName)
                    ReadOnlyRow("配送员", current.deliveryPerson)
                    ReadOnlyRow("检查日期", dateFormat.format(Date(current.inspectionDate)))
                    StatusRow(current.checkResult, showDivider = false)
                }

                HazardSummaryCard(
                    record = current,
                    onPreviewPhoto = { previewPhoto = it }
                )

                IOSGroupCard(title = "整改处理") {
                    ReadOnlyRow("整改日期", dateFormat.format(Date()), showDivider = true)
                    OutlinedTextField(
                        value = rectificationPerson,
                        onValueChange = { rectificationPerson = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        placeholder = { Text("填写整改人", color = GrayTertiaryText) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = GraySeparator,
                            focusedBorderColor = BrandGreen,
                            unfocusedContainerColor = GrayBackground.copy(alpha = 0.7f),
                            focusedContainerColor = GrayBackground.copy(alpha = 0.7f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = rectificationNote,
                        onValueChange = { rectificationNote = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .heightIn(min = 92.dp),
                        placeholder = { Text("填写整改情况", color = GrayTertiaryText) },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = GraySeparator,
                            focusedBorderColor = BrandGreen,
                            unfocusedContainerColor = GrayBackground.copy(alpha = 0.7f),
                            focusedContainerColor = GrayBackground.copy(alpha = 0.7f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    RectificationPhotoRow(
                        photos = rectificationPhotos,
                        onTakePhoto = { takeRectificationPhoto() },
                        onPreviewPhoto = { previewPhoto = it },
                        onDeletePhoto = { photo ->
                            PhotoUtil.deletePhoto(context, photo)
                            rectificationPhotos = rectificationPhotos - photo
                        }
                    )
                    if (current.checkResult == TerminalHazardCatalog.RESULT_NOT_CLEARED) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            IOSPrimaryButton(
                                text = "确认已整改并清零",
                                onClick = { confirmRectification(current) },
                                color = GreenSuccess
                            )
                        }
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
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF1C1C1E),
                modifier = Modifier.weight(1f)
            )
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
    val color = if (value == TerminalHazardCatalog.RESULT_NOT_CLEARED) RedDanger else GreenSuccess
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("检查结果", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(82.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.12f)) {
                Text(
                    value,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    color = color,
                    style = MaterialTheme.typography.bodySmall
                )
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

@Composable
private fun HazardSummaryCard(
    record: TerminalHazardRecord,
    onPreviewPhoto: (String) -> Unit
) {
    val items = TerminalHazardCatalog.selectedItems(record)
    val photoMap = TerminalHazardCatalog.photoListMap(record.hazardPhotos)

    IOSGroupCard(title = "需整改隐患") {
        if (items.isEmpty()) {
            Text(
                "未记录具体隐患项目",
                style = MaterialTheme.typography.bodyMedium,
                color = GraySecondaryText,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            items.groupBy { it.category }.forEach { (category, categoryItems) ->
                HazardCategoryBlock(
                    category = category,
                    items = categoryItems,
                    photoMap = photoMap,
                    onPreviewPhoto = onPreviewPhoto
                )
            }
        }
    }
}

@Composable
private fun HazardCategoryBlock(
    category: String,
    items: List<TerminalHazardItem>,
    photoMap: Map<String, List<String>>,
    onPreviewPhoto: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            category,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = OrangeAccent
        )
        Spacer(Modifier.height(8.dp))
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF1C1C1E),
                    modifier = Modifier.weight(1f)
                )
                val photos = photoMap[item.code].orEmpty()
                if (photos.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        photos.forEach { photo ->
                            AsyncImage(
                                model = photo,
                                contentDescription = item.name,
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(GrayBackground)
                                    .clickable { onPreviewPhoto(photo) },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RectificationPhotoRow(
    photos: List<String>,
    onTakePhoto: () -> Unit,
    onPreviewPhoto: (String) -> Unit,
    onDeletePhoto: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("整改照片", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            photos.forEach { photo ->
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GrayBackground)
                        .clickable { onPreviewPhoto(photo) },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = photo,
                        contentDescription = "整改照片",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = { onDeletePhoto(photo) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "删除照片", tint = RedDanger, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(GrayBackground)
                    .clickable(onClick = onTakePhoto),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "拍照", tint = GraySecondaryText, modifier = Modifier.size(28.dp))
                    Text("拍照", color = GraySecondaryText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
