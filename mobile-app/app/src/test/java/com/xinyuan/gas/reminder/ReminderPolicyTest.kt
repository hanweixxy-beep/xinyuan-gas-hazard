package com.xinyuan.gas.reminder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReminderPolicyTest {
    private fun date(value: String) = LocalDate.parse(value)

    @Test fun firstReminderStartsOnThirdDay() {
        assertFalse(ReminderPolicy.isDue("2026-11-01", null, date("2026-11-03")))
        assertTrue(ReminderPolicy.isDue("2026-11-01", null, date("2026-11-04")))
    }

    @Test fun repeatsThreeDaysAfterActualNotificationWithoutCatchUpSpam() {
        val sent = date("2026-11-05").toEpochDay()
        assertFalse(ReminderPolicy.isDue("2026-11-01", sent, date("2026-11-05")))
        assertFalse(ReminderPolicy.isDue("2026-11-01", sent, date("2026-11-07")))
        assertTrue(ReminderPolicy.isDue("2026-11-01", sent, date("2026-11-08")))
        assertTrue(ReminderPolicy.isDue("2026-11-01", sent, date("2026-12-01")))
    }

    @Test fun monthYearAndLeapDayBoundaries() {
        assertTrue(ReminderPolicy.isDue("2026-12-30", null, date("2027-01-02")))
        assertFalse(ReminderPolicy.isDue("2028-02-28", null, date("2028-03-01")))
        assertTrue(ReminderPolicy.isDue("2028-02-28", null, date("2028-03-02")))
    }

    @Test fun invalidAndFutureDatesAreNeverDue() {
        listOf("", "null", "2026-02-30", "2027-01-01").forEach {
            assertFalse(ReminderPolicy.isDue(it, null, date("2026-11-02")))
        }
    }

    @Test fun clockMovingBackwardsDoesNotCauseDuplicateReminder() {
        assertFalse(ReminderPolicy.isDue("2026-10-01", date("2026-11-06").toEpochDay(), date("2026-11-02")))
    }

    @Test fun completedRecordsStopAndRecentlyNotifiedRecordsWait() {
        val records = listOf(
            ReminderCandidate(1L, "2026-10-01", "pending"),
            ReminderCandidate(2L, "2026-10-01", "rectified"),
            ReminderCandidate(3L, "2026-10-01", "done"),
            ReminderCandidate(4L, "2026-10-01", "pending"),
            ReminderCandidate(5L, "2026-11-01", "pending")
        )
        val due = ReminderPolicy.dueRecords(records, mapOf(4L to date("2026-11-01").toEpochDay()), date("2026-11-02"))
        assertEquals(listOf(1L), due.map { it.id })
        assertTrue(ReminderPolicy.dueRecords(records.map { it.copy(status = "rectified") }, emptyMap(), date("2026-11-02")).isEmpty())
    }
}
