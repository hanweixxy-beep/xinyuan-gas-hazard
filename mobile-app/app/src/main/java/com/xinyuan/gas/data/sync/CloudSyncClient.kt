package com.xinyuan.gas.data.sync

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.xinyuan.gas.data.model.InspectionRecord
import com.xinyuan.gas.data.model.TerminalHazardCatalog
import com.xinyuan.gas.data.model.TerminalHazardRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class CloudSyncResult(
    val remoteId: String,
    val syncedAt: Long = System.currentTimeMillis()
)

data class CloudSettings(
    val baseUrl: String = HttpCloudSyncClient.DEFAULT_BASE_URL,
    val token: String = "",
    val userName: String = "",
    val role: String = ""
) {
    val isLoggedIn: Boolean
        get() = token.isNotBlank()
}

data class CloudLoginSession(
    val baseUrl: String,
    val token: String,
    val userName: String,
    val role: String
)

class CloudSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)

    fun load(): CloudSettings = CloudSettings(
        baseUrl = prefs.getString("baseUrl", HttpCloudSyncClient.DEFAULT_BASE_URL).orEmpty()
            .ifBlank { HttpCloudSyncClient.DEFAULT_BASE_URL },
        token = prefs.getString("token", "").orEmpty(),
        userName = prefs.getString("userName", "").orEmpty(),
        role = prefs.getString("role", "").orEmpty()
    )

    fun save(session: CloudLoginSession): CloudSettings {
        prefs.edit()
            .putString("baseUrl", session.baseUrl)
            .putString("token", session.token)
            .putString("userName", session.userName)
            .putString("role", session.role)
            .apply()
        return load()
    }

    fun clear(): CloudSettings {
        val baseUrl = load().baseUrl
        prefs.edit()
            .clear()
            .putString("baseUrl", baseUrl)
            .apply()
        return load()
    }
}

interface CloudSyncClient {
    suspend fun uploadInspectionRecord(record: InspectionRecord): Result<CloudSyncResult>
    suspend fun uploadTerminalHazardRecord(record: TerminalHazardRecord): Result<CloudSyncResult>
    suspend fun uploadPhoto(localFile: File, ownerUuid: String): Result<CloudSyncResult>
}

class HttpCloudSyncClient(
    private val context: Context,
    baseUrl: String,
    private val token: String
) : CloudSyncClient {
    private val apiBase = normalizeBaseUrl(baseUrl)

    override suspend fun uploadInspectionRecord(record: InspectionRecord): Result<CloudSyncResult> =
        Result.failure(UnsupportedOperationException("瓶装入户记录云同步待接口对齐后启用"))

    override suspend fun uploadTerminalHazardRecord(record: TerminalHazardRecord): Result<CloudSyncResult> = runCatching {
        val remotePhotos = uploadTerminalPhotos(record)
        val payload = buildTerminalPayload(record, remotePhotos)
        val data = postJson("/records", payload)
        CloudSyncResult(remoteId = data.optLong("id").takeIf { it > 0L }?.toString().orEmpty())
    }

    override suspend fun uploadPhoto(localFile: File, ownerUuid: String): Result<CloudSyncResult> = runCatching {
        val path = uploadBytes(
            fileName = localFile.name.ifBlank { "$ownerUuid.jpg" },
            mimeType = guessMimeType(localFile.name),
            bytes = localFile.readBytes()
        )
        CloudSyncResult(remoteId = path)
    }

    private fun uploadTerminalPhotos(record: TerminalHazardRecord): RemoteTerminalPhotos {
        val ownerUuid = record.uuid.ifBlank { "terminal-${record.id}" }
        val storefront = uploadPhotoReference(record.storefrontPhoto, ownerUuid)
        val hazard = TerminalHazardCatalog.photoListMap(record.hazardPhotos).mapValues { (_, photos) ->
            photos.mapNotNull { uploadPhotoReference(it, ownerUuid) }
        }.filterValues { it.isNotEmpty() }
        val cylinderQr = TerminalHazardCatalog.photoList(record.cylinderQrPhotos)
            .mapNotNull { uploadPhotoReference(it, ownerUuid) }
        val cylinder = TerminalHazardCatalog.photoList(record.cylinderPhotos)
            .mapNotNull { uploadPhotoReference(it, ownerUuid) }
        val rectification = TerminalHazardCatalog.photoList(record.rectificationPhotos)
            .mapNotNull { uploadPhotoReference(it, ownerUuid) }

        return RemoteTerminalPhotos(
            storefront = storefront,
            hazard = hazard,
            cylinderQr = cylinderQr,
            cylinder = cylinder,
            rectification = rectification
        )
    }

    private fun uploadPhotoReference(photoUri: String, ownerUuid: String): String? {
        if (photoUri.isBlank()) return null
        val uri = Uri.parse(photoUri)
        val fileName = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "$ownerUuid-${UUID.randomUUID()}.jpg"
        val mimeType = context.contentResolver.getType(uri) ?: guessMimeType(fileName)
        val bytes = when (uri.scheme?.lowercase(Locale.ROOT)) {
            "content" -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            "file" -> uri.path?.let { File(it).takeIf(File::isFile)?.readBytes() }
            else -> File(photoUri).takeIf(File::isFile)?.readBytes()
        } ?: return null
        return uploadBytes(fileName, mimeType, bytes)
    }

    private fun uploadBytes(fileName: String, mimeType: String, bytes: ByteArray): String {
        val boundary = "----XinyuanGas${UUID.randomUUID().toString().replace("-", "")}"
        val connection = openConnection("/files/upload", "POST").apply {
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        DataOutputStream(connection.outputStream).use { output ->
            output.writeBytes("--$boundary\r\n")
            output.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${safeFileName(fileName)}\"\r\n")
            output.writeBytes("Content-Type: $mimeType\r\n")
            output.writeBytes("\r\n")
            output.write(bytes)
            output.writeBytes("\r\n--$boundary--\r\n")
        }

        return readData(connection).optString("filePath")
    }

    private fun buildTerminalPayload(record: TerminalHazardRecord, photos: RemoteTerminalPhotos): JSONObject =
        JSONObject().apply {
            put("clientUuid", record.uuid)
            put("customerName", requiredText(record.customerName))
            put("customerPhone", requiredText(record.customerPhone))
            put("customerType", requiredText(record.customerType))
            put("customerAddress", requiredText(record.customerAddress))
            put("inspectorName", requiredText(record.inspectorName))
            put("deliveryPerson", requiredText(record.deliveryPerson))
            put("inspectionDate", formatDate(record.inspectionDate))
            put("checkResult", serverCheckResult(record))
            put("remark", record.remark)
            put("hazardItems", JSONArray(TerminalHazardCatalog.selectedCodes(record.hazardItems).toList()))
            put("gasSourceIssues", JSONArray(TerminalHazardCatalog.textSet(record.gasSourceIssues).toList()))
            put("rectificationDate", formatDateOrEmpty(record.rectificationDate))
            put("rectificationNote", record.rectificationNote)
            put("rectificationPerson", record.rectificationPerson)
            put("photos", JSONObject().apply {
                put("storefront", JSONArray(photos.storefront?.let(::listOf).orEmpty()))
                put("hazard", JSONObject().apply {
                    photos.hazard.forEach { (code, paths) -> put(code, JSONArray(paths)) }
                })
                put("cylinder_qr", JSONArray(photos.cylinderQr))
                put("cylinder", JSONArray(photos.cylinder))
                put("rectification", JSONArray(photos.rectification))
            })
        }

    private fun postJson(endpoint: String, body: JSONObject): JSONObject {
        val connection = openConnection(endpoint, "POST").apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        return readData(connection)
    }

    private fun openConnection(endpoint: String, method: String): HttpURLConnection {
        val url = URL(apiBase.trimEnd('/') + "/" + endpoint.trimStart('/'))
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            if (token.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
        }
    }

    private fun readData(connection: HttpURLConnection): JSONObject {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val root = text.takeIf { it.isNotBlank() }?.let { JSONObject(it) } ?: JSONObject()
        if (status !in 200..299 || !root.optBoolean("ok", false)) {
            throw IllegalStateException(root.optString("message").ifBlank { "云端请求失败：HTTP $status" })
        }
        return root.optJSONObject("data") ?: JSONObject()
    }

    private fun serverCheckResult(record: TerminalHazardRecord): String =
        when (TerminalHazardCatalog.displayResult(record)) {
            TerminalHazardCatalog.RESULT_NOT_CLEARED -> "隐患未清零"
            TerminalHazardCatalog.RESULT_RECTIFIED_LATER -> "隐患已整改"
            else -> "隐患已清零"
        }

    private fun requiredText(value: String): String = value.trim().ifBlank { "-" }

    private fun formatDate(value: Long): String = dateFormat.format(Date(value.takeIf { it > 0L } ?: System.currentTimeMillis()))

    private fun formatDateOrEmpty(value: Long): String = if (value > 0L) dateFormat.format(Date(value)) else ""

    private fun guessMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "image/jpeg"
    }

    private fun safeFileName(value: String): String =
        value.replace("\"", "_")
            .replace("/", "_")
            .replace("\\", "_")
            .replace("\r", "_")
            .replace("\n", "_")

    private data class RemoteTerminalPhotos(
        val storefront: String?,
        val hazard: Map<String, List<String>>,
        val cylinderQr: List<String>,
        val cylinder: List<String>,
        val rectification: List<String>
    )

    companion object {
        const val DEFAULT_BASE_URL = "http://124.220.26.188/api"

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

        fun normalizeBaseUrl(value: String): String {
            val trimmed = value.trim().trimEnd('/')
            if (trimmed.isBlank()) return DEFAULT_BASE_URL
            return if (trimmed.endsWith("/api", ignoreCase = true)) trimmed else "$trimmed/api"
        }

        fun login(context: Context, baseUrl: String, phone: String, password: String): Result<CloudLoginSession> =
            runCatching {
                val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
                val client = HttpCloudSyncClient(context, normalizedBaseUrl, "")
                val data = client.postJson(
                    "/auth/login",
                    JSONObject().apply {
                        put("phone", phone.trim())
                        put("password", password)
                    }
                )
                val user = data.optJSONObject("user") ?: JSONObject()
                CloudLoginSession(
                    baseUrl = normalizedBaseUrl,
                    token = data.optString("token"),
                    userName = user.optString("name").ifBlank { phone.trim() },
                    role = user.optString("role")
                )
            }
    }
}

class NotConfiguredCloudSyncClient : CloudSyncClient {
    private fun notConfigured(): Result<CloudSyncResult> =
        Result.failure(IllegalStateException("云端服务器未配置"))

    override suspend fun uploadInspectionRecord(record: InspectionRecord): Result<CloudSyncResult> = notConfigured()

    override suspend fun uploadTerminalHazardRecord(record: TerminalHazardRecord): Result<CloudSyncResult> = notConfigured()

    override suspend fun uploadPhoto(localFile: File, ownerUuid: String): Result<CloudSyncResult> = notConfigured()
}
