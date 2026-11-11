package com.xinyuan.gas.ui.cloud

import com.xinyuan.gas.data.sync.CloudRecordSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRecordFiltersTest {
    private val record = CloudRecordSummary(
        id = 1001,
        customerName = "测试用户",
        customerPhone = "",
        customerAddress = "",
        inspectionDate = "2026-11-09",
        checkResult = "",
        status = "pending",
        inspectorName = "李检查员、Zhang",
        deliveryPerson = "王配送工",
        companyName = "甲公司",
        regionId = 7,
        regionName = "东片区",
        storeId = 12,
        storeName = "第一门店"
    )

    @Test fun combinesPartialNamesAndExactOrganizationIds() {
        val filters = CloudRecordFilters(" 王 ", "zhang", regionId = 7, storeId = 12)
        assertTrue(filters.matches(record))
        assertFalse(filters.matches(record.copy(deliveryPerson = "另一位配送工")))
        assertFalse(filters.matches(record.copy(inspectorName = "另一位检查员")))
        assertFalse(filters.matches(record.copy(regionId = 8)))
        assertFalse(filters.matches(record.copy(storeId = 13)))
    }

    @Test fun legacyRecordCanBeFoundByNameWithoutAnOrganization() {
        val legacy = record.copy(regionId = 0, regionName = "", storeId = 0, storeName = "")
        assertTrue(CloudRecordFilters(inspectorName = "李检查员").matches(legacy))
        assertFalse(CloudRecordFilters(regionId = 7).matches(legacy))
        assertFalse(CloudRecordFilters(storeId = 12).matches(legacy))
    }

    @Test fun ownRecordsUseTheCreatorAccountEvenWhenInspectorNamesAreIdentical() {
        val own = record.copy(creatorId = 9)
        val coworker = record.copy(id = 1002, creatorId = 10)
        val missingCreator = record.copy(id = 1003)
        val records = listOf(own, coworker, missingCreator)
        val filters = CloudRecordFilters(ownRecordsOnly = true, regionId = 7)

        assertEquals(2, filters.activeCount)
        assertEquals(listOf(own), filters.recordsInAccountScope(records, accountId = 9).filter(filters::matches))
        assertEquals(emptyList<CloudRecordSummary>(), filters.recordsInAccountScope(records, accountId = 0))
        assertEquals(records, CloudRecordFilters().recordsInAccountScope(records, accountId = 9))
    }

    @Test fun organizationOptionsAreDeduplicatedAndKeepSameNamedCompaniesSeparate() {
        val records = listOf(record, record.copy(id = 1002), record.copy(
            id = 1003, companyName = "乙公司", regionId = 8, storeId = 13
        ))
        assertEquals(setOf("甲公司 / 东片区", "乙公司 / 东片区"),
            recordRegionOptions(records, showCompany = true).map { it.label }.toSet())
        assertEquals(
            listOf(RecordOrganizationOption(12, "东片区 / 第一门店")),
            recordStoreOptions(records, regionId = 7, showCompany = false)
        )
        assertEquals(setOf(12L, 13L),
            recordStoreOptions(records, regionId = 0, showCompany = true).map { it.id }.toSet())
    }
}
