package com.xinyuan.gas.util

import android.content.Context
import android.net.Uri
import com.xinyuan.gas.data.model.InspectionRecord
import com.xinyuan.gas.data.model.TerminalHazardCatalog
import com.xinyuan.gas.data.model.TerminalHazardRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupUtil {
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)
    private const val BACKUP_VERSION = 2
    private const val DATABASE_NAME = "gas_hazard_db"
    private val backupNameRegex = Regex("""鑫远燃气数据备份_(\d{8}_\d{6})\.zip""")

    data class StorageStats(
        val inspectionCount: Int,
        val terminalCount: Int,
        val photoCount: Int,
        val referencedPhotoCount: Int,
        val orphanPhotoCount: Int,
        val databaseBytes: Long,
        val photoBytes: Long,
        val exportBytes: Long,
        val backupBytes: Long
    ) {
        val totalBytes: Long = databaseBytes + photoBytes + exportBytes + backupBytes
    }

    data class BackupResult(val file: File, val photoCount: Int, val bytes: Long)
    data class LocalBackupInfo(
        val file: File,
        val name: String,
        val createdAt: Long,
        val bytes: Long,
        val path: String,
        val exists: Boolean
    )
    data class CleanupResult(val deletedCount: Int, val freedBytes: Long)
    data class OptimizeResult(val optimizedCount: Int, val freedBytes: Long)
    data class RestorePayload(
        val inspectionRecords: List<InspectionRecord>,
        val terminalRecords: List<TerminalHazardRecord>,
        val restoredPhotoCount: Int
    )

    fun calculateStorageStats(
        context: Context,
        inspectionRecords: List<InspectionRecord>,
        terminalRecords: List<TerminalHazardRecord>
    ): StorageStats {
        val photoFiles = photoDir(context).listFiles()?.filter { it.isFile }.orEmpty()
        val referencedNames = referencedPhotoNames(inspectionRecords, terminalRecords)
        val backupDir = backupDir(context)
        val exportFiles = context.getExternalFilesDir(null)
            ?.listFiles()
            ?.filter { it.isFile && it.extension.lowercase(Locale.ROOT) in setOf("xlsx", "csv") }
            .orEmpty()

        return StorageStats(
            inspectionCount = inspectionRecords.size,
            terminalCount = terminalRecords.size,
            photoCount = photoFiles.size,
            referencedPhotoCount = photoFiles.count { it.name in referencedNames },
            orphanPhotoCount = photoFiles.count { it.name !in referencedNames },
            databaseBytes = databaseFiles(context).sumOf { it.lengthIfExists() },
            photoBytes = photoFiles.sumOf { it.length() },
            exportBytes = exportFiles.sumOf { it.length() },
            backupBytes = backupDir.sizeRecursively()
        )
    }

    fun createBackup(
        context: Context,
        inspectionRecords: List<InspectionRecord>,
        terminalRecords: List<TerminalHazardRecord>
    ): BackupResult {
        val outputDir = backupDir(context).apply { mkdirs() }
        val file = File(outputDir, "鑫远燃气数据备份_${fileNameFormat.format(Date())}.zip")
        val referencedNames = referencedPhotoNames(inspectionRecords, terminalRecords)
        val photos = photoDir(context).listFiles()
            ?.filter { it.isFile && it.name in referencedNames }
            .orEmpty()

        ZipOutputStream(FileOutputStream(file)).use { zip ->
            zip.putText(
                "manifest.json",
                JSONObject()
                    .put("backupVersion", BACKUP_VERSION)
                    .put("createdAt", System.currentTimeMillis())
                    .put("inspectionCount", inspectionRecords.size)
                    .put("terminalCount", terminalRecords.size)
                    .put("photoCount", photos.size)
                    .toString(2)
            )
            zip.putText("data/inspection_records.json", inspectionRecordsToJson(inspectionRecords).toString(2))
            zip.putText("data/terminal_hazard_records.json", terminalRecordsToJson(terminalRecords).toString(2))
            photos.forEach { photo ->
                zip.putFile("photos/${photo.name}", photo)
            }
        }

        return BackupResult(file = file, photoCount = photos.size, bytes = file.length())
    }

    fun restoreBackup(context: Context, backupUri: Uri): RestorePayload {
        val input = context.contentResolver.openInputStream(backupUri) ?: error("无法读取备份文件")
        return restoreBackupFromInput(context, input)
    }

    fun restoreBackup(context: Context, backupFile: File): RestorePayload {
        if (!backupFile.exists() || !backupFile.isFile) {
            error("备份包不存在或已被删除")
        }
        if (backupFile.extension.lowercase(Locale.ROOT) != "zip") {
            error("请选择 ZIP 格式的备份包")
        }
        return restoreBackupFromInput(context, backupFile.inputStream())
    }

    fun listLocalBackups(context: Context): List<LocalBackupInfo> {
        return backupDir(context).apply { mkdirs() }
            .listFiles()
            ?.filter { it.isFile && it.extension.lowercase(Locale.ROOT) == "zip" }
            .orEmpty()
            .map { file ->
                LocalBackupInfo(
                    file = file,
                    name = file.name,
                    createdAt = readBackupCreatedAt(file),
                    bytes = file.length(),
                    path = file.absolutePath,
                    exists = file.exists()
                )
            }
            .sortedByDescending { it.createdAt }
    }

    fun backupFolderPath(context: Context): String = backupDir(context).apply { mkdirs() }.absolutePath

    private fun restoreBackupFromInput(context: Context, input: InputStream): RestorePayload {
        val restoredPhotos = mutableSetOf<String>()
        var inspections = emptyList<InspectionRecord>()
        var terminals = emptyList<TerminalHazardRecord>()
        var hasInspectionJson = false
        var hasTerminalJson = false

        input.use {
            ZipInputStream(it).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    when (entry.name) {
                        "data/inspection_records.json" -> {
                            inspections = inspectionsFromJson(zip.readText())
                            hasInspectionJson = true
                        }
                        "data/terminal_hazard_records.json" -> {
                            terminals = terminalRecordsFromJson(zip.readText())
                            hasTerminalJson = true
                        }
                        else -> {
                            if (entry.name.startsWith("photos/")) {
                                val safeName = File(entry.name).name.takeIf { it.isNotBlank() }
                                if (safeName != null) {
                                    val target = File(photoDir(context).apply { mkdirs() }, safeName)
                                    FileOutputStream(target).use { output -> zip.copyTo(output) }
                                    restoredPhotos += safeName
                                }
                            }
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            }
        }

        if (!hasInspectionJson && !hasTerminalJson) {
            error("备份包格式不正确，未找到业务数据")
        }

        return RestorePayload(
            inspectionRecords = inspections,
            terminalRecords = terminals,
            restoredPhotoCount = restoredPhotos.size
        )
    }

    fun cleanupOrphanPhotos(
        context: Context,
        inspectionRecords: List<InspectionRecord>,
        terminalRecords: List<TerminalHazardRecord>
    ): CleanupResult {
        val referencedNames = referencedPhotoNames(inspectionRecords, terminalRecords)
        var deletedCount = 0
        var freedBytes = 0L
        photoDir(context).listFiles()
            ?.filter { it.isFile && it.name !in referencedNames }
            .orEmpty()
            .forEach { file ->
                val length = file.length()
                if (file.delete()) {
                    deletedCount += 1
                    freedBytes += length
                }
            }
        return CleanupResult(deletedCount, freedBytes)
    }

    fun deleteAllPhotos(context: Context): CleanupResult {
        var deletedCount = 0
        var freedBytes = 0L
        photoDir(context).listFiles()
            ?.filter { it.isFile }
            .orEmpty()
            .forEach { file ->
                val length = file.length()
                if (file.delete()) {
                    deletedCount += 1
                    freedBytes += length
                }
            }
        return CleanupResult(deletedCount, freedBytes)
    }

    fun deleteLocalBackupFiles(context: Context): CleanupResult {
        var deletedCount = 0
        var freedBytes = 0L
        backupDir(context).listFiles()
            ?.filter { it.isFile }
            .orEmpty()
            .forEach { file ->
                val length = file.length()
                if (file.delete()) {
                    deletedCount += 1
                    freedBytes += length
                }
            }
        return CleanupResult(deletedCount, freedBytes)
    }

    fun optimizeReferencedPhotos(
        context: Context,
        inspectionRecords: List<InspectionRecord>,
        terminalRecords: List<TerminalHazardRecord>
    ): OptimizeResult {
        var optimizedCount = 0
        var freedBytes = 0L
        referencedPhotoUris(inspectionRecords, terminalRecords).distinct().forEach { uri ->
            val freed = PhotoUtil.optimizePhoto(context, uri)
            if (freed > 0L) {
                optimizedCount += 1
                freedBytes += freed
            }
        }
        return OptimizeResult(optimizedCount, freedBytes)
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "${bytes}B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.CHINA, "%.1fKB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.CHINA, "%.1fMB", mb)
        return String.format(Locale.CHINA, "%.2fGB", mb / 1024.0)
    }

    fun referencedPhotoUris(
        inspectionRecords: List<InspectionRecord>,
        terminalRecords: List<TerminalHazardRecord>
    ): List<String> {
        return inspectionRecords.flatMap { it.photoPath.split(",") } +
            terminalRecords.flatMap { TerminalHazardCatalog.photoUris(it) }
    }

    private fun referencedPhotoNames(
        inspectionRecords: List<InspectionRecord>,
        terminalRecords: List<TerminalHazardRecord>
    ): Set<String> {
        return referencedPhotoUris(inspectionRecords, terminalRecords)
            .mapNotNull { PhotoUtil.photoFileName(it) }
            .toSet()
    }

    private fun inspectionRecordsToJson(records: List<InspectionRecord>): JSONArray =
        JSONArray(records.map { record ->
            JSONObject()
                .put("id", record.id)
                .put("customerName", record.customerName)
                .put("customerPhone", record.customerPhone)
                .put("customerType", record.customerType)
                .put("customerAddress", record.customerAddress)
                .put("photoPath", record.photoPath)
                .put("inspectorName", record.inspectorName)
                .put("inspectionDate", record.inspectionDate)
                .put("hazardAlarm", record.hazardAlarm)
                .put("hazardPipeline", record.hazardPipeline)
                .put("hazardStove", record.hazardStove)
                .put("hazardHose", record.hazardHose)
                .put("hazardConnector", record.hazardConnector)
                .put("hazardValve", record.hazardValve)
                .put("useOil", record.useOil)
                .put("useElectric", record.useElectric)
                .put("useLPG", record.useLPG)
                .put("monthlyGasUsage", record.monthlyGasUsage)
                .put("deliveryPerson", record.deliveryPerson)
                .put("isRectified", record.isRectified)
                .put("rectificationDate", record.rectificationDate)
                .put("remark", record.remark)
                .put("createdAt", record.createdAt)
                .put("updatedAt", record.updatedAt)
                .put("uuid", record.uuid)
                .put("syncStatus", record.syncStatus)
                .put("lastSyncTime", record.lastSyncTime)
                .put("remoteId", record.remoteId)
        })

    private fun terminalRecordsToJson(records: List<TerminalHazardRecord>): JSONArray =
        JSONArray(records.map { record ->
            JSONObject()
                .put("id", record.id)
                .put("customerName", record.customerName)
                .put("storefrontPhoto", record.storefrontPhoto)
                .put("customerPhone", record.customerPhone)
                .put("customerType", record.customerType)
                .put("customerAddress", record.customerAddress)
                .put("locationLatitude", record.locationLatitude)
                .put("locationLongitude", record.locationLongitude)
                .put("locationAccuracyMeters", record.locationAccuracyMeters)
                .put("locationCapturedAt", record.locationCapturedAt)
                .put("inspectorName", record.inspectorName)
                .put("inspectionDate", record.inspectionDate)
                .put("checkResult", record.checkResult)
                .put("hazardItems", record.hazardItems)
                .put("hazardPhotos", record.hazardPhotos)
                .put("gasSourceIssues", record.gasSourceIssues)
                .put("cylinderQrPhotos", record.cylinderQrPhotos)
                .put("cylinderPhotos", record.cylinderPhotos)
                .put("rectificationPhotos", record.rectificationPhotos)
                .put("rectificationDate", record.rectificationDate)
                .put("rectificationNote", record.rectificationNote)
                .put("rectificationPerson", record.rectificationPerson)
                .put("hadUnclearedHazard", record.hadUnclearedHazard)
                .put("deliveryPerson", record.deliveryPerson)
                .put("remark", record.remark)
                .put("createdAt", record.createdAt)
                .put("updatedAt", record.updatedAt)
                .put("uuid", record.uuid)
                .put("syncStatus", record.syncStatus)
                .put("lastSyncTime", record.lastSyncTime)
                .put("remoteId", record.remoteId)
        })

    private fun inspectionsFromJson(json: String): List<InspectionRecord> {
        val array = JSONArray(json)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            InspectionRecord(
                customerName = item.optString("customerName"),
                customerPhone = item.optString("customerPhone"),
                customerType = item.optString("customerType"),
                customerAddress = item.optString("customerAddress"),
                photoPath = item.optString("photoPath"),
                inspectorName = item.optString("inspectorName"),
                inspectionDate = item.optLong("inspectionDate", System.currentTimeMillis()),
                hazardAlarm = item.optString("hazardAlarm"),
                hazardPipeline = item.optString("hazardPipeline"),
                hazardStove = item.optString("hazardStove"),
                hazardHose = item.optString("hazardHose"),
                hazardConnector = item.optString("hazardConnector"),
                hazardValve = item.optString("hazardValve"),
                useOil = item.optBoolean("useOil", false),
                useElectric = item.optBoolean("useElectric", false),
                useLPG = item.optBoolean("useLPG", false),
                monthlyGasUsage = item.optString("monthlyGasUsage"),
                deliveryPerson = item.optString("deliveryPerson"),
                isRectified = item.optString("isRectified", "未整改"),
                rectificationDate = item.optLong("rectificationDate", 0L),
                remark = item.optString("remark"),
                createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                uuid = item.optString("uuid").ifBlank {
                    "inspection_${item.optLong("id", 0L)}_${item.optLong("createdAt", System.currentTimeMillis())}"
                },
                syncStatus = item.optString("syncStatus", "PENDING"),
                lastSyncTime = item.optLong("lastSyncTime", 0L),
                remoteId = item.optString("remoteId")
            )
        }
    }

    private fun terminalRecordsFromJson(json: String): List<TerminalHazardRecord> {
        val array = JSONArray(json)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            TerminalHazardRecord(
                customerName = item.optString("customerName"),
                storefrontPhoto = item.optString("storefrontPhoto"),
                customerPhone = item.optString("customerPhone"),
                customerType = item.optString("customerType"),
                customerAddress = item.optString("customerAddress"),
                locationLatitude = item.optDouble("locationLatitude").takeUnless { item.isNull("locationLatitude") || it.isNaN() },
                locationLongitude = item.optDouble("locationLongitude").takeUnless { item.isNull("locationLongitude") || it.isNaN() },
                locationAccuracyMeters = item.optDouble("locationAccuracyMeters").takeUnless { item.isNull("locationAccuracyMeters") || it.isNaN() }?.toFloat(),
                locationCapturedAt = item.optLong("locationCapturedAt", 0L),
                inspectorName = item.optString("inspectorName"),
                inspectionDate = item.optLong("inspectionDate", System.currentTimeMillis()),
                checkResult = item.optString("checkResult", TerminalHazardCatalog.RESULT_CLEARED),
                hazardItems = item.optString("hazardItems"),
                hazardPhotos = item.optString("hazardPhotos"),
                gasSourceIssues = item.optString("gasSourceIssues"),
                cylinderQrPhotos = item.optString("cylinderQrPhotos"),
                cylinderPhotos = item.optString("cylinderPhotos"),
                rectificationPhotos = item.optString("rectificationPhotos"),
                rectificationDate = item.optLong("rectificationDate", 0L),
                rectificationNote = item.optString("rectificationNote"),
                rectificationPerson = item.optString("rectificationPerson"),
                hadUnclearedHazard = item.optBoolean("hadUnclearedHazard", false),
                deliveryPerson = item.optString("deliveryPerson"),
                remark = item.optString("remark"),
                createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                uuid = item.optString("uuid").ifBlank {
                    "terminal_${item.optLong("id", 0L)}_${item.optLong("createdAt", System.currentTimeMillis())}"
                },
                syncStatus = item.optString("syncStatus", "PENDING"),
                lastSyncTime = item.optLong("lastSyncTime", 0L),
                remoteId = item.optString("remoteId")
            )
        }
    }

    private fun backupDir(context: Context): File = File(context.getExternalFilesDir(null), "backups")

    private fun photoDir(context: Context): File = File(context.getExternalFilesDir(null), "photos")

    private fun databaseFiles(context: Context): List<File> {
        val db = context.getDatabasePath(DATABASE_NAME)
        return listOf(db, File("${db.path}-wal"), File("${db.path}-shm"))
    }

    private fun File.lengthIfExists(): Long = if (exists() && isFile) length() else 0L

    private fun File.sizeRecursively(): Long {
        if (!exists()) return 0L
        if (isFile) return length()
        return listFiles()?.sumOf { it.sizeRecursively() } ?: 0L
    }

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.putFile(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }

    private fun ZipInputStream.readText(): String = readBytes().toString(Charsets.UTF_8)

    private fun readBackupCreatedAt(file: File): Long {
        val manifestCreatedAt = runCatching {
            ZipInputStream(file.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name == "manifest.json") {
                        return@use JSONObject(zip.readText()).optLong("createdAt", 0L)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                0L
            }
        }.getOrDefault(0L)
        if (manifestCreatedAt > 0L) return manifestCreatedAt

        val nameDate = backupNameRegex.matchEntire(file.name)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { runCatching { fileNameFormat.parse(it)?.time ?: 0L }.getOrDefault(0L) }
            ?: 0L
        if (nameDate > 0L) return nameDate

        return file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
    }
}
