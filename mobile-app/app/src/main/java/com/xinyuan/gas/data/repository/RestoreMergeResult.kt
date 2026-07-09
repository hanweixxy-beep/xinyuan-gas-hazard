package com.xinyuan.gas.data.repository

data class RestoreMergeResult(
    val inserted: Int,
    val updated: Int
) {
    val total: Int = inserted + updated
}

