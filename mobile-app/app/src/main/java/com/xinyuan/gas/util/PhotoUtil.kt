package com.xinyuan.gas.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.coroutines.resume

object PhotoUtil {

    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)
    private const val MAX_PHOTO_SIDE = 1600
    private const val JPEG_QUALITY = 72
    private const val LOCATION_TIMEOUT_MILLIS = 15_000L

    data class LocationSnapshot(
        val address: String = "",
        val watermarkText: String = "",
        val latitude: Double? = null,
        val longitude: Double? = null,
        val accuracyMeters: Float? = null,
        val capturedAt: Long = 0L
    ) {
        val isUsable: Boolean
            get() = latitude != null && longitude != null && accuracyMeters != null && capturedAt > 0L
    }

    fun createImageUri(context: Context): Uri {
        val photoDir = File(context.getExternalFilesDir(null), "photos").apply { mkdirs() }
        val photoFile = File(photoDir, "IMG_${fileNameFormat.format(Date())}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
    }

    /**
     * 给照片添加水印：日期、时间、地址、检查人、用户名、电话
     */
    fun addWatermark(
        context: Context,
        photoUri: Uri,
        address: String,
        inspector: String,
        customerName: String,
        customerPhone: String,
        locationText: String = ""
    ) {
        try {
            val inputStream = context.contentResolver.openInputStream(photoUri) ?: return
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (original == null) return

            val scaled = scaleBitmap(original, MAX_PHOTO_SIDE)
            if (scaled !== original) {
                original.recycle()
            }

            val bitmap = scaled.copy(Bitmap.Config.ARGB_8888, true)
            if (bitmap !== scaled) {
                scaled.recycle()
            }
            val canvas = Canvas(bitmap)

            val now = Date()
            val dateLine = SimpleDateFormat("日期：yyyy-MM-dd  时间：HH:mm:ss", Locale.CHINA).format(now)
            val addressLine = "用户地址：$address"
            val inspectorLine = "检查人：$inspector"
            val customerLine = "用户：$customerName  电话：$customerPhone"
            val rawLines = listOf(dateLine, addressLine, inspectorLine, customerLine) +
                locationText.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toList()

            // 根据图片宽度计算字体大小（约为宽度的3.5%）
            val textSize = bitmap.width * 0.035f
            val padding = textSize * 0.6f

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                this.textSize = textSize
                typeface = Typeface.DEFAULT_BOLD
                setShadowLayer(textSize * 0.15f, 2f, 2f, Color.BLACK)
            }

            // 半透明背景
            val bgPaint = Paint().apply {
                    color = Color.argb(100, 0, 0, 0)
                }

            val maxTextWidth = bitmap.width - padding * 2
            val lines = rawLines.flatMap { wrapLine(it, textPaint, maxTextWidth) }
            val lineHeight = textSize * 1.4f
            val totalHeight = lines.size * lineHeight + padding * 2
            val startY = (bitmap.height - totalHeight).coerceAtLeast(0f)

            // 绘制背景
            canvas.drawRect(0f, startY, bitmap.width.toFloat(), bitmap.height.toFloat(), bgPaint)

            // 绘制文字
            lines.forEachIndexed { index, line ->
                canvas.drawText(
                    line,
                    padding,
                    startY + padding + (index + 1) * lineHeight - textSize * 0.2f,
                    textPaint
                )
            }

            // 保存回原文件
            val outputStream = context.contentResolver.openOutputStream(photoUri)
            if (outputStream != null) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
                outputStream.close()
            }
            bitmap.recycle()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getLocationWatermark(context: Context): String {
        return getLocationSnapshot(context).watermarkText
    }

    suspend fun getLocationAddress(context: Context): String {
        return getLocationSnapshot(context).address
    }

    suspend fun getLocationSnapshot(context: Context): LocationSnapshot {
        val bestLocation = getFreshLocation(context) ?: return LocationSnapshot()
        val address = withContext(Dispatchers.IO) {
            reverseGeocode(context, bestLocation).orEmpty()
        }
        return LocationSnapshot(
            address = address,
            watermarkText = bestLocation.toWatermarkText(),
            latitude = bestLocation.latitude,
            longitude = bestLocation.longitude,
            accuracyMeters = bestLocation.accuracy,
            capturedAt = bestLocation.time.takeIf { it > 0L } ?: System.currentTimeMillis()
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun getFreshLocation(context: Context): Location? {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider -> runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false) }
        if (providers.isEmpty()) return null

        return suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            var bestLocation: Location? = null
            var completed = false
            lateinit var listener: LocationListener
            lateinit var timeout: Runnable

            fun finish(location: Location?) {
                if (completed) return
                completed = true
                handler.removeCallbacks(timeout)
                runCatching { locationManager.removeUpdates(listener) }
                if (continuation.isActive) continuation.resume(location)
            }

            listener = LocationListener { location ->
                val ageMillis = location.ageMillis()
                val accuracy = location.accuracy.takeIf { location.hasAccuracy() } ?: Float.POSITIVE_INFINITY
                if (!LocationQualityPolicy.isAcceptable(ageMillis, accuracy)) return@LocationListener

                val current = bestLocation
                if (current == null || accuracy < current.accuracy) {
                    bestLocation = location
                }
                if (LocationQualityPolicy.isTargetAccuracy(ageMillis, accuracy)) {
                    finish(location)
                }
            }

            timeout = Runnable { finish(bestLocation) }
            continuation.invokeOnCancellation {
                handler.post {
                    if (!completed) {
                        completed = true
                        handler.removeCallbacks(timeout)
                        runCatching { locationManager.removeUpdates(listener) }
                    }
                }
            }

            handler.post {
                providers.forEach { provider ->
                    runCatching {
                        locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                    }
                }
                handler.postDelayed(timeout, LOCATION_TIMEOUT_MILLIS)
            }
        }
    }

    fun deletePhoto(context: Context, photoUri: String) {
        runCatching {
            val uri = Uri.parse(photoUri)
            context.contentResolver.delete(uri, null, null)
        }
    }

    fun deletePhotos(context: Context, photoPath: String) {
        photoPath.split(",")
            .filter { it.isNotEmpty() }
            .forEach { deletePhoto(context, it) }
    }

    fun optimizePhoto(context: Context, photoUri: String): Long {
        if (photoUri.isBlank()) return 0L
        return runCatching {
            val uri = Uri.parse(photoUri)
            val originalBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return 0L
            val original = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size) ?: return 0L
            val scaled = scaleBitmap(original, MAX_PHOTO_SIDE)
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            if (scaled !== original) scaled.recycle()
            original.recycle()

            val optimizedBytes = output.toByteArray()
            if (optimizedBytes.isNotEmpty() && optimizedBytes.size < originalBytes.size) {
                context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                    stream.write(optimizedBytes)
                }
                originalBytes.size.toLong() - optimizedBytes.size.toLong()
            } else {
                0L
            }
        }.getOrDefault(0L)
    }

    fun photoFileName(photoUri: String): String? {
        if (photoUri.isBlank()) return null
        return runCatching {
            Uri.parse(photoUri).lastPathSegment
                ?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun scaleBitmap(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longestSide = max(bitmap.width, bitmap.height)
        if (longestSide <= maxSide) return bitmap

        val scale = maxSide.toFloat() / longestSide.toFloat()
        val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun wrapLine(line: String, paint: Paint, maxWidth: Float): List<String> {
        if (line.isBlank() || paint.measureText(line) <= maxWidth) return listOf(line)

        val result = mutableListOf<String>()
        var remaining = line
        while (remaining.isNotBlank()) {
            val count = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
            result += remaining.take(count).trim()
            remaining = remaining.drop(count).trimStart()
        }
        return result
    }

    private fun Location.toWatermarkText(): String {
        val accuracyText = if (hasAccuracy()) "，精度约${accuracy.toInt()}米" else ""
        val coordinateText = String.format(
            Locale.CHINA,
            "定位坐标：纬度%.6f，经度%.6f%s",
            latitude,
            longitude,
            accuracyText
        )
        return coordinateText
    }

    private fun Location.ageMillis(): Long =
        ((SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000L).coerceAtLeast(0L)

    @Suppress("DEPRECATION")
    private fun reverseGeocode(context: Context, location: Location): String? {
        if (!Geocoder.isPresent()) return null

        val geocoder = Geocoder(context.applicationContext, Locale.CHINA)
        return runCatching {
            geocoder.getFromLocation(location.latitude, location.longitude, 3)
                ?.mapNotNull { it.toDetailedText() }
                ?.maxByOrNull { it.length }
        }.getOrNull()
    }

    private fun Address.toDetailedText(): String? {
        val addressLine = runCatching { getAddressLine(0) }.getOrNull()
            ?.trim()
            ?.removePrefix("中国")
            ?.trim('，', ',', ' ')

        val parts = listOf(
            adminArea,
            locality,
            subAdminArea,
            subLocality,
            thoroughfare,
            subThoroughfare,
            premises,
            featureName
        )
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .fold(mutableListOf<String>()) { result, part ->
                if (result.none { it == part || it.contains(part) || part.contains(it) }) {
                    result += part
                }
                result
            }

        val structuredText = parts.joinToString("").takeIf { it.isNotBlank() }
        return when {
            addressLine.isNullOrBlank() -> structuredText
            structuredText.isNullOrBlank() -> addressLine
            addressLine.length >= structuredText.length -> addressLine
            else -> structuredText
        }
    }
}
