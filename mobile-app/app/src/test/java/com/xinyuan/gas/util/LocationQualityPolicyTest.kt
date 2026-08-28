package com.xinyuan.gas.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationQualityPolicyTest {

    @Test
    fun freshAccurateLocationIsAccepted() {
        assertTrue(LocationQualityPolicy.isAcceptable(ageMillis = 8_000L, accuracyMeters = 18f))
    }

    @Test
    fun cachedLocationOlderThanLimitIsRejected() {
        assertFalse(LocationQualityPolicy.isAcceptable(ageMillis = 31_000L, accuracyMeters = 10f))
    }

    @Test
    fun inaccurateLocationIsRejected() {
        assertFalse(LocationQualityPolicy.isAcceptable(ageMillis = 5_000L, accuracyMeters = 101f))
    }

    @Test
    fun targetAccuracyCanFinishAcquisitionEarly() {
        assertTrue(LocationQualityPolicy.isTargetAccuracy(ageMillis = 5_000L, accuracyMeters = 25f))
        assertFalse(LocationQualityPolicy.isTargetAccuracy(ageMillis = 5_000L, accuracyMeters = 45f))
    }
}
