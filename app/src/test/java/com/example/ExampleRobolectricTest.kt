package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.ClientSlotStats
import com.example.model.ControlMode
import com.example.model.ProxyConfig
import com.example.model.SessionLog
import com.example.network.NetworkUtils
import com.example.proxy.HttpProxyServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("HTTP Proxy", appName)
    }

    @Test
    fun `format speed and bytes correctly`() {
        assertEquals("0 bps", NetworkUtils.formatSpeed(0))
        assertEquals("8 kbps", NetworkUtils.formatSpeed(1000))
        assertEquals("8.0 Mbps", NetworkUtils.formatSpeed(1_000_000))

        assertEquals("0 B", NetworkUtils.formatBytes(0))
        assertEquals("1 KB", NetworkUtils.formatBytes(1024))
        assertEquals("1.0 MB", NetworkUtils.formatBytes(1024 * 1024))
    }

    @Test
    fun `default proxy configuration`() {
        val config = ProxyConfig()
        assertEquals("0.0.0.0", config.host)
        assertEquals(8080, config.port)
        assertEquals(false, config.authEnabled)
    }

    @Test
    fun `client slot stats model defaults`() {
        val slot = ClientSlotStats(
            slotIndex = 1,
            clientIp = "192.168.43.2",
            deviceLabel = "User 1 (PC)",
            isConnected = true,
            bytesSent = 1024,
            bytesReceived = 2048,
            totalRequests = 10
        )
        assertEquals(1, slot.slotIndex)
        assertEquals("192.168.43.2", slot.clientIp)
        assertEquals(true, slot.isConnected)
        assertEquals(1024L, slot.bytesSent)
        assertEquals(2048L, slot.bytesReceived)
        assertEquals(ControlMode.USB_SINGLE_USER, ControlMode.values()[0])
    }

    @Test
    fun `server instance initial state`() {
        val server = HttpProxyServer()
        assertEquals(0, server.sessionLogs.value.size)
        assertEquals(3, server.clientSlots.value.size)
    }
}
