package com.xinyuan.gas.reminder

import java.time.LocalDate
import java.time.format.DateTimeParseException

data class ReminderCandidate(val id: Long, val inspectionDate: String, val status: String)

object ReminderPolicy {
    const val INTERVAL_DAYS = 3L

    fun dueRecords(records: List<ReminderCandidate>, lastNotifiedDays: Map<Long, Long>, today: LocalDate): List<ReminderCandidate> =
        records.filter { it.status == "pending" && isDue(it.inspectionDate, lastNotifiedDays[it.id], today) }

    fun isDue(inspectionDate: String, lastNotifiedDay: Long?, today: LocalDate): Boolean {
        val inspected = try {
            LocalDate.parse(inspectionDate)
        } catch (_: DateTimeParseException) {
            return false
        }
        val day = today.toEpochDay()
        if (day - inspected.toEpochDay() < INTERVAL_DAYS) return false
        return lastNotifiedDay == null || day - lastNotifiedDay >= INTERVAL_DAYS
    }
}
