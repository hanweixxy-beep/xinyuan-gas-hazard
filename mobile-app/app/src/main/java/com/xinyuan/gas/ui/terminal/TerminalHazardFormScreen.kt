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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.xinyuan.gas.data.sync.CloudOrganizationOptions
import com.xinyuan.gas.ui.components.BrandLogo
import com.xinyuan.gas.ui.components.IOSClickableRow
import com.xinyuan.gas.ui.components.IOSDropdownRow
import com.xinyuan.gas.ui.components.IOSGroupCard
import com.xinyuan.gas.ui.components.IOSPrimaryButton
import com.xinyuan.gas.ui.components.IOSTextField
import com.xinyuan.gas.ui.components.IOSTopBar
import com.xinyuan.gas.ui.inspection.InspectionViewModel
import com.xinyuan.gas.ui.inspection.CloudSaveStatus
import com.xinyuan.gas.ui.inspection.CloudSaveUiState
import com.xinyuan.gas.ui.theme.BluePrimary
import com.xinyuan.gas.ui.theme.BrandGreen
import com.xinyuan.gas.ui.theme.BrandGreenSoft
import com.xinyuan.gas.ui.theme.BrandGreenStrong
import com.xinyuan.gas.ui.theme.GrayBackground
import com.xinyuan.gas.ui.theme.GraySecondaryText
import com.xinyuan.gas.ui.theme.GraySeparator
import com.xinyuan.gas.ui.theme.GraySurface
import com.xinyuan.gas.ui.theme.GrayTertiaryText
import com.xinyuan.gas.ui.theme.GreenSuccess
import com.xinyuan.gas.ui.theme.InkPrimary
import com.xinyuan.gas.ui.theme.OrangeAccent
import com.xinyuan.gas.ui.theme.OrangeSoft
import com.xinyuan.gas.ui.theme.RedDanger
import com.xinyuan.gas.ui.theme.RedSoft
import com.xinyuan.gas.util.PhotoUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun TerminalHazardFormScreen(
    viewModel: InspectionViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit = onBack,
    modifier: Modifier = Modifier,
    editRecordId: Long = 0L
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val formScrollState = rememberScrollState()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }
    val prefs = remember { context.getSharedPreferences("gas_prefs", android.content.Context.MODE_PRIVATE) }
    val cloudSettings by viewModel.cloudSettings.collectAsState()
    val organizationOptions by viewModel.organizationOptions.collectAsState()
    val organizationOptionsLoading by viewModel.organizationOptionsLoading.collectAsState()
    val organizationOptionsLoaded by viewModel.organizationOptionsLoaded.collectAsState()
    val cloudSaveState by viewModel.cloudSaveState.collectAsState()
    val cloudSubmitInProgress by viewModel.cloudSubmitInProgress.collectAsState()
    var draftUuid by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var draftCreated by rememberSaveable { mutableStateOf(false) }
    var showSubmitConfirmation by remember { mutableStateOf(false) }

    var customerName by remember { mutableStateOf("") }
    var storefrontPhoto by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var customerType by remember { mutableStateOf("") }
    var customerAddress by remember { mutableStateOf("") }
    var locationLatitude by remember { mutableStateOf<Double?>(null) }
    var locationLongitude by remember { mutableStateOf<Double?>(null) }
    var locationAccuracyMeters by remember { mutableStateOf<Float?>(null) }
    var locationCapturedAt by remember { mutableStateOf(0L) }
    var isResolvingLocation by remember { mutableStateOf(false) }
    var inspectorName by remember {
        mutableStateOf(cloudSettings.userName.ifBlank { prefs.getString("last_inspector", "") ?: "" })
    }
    val organizationPreferenceKey = "inspection_org:${cloudSettings.baseUrl}:${cloudSettings.companyId}:${cloudSettings.userId}"
    var inspectionRegionId by rememberSaveable(organizationPreferenceKey) {
        mutableStateOf(prefs.getLong("$organizationPreferenceKey:regionId", 0L))
    }
    var inspectionRegionName by rememberSaveable(organizationPreferenceKey) {
        mutableStateOf(prefs.getString("$organizationPreferenceKey:regionName", "").orEmpty())
    }
    var inspectionStoreId by rememberSaveable(organizationPreferenceKey) {
        mutableStateOf(prefs.getLong("$organizationPreferenceKey:storeId", 0L))
    }
    var inspectionStoreName by rememberSaveable(organizationPreferenceKey) {
        mutableStateOf(prefs.getString("$organizationPreferenceKey:storeName", "").orEmpty())
    }
    var usingPreviousOrganization by rememberSaveable(organizationPreferenceKey) { mutableStateOf(true) }
    var showOrganizationPicker by rememberSaveable { mutableStateOf(false) }
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
    val effectiveInspectorName = cloudSettings.userName.ifBlank { inspectorName }
    val submissionMissingFields = listOfNotNull(
        "用户名".takeIf { customerName.isBlank() },
        "电话".takeIf { customerPhone.isBlank() },
        "用户类型".takeIf { customerType.isBlank() },
        "检查人".takeIf { effectiveInspectorName.isBlank() },
        "配送员".takeIf { deliveryPerson.isBlank() }
    )
    val storefrontMissingFields = listOfNotNull("配送员".takeIf { deliveryPerson.isBlank() })
    val storefrontWatermarkReady = storefrontMissingFields.isEmpty()
    val organizationSummary = buildString {
        append(inspectionRegionName.ifBlank { "请选择检查片区和门店" })
        if (inspectionStoreName.isNotBlank()) append(" · $inspectionStoreName")
    }
    LaunchedEffect(cloudSettings.userId) {
        showSubmitConfirmation = false
        viewModel.refreshOrganizationOptions()
    }

    LaunchedEffect(organizationOptions, organizationOptionsLoaded, inspectionRegionId, inspectionStoreId) {
        if (organizationOptionsLoaded) {
            val region = organizationOptions.regions.find { it.id == inspectionRegionId }
            val store = organizationOptions.stores.find { it.id == inspectionStoreId && it.regionId == region?.id }
            inspectionRegionId = region?.id ?: 0L
            inspectionRegionName = region?.name.orEmpty()
            inspectionStoreId = store?.id ?: 0L
            inspectionStoreName = store?.name.orEmpty()
        }
    }

    LaunchedEffect(editRecordId) {
        if (editRecordId > 0) {
            viewModel.getTerminalById(editRecordId).collectLatest { record ->
                if (record != null) {
                    draftUuid = record.uuid.ifBlank { draftUuid }
                    draftCreated = record.remoteId.isNotBlank()
                    customerName = record.customerName
                    storefrontPhoto = record.storefrontPhoto
                    customerPhone = record.customerPhone
                    customerType = record.customerType
                    customerAddress = record.customerAddress
                    locationLatitude = record.locationLatitude
                    locationLongitude = record.locationLongitude
                    locationAccuracyMeters = record.locationAccuracyMeters
                    locationCapturedAt = record.locationCapturedAt
                    inspectorName = record.inspectorName
                    inspectionRegionId = record.regionId
                    inspectionRegionName = record.regionName
                    inspectionStoreId = record.storeId
                    inspectionStoreName = record.storeName
                    usingPreviousOrganization = false
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

    fun sharedLocationWatermark(): String {
        val latitude = locationLatitude ?: return ""
        val longitude = locationLongitude ?: return ""
        val accuracy = locationAccuracyMeters?.let { "，精度约${it.toInt()}米" }.orEmpty()
        return String.format(Locale.CHINA, "定位坐标：纬度%.6f，经度%.6f%s", latitude, longitude, accuracy)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val target = currentPhotoTarget
        val uri = currentPhotoUri
        if (success && uri != null) {
            if (target == "storefront") {
                isResolvingLocation = true
                coroutineScope.launch {
                    val locationSnapshot = PhotoUtil.getLocationSnapshot(context)
                    if (!locationSnapshot.isUsable || locationSnapshot.address.isBlank()) {
                        PhotoUtil.deletePhoto(context, uri.toString())
                        Toast.makeText(context, "未获得有效的新定位，请到室外确认定位已开启后重新拍照", Toast.LENGTH_LONG).show()
                    } else {
                        customerAddress = locationSnapshot.address
                        locationLatitude = locationSnapshot.latitude
                        locationLongitude = locationSnapshot.longitude
                        locationAccuracyMeters = locationSnapshot.accuracyMeters
                        locationCapturedAt = locationSnapshot.capturedAt
                        PhotoUtil.addWatermark(
                            context,
                            uri,
                            customerAddress,
                            effectiveInspectorName,
                            customerName,
                            customerPhone,
                            locationSnapshot.watermarkText,
                            deliveryPerson,
                            customerType
                        )
                        if (storefrontPhoto.isNotEmpty()) PhotoUtil.deletePhoto(context, storefrontPhoto)
                        storefrontPhoto = uri.toString()
                        draftCreated = true
                    }
                    isResolvingLocation = false
                }
            } else if (target == "cylinder_qr") {
                PhotoUtil.addWatermark(
                    context, uri, customerAddress, effectiveInspectorName, customerName, customerPhone,
                    sharedLocationWatermark(), deliveryPerson, customerType
                )
                cylinderQrPhotos = cylinderQrPhotos + uri.toString()
            } else if (target == "cylinder") {
                PhotoUtil.addWatermark(
                    context, uri, customerAddress, effectiveInspectorName, customerName, customerPhone,
                    sharedLocationWatermark(), deliveryPerson, customerType
                )
                cylinderPhotos = cylinderPhotos + uri.toString()
            } else if (target.isNotEmpty()) {
                PhotoUtil.addWatermark(
                    context, uri, customerAddress, effectiveInspectorName, customerName, customerPhone,
                    sharedLocationWatermark(), deliveryPerson, customerType
                )
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
        if (isResolvingLocation) {
            Toast.makeText(context, "正在获取新定位，请稍候", Toast.LENGTH_SHORT).show()
            return
        }
        if (!storefrontWatermarkReady) {
            Toast.makeText(
                context,
                "请先填写配送员后再拍照",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (
            target != "storefront" &&
            (storefrontPhoto.isBlank() || customerAddress.isBlank() || locationLatitude == null || locationLongitude == null)
        ) {
            Toast.makeText(context, "请先拍摄门头照片获取用户地址和定位", Toast.LENGTH_SHORT).show()
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

    fun currentRecord(
        resultToSave: String = checkResult,
        rectificationDateToSave: Long = rectificationDate
    ): TerminalHazardRecord {
        val activePhotos = hazardPhotos.filterKeys { it in selectedHazards }
        return TerminalHazardRecord(
            customerName = customerName,
            storefrontPhoto = storefrontPhoto,
            customerPhone = customerPhone,
            customerType = customerType,
            customerAddress = customerAddress,
            locationLatitude = locationLatitude,
            locationLongitude = locationLongitude,
            locationAccuracyMeters = locationAccuracyMeters,
            locationCapturedAt = locationCapturedAt,
            inspectorName = effectiveInspectorName,
            regionId = inspectionRegionId,
            regionName = inspectionRegionName,
            storeId = inspectionStoreId,
            storeName = inspectionStoreName,
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
            remark = remark,
            uuid = draftUuid
        )
    }

    fun validateAndSave(
        resultToSave: String = checkResult,
        rectificationDateToSave: Long = rectificationDate,
        confirmed: Boolean = false
    ) {
        if (cloudSubmitInProgress) return
        if (!organizationOptionsLoaded) {
            Toast.makeText(context, "正在获取检查片区和门店，请稍后再提交", Toast.LENGTH_SHORT).show()
            viewModel.refreshOrganizationOptions()
            showOrganizationPicker = true
            return
        }
        val selectedRegion = organizationOptions.regions.find { it.id == inspectionRegionId }
        val selectedStore = organizationOptions.stores.find {
            it.id == inspectionStoreId && it.regionId == selectedRegion?.id
        }
        if (selectedRegion == null || selectedStore == null) {
            Toast.makeText(context, "请选择本次检查的片区和门店后再提交", Toast.LENGTH_SHORT).show()
            showOrganizationPicker = true
            return
        }
        if (submissionMissingFields.isNotEmpty()) {
            Toast.makeText(context, "请先填写${submissionMissingFields.joinToString("、")}", Toast.LENGTH_SHORT).show()
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
            if (selectedHazards.isEmpty() && gasSourceIssues.isEmpty()) {
                Toast.makeText(context, "请选择未清零隐患项目或气源排查异常", Toast.LENGTH_SHORT).show()
                return
            }
            val missingPhoto = selectedHazards.any { hazardPhotos[it].orEmpty().isEmpty() }
            if (missingPhoto) {
                Toast.makeText(context, "每个未清零隐患项目都需要拍照", Toast.LENGTH_SHORT).show()
                return
            }
        }
        if (gasSourceIssues.isNotEmpty() && cylinderQrPhotos.isEmpty() && cylinderPhotos.isEmpty()) {
            Toast.makeText(context, "气源排查异常时，请拍摄钢瓶二维码或钢瓶照片", Toast.LENGTH_SHORT).show()
            return
        }
        if (
            editRecordId > 0L &&
            originalCheckResult == TerminalHazardCatalog.RESULT_NOT_CLEARED &&
            resultToSave == TerminalHazardCatalog.RESULT_CLEARED
        ) {
            Toast.makeText(context, "请通过快捷操作中的隐患整改完成清零", Toast.LENGTH_SHORT).show()
            return
        }

        if (!confirmed) {
            showSubmitConfirmation = true
            return
        }
        prefs.edit().putString("last_inspector", effectiveInspectorName).apply()
        viewModel.saveTerminalRecord(currentRecord(resultToSave, rectificationDateToSave))
    }

    LaunchedEffect(
        draftCreated,
        customerName,
        customerPhone,
        customerType,
        customerAddress,
        locationLatitude,
        locationLongitude,
        locationAccuracyMeters,
        locationCapturedAt,
        inspectionRegionId,
        inspectionStoreId,
        storefrontPhoto,
        deliveryPerson,
        inspectionDate,
        checkResult,
        selectedHazards,
        hazardPhotos,
        gasSourceIssues,
        cylinderQrPhotos,
        cylinderPhotos,
        remark
    ) {
        if (
            draftCreated &&
            storefrontWatermarkReady &&
            storefrontPhoto.isNotBlank() &&
            customerAddress.isNotBlank()
        ) {
            delay(700)
            viewModel.saveTerminalDraft(currentRecord())
        }
    }

    LaunchedEffect(Unit) {
        viewModel.terminalSaveResult.collectLatest { result ->
            result.fold(
                onSuccess = {
                    Toast.makeText(context, "记录已提交到云端", Toast.LENGTH_SHORT).show()
                    onSaved()
                },
                onFailure = {
                    Toast.makeText(context, "保存失败: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    if (showSubmitConfirmation) {
        AlertDialog(
            onDismissRequest = { showSubmitConfirmation = false },
            title = { Text("确认提交检查记录？") },
            text = {
                Text("用户：$customerName\n检查结果：$checkResult\n\n请核对用户信息、检查结果及照片，确认后提交本次检查记录。")
            },
            confirmButton = {
                TextButton(
                    enabled = !cloudSubmitInProgress,
                    onClick = {
                        showSubmitConfirmation = false
                        validateAndSave(confirmed = true)
                    }
                ) { Text("确认提交") }
            },
            dismissButton = {
                TextButton(onClick = { showSubmitConfirmation = false }) { Text("返回检查") }
            }
        )
    }

    Scaffold(
        modifier = modifier.imePadding(),
        topBar = {
            IOSTopBar(
                title = if (editRecordId > 0) "编辑检查" else "新增检查",
                onBack = onBack
            )
        },
        bottomBar = {
            FormActionBar(
                submitting = cloudSubmitInProgress || isResolvingLocation,
                onCancel = onBack,
                onSubmit = { validateAndSave() }
            )
        },
        containerColor = GrayBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            CloudSaveBanner(cloudSaveState)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(formScrollState)
                    .padding(horizontal = 18.dp)
            ) {
                listOf(0, 1, 2).forEach { section ->
                when (section) {
                    0 -> {
                        FormIntroCard()
                        IOSGroupCard(title = "用户信息") {
                            IOSTextField("用户名称", customerName, { customerName = it }, required = true)
                            IOSTextField(
                                "联系电话",
                                customerPhone,
                                { customerPhone = it },
                                keyboardType = KeyboardType.Phone,
                                required = true
                            )
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
                            LockedAccountField(
                                label = "检查人",
                                value = cloudSettings.userName.ifBlank { inspectorName }
                            )
                            InspectionOrganizationRow(
                                summary = organizationSummary,
                                usingPrevious = usingPreviousOrganization,
                                loading = organizationOptionsLoading,
                                complete = inspectionRegionId > 0L && inspectionStoreId > 0L,
                                onChange = {
                                    viewModel.refreshOrganizationOptions()
                                    showOrganizationPicker = true
                                }
                            )
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
                            IOSTextField(
                                "配送员",
                                deliveryPerson,
                                { deliveryPerson = it },
                                showDivider = false,
                                required = true
                            )
                        }

                        IOSGroupCard(title = "现场信息") {
                            LocationCard(customerAddress, isResolvingLocation, locationAccuracyMeters)
                            Text(
                                text = "填写配送员即可拍照。已填写的用户信息显示在水印中，未填写的内容不显示；提交前须补齐全部必填信息。",
                                style = MaterialTheme.typography.bodySmall,
                                color = GraySecondaryText,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                            PhotoPickerRow(
                                title = "门头照片",
                                photoUri = storefrontPhoto,
                                required = true,
                                enabled = !isResolvingLocation,
                                helperText = if (isResolvingLocation) {
                                    "正在主动获取新的高精度定位…"
                                } else if (storefrontWatermarkReady) {
                                    "拍摄时自动获取用户地址与经纬度"
                                } else {
                                    "请先填写：${storefrontMissingFields.joinToString("、")}"
                                },
                                onTakePhoto = { takePhoto("storefront") },
                                onDeletePhoto = {
                                    PhotoUtil.deletePhoto(context, storefrontPhoto)
                                    storefrontPhoto = ""
                                    customerAddress = ""
                                    locationLatitude = null
                                    locationLongitude = null
                                    locationAccuracyMeters = null
                                    locationCapturedAt = 0L
                                },
                                onPreviewPhoto = { previewPhoto = storefrontPhoto }
                            )
                        }
                    }

                    1 -> {
                        IOSGroupCard(title = "检查结论") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ResultChoice(
                                    text = TerminalHazardCatalog.RESULT_CLEARED,
                                    selected = checkResult == TerminalHazardCatalog.RESULT_CLEARED,
                                    color = BrandGreen,
                                    softColor = BrandGreenSoft,
                                    onClick = { setResult(TerminalHazardCatalog.RESULT_CLEARED) },
                                    modifier = Modifier.weight(1f)
                                )
                                ResultChoice(
                                    text = "发现隐患",
                                    selected = checkResult == TerminalHazardCatalog.RESULT_NOT_CLEARED,
                                    color = OrangeAccent,
                                    softColor = OrangeSoft,
                                    onClick = { setResult(TerminalHazardCatalog.RESULT_NOT_CLEARED) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        if (checkResult == TerminalHazardCatalog.RESULT_CLEARED) {
                            IOSGroupCard(title = "隐患项目") {
                                Text(
                                    text = "本次检查未发现需要登记的未清零隐患。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = GraySecondaryText,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                                )
                            }
                        } else {
                            TerminalHazardCatalog.categories.forEach { category ->
                                IOSGroupCard(title = category) {
                                    TerminalHazardCatalog.items
                                        .filter { it.category == category }
                                        .forEach { item ->
                                            val selected = item.code in selectedHazards
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                                                    .border(
                                                        1.dp,
                                                        if (selected) OrangeAccent.copy(alpha = 0.35f) else GraySeparator,
                                                        RoundedCornerShape(11.dp)
                                                    )
                                                    .background(
                                                        if (selected) OrangeSoft else Color.White,
                                                        RoundedCornerShape(11.dp)
                                                    )
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { setHazardSelected(item.code, !selected) }
                                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Checkbox(
                                                        checked = selected,
                                                        onCheckedChange = { checked -> setHazardSelected(item.code, checked) }
                                                    )
                                                    Text(
                                                        item.name,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                                        ),
                                                        color = InkPrimary,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                if (selected) {
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
                                            }
                                        }
                                    Spacer(Modifier.height(11.dp))
                                }
                            }
                        }

                        IOSGroupCard(title = "气源排查") {
                            Text(
                                "如发现异常请选择对应项目；无异常可以直接继续。",
                                style = MaterialTheme.typography.bodySmall,
                                color = GraySecondaryText,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            TerminalHazardCatalog.gasSourceIssues.forEach { issue ->
                                val selected = issue in gasSourceIssues
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 2.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(if (selected) OrangeSoft else Color.Transparent)
                                        .clickable {
                                            gasSourceIssues = if (selected) gasSourceIssues - issue else gasSourceIssues + issue
                                        }
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = { checked ->
                                            gasSourceIssues = if (checked) gasSourceIssues + issue else gasSourceIssues - issue
                                        }
                                    )
                                    Text(issue, style = MaterialTheme.typography.bodyMedium, color = InkPrimary)
                                }
                            }
                            MultiPhotoPickerRow(
                                title = "钢瓶二维码照片",
                                photos = cylinderQrPhotos,
                                onTakePhoto = { takePhoto("cylinder_qr") },
                                onPreviewPhoto = { previewPhoto = it },
                                onDeletePhoto = { photo ->
                                    PhotoUtil.deletePhoto(context, photo)
                                    cylinderQrPhotos = cylinderQrPhotos - photo
                                }
                            )
                            MultiPhotoPickerRow(
                                title = "钢瓶照片",
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
                                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                                    .heightIn(min = 88.dp),
                                placeholder = { Text("补充说明，选填", color = GrayTertiaryText) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = GraySeparator,
                                    focusedBorderColor = BrandGreen,
                                    cursorColor = BrandGreen
                                )
                            )
                        }
                    }

                    else -> {
                        IOSGroupCard(title = "用户与现场") {
                            SummaryRow("用户", "$customerName · $customerType")
                            SummaryRow("电话", customerPhone)
                            SummaryRow("地址", customerAddress)
                            SummaryRow("检查日期", dateFormat.format(Date(inspectionDate)), showDivider = false)
                        }
                        IOSGroupCard(title = "检查结果") {
                            SummaryRow(
                                "结论",
                                if (checkResult == TerminalHazardCatalog.RESULT_NOT_CLEARED) {
                                    "发现 ${selectedHazards.size} 项隐患"
                                } else {
                                    TerminalHazardCatalog.RESULT_CLEARED
                                },
                                valueColor = if (checkResult == TerminalHazardCatalog.RESULT_NOT_CLEARED) {
                                    OrangeAccent
                                } else {
                                    BrandGreenStrong
                                }
                            )
                            SummaryRow("检查人", inspectorName)
                            SummaryRow("检查归属", organizationSummary)
                            SummaryRow("配送员", deliveryPerson)
                            SummaryRow(
                                "现场照片",
                                "门头 1 张 · 隐患 ${hazardPhotos.values.sumOf { it.size }} 张 · 钢瓶 ${cylinderQrPhotos.size + cylinderPhotos.size} 张",
                                showDivider = false
                            )
                        }
                        if (selectedHazards.isNotEmpty()) {
                            IOSGroupCard(title = "隐患项目") {
                                TerminalHazardCatalog.items
                                    .filter { it.code in selectedHazards }
                                    .forEachIndexed { index, item ->
                                        SummaryRow(
                                            label = "${index + 1}",
                                            value = item.name,
                                            showDivider = index < selectedHazards.size - 1,
                                            valueColor = OrangeAccent
                                        )
                                    }
                            }
                        }
                        IOSGroupCard(title = "提交说明") {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(BrandGreenSoft, RoundedCornerShape(9.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("云", color = BrandGreen, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    "记录会先保存在本机；已登录云端时自动上传，网络异常时保留为待上传。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = GraySecondaryText,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    if (showOrganizationPicker) {
        OrganizationPickerDialog(
            options = organizationOptions,
            loading = organizationOptionsLoading,
            loaded = organizationOptionsLoaded,
            initialRegionId = inspectionRegionId,
            initialStoreId = inspectionStoreId,
            onDismiss = { showOrganizationPicker = false },
            onConfirm = { regionId, regionName, storeId, storeName ->
                inspectionRegionId = regionId
                inspectionRegionName = regionName
                inspectionStoreId = storeId
                inspectionStoreName = storeName
                usingPreviousOrganization = false
                prefs.edit()
                    .putLong("$organizationPreferenceKey:regionId", regionId)
                    .putString("$organizationPreferenceKey:regionName", regionName)
                    .putLong("$organizationPreferenceKey:storeId", storeId)
                    .putString("$organizationPreferenceKey:storeName", storeName)
                    .apply()
                showOrganizationPicker = false
            }
        )
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
private fun InspectionOrganizationRow(
    summary: String,
    usingPrevious: Boolean,
    loading: Boolean,
    complete: Boolean,
    onChange: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("本次检查归属", style = MaterialTheme.typography.labelMedium, color = GraySecondaryText)
            Spacer(Modifier.height(5.dp))
            Text(summary, style = MaterialTheme.typography.bodyLarge, color = InkPrimary)
            Text(
                when {
                    !complete -> "必选"
                    usingPrevious -> "沿用上次选择"
                    else -> "本次检查"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (complete) GrayTertiaryText else OrangeAccent
            )
        }
        TextButton(onClick = onChange, enabled = !loading) {
            Text(
                when {
                    loading -> "加载中"
                    complete -> "更换"
                    else -> "选择"
                },
                color = BrandGreenStrong,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = GraySeparator)
}

@Composable
private fun OrganizationPickerDialog(
    options: CloudOrganizationOptions,
    loading: Boolean,
    loaded: Boolean,
    initialRegionId: Long,
    initialStoreId: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long, String, Long, String) -> Unit
) {
    var regionId by remember(initialRegionId) { mutableStateOf(initialRegionId) }
    var storeId by remember(initialStoreId) { mutableStateOf(initialStoreId) }
    val selectedRegion = options.regions.find { it.id == regionId }
    val availableStores = options.stores.filter { it.regionId == regionId }
    val selectedStore = availableStores.find { it.id == storeId }
    val regionNames = options.regions.map { it.name }
    val storeNames = availableStores.map { it.name }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择检查片区和门店") },
        text = {
            Column {
                if (loading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = BrandGreen)
                    }
                } else if (!loaded) {
                    Text("片区和门店未加载成功，请检查网络后重试。", color = OrangeAccent)
                } else if (options.regions.isEmpty()) {
                    Text("暂无可选片区，请联系管理员在后台录入。", color = OrangeAccent)
                } else {
                    IOSDropdownRow(
                        label = "片区",
                        value = selectedRegion?.name.orEmpty(),
                        options = regionNames,
                        onSelect = { name ->
                            regionId = options.regions.firstOrNull { it.name == name }?.id ?: 0L
                            storeId = 0L
                        },
                        required = true
                    )
                    IOSDropdownRow(
                        label = "门店",
                        value = selectedStore?.name.orEmpty(),
                        options = storeNames,
                        onSelect = { name ->
                            storeId = availableStores.firstOrNull { it.name == name }?.id ?: 0L
                        },
                        showDivider = false,
                        required = true
                    )
                    if (selectedRegion != null && availableStores.isEmpty()) {
                        Text("该片区暂无可用门店，请联系管理员。", color = OrangeAccent)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        regionId,
                        selectedRegion?.name.orEmpty(),
                        storeId,
                        selectedStore?.name.orEmpty()
                    )
                },
                enabled = loaded && !loading && selectedRegion != null && selectedStore != null
            ) {
                Text("确认选择")
            }
        }
    )
}

@Composable
private fun LockedAccountField(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = GraySecondaryText
        )
        Spacer(Modifier.height(6.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = GraySurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, GraySeparator)
        ) {
            Text(
                text = value,
                color = InkPrimary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
            )
        }
        Text(
            text = "自动关联当前登录账号",
            style = MaterialTheme.typography.bodySmall,
            color = GrayTertiaryText,
            modifier = Modifier.padding(top = 5.dp, bottom = 6.dp)
        )
    }
}

@Composable
private fun CloudSaveBanner(state: CloudSaveUiState) {
    if (state.status == CloudSaveStatus.IDLE) return
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.CHINA) }
    val background = when (state.status) {
        CloudSaveStatus.SAVED -> BrandGreenSoft
        CloudSaveStatus.ERROR -> RedSoft
        else -> OrangeSoft
    }
    val foreground = when (state.status) {
        CloudSaveStatus.SAVED -> BrandGreenStrong
        CloudSaveStatus.ERROR -> RedDanger
        else -> OrangeAccent
    }
    val label = when (state.status) {
        CloudSaveStatus.SAVING -> "正在保存到云端…"
        CloudSaveStatus.SAVED -> {
            val suffix = state.savedAt.takeIf { it > 0L }?.let {
                " · ${timeFormat.format(Date(it))}"
            }.orEmpty()
            "${state.message.ifBlank { "草稿已保存到云端" }}$suffix"
        }
        CloudSaveStatus.ERROR -> state.message.ifBlank { "尚未保存，请检查网络" }
        CloudSaveStatus.IDLE -> ""
    }
    Surface(color = background) {
        Text(
            text = label,
            color = foreground,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun FormActionBar(
    submitting: Boolean,
    onCancel: () -> Unit,
    onSubmit: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !submitting,
                modifier = Modifier
                    .weight(1.15f)
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GraySeparator),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandGreenStrong)
            ) {
                Text(
                    "取消",
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Button(
                onClick = onSubmit,
                enabled = !submitting,
                modifier = Modifier
                    .weight(1.85f)
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(19.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Text("  正在提交", style = MaterialTheme.typography.labelLarge)
                } else {
                    Text("提交检查记录", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun LocationCard(address: String, resolving: Boolean, accuracyMeters: Float?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(BrandGreenSoft, RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color.White, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("位", color = BrandGreen, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                when {
                    resolving -> "正在获取新定位"
                    address.isBlank() -> "等待获取用户地址"
                    else -> "已获取用户地址"
                },
                style = MaterialTheme.typography.labelMedium,
                color = InkPrimary
            )
            Text(
                when {
                    resolving -> "请保持在当前用户位置，通常需要数秒"
                    address.isBlank() -> "拍摄门头照片后自动记录定位地址"
                    accuracyMeters != null -> "$address · 精度约${accuracyMeters.toInt()}米"
                    else -> address
                },
                style = MaterialTheme.typography.bodySmall,
                color = GraySecondaryText,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun ResultChoice(
    text: String,
    selected: Boolean,
    color: Color,
    softColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 50.dp)
            .border(
                1.dp,
                if (selected) color.copy(alpha = 0.55f) else GraySeparator,
                RoundedCornerShape(12.dp)
            )
            .background(if (selected) softColor else Color.White, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) color else GraySecondaryText,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    showDivider: Boolean = true,
    valueColor: Color = InkPrimary
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = GraySecondaryText,
                modifier = Modifier.size(width = 74.dp, height = 24.dp)
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = valueColor,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
        if (showDivider) {
            HorizontalDivider(color = GraySeparator)
        }
    }
}

@Composable
private fun FormIntroCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BrandGreenSoft)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column {
            Text(
                text = "新增终端核查",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = InkPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "所有内容集中在当前页面，可按现场顺序填写；拍照前会自动检查水印信息。",
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
