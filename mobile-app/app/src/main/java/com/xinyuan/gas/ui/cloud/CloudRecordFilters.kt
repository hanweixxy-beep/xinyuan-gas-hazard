package com.xinyuan.gas.ui.cloud

import com.xinyuan.gas.data.sync.CloudRecordSummary

internal data class CloudRecordFilters(
    val deliveryPerson: String = "",
    val inspectorName: String = "",
    val regionId: Long = 0,
    val storeId: Long = 0
) {
    val activeCount: Int
        get() = listOf(deliveryPerson.isNotBlank(), inspectorName.isNotBlank(), regionId > 0, storeId > 0).count { it }

    fun matches(record: CloudRecordSummary): Boolean =
        record.deliveryPerson.contains(deliveryPerson.trim(), ignoreCase = true) &&
            record.inspectorName.contains(inspectorName.trim(), ignoreCase = true) &&
            (regionId == 0L || record.regionId == regionId) &&
            (storeId == 0L || record.storeId == storeId)
}

internal data class RecordOrganizationOption(val id: Long, val label: String)

internal fun recordRegionOptions(
    records: List<CloudRecordSummary>,
    showCompany: Boolean
): List<RecordOrganizationOption> = records
    .filter { it.regionId > 0 && it.regionName.isNotBlank() }
    .distinctBy { it.regionId }
    .map { RecordOrganizationOption(it.regionId, organizationLabel(it, showCompany, it.regionName)) }
    .sortedBy { it.label }

internal fun recordStoreOptions(
    records: List<CloudRecordSummary>,
    regionId: Long,
    showCompany: Boolean
): List<RecordOrganizationOption> = records
    .filter { it.storeId > 0 && it.storeName.isNotBlank() && (regionId == 0L || it.regionId == regionId) }
    .distinctBy { it.storeId }
    .map {
        val name = listOf(it.regionName, it.storeName).filter(String::isNotBlank).joinToString(" / ")
        RecordOrganizationOption(it.storeId, organizationLabel(it, showCompany, name))
    }
    .sortedBy { it.label }

private fun organizationLabel(record: CloudRecordSummary, showCompany: Boolean, name: String): String =
    if (showCompany && record.companyName.isNotBlank()) "${record.companyName} / $name" else name
