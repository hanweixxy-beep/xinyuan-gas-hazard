package com.xinyuan.gas.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.xinyuan.gas.data.model.TerminalHazardCatalog
import com.xinyuan.gas.data.model.TerminalHazardItem
import com.xinyuan.gas.data.model.TerminalHazardRecord
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFClientAnchor
import org.apache.poi.xssf.usermodel.XSSFPicture
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object TerminalHazardExcelImportUtil {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.CHINA)
    private val supportedDateFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA),
        SimpleDateFormat("yyyy/M/d", Locale.CHINA),
        SimpleDateFormat("yyyy.MM.dd", Locale.CHINA),
        SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
    )

    data class ImportResult(
        val records: List<TerminalHazardRecord>,
        val skippedDuplicateCount: Int,
        val importedPhotoCount: Int,
        val warningCount: Int
    )

    private data class PictureRef(
        val id: Int,
        val row: Int,
        val col: Int,
        val bytes: ByteArray,
        val extension: String
    )

    private data class ColumnRoles(
        val storefrontPhotoCol: Int,
        val categoryColumns: List<Pair<String, Pair<Int, Int>>>,
        val deliveryPersonCol: Int,
        val remarkCol: Int,
        val gasSourceCol: Int,
        val qrPhotoCols: Set<Int>,
        val cylinderPhotoCols: Set<Int>,
        val rectificationDateCol: Int,
        val rectificationNoteCol: Int,
        val rectificationPersonCol: Int,
        val rectificationPhotoCols: Set<Int>
    )

    fun importWorkbook(
        context: Context,
        workbookUri: Uri,
        existingRecords: List<TerminalHazardRecord>
    ): ImportResult {
        val input = context.contentResolver.openInputStream(workbookUri)
            ?: error("无法读取 Excel 文件")

        input.use { stream ->
            XSSFWorkbook(stream).use { workbook ->
                val sheet = workbook.getSheet("终端用户隐患核查清单") ?: workbook.getSheetAt(0)
                val formatter = DataFormatter(Locale.CHINA)
                val headerRowIndex = findHeaderRow(sheet, formatter)
                val headerRow = sheet.getRow(headerRowIndex)
                val secondHeaderRow = sheet.getRow(headerRowIndex + 1)
                val dataStartRow = if (looksLikeSecondHeader(secondHeaderRow, formatter)) {
                    headerRowIndex + 2
                } else {
                    headerRowIndex + 1
                }
                val roles = detectColumnRoles(headerRow, secondHeaderRow, formatter)
                val pictures = readPictures(sheet)
                val pictureRows = pictures.map { it.row }.toSet()
                val groups = groupDataRows(sheet, dataStartRow, roles, formatter, pictureRows)
                val existingKeys = existingRecords.map { duplicateKey(it) }.toMutableSet()

                val records = mutableListOf<TerminalHazardRecord>()
                var skipped = 0
                var importedPhotos = 0
                var warnings = 0

                groups.forEach { rows ->
                    val firstRow = rows.firstOrNull() ?: return@forEach
                    val draft = buildRecordDraft(context, rows, roles, pictures, formatter)
                    if (draft == null) {
                        warnings += 1
                        return@forEach
                    }

                    val key = duplicateKey(draft)
                    if (key.isNotBlank() && key in existingKeys) {
                        skipped += 1
                        return@forEach
                    }

                    existingKeys += key
                    records += draft
                    importedPhotos += TerminalHazardCatalog.photoUris(draft).count { it.isNotBlank() }
                    if (cellText(firstRow, 1, formatter).isBlank() || cellText(firstRow, 3, formatter).isBlank()) {
                        warnings += 1
                    }
                }

                return ImportResult(
                    records = records,
                    skippedDuplicateCount = skipped,
                    importedPhotoCount = importedPhotos,
                    warningCount = warnings
                )
            }
        }
    }

    private fun findHeaderRow(sheet: Sheet, formatter: DataFormatter): Int {
        for (rowIndex in 0..sheet.lastRowNum.coerceAtMost(8)) {
            val row = sheet.getRow(rowIndex) ?: continue
            val texts = (0 until row.lastCellNum.toInt().coerceAtLeast(0))
                .map { cellText(row, it, formatter) }
                .toSet()
            if ("序号" in texts && "用户名" in texts) return rowIndex
        }
        error("未找到终端用户隐患核查清单表头")
    }

    private fun looksLikeSecondHeader(row: Row?, formatter: DataFormatter): Boolean {
        if (row == null) return false
        val rowTexts = (0 until row.lastCellNum.toInt().coerceAtLeast(0)).map { cellText(row, it, formatter) }
        return rowTexts.any { it in TerminalHazardCatalog.categories || it == "照片" }
    }

    private fun detectColumnRoles(
        headerRow: Row?,
        secondHeaderRow: Row?,
        formatter: DataFormatter
    ): ColumnRoles {
        val labels = headerLabels(headerRow, secondHeaderRow, formatter)
        val qrCols = labels.filterValues { it.startsWith("钢瓶二维码照片") }.keys.ifEmpty { setOf(22) }
        val cylinderCols = labels
            .filterValues { it.startsWith("钢瓶照片") && !it.startsWith("钢瓶二维码照片") }
            .keys
            .ifEmpty { setOf(23) }
        val rectificationPhotoCols = labels.filterValues { it.startsWith("整改照片") }.keys.ifEmpty { setOf(27) }

        return ColumnRoles(
            storefrontPhotoCol = labels.findColumn("门头照片") ?: 2,
            categoryColumns = listOf(
                "可燃气体报警器" to (9 to 10),
                "软管" to (11 to 12),
                "用气环境" to (13 to 14),
                "减压阀" to (15 to 16),
                "安全用气指南" to (17 to 18)
            ),
            deliveryPersonCol = labels.findColumn("配送员") ?: 19,
            remarkCol = labels.findColumn("备注") ?: 20,
            gasSourceCol = labels.findColumn("气源排查项目") ?: 21,
            qrPhotoCols = qrCols,
            cylinderPhotoCols = cylinderCols,
            rectificationDateCol = labels.findColumn("整改日期") ?: 24,
            rectificationNoteCol = labels.findColumn("整改情况") ?: 25,
            rectificationPersonCol = labels.findColumn("整改人") ?: 26,
            rectificationPhotoCols = rectificationPhotoCols
        )
    }

    private fun headerLabels(
        headerRow: Row?,
        secondHeaderRow: Row?,
        formatter: DataFormatter
    ): Map<Int, String> {
        val maxCol = maxOf(
            headerRow?.lastCellNum?.toInt() ?: 0,
            secondHeaderRow?.lastCellNum?.toInt() ?: 0,
            40
        )
        return (0 until maxCol).mapNotNull { col ->
            val first = headerRow?.let { cellText(it, col, formatter) }.orEmpty()
            val second = secondHeaderRow?.let { cellText(it, col, formatter) }.orEmpty()
            val label = when {
                first.isNotBlank() && first != "未清零隐患项目清单" -> first
                second.isNotBlank() -> second
                else -> ""
            }
            label.takeIf { it.isNotBlank() }?.let { col to it }
        }.toMap()
    }

    private fun Map<Int, String>.findColumn(label: String): Int? =
        entries.firstOrNull { (_, value) -> value == label || value.startsWith(label) }?.key

    private fun groupDataRows(
        sheet: Sheet,
        dataStartRow: Int,
        roles: ColumnRoles,
        formatter: DataFormatter,
        pictureRows: Set<Int>
    ): List<List<Row>> {
        val groups = mutableListOf<List<Row>>()
        val current = mutableListOf<Row>()

        for (rowIndex in dataStartRow..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            val hasText = rowHasText(row, formatter)
            val hasPicture = rowIndex in pictureRows
            if (!hasText && !hasPicture) continue

            val startsRecord = cellText(row, 0, formatter).isNotBlank() ||
                (cellText(row, 1, formatter).isNotBlank() && cellText(row, 3, formatter).isNotBlank()) ||
                (cellText(row, 1, formatter).isNotBlank() && cellText(row, 5, formatter).isNotBlank())

            if (startsRecord) {
                if (current.isNotEmpty()) {
                    groups += current.toList()
                    current.clear()
                }
                current += row
            } else if (current.isNotEmpty()) {
                current += row
            } else if (hasText || hasPicture) {
                current += row
            }
        }
        if (current.isNotEmpty()) groups += current.toList()
        return groups
    }

    private fun buildRecordDraft(
        context: Context,
        rows: List<Row>,
        roles: ColumnRoles,
        pictures: List<PictureRef>,
        formatter: DataFormatter
    ): TerminalHazardRecord? {
        val firstRow = rows.firstOrNull() ?: return null
        val firstRowIndex = rows.first().rowNum
        val lastRowIndex = rows.last().rowNum
        val groupPictures = pictures.filter { it.row in firstRowIndex..lastRowIndex }
        val selectedCodes = linkedSetOf<String>()
        val hazardPhotoRefs = linkedMapOf<String, MutableList<PictureRef>>()

        rows.forEach { row ->
            roles.categoryColumns.forEach { (category, columns) ->
                val (itemCol, photoCol) = columns
                val items = splitLines(cellText(row, itemCol, formatter))
                    .mapNotNull { findHazardItem(category, it) }
                selectedCodes += items.map { it.code }
                val rowPhotos = groupPictures.filter { it.row == row.rowNum && it.col == photoCol }
                val targetItem = items.firstOrNull()
                if (targetItem != null && rowPhotos.isNotEmpty()) {
                    hazardPhotoRefs.getOrPut(targetItem.code) { mutableListOf() } += rowPhotos
                }
            }
        }

        val storefrontPhoto = savePictures(
            context,
            groupPictures.filter { it.col == roles.storefrontPhotoCol }
        ).firstOrNull().orEmpty()
        val hazardPhotos = hazardPhotoRefs.mapValues { (_, refs) -> savePictures(context, refs) }
        val qrPhotos = savePictures(context, groupPictures.filter { it.col in roles.qrPhotoCols })
        val cylinderPhotos = savePictures(context, groupPictures.filter { it.col in roles.cylinderPhotoCols })
        val rectificationPhotos = savePictures(context, groupPictures.filter { it.col in roles.rectificationPhotoCols })

        val rawResult = cellText(firstRow, 8, formatter)
        val checkResult = when {
            rawResult.contains("未清零") -> TerminalHazardCatalog.RESULT_NOT_CLEARED
            else -> TerminalHazardCatalog.RESULT_CLEARED
        }
        val rectificationDate = parseDate(firstRow, roles.rectificationDateCol, formatter, 0L)
        val rectificationNote = cellText(firstRow, roles.rectificationNoteCol, formatter)
        val rectificationPerson = cellText(firstRow, roles.rectificationPersonCol, formatter)
        val hadUncleared = rawResult.contains("后期") ||
            selectedCodes.isNotEmpty() ||
            rectificationDate > 0L ||
            rectificationNote.isNotBlank() ||
            rectificationPerson.isNotBlank() ||
            rectificationPhotos.isNotEmpty()

        return TerminalHazardRecord(
            customerName = cellText(firstRow, 1, formatter),
            storefrontPhoto = storefrontPhoto,
            customerPhone = cellText(firstRow, 3, formatter),
            customerType = cellText(firstRow, 4, formatter),
            customerAddress = cellText(firstRow, 5, formatter),
            inspectorName = cellText(firstRow, 6, formatter),
            inspectionDate = parseDate(firstRow, 7, formatter, System.currentTimeMillis()),
            checkResult = checkResult,
            hazardItems = selectedCodes.joinToString(","),
            hazardPhotos = TerminalHazardCatalog.serializePhotoLists(hazardPhotos),
            gasSourceIssues = TerminalHazardCatalog.serializeTextSet(splitLines(cellText(firstRow, roles.gasSourceCol, formatter)).toSet()),
            cylinderQrPhotos = TerminalHazardCatalog.serializePhotoList(qrPhotos),
            cylinderPhotos = TerminalHazardCatalog.serializePhotoList(cylinderPhotos),
            rectificationPhotos = TerminalHazardCatalog.serializePhotoList(rectificationPhotos),
            rectificationDate = rectificationDate,
            rectificationNote = rectificationNote,
            rectificationPerson = rectificationPerson,
            hadUnclearedHazard = hadUncleared,
            deliveryPerson = cellText(firstRow, roles.deliveryPersonCol, formatter),
            remark = cellText(firstRow, roles.remarkCol, formatter),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun readPictures(sheet: Sheet): List<PictureRef> {
        val drawing = (sheet as? org.apache.poi.xssf.usermodel.XSSFSheet)?.drawingPatriarch ?: return emptyList()
        return drawing.shapes.mapIndexedNotNull { index, shape ->
            val picture = shape as? XSSFPicture ?: return@mapIndexedNotNull null
            val anchor = picture.clientAnchor as? XSSFClientAnchor ?: return@mapIndexedNotNull null
            val data = picture.pictureData
            PictureRef(
                id = index,
                row = anchor.row1,
                col = anchor.col1.toInt(),
                bytes = data.data,
                extension = normalizeExtension(data.suggestFileExtension())
            )
        }
    }

    private fun savePictures(context: Context, pictures: List<PictureRef>): List<String> {
        if (pictures.isEmpty()) return emptyList()
        val photoDir = File(context.getExternalFilesDir(null), "photos").apply { mkdirs() }
        return pictures.distinctBy { it.id }.mapIndexedNotNull { index, picture ->
            runCatching {
                val fileName = "IMP_${fileNameFormat.format(Date())}_${abs(picture.id)}_$index.${picture.extension}"
                val target = File(photoDir, fileName)
                target.writeBytes(picture.bytes)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target).toString()
            }.getOrNull()
        }
    }

    private fun normalizeExtension(extension: String?): String =
        when (extension?.lowercase(Locale.ROOT)) {
            "jpeg", "jpg" -> "jpg"
            "png" -> "png"
            else -> "jpg"
        }

    private fun findHazardItem(category: String, text: String): TerminalHazardItem? {
        val normalized = normalizeText(text)
        return TerminalHazardCatalog.items.firstOrNull { item ->
            item.category == category &&
                (normalizeText(item.name) == normalized ||
                    normalizeText(item.name).contains(normalized) ||
                    normalized.contains(normalizeText(item.name)))
        }
    }

    private fun rowHasText(row: Row, formatter: DataFormatter): Boolean {
        val lastCell = row.lastCellNum.toInt().coerceAtLeast(0)
        return (0 until lastCell).any { cellText(row, it, formatter).isNotBlank() }
    }

    private fun cellText(row: Row, col: Int, formatter: DataFormatter): String {
        val cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) ?: return ""
        return formatter.formatCellValue(cell).trim()
    }

    private fun splitLines(value: String): List<String> =
        value.replace("\r", "\n")
            .split("\n", "；", ";")
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun parseDate(row: Row, col: Int, formatter: DataFormatter, defaultValue: Long): Long {
        val cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) ?: return defaultValue
        if (cell.isDateCell()) return cell.dateCellValue.time
        val text = formatter.formatCellValue(cell).trim()
        if (text.isBlank()) return defaultValue
        text.toLongOrNull()?.let { timestamp ->
            if (timestamp > 10_000_000_000L) return timestamp
        }
        supportedDateFormats.forEach { format ->
            runCatching { format.parse(text)?.time }.getOrNull()?.let { return it }
        }
        return defaultValue
    }

    private fun Cell.isDateCell(): Boolean =
        runCatching { DateUtil.isCellDateFormatted(this) && dateCellValue != null }.getOrDefault(false)

    private fun duplicateKey(record: TerminalHazardRecord): String =
        listOf(
            normalizeText(record.customerName),
            normalizePhone(record.customerPhone),
            normalizeText(record.customerAddress),
            dateFormat.format(Date(record.inspectionDate))
        ).joinToString("|")

    private fun normalizeText(value: String): String =
        value.trim().replace(Regex("\\s+"), "")

    private fun normalizePhone(value: String): String =
        value.filter { it.isDigit() || it == '+' }
}
