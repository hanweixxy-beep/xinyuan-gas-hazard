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
    val userId: Long = 0L,
    val userName: String = "",
    val userPhone: String = "",
    val companyId: Long = 0L,
    val companyName: String = "",
    val role: String = "",
    val loginScope: String = ""
) {
    val isLoggedIn: Boolean
        get() = token.isNotBlank()
}

data class CloudLoginSession(
    val baseUrl: String,
    val token: String,
    val userId: Long,
    val userName: String,
    val userPhone: String,
    val companyId: Long,
    val companyName: String,
    val role: String,
    val loginScope: String
)

data class CloudRecordSummary(
    val id: Long,
    val customerName: String,
    val customerPhone: String,
    val customerAddress: String,
    val inspectionDate: String,
    val checkResult: String,
    val status: String,
    val inspectorName: String
)

class CloudApiException(
    val statusCode: Int,
    message: String
) : IllegalStateException(message)

class CloudSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)

    fun load(): CloudSettings = CloudSettings(
        baseUrl = HttpCloudSyncClient.normalizeBaseUrl(
            prefs.getString("baseUrl", HttpCloudSyncClient.DEFAULT_BASE_URL).orEmpty()
        ),
        token = prefs.getString("token", "").orEmpty(),
        userId = prefs.getLong("userId", 0L),
        userName = prefs.getString("userName", "").orEmpty(),
        userPhone = prefs.getString("userPhone", "").orEmpty(),
        companyId = prefs.getLong("companyId", 0L),
        companyName = prefs.getString("companyName", "").orEmpty(),
        role = prefs.getString("role", "").orEmpty(),
        loginScope = prefs.getString("loginScope", "").orEmpty()
    )

    fun save(session: CloudLoginSession): CloudSettings {
        prefs.edit()
            .putString("baseUrl", session.baseUrl)
            .putString("token", session.token)
            .putLong("userId", session.userId)
            .putString("userName", session.userName)
            .putString("userPhone", session.userPhone)
            .putLong("companyId", session.companyId)
            .putString("companyName", session.companyName)
            .putString("role", session.role)
            .putString("loginScope", session.loginScope)
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
    suspend fun saveTerminalDraft(record: TerminalHazardRecord): Result<CloudSyncResult>
    suspend fun submitTerminalDraft(record: TerminalHazardRecord): Result<CloudSyncResult>
    suspend fun fetchCurrentSession(): Result<CloudLoginSession>
    suspend fun fetchTerminalRecords(): Result<List<CloudRecordSummary>>
    suspend fun uploadPhoto(localFile: File, ownerUuid: String): Result<CloudSyncResult>
}

class HttpCloudSyncClient(
    private val context: Context,
    baseUrl: String,
    private val token: String
) : CloudSyncClient {
    private val apiBase = normalizeBaseUrl(baseUrl)
    private val uploadedPhotoCache = mutableMapOf<String, String>()

    override suspend fun uploadInspectionRecord(record: InspectionRecord): Result<CloudSyncResult> =
        Result.failure(UnsupportedOperationException("瓶装入户记录云同步待接口对齐后启用"))

    override suspend fun uploadTerminalHazardRecord(record: TerminalHazardRecord): Result<CloudSyncResult> = runCatching {
        val remotePhotos = uploadTerminalPhotos(record)
        val payload = buildTerminalPayload(record, remotePhotos)
        val data = postJson("/records", payload)
        CloudSyncResult(remoteId = data.optLong("id").takeIf { it > 0L }?.toString().orEmpty())
    }

    override suspend fun saveTerminalDraft(record: TerminalHazardRecord): Result<CloudSyncResult> = runCatching {
        val remotePhotos = uploadTerminalPhotos(record)
        val data = postJson("/records/drafts", buildTerminalPayload(record, remotePhotos))
        CloudSyncResult(remoteId = data.optLong("id").takeIf { it > 0L }?.toString().orEmpty())
    }

    override suspend fun submitTerminalDraft(record: TerminalHazardRecord): Result<CloudSyncResult> = runCatching {
        val remotePhotos = uploadTerminalPhotos(record)
        val data = postJson("/records/drafts/submit", buildTerminalPayload(record, remotePhotos))
        CloudSyncResult(remoteId = data.optLong("id").takeIf { it > 0L }?.toString().orEmpty())
    }

    override suspend fun fetchCurrentSession(): Result<CloudLoginSession> = runCatching {
        val user = getJson("/auth/me").optJSONObject("user") ?: JSONObject()
        CloudLoginSession(
            baseUrl = apiBase,
            token = token,
            userId = user.optLong("id"),
            userName = user.optString("name"),
            userPhone = user.optString("phone"),
            companyId = user.optLong("companyId"),
            companyName = user.optString("companyName"),
            role = user.optString("role"),
            loginScope = user.optString("loginScope")
        )
    }

    override suspend fun fetchTerminalRecords(): Result<List<CloudRecordSummary>> = runCatching {
        val records = getJson("/records").optJSONArray("records") ?: JSONArray()
        buildList {
            for (index in 0 until records.length()) {
                val item = records.optJSONObject(index) ?: continue
                add(
                    CloudRecordSummary(
                        id = item.optLong("id"),
                        customerName = item.optString("customer_name"),
                        customerPhone = item.optString("customer_phone"),
                        customerAddress = item.optString("customer_address"),
                        inspectionDate = item.optString("inspection_date"),
                        checkResult = item.optString("check_result"),
                        status = item.optString("status"),
                        inspectorName = item.optString("inspector_name")
                    )
                )
            }
        }
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
        if (photoUri.startsWith("/uploads/")) return photoUri
        uploadedPhotoCache[photoUri]?.let { return it }
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
        return uploadBytes(fileName, mimeType, bytes).also { uploadedPath ->
            uploadedPhotoCache[photoUri] = uploadedPath
        }
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

    private fun getJson(endpoint: String): JSONObject =
        readData(openConnection(endpoint, "GET"))

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
            throw CloudApiException(
                status,
                root.optString("message").ifBlank { "云端请求失败：HTTP $status" }
            )
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
        const val DEFAULT_BASE_URL = "https://www.gashbxy.com/api"

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

        fun normalizeBaseUrl(value: String): String {
            val trimmed = value.trim().trimEnd('/')
            if (trimmed.isBlank()) return DEFAULT_BASE_URL
            if (trimmed.equals("http://124.220.26.188", true)
                || trimmed.equals("http://124.220.26.188/api", true)
                || trimmed.equals("https://124.220.26.188", true)
                || trimmed.equals("https://124.220.26.188/api", true)
            ) {
                return DEFAULT_BASE_URL
            }
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
                        put("clientType", "app")
                    }
                )
                val user = data.optJSONObject("user") ?: JSONObject()
                CloudLoginSession(
                    baseUrl = normalizedBaseUrl,
                    token = data.optString("token"),
                    userId = user.optLong("id"),
                    userName = user.optString("name").ifBlank { phone.trim() },
                    userPhone = user.optString("phone"),
                    companyId = user.optLong("companyId"),
                    companyName = user.optString("companyName"),
                    role = user.optString("role"),
                    loginScope = user.optString("loginScope")
                )
            }
    }
}

class NotConfiguredCloudSyncClient : CloudSyncClient {
    private fun <T> notConfigured(): Result<T> =
        Result.failure(IllegalStateException("云端服务器未配置"))

    override suspend fun uploadInspectionRecord(record: InspectionRecord): Result<CloudSyncResult> = notConfigured()

    override suspend fun uploadTerminalHazardRecord(record: TerminalHazardRecord): Result<CloudSyncResult> = notConfigured()

    override suspend fun saveTerminalDraft(record: TerminalHazardRecord): Result<CloudSyncResult> = notConfigured()

    override suspend fun submitTerminalDraft(record: TerminalHazardRecord): Result<CloudSyncResult> = notConfigured()

    override suspend fun fetchCurrentSession(): Result<CloudLoginSession> = notConfigured()

    override suspend fun fetchTerminalRecords(): Result<List<CloudRecordSummary>> = notConfigured()

    override suspend fun uploadPhoto(localFile: File, ownerUuid: String): Result<CloudSyncResult> = notConfigured()
}
