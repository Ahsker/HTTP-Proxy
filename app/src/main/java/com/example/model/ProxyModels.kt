package com.example.model

enum class ServerStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

enum class ControlMode {
    USB_SINGLE_USER,
    HOTSPOT_MULTI_USER
}

data class ProxyConfig(
    val host: String = "0.0.0.0",
    val port: Int = 8080,
    val authEnabled: Boolean = false,
    val username: String = "",
    val password: String = "",
    val powerSave: Boolean = true,
    val autoStartOnBoot: Boolean = false,
    val keepCpuAwake: Boolean = true
)

data class TrafficSample(
    val timestamp: Long,
    val upBps: Long,
    val downBps: Long
)

data class TrafficStats(
    val currentUpBps: Long = 0L,
    val currentDownBps: Long = 0L,
    val maxUpBps: Long = 0L,
    val maxDownBps: Long = 0L,
    val totalUpBytes: Long = 0L,
    val totalDownBytes: Long = 0L,
    val history: List<TrafficSample> = emptyList()
)

data class ClientSlotStats(
    val slotIndex: Int,
    val clientIp: String,
    val deviceLabel: String,
    val isConnected: Boolean,
    val activeSockets: Int = 0,
    val bytesSent: Long = 0L,
    val bytesReceived: Long = 0L,
    val currentSpeedBps: Long = 0L,
    val totalRequests: Int = 0,
    val lastActiveTimestamp: Long = 0L
)

data class SessionLog(
    val id: String,
    val timestamp: Long,
    val clientAddress: String,
    val method: String,
    val targetHost: String,
    val targetPort: Int,
    val statusCode: Int,
    val bytesSent: Long,
    val bytesReceived: Long,
    val durationMs: Long,
    val isHttpsTunnel: Boolean,
    val error: String? = null
)

enum class InterfaceType {
    USB_TETHERING,
    WIFI_HOTSPOT,
    WIFI,
    MOBILE,
    LOOPBACK,
    ETHERNET,
    OTHER
}

data class NetworkInterfaceInfo(
    val name: String,
    val displayName: String,
    val ipAddress: String,
    val type: InterfaceType,
    val isPreferredForWindows: Boolean = false
)

data class TestResult(
    val success: Boolean,
    val latencyMs: Long,
    val message: String,
    val targetUrl: String,
    val responseCode: Int = 0
)
