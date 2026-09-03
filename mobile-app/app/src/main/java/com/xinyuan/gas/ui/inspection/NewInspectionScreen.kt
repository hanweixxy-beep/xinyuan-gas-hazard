package com.xinyuan.gas.ui.inspection

import android.app.DatePickerDialog
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.xinyuan.gas.data.model.InspectionRecord
import com.xinyuan.gas.ui.components.*
import com.xinyuan.gas.ui.theme.*
import com.xinyuan.gas.util.PhotoUtil
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewInspectionScreen(
    viewModel: InspectionViewModel,
    onBack: () -> Unit,
    editRecordId: Long = 0L
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }
    val prefs = remember { context.getSharedPreferences("gas_prefs", android.content.Context.MODE_PRIVATE) }

    // 表单状态
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var customerType by remember { mutableStateOf("") }
    var customerAddress by remember { mutableStateOf("") }
    var inspectorName by remember { mutableStateOf(prefs.getString("last_inspector", "") ?: "") }
    var inspectionDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var photoUris by remember { mutableStateOf(listOf<String>()) }
    var previewPhoto by remember { mutableStateOf<String?>(null) }
    var photoLocationSnapshot by remember { mutableStateOf<PhotoUtil.LocationSnapshot?>(null) }

    var hazardAlarm by remember { mutableStateOf("") }
    var hazardPipeline by remember { mutableStateOf("") }
    var hazardStove by remember { mutableStateOf("") }
    var hazardHose by remember { mutableStateOf("") }
    var hazardConnector by remember { mutableStateOf("") }
    var hazardValve by remember { mutableStateOf("") }

    var useOil by remember { mutableStateOf(false) }
    var useElectric by remember { mutableStateOf(false) }
    var useLPG by remember { mutableStateOf(false) }
    var usePipelineGas by remember { mutableStateOf(false) }
    var monthlyGasUsage by remember { mutableStateOf("") }

    var deliveryPerson by remember { mutableStateOf("") }
    var isRectified by remember { mutableStateOf("未整改") }
    var rectificationDate by remember { mutableStateOf(0L) }
    var remark by remember { mutableStateOf("") }

    // 加载编辑数据
    LaunchedEffect(editRecordId) {
        if (editRecordId > 0) {
            viewModel.getById(editRecordId).collectLatest { record ->
                if (record != null) {
                    customerName = record.customerName
                    customerPhone = record.customerPhone
                    customerType = record.customerType
                    customerAddress = record.customerAddress
                    inspectorName = record.inspectorName
                    inspectionDate = record.inspectionDate
                    photoUris = record.photoPath.split(",").filter { it.isNotEmpty() }
                    hazardAlarm = record.hazardAlarm
                    hazardPipeline = record.hazardPipeline
                    hazardStove = record.hazardStove
                    hazardHose = record.hazardHose
                    hazardConnector = record.hazardConnector
                    hazardValve = record.hazardValve
                    useOil = record.useOil
                    useElectric = record.useElectric
                    useLPG = record.useLPG
                    usePipelineGas = record.hazardPipeline == "有"
                    monthlyGasUsage = record.monthlyGasUsage
                    deliveryPerson = record.deliveryPerson
                    isRectified = record.isRectified
                    rectificationDate = record.rectificationDate
                    remark = record.remark
                }
            }
        }
    }

    // 必填校验函数
    fun validateRequired(): Boolean {
        if (customerName.isBlank() || customerPhone.isBlank() || customerType.isBlank() || customerAddress.isBlank()) return false
        if (inspectorName.isBlank()) return false
        if (hazardAlarm.isBlank() || hazardStove.isBlank() || hazardHose.isBlank() || hazardConnector.isBlank() || hazardValve.isBlank()) return false
        if (deliveryPerson.isBlank()) return false
        return true
    }

    // 保存逻辑
    fun doSave() {
        if (!validateRequired()) {
            Toast.makeText(context, "请填写必填项", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit().putString("last_inspector", inspectorName).apply()
        val record = InspectionRecord(
            id = editRecordId,
            customerName = customerName,
            customerPhone = customerPhone,
            customerType = customerType,
            customerAddress = customerAddress,
            inspectorName = inspectorName,
            inspectionDate = inspectionDate,
            photoPath = photoUris.joinToString(","),
            hazardAlarm = hazardAlarm,
            hazardPipeline = if (usePipelineGas) "有" else "无",
            hazardStove = hazardStove,
            hazardHose = hazardHose,
            hazardConnector = hazardConnector,
            hazardValve = hazardValve,
            useOil = useOil,
            useElectric = useElectric,
            useLPG = useLPG,
            monthlyGasUsage = monthlyGasUsage,
            deliveryPerson = deliveryPerson,
            isRectified = isRectified,
            rectificationDate = rectificationDate,
            remark = remark
        )
        viewModel.saveRecord(record)
    }

    // 拍照
    var currentPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && currentPhotoUri != null) {
            val uri = currentPhotoUri!!
            coroutineScope.launch {
                val locationSnapshot = photoLocationSnapshot
                    ?: PhotoUtil.getLocationSnapshot(context).also { photoLocationSnapshot = it }
                if (customerAddress.isBlank() && locationSnapshot.address.isNotBlank()) {
                    customerAddress = locationSnapshot.address
                }
                PhotoUtil.addWatermark(context, uri, customerAddress, inspectorName, customerName, customerPhone, locationSnapshot.watermarkText)
                photoUris = photoUris + uri.toString()
            }
        } else {
            currentPhotoUri?.let { PhotoUtil.deletePhoto(context, it.toString()) }
        }
        currentPhotoUri = null
    }

    // 相机权限请求
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

    // 保存结果
    LaunchedEffect(Unit) {
        viewModel.saveResult.collectLatest { result ->
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
                title = if (editRecordId > 0) "编辑检查记录" else "新建检查登记",
                onBack = onBack,
                actions = {
                    TextButton(onClick = { doSave() }) {
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
            BrandLogo(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp, bottom = 12.dp)
            )

            // 用户信息
            IOSGroupCard(title = "用户信息") {
                IOSTextField("用户名", customerName, { customerName = it }, required = true)
                // 照片
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("现场照片", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        photoUris.forEachIndexed { index, uri ->
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clickable { previewPhoto = uri }
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "照片${index + 1}",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = {
                                        PhotoUtil.deletePhoto(context, uri)
                                        photoUris = photoUris.toMutableList().apply { removeAt(index) }
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "删除",
                                        tint = Color.Red, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        // 添加照片按钮
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(GrayBackground)
                                .clickable {
                                    cameraPermissionLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.CAMERA,
                                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "拍照",
                                tint = GraySecondaryText, modifier = Modifier.size(32.dp))
                        }
                    }
                }
                IOSTextField("电话", customerPhone, { customerPhone = it },
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone, required = true)
                IOSDropdownRow("用户类型", customerType,
                    listOf("居民用户", "商业用户", "餐饮用户", "工业用户", "流动摊贩"),
                    { customerType = it }, required = true)
                IOSTextField(
                    "地址",
                    customerAddress,
                    { customerAddress = it },
                    showDivider = false,
                    required = true,
                    singleLine = false,
                    minLines = 2
                )
            }

            // 检查信息
            IOSGroupCard(title = "检查信息") {
                IOSTextField("检查人", inspectorName, { inspectorName = it }, required = true)
                IOSClickableRow(
                    label = "检查日期",
                    value = dateFormat.format(Date(inspectionDate)),
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = inspectionDate }
                        DatePickerDialog(context, { _, y, m, d ->
                            val selected = Calendar.getInstance().apply { set(y, m, d) }
                            inspectionDate = selected.timeInMillis
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                    }
                )
            }

            // 隐患部位
            IOSGroupCard(title = "隐患部位") {
                IOSDropdownRow("报警器", hazardAlarm, listOf("无报警器", "合格"), { hazardAlarm = it }, required = true)
                IOSDropdownRow("灶具", hazardStove, listOf("无熄保", "合格"), { hazardStove = it }, required = true)
                IOSDropdownRow("软管", hazardHose, listOf("过长", "合格", "非国标"), { hazardHose = it }, required = true)
                IOSDropdownRow("接头", hazardConnector, listOf("三通", "合格"), { hazardConnector = it }, required = true)
                IOSDropdownRow("阀门", hazardValve, listOf("中压阀", "合格", "非国标"), { hazardValve = it }, showDivider = false, required = true)
            }

            // 用气情况
            IOSGroupCard(title = "用气情况") {
                IOSSwitchRow("管道气", usePipelineGas, { usePipelineGas = it })
                IOSSwitchRow("用油", useOil, { useOil = it })
                IOSSwitchRow("用电", useElectric, { useElectric = it })
                IOSSwitchRow("用液化气", useLPG, { useLPG = it })
                IOSTextField("月均用气量", monthlyGasUsage, { monthlyGasUsage = it }, showDivider = false)
            }

            // 配送信息
            IOSGroupCard(title = "配送信息") {
                IOSTextField("配送员", deliveryPerson, { deliveryPerson = it }, showDivider = false, required = true)
            }

            // 整改信息
            IOSGroupCard(title = "整改信息") {
                IOSDropdownRow("是否整改", isRectified, listOf("未整改", "已整改"), { isRectified = it })
                IOSClickableRow(
                    label = "整改日期",
                    value = if (rectificationDate > 0) dateFormat.format(Date(rectificationDate)) else "",
                    onClick = {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(context, { _, y, m, d ->
                            val selected = Calendar.getInstance().apply { set(y, m, d) }
                            rectificationDate = selected.timeInMillis
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                    },
                    showDivider = false
                )
            }

            // 备注
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

            IOSPrimaryButton("保存记录", onClick = { doSave() })

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
