package com.xinyuan.gas.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordPaginationTest {
    @Test fun readsBeyondOneThousandRecords() {
        val source = (1L..2334L).toList()
        val requested = mutableListOf<Int>()
        val records = RecordPagination.collect({ it }) { page ->
            requested.add(page)
            RecordPage(page, 24, source.size, source.drop((page - 1) * 100).take(100))
        }
        assertEquals(source, records)
        assertEquals((1..24).toList(), requested)
    }

    @Test fun emptyResultsAreValid() {
        assertTrue(RecordPagination.collect<Long>({ it }) { RecordPage(1, 1, 0, emptyList()) }.isEmpty())
    }

    @Test(expected = IllegalStateException::class) fun rejectsMissingPaginationPages() {
        RecordPagination.collect({ it }) { page -> RecordPage(1, 2, 2, listOf(page.toLong())) }
    }

    @Test(expected = IllegalStateException::class) fun rejectsDuplicateIdsWhenPageMoves() {
        RecordPagination.collect({ it }) { page -> RecordPage(page, 2, 2, listOf(1L)) }
    }

    @Test(expected = IllegalStateException::class) fun rejectsChangedTotal() {
        RecordPagination.collect({ it }) { page -> RecordPage(page, 2, if (page == 1) 2 else 3, listOf(page.toLong())) }
    }

    @Test(expected = IllegalStateException::class) fun rejectsTruncatedLastPage() {
        RecordPagination.collect({ it }) { RecordPage(1, 1, 2, listOf(1L)) }
    }

    @Test(expected = IllegalStateException::class) fun rejectsEmptyIntermediatePage() {
        RecordPagination.collect<Long>({ it }) { RecordPage(it, 2, 2, emptyList()) }
    }
}
