package com.xinyuan.gas.data.sync

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

fun cloudErrorMessage(error: Throwable): String = when (error) {
    is UnknownHostException -> "无法解析服务器地址，请切换 Wi-Fi 或移动网络后重试"
    is SocketTimeoutException -> "连接服务器超时，请检查网络后重试"
    is ConnectException -> "暂时无法连接服务器，请稍后重试"
    else -> error.message?.takeIf { it.isNotBlank() } ?: "请求失败，请稍后重试"
}
