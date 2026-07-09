package com.xinyuan.gas.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xinyuan.gas.BuildConfig
import com.xinyuan.gas.ui.components.BrandLogo
import com.xinyuan.gas.ui.components.IOSTopBar
import com.xinyuan.gas.ui.theme.BluePrimary
import com.xinyuan.gas.ui.theme.GrayBackground
import com.xinyuan.gas.ui.theme.GraySecondaryText
import com.xinyuan.gas.ui.theme.GreenSuccess
import com.xinyuan.gas.ui.theme.OrangeAccent

object AppGuideContent {
    const val APP_TITLE = "淮北鑫远燃气隐患排查登记"
    const val VERSION_SUMMARY = "终端核查自动上传、本机备份包列表、一键恢复、多图隐患拍照与照片压缩优化"

    val mainFeatures = listOf(
        "瓶装液化石油气隐患排查登记：填写用户、检查、整改和现场照片信息。",
        "终端用户隐患核查清单：登记清零状态，未清零项目需按项拍照留证。",
        "隐患整改：可搜索未清零记录，上传整改照片并填写整改说明。",
        "数据导出：支持按条件导出 Excel 或 CSV，Excel 会带入照片。",
        "导出分享：导出的 Excel/CSV 为临时文件，分享或取消后返回 APP 会自动删除。",
        "数据管理与备份：可查看本机备份包列表，按日期识别最新备份，并一键恢复、分享或删除。",
        "未清零隐患项目：每个项目可拍摄多张带水印照片，旧版单张照片记录继续兼容。",
        "云端同步：终端核查记录保存后自动上传，网络异常时保留待上传并自动重试。"
    )

    val dailyWorkflow = listOf(
        "首次使用前，确认相机和定位权限已允许。",
        "现场检查时先填写用户信息，再拍摄现场照片；照片会自动添加日期、时间、地址和定位水印。",
        "地址会优先使用拍照时定位解析结果；没有定位时可以手动填写。",
        "导出前可用筛选条件缩小范围，避免一次性导出过多照片导致等待时间过长。",
        "手机空间不足前，先在“数据管理与备份”生成备份包并分享到电脑或微信文件传输助手，再清理本机数据。"
    )

    val dataSafety = listOf(
        "终端核查记录会先保存在手机本地；已登录云端账号时会自动上传，未登录或网络异常时会保留为待上传。",
        "备份包可多次导出和多次导入，恢复时会按记录唯一编号合并，尽量避免重复数据。",
        "升级 APK 一般不会清空原有数据；如遇大版本变更，仍建议升级前先生成备份。",
        "签名 APK 后续升级必须使用同一个签名文件，请妥善保管本机签名目录。"
    )

    fun versionLabel(): String = "版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）"
}

@Composable
fun AppGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("我已了解", color = BluePrimary, fontWeight = FontWeight.SemiBold)
            }
        },
        title = {
            Text(
                "版本与使用说明",
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFF1C1C1E)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    AppGuideContent.versionLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    color = BluePrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    AppGuideContent.VERSION_SUMMARY,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GraySecondaryText,
                    lineHeight = 21.sp
                )
                Spacer(Modifier.height(14.dp))
                CompactGuideSection("主要功能", AppGuideContent.mainFeatures)
                CompactGuideSection("日常使用", AppGuideContent.dailyWorkflow)
                CompactGuideSection("数据安全", AppGuideContent.dataSafety)
            }
        },
        shape = RoundedCornerShape(18.dp),
        containerColor = Color.White
    )
}

@Composable
fun AppGuideScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { IOSTopBar(title = "版本与使用说明", onBack = onBack) },
        containerColor = GrayBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GrayBackground)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            BrandLogo(large = true, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(12.dp))
            Text(
                AppGuideContent.APP_TITLE,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 23.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color(0xFF1C1C1E),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                AppGuideContent.versionLabel(),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = BluePrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))
            GuideSection("版本重点", listOf(AppGuideContent.VERSION_SUMMARY), OrangeAccent)
            GuideSection("主要功能", AppGuideContent.mainFeatures, BluePrimary)
            GuideSection("日常使用流程", AppGuideContent.dailyWorkflow, GreenSuccess)
            GuideSection("备份与升级注意事项", AppGuideContent.dataSafety, OrangeAccent)
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun CompactGuideSection(title: String, items: List<String>) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = Color(0xFF1C1C1E)
    )
    Spacer(Modifier.height(6.dp))
    items.forEach { item ->
        GuideBullet(item)
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun GuideSection(title: String, items: List<String>, color: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(14.dp),
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
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF1C1C1E)
                )
                Text(
                    "${items.size} 项",
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
            }
            Spacer(Modifier.height(10.dp))
            items.forEach { item ->
                GuideBullet(item)
            }
        }
    }
}

@Composable
private fun GuideBullet(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("•", color = BluePrimary, fontWeight = FontWeight.Bold)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
            color = Color(0xFF3A3A3C),
            modifier = Modifier.weight(1f)
        )
    }
}
