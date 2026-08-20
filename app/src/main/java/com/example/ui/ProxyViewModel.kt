package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ProxyPreferences
import com.example.model.ClientSlotStats
import com.example.model.ControlMode
import com.example.model.NetworkInterfaceInfo
import com.example.model.ProxyConfig
import com.example.model.ServerStatus
import com.example.model.SessionLog
import com.example.model.TestResult
import com.example.model.ThemeMode
import com.example.model.TrafficStats
import com.example.network.NetworkUtils
import com.example.service.ProxyForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

class ProxyViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = ProxyPreferences(application)
    private val server = ProxyForegroundService.serverInstance

    private val _config = MutableStateFlow(preferences.loadConfig())
    val config: StateFlow<ProxyConfig> = _config.asStateFlow()

    private val _themeMode = MutableStateFlow(preferences.loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _controlMode = MutableStateFlow(preferences.loadControlMode())
    val controlMode: StateFlow<ControlMode> = _controlMode.asStateFlow()

    val serverStatus: StateFlow<ServerStatus> = server.status
    val errorMessage: StateFlow<String?> = server.errorMessage
    val trafficStats: StateFlow<TrafficStats> = server.trafficStats
    val activeConnectionsCount: StateFlow<Int> = server.activeConnectionsCount
    val connectedClients: StateFlow<Set<String>> = server.connectedClients
    val clientSlots: StateFlow<List<ClientSlotStats>> = server.clientSlots
    val sessionLogs: StateFlow<List<SessionLog>> = server.sessionLogs

    private val _networkInterfaces = MutableStateFlow<List<NetworkInterfaceInfo>>(emptyList())
    val networkInterfaces: StateFlow<List<NetworkInterfaceInfo>> = _networkInterfaces.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _testResult = MutableStateFlow<TestResult?>(null)
    val testResult: StateFlow<TestResult?> = _testResult.asStateFlow()

    private val _uptimeSeconds = MutableStateFlow(0L)
    val uptimeSeconds: StateFlow<Long> = _uptimeSeconds.asStateFlow()

    private var serverStartTimestamp: Long = 0L

    init {
        server.connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        refreshNetworkInterfaces()

        // Uptime ticker coroutine
        viewModelScope.launch {
            while (isActive) {
                if (serverStatus.value == ServerStatus.RUNNING) {
                    if (serverStartTimestamp == 0L) {
                        serverStartTimestamp = System.currentTimeMillis()
                    }
                    _uptimeSeconds.value = (System.currentTimeMillis() - serverStartTimestamp) / 1000
                } else {
                    serverStartTimestamp = 0L
                    _uptimeSeconds.value = 0L
                }
                delay(1000)
            }
        }
    }

    fun setControlMode(mode: ControlMode) {
        _controlMode.value = mode
        preferences.saveControlMode(mode)
    }

    fun toggleDayNight() {
        val newMode = if (_themeMode.value == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
        _themeMode.value = newMode
        preferences.saveThemeMode(newMode)
    }

    fun refreshNetworkInterfaces() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = NetworkUtils.getAvailableNetworkInterfaces()
            _networkInterfaces.value = list
        }
    }

    fun toggleServer(targetMode: ControlMode? = null) {
        if (targetMode != null) {
            setControlMode(targetMode)
        }
        val current = serverStatus.value
        if (current == ServerStatus.RUNNING || current == ServerStatus.STARTING) {
            ProxyForegroundService.stopService(getApplication())
        } else {
            ProxyForegroundService.startService(getApplication())
        }
    }

    fun saveConfig(newConfig: ProxyConfig) {
        _config.value = newConfig
        preferences.saveConfig(newConfig)

        // If server is currently running, restart it with new config
        if (serverStatus.value == ServerStatus.RUNNING) {
            ProxyForegroundService.stopService(getApplication())
            ProxyForegroundService.startService(getApplication())
        }
    }

    fun clearLogs() {
        server.clearSessionLogs()
    }

    fun resetStats() {
        server.resetStats()
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(getApplication(), "Copied $label to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun openHotspotSettings() {
        val actions = listOf(
            "android.settings.WIFI_AP_SETTINGS",
            "android.settings.TETHER_SETTINGS",
            Settings.ACTION_WIRELESS_SETTINGS,
            Settings.ACTION_SETTINGS
        )
        for (action in actions) {
            try {
                val intent = Intent(action).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                getApplication<Application>().startActivity(intent)
                return
            } catch (_: Exception) {
                // Try next action
            }
        }
        Toast.makeText(getApplication(), "Please open Hotspot settings in Android Settings", Toast.LENGTH_SHORT).show()
    }

    fun openTetheringSettings() {
        openHotspotSettings()
    }

    fun runProxySelfTest(targetUrl: String = "https://www.google.com/generate_204") {
        if (_isTesting.value) return
        _isTesting.value = true
        _testResult.value = null

        viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                val currentConfig = _config.value
                val currentPort = currentConfig.port
                val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", currentPort))
                val url = URL(targetUrl)
                val connection = url.openConnection(proxy) as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.instanceFollowRedirects = true

                // Attach Proxy-Authorization header if authentication is configured
                if (currentConfig.authEnabled && currentConfig.username.isNotBlank()) {
                    val credentials = "${currentConfig.username}:${currentConfig.password}"
                    val encoded = android.util.Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
                    connection.setRequestProperty("Proxy-Authorization", "Basic $encoded")
                }

                val code = connection.responseCode
                val latency = System.currentTimeMillis() - startTime
                val success = code in 200..399

                val msg = when {
                    success -> "Proxy responded successfully (HTTP $code) in ${latency}ms"
                    code == 407 -> "Authentication Required (HTTP 407) — Check username/password"
                    code == 502 -> "Bad Gateway (HTTP 502) — Phone mobile data connection failed"
                    code == 504 -> "Gateway Timeout (HTTP 504) — Upstream network took too long to respond"
                    else -> "Server returned HTTP $code"
                }

                _testResult.value = TestResult(
                    success = success,
                    latencyMs = latency,
                    message = msg,
                    targetUrl = targetUrl,
                    responseCode = code
                )
                connection.disconnect()
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startTime
                val errorMsg = when {
                    e is java.net.ConnectException -> "Proxy service not running or port bind failed (Connection Refused)"
                    e is java.net.SocketTimeoutException -> "Self-test timed out (No response from upstream mobile network)"
                    else -> e.localizedMessage ?: "Connection failed"
                }
                _testResult.value = TestResult(
                    success = false,
                    latencyMs = latency,
                    message = errorMsg,
                    targetUrl = targetUrl,
                    responseCode = 0
                )
            } finally {
                _isTesting.value = false
            }
        }
    }

    fun clearTestResult() {
        _testResult.value = null
    }
}
