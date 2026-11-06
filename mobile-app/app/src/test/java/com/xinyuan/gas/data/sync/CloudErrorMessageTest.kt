package com.xinyuan.gas.data.sync

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CloudErrorMessageTest {
    @Test fun dnsFailureProvidesAnActionInsteadOfTheRawHostError() {
        val message = cloudErrorMessage(UnknownHostException("Unable to resolve host example.invalid"))
        assertEquals("无法解析服务器地址，请切换 Wi-Fi 或移动网络后重试", message)
        assertFalse(message.contains("example.invalid"))
    }

    @Test fun timeoutHasItsOwnRetryMessage() {
        assertEquals("连接服务器超时，请检查网络后重试", cloudErrorMessage(SocketTimeoutException("timeout")))
    }

    @Test fun refusedConnectionHasItsOwnRetryMessage() {
        assertEquals("暂时无法连接服务器，请稍后重试", cloudErrorMessage(ConnectException("refused")))
    }

    @Test fun serverValidationAndLoginErrorsKeepTheirMeaning() {
        assertEquals("门店与片区不匹配，请重新选择。", cloudErrorMessage(CloudApiException(422, "门店与片区不匹配，请重新选择。")))
        assertEquals("手机号或密码不正确。", cloudErrorMessage(CloudApiException(401, "手机号或密码不正确。")))
    }

    @Test fun missingDetailsStillProduceAReadableMessage() {
        assertEquals("请求失败，请稍后重试", cloudErrorMessage(IllegalStateException()))
    }
}
