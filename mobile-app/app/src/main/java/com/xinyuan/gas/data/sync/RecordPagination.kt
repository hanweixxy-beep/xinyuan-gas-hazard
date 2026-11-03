package com.xinyuan.gas.data.sync

data class RecordPage<T>(val page: Int, val pageCount: Int, val total: Int, val records: List<T>)

/** Reject incomplete/changing snapshots rather than silently dropping records. */
object RecordPagination {
    fun <T> collect(id: (T) -> Long, load: (Int) -> RecordPage<T>): List<T> {
        val records = linkedMapOf<Long, T>()
        var requestedPage = 1
        var expectedTotal: Int? = null
        var expectedPages: Int? = null
        while (true) {
            val response = load(requestedPage)
            check(response.page == requestedPage && response.pageCount >= requestedPage && response.total >= 0) {
                "云端分页信息无效，请稍后重试"
            }
            if (expectedTotal == null) {
                expectedTotal = response.total
                expectedPages = response.pageCount
            }
            check(expectedTotal == response.total && expectedPages == response.pageCount) {
                "记录正在变化，请稍后刷新"
            }
            response.records.forEach { record ->
                val recordId = id(record)
                check(recordId > 0 && !records.containsKey(recordId)) { "记录分页已变化，请稍后刷新" }
                records[recordId] = record
            }
            if (requestedPage == response.pageCount) break
            check(response.records.isNotEmpty()) { "云端分页不完整，请稍后重试" }
            requestedPage++
        }
        check(records.size == expectedTotal) { "云端记录未读取完整，请稍后重试" }
        return records.values.toList()
    }
}
