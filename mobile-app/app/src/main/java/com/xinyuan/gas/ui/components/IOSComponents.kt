package com.xinyuan.gas.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xinyuan.gas.R
import com.xinyuan.gas.ui.theme.*

@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    large: Boolean = false
) {
    Image(
        painter = painterResource(R.drawable.logo_xinyuan),
        contentDescription = "淮北鑫远燃气有限公司",
        modifier = modifier
            .width(if (large) 212.dp else 150.dp)
            .height(if (large) 122.dp else 86.dp),
        contentScale = ContentScale.Fit
    )
}

// iOS风格分组卡片
@Composable
fun IOSGroupCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = GraySecondaryText,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp, top = 16.dp)
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                content()
            }
        }
    }
}

// iOS风格文本输入行
@Composable
fun IOSTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    showDivider: Boolean = true,
    required: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.width(80.dp), verticalAlignment = Alignment.CenterVertically) {
                if (required) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color.Red, CircleShape)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = singleLine,
                minLines = minLines,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent
                ),
                placeholder = {
                    Text("请输入$label", color = GrayTertiaryText, fontSize = 15.sp)
                }
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                color = GraySeparator.copy(alpha = 0.3f),
                thickness = 0.5.dp
            )
        }
    }
}

// iOS风格下拉选择行
@Composable
fun IOSDropdownRow(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    showDivider: Boolean = true,
    required: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.width(80.dp), verticalAlignment = Alignment.CenterVertically) {
                if (required) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color.Red, CircleShape)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value.ifEmpty { "请选择" },
                    color = if (value.isEmpty()) GrayTertiaryText else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = GrayTertiaryText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                color = GraySeparator.copy(alpha = 0.3f),
                thickness = 0.5.dp
            )
        }
    }
}

// iOS风格开关行
@Composable
fun IOSSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = GreenSuccess,
                    checkedThumbColor = Color.White
                )
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                color = GraySeparator.copy(alpha = 0.3f),
                thickness = 0.5.dp
            )
        }
    }
}

// 可点击行
@Composable
fun IOSClickableRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    showDivider: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(80.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value.ifEmpty { "请选择" },
                    color = if (value.isEmpty()) GrayTertiaryText else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = GrayTertiaryText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                color = GraySeparator.copy(alpha = 0.3f),
                thickness = 0.5.dp
            )
        }
    }
}

// 主按钮
@Composable
fun IOSPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = BluePrimary
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(text, fontSize = 17.sp)
    }
}

// iOS风格顶部栏
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IOSTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(title, style = MaterialTheme.typography.titleMedium)
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = BluePrimary)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = GrayBackground,
            titleContentColor = Color(0xFF1C1C1E)
        )
    )
}

// 统计卡片
@Composable
fun StatCard(
    title: String,
    value: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.bodySmall, color = GraySecondaryText)
        }
    }
}

// 快捷入口按钮
@Composable
fun QuickActionButton(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// 记录卡片
@Composable
fun RecordCard(
    record: com.xinyuan.gas.data.model.InspectionRecord,
    onClick: () -> Unit
) {
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
                Text(record.customerName, style = MaterialTheme.typography.titleMedium)
                val statusColor = if (record.isRectified == "已整改") GreenSuccess else OrangeAccent
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        record.isRectified,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = statusColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "地址：${record.customerAddress}",
                style = MaterialTheme.typography.bodySmall,
                color = GraySecondaryText,
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "检查人：${record.inspectorName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = GraySecondaryText
                )
                Text(
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                        .format(java.util.Date(record.inspectionDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = GraySecondaryText
                )
            }
        }
    }
}
