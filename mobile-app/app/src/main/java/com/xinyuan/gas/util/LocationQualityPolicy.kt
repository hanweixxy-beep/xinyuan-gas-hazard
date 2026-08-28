package com.xinyuan.gas.util

object LocationQualityPolicy {
    const val MAX_AGE_MILLIS = 30_000L
    const val TARGET_ACCURACY_METERS = 30f
    const val MAX_ACCURACY_METERS = 100f

    fun isAcceptable(ageMillis: Long, accuracyMeters: Float): Boolean =
        ageMillis in 0..MAX_AGE_MILLIS &&
            accuracyMeters.isFinite() &&
            accuracyMeters in 0f..MAX_ACCURACY_METERS

    fun isTargetAccuracy(ageMillis: Long, accuracyMeters: Float): Boolean =
        isAcceptable(ageMillis, accuracyMeters) && accuracyMeters <= TARGET_ACCURACY_METERS
}
