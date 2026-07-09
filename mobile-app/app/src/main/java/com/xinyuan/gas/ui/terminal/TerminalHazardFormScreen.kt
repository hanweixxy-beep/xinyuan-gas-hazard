package com.xinyuan.gas.ui.terminal

import android.app.DatePickerDialog
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.xinyuan.gas.data.model.TerminalHazardCatalog
import com.xinyuan.gas.data.model.TerminalHazardRecord
import com.xinyuan.gas.ui.components.BrandLogo
import com.xinyuan.gas.ui.components.IOSClickableRow
import com.xinyuan.gas.ui.components.IOSDropdownRow
import com.xinyuan.gas.ui.components.IOSGroupCard
import com.xinyuan.gas.ui.components.IOSPrimaryButton
import com.xinyuan.gas.ui.components.IOSTextField
import com.xinyuan.gas.ui.components.IOSTopBar
import com.xinyuan.gas.ui.inspection.InspectionViewModel
import com.xinyuan.gas.ui.theme.BluePrimary
import com.xinyuan.gas.ui.theme.GrayBackground
import com.xinyuan.gas.ui.theme.GraySecondaryText
import com.xinyuan.gas.ui.theme.GrayTertiaryText
import com.xinyuan.gas.ui.theme.GreenSuccess
import com.xinyuan.gas.ui.theme.RedDanger
import com.xinyuan.gas.util.PhotoUtil
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TerminalHazardFormScreen(
    viewModel: InspectionViewModel,
    onBack: () -> Unit,
    editRecordId: Long = 0L
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }
    val prefs = remember { context.getSharedPreferences("gas_prefs", android.content.Context.MODE_PRIVATE) }

    var customerName by remember { mutableStateOf("") }
    var storefrontPhoto by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var customerType by remember { mutableStateOf("") }
    var customerAddress by remember { mutableStateOf("") }
    var inspectorName by remember { mutableStateOf(prefs.getString("last_inspector", "") ?: "") }
    var inspectionDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var checkResult by remember { mutableStateOf(TerminalHazardCatalog.RESULT_CLEARED) }
    var originalCheckResult by remember { mutableStateOf("") }
    var selectedHazards by remember { mutableStateOf(setOf<String>()) }
    var hazardPhotos by remember { mutableStateOf(mapOf<String, List<String>>()) }
    var gasSourceIssues by remember { mutableStateOf(setOf<String>()) }
    var cylinderQrPhotos by remember { mutableStateOf(listOf<String>()) }
    var cylinderPhotos by remember { mutableStateOf(listOf<String>()) }
    var rectificationPhotos by remember { mutableStateOf(listOf<String>()) }
    var rectificationDate by remember { mutableStateOf(0L) }
    var rectificationNote by remember { mutableStateOf("") }
    var rectificationPerson by remember { mutableStateOf("") }
    var hadUnclearedHazard by remember { mutableStateOf(false) }
    var deliveryPerson by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }
    var previewPhoto by remember { mutableStateOf<String?>(null) }
    val storefrontMissingFields = listOfNotNull(
        "用户名".takeIf { customerName.isBlank() },
        "电话".takeIf { customerPhone.isBlank() },
        "用户类型".takeIf { customerType.isBlank() },
        "检查人".takeIf { inspectorName.isBlank() },
        "配送员".takeIf { deliveryPerson.isBlank() }
    )
    val storefrontWatermarkReady = storefrontMissingFields.isEmpty()

    LaunchedEffect(editRecordId) {
        if (editRecordId > 0) {
            viewModel.getTerminalById(editRecordId).collectLatest { record ->
                if (record != null) {
                    customerName = record.customerName
                    storefrontPhoto = record.storefrontPhoto
                    customerPhone = record.customerPhone
                    customerType = record.customerType
                    customerAddress = record.customerAddress
                    inspectorName = record.inspectorName
                    inspectionDate = record.inspectionDate
                    checkResult = record.checkResult
                    originalCheckResult = record.checkResult
                    selectedHazards = TerminalHazardCatalog.selectedCodes(record.hazardItems)
                    hazardPhotos = TerminalHazardCatalog.photoListMap(record.hazardPhotos)
                    gasSourceIssues = TerminalHazardCatalog.textSet(record.gasSourceIssues)
                    cylinderQrPhotos = TerminalHazardCatalog.photoList(record.cylinderQrPhotos)
                    cylinderPhotos = TerminalHazardCatalog.photoList(record.cylinderPhotos)
                    rectificationPhotos = TerminalHazardCatalog.photoList(record.rectificationPhotos)
                    rectificationDate = record.rectificationDate
                    rectificationNote = record.rectificationNote
                    rectificationPerson = record.rectificationPerson
                    hadUnclearedHazard = TerminalHazardCatalog.hadUnclearedHazard(record)
                    deliveryPerson = record.deliveryPerson
                    remark = record.remark
                }
            }
        }
    }

    fun clearHazardsAndPhotos() {
        hazardPhotos.values.flatten().forEach { PhotoUtil.deletePhoto(context, it) }
        selectedHazards = emptySet()
        hazardPhotos = emptyMap()
    }

    fun setResult(result: String) {
        checkResult = result
        if (editRecordId == 0L && result == TerminalHazardCatalog.RESULT_CLEARED) {
            clearHazardsAndPhotos()
        }
    }

    fun setHazardSelected(code: String, selected: Boolean) {
        selectedHazards = if (selected) {
            selectedHazards + code
        } else {
            hazardPhotos[code].orEmpty().forEach { PhotoUtil.deletePhoto(context, it) }
            hazardPhotos = hazardPhotos - code
            selectedHazards - code
        }
    }

    var currentPhotoTarget by remember { mutableStateOf("") }
    var currentPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val target = currentPhotoTarget
        val uri = currentPhotoUri
        if (success && uri != null) {
            if (target == "storefront") {
                val locationSnapshot = PhotoUtil.getLocationSnapshot(context)
                if (locationSnapshot.address.isBlank() && locationSnapshot.watermarkText.isBlank()) {
                    PhotoUtil.deletePhoto(context, uri.toString())
                    Toast.makeText(context, "未获取到用户地址，请确认定位权限后重新拍照", Toast.LENGTH_SHORT).show()
                } else {
                    customerAddress = locationSnapshot.address.ifBlank { "用户地址坐标已记录" }
                    PhotoUtil.addWatermark(
                        context,
                        uri,
                        customerAddress,
                        inspectorName,
                        customerName,
                        customerPhone,
                        locationSnapshot.watermarkText
                    )
                    if (storefrontPhoto.isNotEmpty()) PhotoUtil.deletePhoto(context, storefrontPhoto)
                    storefrontPhoto = uri.toString()
                }
            } else if (target == "cylinder_qr") {
                PhotoUtil.addWatermark(context, uri, customerAddress, inspectorName, customerName, customerPhone)
                cylinderQrPhotos = cylinderQrPhotos + uri.toString()
            } else if (target == "cylinder") {
                PhotoUtil.addWatermark(context, uri, customerAddress, inspectorName, customerName, customerPhone)
                cylinderPhotos = cylinderPhotos + uri.toString()
            } else if (target.isNotEmpty()) {
                PhotoUtil.addWatermark(context, uri, customerAddress, inspectorName, customerName, customerPhone)
                hazardPhotos = hazardPhotos + (target to (hazardPhotos[target].orEmpty() + uri.toString()))
            }
        } else {
            uri?.let { PhotoUtil.deletePhoto(context, it.toString()) }
        }
        currentPhotoTarget = ""
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

    fun takePhoto(target: String) {
        if (target == "storefront" && !storefrontWatermarkReady) {
            Toast.makeText(
                context,
                "请先填写${storefrontMissingFields.joinToString("、")}后再拍门头照片",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (target != "storefront" && (storefrontPhoto.isBlank() || customerAddress.isBlank())) {
            Toast.makeText(context, "请先拍摄门头照片获取用户地址", Toast.LENGTH_SHORT).show()
            return
        }
        currentPhotoTarget = target
        cameraPermissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    fun validateAndSave(
        resultToSave: String = checkResult,
        rectificationDateToSave: Long = rectificationDate
    ) {
        if (!storefrontWatermarkReady) {
            Toast.makeText(context, "请先填写${storefrontMissingFields.joinToString("、")}", Toast.LENGTH_SHORT).show()
            return
        }
        if (storefrontPhoto.isBlank()) {
            Toast.makeText(context, "请拍摄门头照片", Toast.LENGTH_SHORT).show()
            return
        }
        if (customerAddress.isBlank()) {
            Toast.makeText(context, "请重新拍摄门头照片获取用户地址", Toast.LENGTH_SHORT).show()
            return
        }
        if (resultToSave == TerminalHazardCatalog.RESULT_NOT_CLEARED) {
            if (selectedHazards.isEmpty()) {
                Toast.makeText(context, "请选择未清零隐患项目", Toast.LENGTH_SHORT).show()
                return
            }
            val missingPhoto = selectedHazards.any { hazardPhotos[it].orEmpty().isEmpty() }
            if (missingPhoto) {
                Toast.makeText(context, "每个未清零隐患项目都需要拍照", Toast.LENGTH_SHORT).show()
                return
            }
        }
        if (
            editRecordId > 0L &&
            originalCheckResult == TerminalHazardCatalog.RESULT_NOT_CLEARED &&
            resultToSave == TerminalHazardCatalog.RESULT_CLEARED
        ) {
            Toast.makeText(context, "请通过快捷操作中的隐患整改完成清零", Toast.LENGTH_SHORT).show()
            return
        }

        prefs.edit().putString("last_inspector", inspectorName).apply()
        val activePhotos = hazardPhotos.filterKeys { it in selectedHazards }
        val record = TerminalHazardRecord(
            id = editRecordId,
            customerName = customerName,
            storefrontPhoto = storefrontPhoto,
            customerPhone = customerPhone,
            customerType = customerType,
            customerAddress = customerAddress,
            inspectorName = inspectorName,
            inspectionDate = inspectionDate,
            checkResult = resultToSave,
            hazardItems = selectedHazards.joinToString(","),
            hazardPhotos = TerminalHazardCatalog.serializePhotoLists(activePhotos),
            gasSourceIssues = TerminalHazardCatalog.serializeTextSet(gasSourceIssues),
            cylinderQrPhotos = TerminalHazardCatalog.serializePhotoList(cylinderQrPhotos),
            cylinderPhotos = TerminalHazardCatalog.serializePhotoList(cylinderPhotos),
            rectificationPhotos = TerminalHazardCatalog.serializePhotoList(rectificationPhotos),
            rectificationDate = rectificationDateToSave,
            rectificationNote = rectificationNote,
            rectificationPerson = rectificationPerson,
            hadUnclearedHazard = hadUnclearedHazard ||
                resultToSave == TerminalHazardCatalog.RESULT_NOT_CLEARED ||
                selectedHazards.isNotEmpty(),
            deliveryPerson = deliveryPerson,
            remark = remark
        )
        viewModel.saveTerminalRecord(record)
    }

    LaunchedEffect(Unit) {
        viewModel.terminalSaveResult.collectLatest { result ->
            result.fold(
                onSuccess = {
                    Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show()
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
                title = if (editRecordId > 0) "编辑检查" else "新增检查",
                onBack = onBack,
                actions = {
                    TextButton(onClick = { validateAndSave() }) {
                        Text("保存", color = BluePrimary)
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            FormIntroCard()

            IOSGroupCard(title = "用户信息") {
                IOSTextField("用户名", customerName, { customerName = it }, required = true)
                IOSTextField("电话", customerPhone, { customerPhone = it }, keyboardType = KeyboardType.Phone, required = true)
                IOSDropdownRow(
                    "用户类型",
                    customerType,
                    listOf("居民用户", "商业用户", "餐饮用户", "工业用户", "流动摊贩"),
                    { customerType = it },
                    showDivider = false,
                    required = true
                )
            }

            IOSGroupCard(title = "检查信息") {
                IOSTextField("检查人", inspectorName, { inspectorName = it }, required = true)
                IOSClickableRow(
                    label = "检查日期",
                    value = dateFormat.format(Date(inspectionDate)),
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = inspectionDate }
                        DatePickerDialog(context, { _, y, m, d ->
                            val selected = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
                            inspectionDate = selected.timeInMillis
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                    }
                )
                IOSTextField("配送员", deliveryPerson, { deliveryPerson = it }, showDivider = false, required = true)
            }

            IOSGroupCard(title = "门头照片 *") {
                Text(
                    text = "请先填写用户、电话、用户类型、检查人、配送员。点击拍照时会自动定位一次，后续照片都使用用户地址。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GraySecondaryText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .border(
                            width = 1.dp,
                            color = GreenSuccess.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .background(
                            color = GreenSuccess.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "用户地址",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = GreenSuccess
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = customerAddress.ifBlank { "拍摄门头照片时自动记录用户地址" },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (customerAddress.isBlank()) GraySecondaryText else Color(0xFF1C1C1E)
                        )
                    }
                }
                PhotoPickerRow(
                    title = "拍照",
                    photoUri = storefrontPhoto,
                    required = false,
                    enabled = storefrontWatermarkReady,
                    helperText = if (storefrontWatermarkReady) {
                        "拍摄后将写入日期时间、用户电话、检查人和用户地址水印"
                    } else {
                        "请先填写：${storefrontMissingFields.joinToString("、")}"
                    },
                    onTakePhoto = { takePhoto("storefront") },
                    onDeletePhoto = {
                        PhotoUtil.deletePhoto(context, storefrontPhoto)
                        storefrontPhoto = ""
                        customerAddress = ""
                    },
                    onPreviewPhoto = { previewPhoto = storefrontPhoto }
                )
            }

            IOSGroupCard(title = "检查结果") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(
                        TerminalHazardCatalog.RESULT_CLEARED to GreenSuccess,
                        TerminalHazardCatalog.RESULT_NOT_CLEARED to RedDanger
                    ).forEach { (result, color) ->
                        FilterChip(
                            selected = checkResult == result,
                            onClick = { setResult(result) },
                            label = { Text(result) },
                            modifier = Modifier.weight(1f),
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                selectedContainerColor = color.copy(alpha = 0.14f),
                                selectedLabelColor = color
                            )
                        )
                    }
                }
            }

            if (checkResult == TerminalHazardCatalog.RESULT_CLEARED) {
                IOSGroupCard(title = "未清零隐患项目") {
                    Text(
                        text = "当前为“隐患已清零”。继续登记气源排查、钢瓶照片和备注后即可保存。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GraySecondaryText,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else if (checkResult == TerminalHazardCatalog.RESULT_NOT_CLEARED) {
                TerminalHazardCatalog.categories.forEach { category ->
                    IOSGroupCard(title = category) {
                        TerminalHazardCatalog.items.filter { it.category == category }.forEachIndexed { index, item ->
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = item.code in selectedHazards,
                                        onCheckedChange = { checked -> setHazardSelected(item.code, checked) }
                                    )
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFF1C1C1E),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (item.code in selectedHazards) {
                                    MultiPhotoPickerRow(
                                        title = "项目照片（可多张）",
                                        photos = hazardPhotos[item.code].orEmpty(),
                                        required = true,
                                        onTakePhoto = { takePhoto(item.code) },
                                        onDeletePhoto = { photo ->
                                            PhotoUtil.deletePhoto(context, photo)
                                            val remaining = hazardPhotos[item.code].orEmpty() - photo
                                            hazardPhotos = if (remaining.isEmpty()) {
                                                hazardPhotos - item.code
                                            } else {
                                                hazardPhotos + (item.code to remaining)
                                            }
                                        },
                                        onPreviewPhoto = { previewPhoto = it },
                                        compact = true
                                    )
                                }
                                if (index < TerminalHazardCatalog.items.count { it.category == category } - 1) {
                                    androidx.compose.material3.HorizontalDivider(
                                        modifier = Modifier.padding(start = 16.dp),
                                        color = com.xinyuan.gas.ui.theme.GraySeparator.copy(alpha = 0.3f),
                                        thickness = 0.5.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            IOSGroupCard(modifier = Modifier.padding(top = 16.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .border(
                            width = 1.dp,
                            color = BluePrimary.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(
                            color = BluePrimary.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "气源排查登记（如有异常需登记）",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = BluePrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TerminalHazardCatalog.gasSourceIssues.forEachIndexed { index, issue ->
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = issue in gasSourceIssues,
                                onCheckedChange = { checked ->
                                    gasSourceIssues = if (checked) gasSourceIssues + issue else gasSourceIssues - issue
                                }
                            )
                            Text(
                                issue,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF1C1C1E),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (index < TerminalHazardCatalog.gasSourceIssues.lastIndex) {
                            androidx.compose.material3.HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                color = com.xinyuan.gas.ui.theme.GraySeparator.copy(alpha = 0.3f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
                MultiPhotoPickerRow(
                    title = "钢瓶二维码拍照",
                    photos = cylinderQrPhotos,
                    onTakePhoto = { takePhoto("cylinder_qr") },
                    onPreviewPhoto = { previewPhoto = it },
                    onDeletePhoto = { photo ->
                        PhotoUtil.deletePhoto(context, photo)
                        cylinderQrPhotos = cylinderQrPhotos - photo
                    }
                )
                MultiPhotoPickerRow(
                    title = "钢瓶拍照",
                    photos = cylinderPhotos,
                    onTakePhoto = { takePhoto("cylinder") },
                    onPreviewPhoto = { previewPhoto = it },
                    onDeletePhoto = { photo ->
                        PhotoUtil.deletePhoto(context, photo)
                        cylinderPhotos = cylinderPhotos - photo
                    }
                )
            }

            IOSGroupCard(title = "备注") {
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .heightIn(min = 80.dp),
                    placeholder = { Text("请输入备注信息", color = GrayTertiaryText) },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent
                    )
                )
            }

            Spacer(Modifier.height(16.dp))
            IOSPrimaryButton("保存核查记录", onClick = { validateAndSave() })
            Spacer(Modifier.height(32.dp))
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
private fun FormIntroCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(GreenSuccess.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column {
            Text(
                text = "新增终端核查",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF1C1C1E)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "按 APP 版“终端隐患核查清单登记”录入，保存后写入云端并按公司隔离。",
                style = MaterialTheme.typography.bodyMedium,
                color = GraySecondaryText
            )
        }
    }
}

@Composable
private fun PhotoPickerRow(
    title: String,
    photoUri: String,
    required: Boolean,
    onTakePhoto: () -> Unit,
    onDeletePhoto: () -> Unit,
    onPreviewPhoto: () -> Unit,
    enabled: Boolean = true,
    helperText: String? = null,
    compact: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (required) {
                    Text(" *", color = RedDanger, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (!helperText.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = helperText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) GraySecondaryText else RedDanger
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Box(
            modifier = Modifier
                .size(if (compact) 74.dp else 86.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (enabled || photoUri.isNotEmpty()) GrayBackground else Color(0xFFF0F0F0))
                .clickable(
                    enabled = enabled || photoUri.isNotEmpty(),
                    onClick = if (photoUri.isNotEmpty()) onPreviewPhoto else onTakePhoto
                ),
            contentAlignment = Alignment.Center
        ) {
            if (photoUri.isNotEmpty()) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                IconButton(
                    onClick = onDeletePhoto,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "删除照片", tint = RedDanger, modifier = Modifier.size(16.dp))
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val iconColor = if (enabled) GraySecondaryText else GrayTertiaryText
                    Icon(Icons.Default.CameraAlt, contentDescription = "拍照", tint = iconColor, modifier = Modifier.size(28.dp))
                    Text("拍照", color = iconColor, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun MultiPhotoPickerRow(
    title: String,
    photos: List<String>,
    required: Boolean = false,
    compact: Boolean = false,
    onTakePhoto: () -> Unit,
    onPreviewPhoto: (String) -> Unit,
    onDeletePhoto: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            if (required) {
                Text(" *", color = RedDanger, style = MaterialTheme.typography.titleMedium)
            }
        }
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
                        contentDescription = title,
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
