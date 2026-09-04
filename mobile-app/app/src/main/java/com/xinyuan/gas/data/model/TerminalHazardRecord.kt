package com.xinyuan.gas.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.xinyuan.gas.data.sync.SyncStatus
import java.util.UUID

@Entity(tableName = "terminal_hazard_records")
data class TerminalHazardRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val customerName: String = "",
    val storefrontPhoto: String = "",
    val customerPhone: String = "",
    val customerType: String = "",
    val customerAddress: String = "",
    val locationLatitude: Double? = null,
    val locationLongitude: Double? = null,
    val locationAccuracyMeters: Float? = null,
    val locationCapturedAt: Long = 0L,
    val inspectorName: String = "",
    val regionId: Long = 0L,
    val regionName: String = "",
    val storeId: Long = 0L,
    val storeName: String = "",
    val inspectionDate: Long = System.currentTimeMillis(),
    val checkResult: String = TerminalHazardCatalog.RESULT_CLEARED,
    val hazardItems: String = "",
    val hazardPhotos: String = "",
    val gasSourceIssues: String = "",
    val cylinderQrPhotos: String = "",
    val cylinderPhotos: String = "",
    val rectificationPhotos: String = "",
    val rectificationDate: Long = 0L,
    val rectificationNote: String = "",
    val rectificationPerson: String = "",
    val hadUnclearedHazard: Boolean = false,
    val deliveryPerson: String = "",
    val remark: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // 云端同步预留字段
    val uuid: String = UUID.randomUUID().toString(),
    val syncStatus: String = SyncStatus.PENDING,
    val lastSyncTime: Long = 0L,
    val remoteId: String = ""
)

data class TerminalHazardItem(
    val code: String,
    val category: String,
    val name: String
)

object TerminalHazardCatalog {
    const val RESULT_CLEARED = "隐患已清零"
    const val RESULT_NOT_CLEARED = "隐患未清零"
    const val RESULT_RECTIFIED_LATER = "后期已整改"

    val items = listOf(
        TerminalHazardItem("alarm_missing", "可燃气体报警器", "未安装可燃气体报警器（重大隐患）"),
        TerminalHazardItem("alarm_fault", "可燃气体报警器", "可燃气体报警器故障需更换"),
        TerminalHazardItem("alarm_wrong_gas", "可燃气体报警器", "可燃气体报警器检测气体类型错误"),
        TerminalHazardItem("alarm_site_fix", "可燃气体报警器", "如报警器未插电或安装位置不对，现场整改，不做统计"),
        TerminalHazardItem("hose_tee", "软管", "有三通（重大隐患）"),
        TerminalHazardItem("hose_non_standard", "软管", "非国标软管（重大隐患）"),
        TerminalHazardItem("hose_hidden", "软管", "软管穿墙或暗埋"),
        TerminalHazardItem("hose_no_clamp", "软管", "软管未用管箍固定（重大隐患）"),
        TerminalHazardItem("hose_too_long", "软管", "软管长度超过2米"),
        TerminalHazardItem("environment_bad", "用气环境", "在地下室、半地下室等通风不畅的场所使用瓶装液化气（重大隐患）"),
        TerminalHazardItem("valve_adjustable", "减压阀", "使用可调式减压阀"),
        TerminalHazardItem("guide_wrong", "安全用气指南", "未正确填写并张贴安全用气指南")
    )

    val categories: List<String> = items.map { it.category }.distinct()

    val gasSourceIssues = listOf(
        "钢瓶扫码无信息",
        "钢瓶无二维码",
        "钢瓶超期或报废",
        "钢瓶充装日期与客户实际使用周期不符",
        "钢瓶为非鑫远公司气瓶"
    )

    fun selectedCodes(value: String): Set<String> =
        value.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun photoMap(value: String): Map<String, String> =
        photoListMap(value).mapValues { it.value.firstOrNull().orEmpty() }

    fun photoListMap(value: String): Map<String, List<String>> =
        value.lines()
            .mapNotNull { line ->
                val parts = line.split("|", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    parts[0] to parts[1]
                } else {
                    null
                }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, photos) -> photos.filter { it.isNotBlank() }.distinct() }

    fun serializePhotos(value: Map<String, String>): String =
        value.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .joinToString("\n") { "${it.key}|${it.value}" }

    fun serializePhotoLists(value: Map<String, List<String>>): String =
        value.entries
            .flatMap { (code, photos) ->
                photos.filter { it.isNotBlank() }.distinct().map { photo -> code to photo }
            }
            .filter { it.first.isNotBlank() && it.second.isNotBlank() }
            .joinToString("\n") { "${it.first}|${it.second}" }

    fun textSet(value: String): Set<String> =
        value.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun serializeTextSet(value: Set<String>): String =
        value.filter { it.isNotBlank() }.joinToString("\n")

    fun photoList(value: String): List<String> =
        value.lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun serializePhotoList(value: List<String>): String =
        value.filter { it.isNotBlank() }.joinToString("\n")

    fun selectedItems(record: TerminalHazardRecord): List<TerminalHazardItem> {
        val codes = selectedCodes(record.hazardItems)
        return items.filter { it.code in codes }
    }

    fun hadUnclearedHazard(record: TerminalHazardRecord): Boolean =
        record.hadUnclearedHazard ||
            selectedItems(record).isNotEmpty() ||
            photoList(record.rectificationPhotos).isNotEmpty() ||
            record.rectificationDate > 0L

    fun displayResult(record: TerminalHazardRecord): String =
        when {
            record.checkResult == RESULT_NOT_CLEARED -> RESULT_NOT_CLEARED
            hadUnclearedHazard(record) -> RESULT_RECTIFIED_LATER
            else -> RESULT_CLEARED
        }

    fun photoUris(record: TerminalHazardRecord): List<String> =
        listOf(record.storefrontPhoto) +
            photoListMap(record.hazardPhotos).values.flatten() +
            photoList(record.cylinderQrPhotos) +
            photoList(record.cylinderPhotos) +
            photoList(record.rectificationPhotos)
}
