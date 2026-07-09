package com.xinyuan.gas.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.xinyuan.gas.data.model.InspectionRecord
import com.xinyuan.gas.data.model.TerminalHazardCatalog
import com.xinyuan.gas.data.model.TerminalHazardRecord
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFClientAnchor
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

object ExportUtil {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)
    private const val TEMP_EXPORT_DIR = "temp_exports"
    private const val EXPORT_IMAGE_MAX_SIDE = 900
    private const val EXPORT_IMAGE_QUALITY = 75

    private val HEADERS = arrayOf(
        "序号", "用户名", "电话", "用户类型", "地址",
        "检查人", "检查日期",
        "报警器", "管道气", "灶具", "软管", "接头", "阀门",
        "用油", "用电", "用液化气", "月均用气量",
        "配送员", "是否整改", "整改日期", "备注", "现场照片"
    )

    fun exportToExcel(context: Context, records: List<InspectionRecord>): File {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("隐患排查记录")
        val drawing = sheet.createDrawingPatriarch()

        // 标题样式
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.LIGHT_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
            val font = workbook.createFont()
            font.bold = true
            font.fontHeightInPoints = 11
            setFont(font)
        }

        // 数据样式
        val dataStyle = workbook.createCellStyle().apply {
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
            wrapText = true
            val font = workbook.createFont()
            font.fontHeightInPoints = 10
            setFont(font)
        }

        // 表头
        val headerRow = sheet.createRow(0)
        HEADERS.forEachIndexed { index, header ->
            headerRow.createCell(index).apply {
                setCellValue(header)
                cellStyle = headerStyle
            }
        }

        val photoColIndex = HEADERS.size - 1 // 最后一列是照片

        // 数据
        records.forEachIndexed { index, record ->
            val row = sheet.createRow(index + 1)
            val values = recordToStringArray(index + 1, record)
            values.forEachIndexed { colIndex, value ->
                row.createCell(colIndex).apply {
                    setCellValue(value)
                    cellStyle = dataStyle
                }
            }

            // 插入照片
            val photos = record.photoPath.split(",").filter { it.isNotEmpty() }
            if (photos.isNotEmpty()) {
                // 设置行高以容纳照片
                row.heightInPoints = 80f

                photos.forEachIndexed { photoIdx, photoUriStr ->
                    try {
                        val photoUri = Uri.parse(photoUriStr)
                        val imageBytes = readPhotoAsJpeg(context, photoUri)
                        if (imageBytes != null) {
                            val pictureIdx = workbook.addPicture(imageBytes, Workbook.PICTURE_TYPE_JPEG)
                            val anchor = XSSFClientAnchor().apply {
                                setCol1(photoColIndex + photoIdx)
                                row1 = index + 1
                                setCol2(photoColIndex + photoIdx + 1)
                                row2 = index + 2
                            }
                            drawing.createPicture(anchor, pictureIdx)
                        }
                    } catch (_: Exception) {
                        // 照片读取失败时跳过
                    }
                }
            }
        }

        // 自动列宽
        for (i in HEADERS.indices) {
            sheet.setColumnWidth(i, 4000)
        }
        // 地址列加宽
        sheet.setColumnWidth(4, 8000)
        // 照片列加宽
        val maxPhotoCount = records.maxOfOrNull {
            it.photoPath.split(",").filter { photo -> photo.isNotEmpty() }.size
        } ?: 0
        for (i in 0 until maxPhotoCount.coerceAtLeast(1)) {
            sheet.setColumnWidth(photoColIndex + i, 6000)
        }

        val file = createTemporaryExportFile(context, "隐患排查_${fileNameFormat.format(Date())}.xlsx")
        FileOutputStream(file).use { workbook.write(it) }
        workbook.close()
        return file
    }

    fun exportToCsv(context: Context, records: List<InspectionRecord>): File {
        val file = createTemporaryExportFile(context, "隐患排查_${fileNameFormat.format(Date())}.csv")

        FileWriter(file).use { writer ->
            // BOM for Excel UTF-8
            writer.write("\uFEFF")
            // 表头
            writer.write(HEADERS.joinToString(",") { "\"$it\"" })
            writer.write("\n")
            // 数据
            records.forEachIndexed { index, record ->
                val values = recordToStringArray(index + 1, record)
                writer.write(values.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
                writer.write("\n")
            }
        }
        return file
    }

    fun exportTerminalHazardList(context: Context, records: List<TerminalHazardRecord>): File {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("终端用户隐患核查清单")
        val drawing = sheet.createDrawingPatriarch()
        val headers = listOf(
            "序号", "用户名", "门头照片", "电话", "用户类型", "地址", "检查人", "检查日期", "检查结果",
            "可燃气体报警器", "照片", "软管", "照片", "用气环境", "照片", "减压阀", "照片", "安全用气指南", "照片",
            "配送员", "备注", "气源排查项目", "钢瓶二维码照片", "钢瓶照片", "整改日期", "整改情况", "整改人", "整改照片"
        )
        val lastColumn = headers.lastIndex
        val qrPhotoCol = 22
        val cylinderPhotoCol = 23
        val rectificationDateCol = 24
        val rectificationNoteCol = 25
        val rectificationPersonCol = 26
        val rectificationPhotoCol = 27

        val titleStyle = workbook.createCellStyle().apply {
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            val font = workbook.createFont()
            font.bold = true
            font.fontHeightInPoints = 16
            setFont(font)
        }
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.LIGHT_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            wrapText = true
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
            val font = workbook.createFont()
            font.bold = true
            font.fontHeightInPoints = 10
            setFont(font)
        }
        val dataStyle = workbook.createCellStyle().apply {
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            wrapText = true
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
            val font = workbook.createFont()
            font.fontHeightInPoints = 10
            setFont(font)
        }

        val titleRow = sheet.createRow(0)
        titleRow.heightInPoints = 28f
        titleRow.createCell(0).apply {
            setCellValue("终端用户隐患核查清单")
            cellStyle = titleStyle
        }
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, lastColumn))

        val headerRow = sheet.createRow(1)
        headerRow.heightInPoints = 32f
        headers.forEachIndexed { index, header ->
            headerRow.createCell(index).apply {
                setCellValue(header)
                cellStyle = headerStyle
            }
        }

        val categoryColumns = listOf(
            "可燃气体报警器" to 9,
            "软管" to 11,
            "用气环境" to 13,
            "减压阀" to 15,
            "安全用气指南" to 17
        )

        val metaRows = mutableListOf<List<String>>()
        var rowIndex = 2
        records.forEachIndexed { index, record ->
            val selectedItems = TerminalHazardCatalog.selectedItems(record)
            val groupedItems = selectedItems.groupBy { it.category }
            val photoMap = TerminalHazardCatalog.photoListMap(record.hazardPhotos)
            val qrPhotos = TerminalHazardCatalog.photoList(record.cylinderQrPhotos)
            val cylinderPhotos = TerminalHazardCatalog.photoList(record.cylinderPhotos)
            val rectificationPhotos = TerminalHazardCatalog.photoList(record.rectificationPhotos)
            val categoryRows = categoryColumns.associate { (category, _) ->
                category to groupedItems[category].orEmpty().flatMap { item ->
                    val photos = photoMap[item.code].orEmpty().ifEmpty { listOf("") }
                    photos.map { photo -> item to photo }
                }
            }
            val categoryRowCount = categoryColumns.maxOf { (category, _) ->
                categoryRows[category]?.size ?: 0
            }
            val rowCount = maxOf(
                categoryRowCount,
                qrPhotos.size,
                cylinderPhotos.size,
                rectificationPhotos.size,
                1
            )
            val firstRow = rowIndex
            val lastRow = rowIndex + rowCount - 1

            repeat(rowCount) { offset ->
                val row = sheet.createRow(rowIndex + offset)
                row.heightInPoints = 78f
                for (col in 0..lastColumn) {
                    row.createCell(col).cellStyle = dataStyle
                }

                if (offset == 0) {
                    val values = listOf(
                        (index + 1).toString(),
                        record.customerName,
                        "",
                        record.customerPhone,
                        record.customerType,
                        record.customerAddress,
                        record.inspectorName,
                        dateFormat.format(Date(record.inspectionDate)),
                        TerminalHazardCatalog.displayResult(record)
                    )
                    values.forEachIndexed { col, value ->
                        row.getCell(col).setCellValue(value)
                    }
                    row.getCell(19).setCellValue(record.deliveryPerson)
                    row.getCell(20).setCellValue(record.remark)
                    row.getCell(21).setCellValue(TerminalHazardCatalog.textSet(record.gasSourceIssues).joinToString("\n"))
                    row.getCell(rectificationDateCol).setCellValue(
                        if (record.rectificationDate > 0) dateFormat.format(Date(record.rectificationDate)) else ""
                    )
                    row.getCell(rectificationNoteCol).setCellValue(record.rectificationNote)
                    row.getCell(rectificationPersonCol).setCellValue(record.rectificationPerson)
                }

                categoryColumns.forEach { (category, col) ->
                    val entry = categoryRows[category]?.getOrNull(offset)
                    if (entry != null) {
                        val (item, photo) = entry
                        row.getCell(col).setCellValue(item.name)
                        insertPhoto(context, workbook, drawing, photo, col + 1, rowIndex + offset, col + 2, rowIndex + offset + 1)
                    }
                }

                qrPhotos.getOrNull(offset)?.let { photo ->
                    insertPhoto(context, workbook, drawing, photo, qrPhotoCol, rowIndex + offset, qrPhotoCol + 1, rowIndex + offset + 1)
                }
                cylinderPhotos.getOrNull(offset)?.let { photo ->
                    insertPhoto(context, workbook, drawing, photo, cylinderPhotoCol, rowIndex + offset, cylinderPhotoCol + 1, rowIndex + offset + 1)
                }
                rectificationPhotos.getOrNull(offset)?.let { photo ->
                    insertPhoto(context, workbook, drawing, photo, rectificationPhotoCol, rowIndex + offset, rectificationPhotoCol + 1, rowIndex + offset + 1)
                }
            }

            insertPhoto(context, workbook, drawing, record.storefrontPhoto, 2, firstRow, 3, lastRow + 1)

            if (rowCount > 1) {
                ((0..8).toList() + listOf(19, 20, 21, rectificationDateCol, rectificationNoteCol, rectificationPersonCol)).forEach { col ->
                    sheet.addMergedRegion(CellRangeAddress(firstRow, lastRow, col, col))
                }
            }
            metaRows += listOf(record.uuid, (firstRow + 1).toString(), (lastRow + 1).toString())
            rowIndex += rowCount
        }

        val widths = intArrayOf(
            1800, 3600, 5200, 3600, 3600, 7800, 3200, 3600, 3600,
            7800, 5200, 7200, 5200, 9000, 5200, 5200, 5200, 7200, 5200,
            3600, 6000, 8200, 5200, 5200, 3600, 7600, 3600, 5200
        )
        widths.forEachIndexed { index, width -> sheet.setColumnWidth(index, width) }
        sheet.createFreezePane(0, 2)
        sheet.setAutoFilter(CellRangeAddress(1, 1, 0, lastColumn))

        val metaSheet = workbook.createSheet("_导入元数据")
        metaSheet.createRow(0).apply {
            createCell(0).setCellValue("schema")
            createCell(1).setCellValue("terminal_hazard_excel_v2")
        }
        metaSheet.createRow(1).apply {
            createCell(0).setCellValue("exportedAt")
            createCell(1).setCellValue(System.currentTimeMillis().toString())
        }
        metaSheet.createRow(2).apply {
            createCell(0).setCellValue("duplicateKey")
            createCell(1).setCellValue("用户名+电话+地址+检查日期")
        }
        metaSheet.createRow(3).apply {
            createCell(0).setCellValue("columns")
            createCell(1).setCellValue(headers.joinToString("|"))
        }
        metaRows.forEachIndexed { index, values ->
            metaSheet.createRow(index + 4).apply {
                createCell(0).setCellValue(values[0])
                createCell(1).setCellValue(values[1])
                createCell(2).setCellValue(values[2])
            }
        }
        workbook.setSheetHidden(workbook.getSheetIndex(metaSheet), true)

        val file = createTemporaryExportFile(context, "终端用户隐患核查清单_${fileNameFormat.format(Date())}.xlsx")
        FileOutputStream(file).use { workbook.write(it) }
        workbook.close()
        return file
    }

    fun exportTerminalHazardListToCsv(context: Context, records: List<TerminalHazardRecord>): File {
        val headers = arrayOf(
            "序号", "用户名", "电话", "用户类型", "地址", "检查人", "检查日期", "状态",
            "隐患项目", "配送员", "备注", "气源排查项目", "整改日期", "整改情况", "整改人",
            "门头照片", "隐患照片数量", "钢瓶二维码照片数量", "钢瓶照片数量", "整改照片数量"
        )
        val file = createTemporaryExportFile(context, "终端用户隐患核查清单_${fileNameFormat.format(Date())}.csv")

        FileWriter(file).use { writer ->
            writer.write("\uFEFF")
            writer.write(headers.joinToString(",") { "\"$it\"" })
            writer.write("\n")
            records.forEachIndexed { index, record ->
                val values = terminalRecordToStringArray(index + 1, record)
                writer.write(values.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
                writer.write("\n")
            }
        }
        return file
    }

    fun cleanupTemporaryExports(context: Context): Int {
        return temporaryExportDir(context)
            .listFiles()
            ?.filter { it.isFile }
            .orEmpty()
            .count { it.delete() }
    }

    fun deleteTemporaryExport(context: Context, file: File?): Boolean {
        if (file == null || !file.exists() || !file.isFile) return false
        val exportDirPath = temporaryExportDir(context).canonicalPath
        val filePath = file.canonicalPath
        if (!filePath.startsWith("$exportDirPath${File.separator}")) return false
        return file.delete()
    }

    private fun recordToStringArray(seq: Int, record: InspectionRecord): Array<String> {
        val photoCount = record.photoPath.split(",").filter { it.isNotEmpty() }.size
        val photoText = if (photoCount > 0) "${photoCount}张照片" else "无"
        return arrayOf(
            seq.toString(),
            record.customerName,
            record.customerPhone,
            record.customerType,
            record.customerAddress,
            record.inspectorName,
            dateFormat.format(Date(record.inspectionDate)),
            record.hazardAlarm,
            record.hazardPipeline,
            record.hazardStove,
            record.hazardHose,
            record.hazardConnector,
            record.hazardValve,
            if (record.useOil) "是" else "否",
            if (record.useElectric) "是" else "否",
            if (record.useLPG) "是" else "否",
            record.monthlyGasUsage,
            record.deliveryPerson,
            record.isRectified,
            if (record.rectificationDate > 0) dateFormat.format(Date(record.rectificationDate)) else "",
            record.remark,
            photoText
        )
    }

    private fun terminalRecordToStringArray(seq: Int, record: TerminalHazardRecord): Array<String> {
        val selectedItems = TerminalHazardCatalog.selectedItems(record).joinToString("\n") { it.name }
        val hazardPhotoCount = TerminalHazardCatalog.photoListMap(record.hazardPhotos).values.sumOf { it.size }
        val qrPhotoCount = TerminalHazardCatalog.photoList(record.cylinderQrPhotos).size
        val cylinderPhotoCount = TerminalHazardCatalog.photoList(record.cylinderPhotos).size
        val rectificationPhotoCount = TerminalHazardCatalog.photoList(record.rectificationPhotos).size
        return arrayOf(
            seq.toString(),
            record.customerName,
            record.customerPhone,
            record.customerType,
            record.customerAddress,
            record.inspectorName,
            dateFormat.format(Date(record.inspectionDate)),
            TerminalHazardCatalog.displayResult(record),
            selectedItems,
            record.deliveryPerson,
            record.remark,
            TerminalHazardCatalog.textSet(record.gasSourceIssues).joinToString("\n"),
            if (record.rectificationDate > 0) dateFormat.format(Date(record.rectificationDate)) else "",
            record.rectificationNote,
            record.rectificationPerson,
            if (record.storefrontPhoto.isNotBlank()) "1张" else "无",
            if (hazardPhotoCount > 0) "${hazardPhotoCount}张" else "无",
            if (qrPhotoCount > 0) "${qrPhotoCount}张" else "无",
            if (cylinderPhotoCount > 0) "${cylinderPhotoCount}张" else "无",
            if (rectificationPhotoCount > 0) "${rectificationPhotoCount}张" else "无"
        )
    }

    private fun insertPhoto(
        context: Context,
        workbook: Workbook,
        drawing: Drawing<*>,
        photoUri: String,
        col1: Int,
        row1: Int,
        col2: Int,
        row2: Int
    ) {
        if (photoUri.isBlank()) return
        try {
            val imageBytes = readPhotoAsJpeg(context, Uri.parse(photoUri)) ?: return
            val pictureIndex = workbook.addPicture(imageBytes, Workbook.PICTURE_TYPE_JPEG)
            val anchor = XSSFClientAnchor().apply {
                setCol1(col1)
                this.row1 = row1
                setCol2(col2)
                this.row2 = row2
            }
            drawing.createPicture(anchor, pictureIndex)
        } catch (_: Exception) {
            // Ignore unreadable photos so export can still finish.
        }
    }

    private fun readPhotoAsJpeg(context: Context, uri: Uri): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, EXPORT_IMAGE_MAX_SIDE)
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return null

        val scaled = scaleBitmap(decoded, EXPORT_IMAGE_MAX_SIDE)
        if (scaled !== decoded) {
            decoded.recycle()
        }

        return ByteArrayOutputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, EXPORT_IMAGE_QUALITY, output)
            scaled.recycle()
            output.toByteArray()
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sampleSize = 1
        var currentWidth = width
        var currentHeight = height
        while (max(currentWidth, currentHeight) / 2 >= maxSide) {
            currentWidth /= 2
            currentHeight /= 2
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun scaleBitmap(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longestSide = max(bitmap.width, bitmap.height)
        if (longestSide <= maxSide) return bitmap

        val scale = maxSide.toFloat() / longestSide.toFloat()
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun createTemporaryExportFile(context: Context, fileName: String): File {
        return File(temporaryExportDir(context), fileName)
    }

    private fun temporaryExportDir(context: Context): File =
        File(context.getExternalFilesDir(null), TEMP_EXPORT_DIR).apply { mkdirs() }
}
