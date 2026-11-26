package com.xinyuan.gas.ui.cloud

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.xinyuan.gas.data.sync.CloudRecordDetail
import com.xinyuan.gas.ui.theme.RedDanger
import com.xinyuan.gas.util.PhotoUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
internal fun CloudRectificationPhotoSupplementDialog(
    detail: CloudRecordDetail,
    onDismiss: () -> Unit,
    onSave: suspend (Long, String, String, Long?, String) -> Result<Unit>,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val originals = remember(detail.photos) { detail.photos.filter { it.type == "rectification" && it.id > 0 } }
    var replacePhotoId by rememberSaveable(detail.id) { mutableStateOf<Long?>(null) }
    var newPhoto by rememberSaveable(detail.id) { mutableStateOf<String?>(null) }
    var reason by rememberSaveable(detail.id) { mutableStateOf("") }
    val operationId = rememberSaveable(detail.id, replacePhotoId, newPhoto, reason) { UUID.randomUUID().toString() }
    var importing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val busy = importing || saving

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            importing = true
            scope.launch {
                try {
                    val copied = withContext(Dispatchers.IO) { PhotoUtil.copyAlbumPhoto(context, uri) }
                    newPhoto?.let { PhotoUtil.deletePhoto(context, it) }
                    newPhoto = copied.toString()
                    errorMessage = ""
                } catch (error: Exception) {
                    errorMessage = error.message ?: "无法读取相册照片"
                } finally {
                    importing = false
                }
            }
        }
    }

    fun dismiss() {
        if (busy) return
        newPhoto?.let { PhotoUtil.deletePhoto(context, it) }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = ::dismiss,
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
        title = { Text("补录整改照片") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("每次补录一张。替换的原照片会留档。", style = MaterialTheme.typography.bodyMedium)
                if (originals.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { replacePhotoId = null },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = replacePhotoId == null, onClick = null, modifier = Modifier.size(36.dp))
                        Text("追加照片")
                    }
                    Text("要替换错图，请选择下方对应的原照片。", style = MaterialTheme.typography.bodySmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(originals, key = { _, photo -> photo.id }) { index, photo ->
                            Column(
                                modifier = Modifier.clickable(enabled = !busy) { replacePhotoId = photo.id },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AsyncImage(photo.url, "整改原照片 ${index + 1}", Modifier.size(76.dp), contentScale = ContentScale.Crop)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = replacePhotoId == photo.id, onClick = null, modifier = Modifier.size(26.dp))
                                    Text("第 ${index + 1} 张", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { gallery.launch("image/*") }, enabled = !busy) {
                    Text(if (newPhoto == null) "从相册选择" else "重新选择照片")
                }
                newPhoto?.let { uri ->
                    AsyncImage(uri, "待补录照片", Modifier.size(112.dp), contentScale = ContentScale.Fit)
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 200) reason = it },
                    label = { Text("补录原因") },
                    enabled = !busy,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage.isNotBlank()) {
                    Text(errorMessage, color = RedDanger, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = { TextButton(onClick = ::dismiss, enabled = !busy) { Text("取消") } },
        confirmButton = {
            Button(
                enabled = !busy && newPhoto != null && reason.isNotBlank(),
                onClick = {
                    val photo = newPhoto ?: return@Button
                    val replacement = replacePhotoId
                    val explanation = reason.trim()
                    saving = true
                    scope.launch {
                        try {
                            onSave(detail.id, operationId, explanation, replacement, photo).fold(
                                onSuccess = {
                                    PhotoUtil.deletePhoto(context, photo)
                                    onSaved()
                                },
                                onFailure = { errorMessage = it.message ?: "照片补录失败，请重试" }
                            )
                        } finally {
                            saving = false
                        }
                    }
                }
            ) {
                Text(when {
                    saving -> "保存中"
                    replacePhotoId != null -> "保存替换"
                    else -> "保存补录"
                })
            }
        }
    )
}
