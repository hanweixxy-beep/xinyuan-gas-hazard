package com.xinyuan.gas.ui.cloud

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.xinyuan.gas.data.sync.CloudRecordDetail
import com.xinyuan.gas.ui.theme.BrandGreen
import com.xinyuan.gas.ui.theme.BrandGreenSoft
import com.xinyuan.gas.ui.theme.BrandGreenStrong
import com.xinyuan.gas.ui.theme.GrayBackground
import com.xinyuan.gas.ui.theme.GraySeparator
import com.xinyuan.gas.ui.theme.GraySurface
import com.xinyuan.gas.ui.theme.GrayTertiaryText
import com.xinyuan.gas.ui.theme.GreenSuccess
import com.xinyuan.gas.ui.theme.InkPrimary
import com.xinyuan.gas.ui.theme.InkSecondary
import com.xinyuan.gas.ui.theme.OrangeAccent
import com.xinyuan.gas.ui.theme.OrangeSoft
import com.xinyuan.gas.ui.theme.RedDanger
import com.xinyuan.gas.util.PhotoUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CloudRectificationDialog(
    detail: CloudRecordDetail,
    defaultPerson: String,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        recordId: Long,
        rectificationDate: String,
        rectificationNote: String,
        rectificationPerson: String,
        photoUris: List<String>
    ) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val dateText = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date()) }
    var rectificationPerson by remember(detail.id) {
        mutableStateOf(defaultPerson.ifBlank { detail.inspectorName })
    }
    var rectificationNote by remember(detail.id) { mutableStateOf("") }
    var photoUris by remember(detail.id) { mutableStateOf(emptyList<String>()) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var previewPhotoUri by remember { mutableStateOf<String?>(null) }
    var validationError by remember { mutableStateOf("") }
    val keyboardVisible = WindowInsets.isImeVisible

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingPhotoUri
        if (success && uri != null) {
            PhotoUtil.addWatermark(
                context = context,
                photoUri = uri,
                address = detail.customerAddress,
                inspector = rectificationPerson.ifBlank { defaultPerson },
                customerName = detail.customerName,
                customerPhone = detail.customerPhone,
                locationText = PhotoUtil.getLocationWatermark(context)
            )
            photoUris = photoUris + uri.toString()
            validationError = ""
        } else {
            uri?.let { PhotoUtil.deletePhoto(context, it.toString()) }
        }
        pendingPhotoUri = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            val uri = PhotoUtil.createImageUri(context)
            pendingPhotoUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "需要相机权限才能拍摄整改照片", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestPhoto() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    fun dismissAndClean() {
        if (submitting) return
        photoUris.forEach { PhotoUtil.deletePhoto(context, it) }
        pendingPhotoUri?.let { PhotoUtil.deletePhoto(context, it.toString()) }
        onDismiss()
    }

    fun submit() {
        validationError = when {
            rectificationPerson.isBlank() -> "请填写整改人"
            rectificationNote.isBlank() -> "请填写整改情况"
            photoUris.isEmpty() -> "请至少拍摄一张整改照片"
            else -> ""
        }
        if (validationError.isNotBlank()) {
            Toast.makeText(context, validationError, Toast.LENGTH_SHORT).show()
            return
        }
        focusManager.clearFocus()
        onSubmit(
            detail.id,
            dateText,
            rectificationNote.trim(),
            rectificationPerson.trim(),
            photoUris
        )
    }

    BackHandler(enabled = !submitting, onBack = ::dismissAndClean)
    Surface(modifier = Modifier.fillMaxSize(), color = GrayBackground) {
        Scaffold(
            modifier = Modifier.safeDrawingPadding(),
            containerColor = GrayBackground,
            topBar = {
                Surface(color = Color.White, border = BorderStroke(0.5.dp, GraySeparator)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(start = 18.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("隐患整改", color = InkPrimary, style = MaterialTheme.typography.titleMedium)
                            Text(
                                detail.customerName,
                                color = InkSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = ::dismissAndClean, enabled = !submitting) {
                            Icon(Icons.Default.Close, contentDescription = "关闭整改", tint = InkPrimary)
                        }
                    }
                }
            },
            bottomBar = {
                if (!keyboardVisible) {
                    Surface(color = Color.White, border = BorderStroke(0.5.dp, GraySeparator)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = ::dismissAndClean,
                                enabled = !submitting,
                                modifier = Modifier
                                    .weight(0.42f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, GraySeparator)
                            ) {
                                Text("取消", color = InkSecondary)
                            }
                            Button(
                                onClick = ::submit,
                                enabled = !submitting,
                                modifier = Modifier
                                    .weight(0.58f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
                            ) {
                                if (submitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Text("  正在提交")
                                } else {
                                    Text("提交整改")
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = OrangeSoft,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = OrangeAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.padding(start = 9.dp)) {
                                Text("待整改隐患", color = InkPrimary, fontWeight = FontWeight.SemiBold)
                                Text(
                                    detail.hazards.joinToString("；") { it.name }.ifBlank { detail.checkResult },
                                    color = InkSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
                item {
                    RectificationSection(title = "用户信息") {
                        RectificationInfoLine("用户", detail.customerName)
                        RectificationInfoLine("电话", detail.customerPhone)
                        RectificationInfoLine("地址", detail.customerAddress, showDivider = false)
                    }
                }
                item {
                    RectificationSection(title = "整改登记") {
                        RectificationInfoLine("整改日期", dateText)
                        OutlinedTextField(
                            value = rectificationPerson,
                            onValueChange = {
                                rectificationPerson = it
                                validationError = ""
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            label = { Text("整改人") },
                            singleLine = true,
                            isError = validationError == "请填写整改人",
                            supportingText = if (validationError == "请填写整改人") {
                                { Text(validationError) }
                            } else {
                                null
                            },
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            colors = rectificationFieldColors()
                        )
                        OutlinedTextField(
                            value = rectificationNote,
                            onValueChange = {
                                rectificationNote = it
                                validationError = ""
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                                .heightIn(min = 108.dp),
                            label = { Text("整改情况") },
                            placeholder = { Text("填写采取的整改措施和现场结果") },
                            minLines = 3,
                            isError = validationError == "请填写整改情况",
                            supportingText = if (validationError == "请填写整改情况") {
                                { Text(validationError) }
                            } else {
                                null
                            },
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            colors = rectificationFieldColors()
                        )
                            Text(
                                "整改照片",
                                color = InkPrimary,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(start = 14.dp, top = 12.dp)
                            )
                            Text(
                                "可连续拍摄多张，照片会自动添加用户、地址和拍摄时间水印。",
                                color = GrayTertiaryText,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item {
                                    Surface(
                                        modifier = Modifier
                                            .size(104.dp)
                                            .clickable(enabled = !submitting, onClick = ::requestPhoto),
                                        color = BrandGreenSoft,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, BrandGreenSoft)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = BrandGreenStrong)
                                            Text(
                                                if (photoUris.isEmpty()) "拍摄" else "继续拍摄",
                                                color = BrandGreenStrong,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(top = 6.dp)
                                            )
                                        }
                                    }
                                }
                                items(photoUris, key = { it }) { photoUri ->
                                    Box(
                                        modifier = Modifier
                                            .size(104.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(GraySurface)
                                    ) {
                                        SubcomposeAsyncImage(
                                            model = photoUri,
                                            contentDescription = "整改照片",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clickable { previewPhotoUri = photoUri },
                                            contentScale = ContentScale.Crop,
                                            loading = {
                                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(22.dp),
                                                        color = BrandGreen,
                                                        strokeWidth = 2.dp
                                                    )
                                                }
                                            }
                                        )
                                        Surface(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp),
                                            color = Color.Black.copy(alpha = 0.62f),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    PhotoUtil.deletePhoto(context, photoUri)
                                                    photoUris = photoUris - photoUri
                                                },
                                                enabled = !submitting,
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "删除照片",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (validationError == "请至少拍摄一张整改照片") {
                                Text(
                                    validationError,
                                    color = RedDanger,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    previewPhotoUri?.let { photoUri ->
        Dialog(
            onDismissRequest = { previewPhotoUri = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xF2181818)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "整改照片",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { previewPhotoUri = null }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭预览", tint = Color.White)
                        }
                    }
                    SubcomposeAsyncImage(
                        model = photoUri,
                        contentDescription = "整改照片预览",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

@Composable
private fun RectificationSection(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, GraySeparator)
    ) {
        Column {
            Text(
                title,
                color = InkPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            )
            HorizontalDivider(color = GraySeparator)
            content()
        }
    }
}

@Composable
private fun RectificationInfoLine(label: String, value: String, showDivider: Boolean = true) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(label, color = GrayTertiaryText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(76.dp))
            Text(value.ifBlank { "-" }, color = InkPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        if (showDivider) HorizontalDivider(color = GraySeparator, modifier = Modifier.padding(start = 14.dp))
    }
}

@Composable
private fun rectificationFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BrandGreen,
    unfocusedBorderColor = GraySeparator,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White
)
