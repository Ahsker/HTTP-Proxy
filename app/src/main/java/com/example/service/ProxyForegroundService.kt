package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ProxyPreferences
import com.example.model.ControlMode
import com.example.model.InterfaceType
import com.example.model.ProxyConfig
import com.example.model.ServerStatus
import com.example.network.NetworkUtils
import com.example.proxy.HttpProxyServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ProxyForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.example.proxy.ACTION_START"
        const val ACTION_STOP = "com.example.proxy.ACTION_STOP"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "proxy_service_channel"
        private const val TAG = "ProxyForegroundService"

        // Singleton server instance accessible from UI & ViewModel
        val serverInstance: HttpProxyServer by lazy { HttpProxyServer() }

        fun startService(context: Context) {
            val intent = Intent(context, ProxyForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ProxyForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var notificationJob: Job? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastBoundIp: String? = null

    private lateinit var preferences: ProxyPreferences

    override fun onCreate() {
        super.onCreate()
        preferences = ProxyPreferences(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP) {
            stopProxy()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_START || action == null) {
            startForeground(NOTIFICATION_ID, buildNotification("Starting HTTP Proxy...", ""))
            startProxy()
        }

        return START_STICKY
    }

    private fun startProxy() {
        val mode = preferences.loadControlMode()
        val config = preferences.loadConfig()
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        connectivityManager = cm

        if (config.keepCpuAwake) {
            acquireLocks(mode)
        }

        registerNetworkMonitor()

        val targetIp = NetworkUtils.getTargetBindIp(mode)
        if (targetIp == null) {
            val errorMsg = if (mode == ControlMode.USB_SINGLE_USER) {
                "USB tethering not active — plug in the cable first"
            } else {
                "Hotspot not active — turn it on first"
            }
            serverInstance.setError(errorMsg)
            val notification = buildNotification("HTTP Proxy Error", errorMsg)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
            stopSelf()
            return
        }

        lastBoundIp = targetIp
        val effectiveConfig = config.copy(host = targetIp)
        serverInstance.start(effectiveConfig, mode, cm)

        // Observe server stats and update notification dynamically
        notificationJob?.cancel()
        notificationJob = serviceScope.launch {
            combine(
                serverInstance.status,
                serverInstance.trafficStats,
                serverInstance.activeConnectionsCount
            ) { status, stats, conns ->
                Triple(status, stats, conns)
            }.collect { (status, stats, conns) ->
                when (status) {
                    ServerStatus.RUNNING -> {
                        val modeLabel = if (mode == ControlMode.USB_SINGLE_USER) "USB" else "Hotspot"
                        val title = "HTTP Proxy Active [$modeLabel] on $targetIp:${config.port}"
                        val text = "Active sockets: $conns • Up: ${NetworkUtils.formatBytes(stats.totalUpBytes)} • Down: ${NetworkUtils.formatBytes(stats.totalDownBytes)}"
                        val notification = buildNotification(title, text)
                        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        manager.notify(NOTIFICATION_ID, notification)
                    }
                    ServerStatus.ERROR -> {
                        val title = "HTTP Proxy Error"
                        val text = serverInstance.errorMessage.value ?: "Unable to bind port"
                        val notification = buildNotification(title, text)
                        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        manager.notify(NOTIFICATION_ID, notification)
                    }
                    ServerStatus.STOPPED -> {
                        // handled when stopping
                    }
                    else -> {}
                }
            }
        }
    }

    private fun stopProxy() {
        notificationJob?.cancel()
        notificationJob = null
        lastBoundIp = null
        serverInstance.stop()
        releaseLocks()
        unregisterNetworkMonitor()
    }

    private fun acquireLocks(mode: ControlMode) {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "HTTPProxy::WakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire(24 * 60 * 60 * 1000L) // Max 24 hours
                }
            }

            // Only acquire WifiLock in Hotspot mode. In USB mode, disabling WifiLock eliminates Wi-Fi power throttling.
            if (mode == ControlMode.HOTSPOT_MULTI_USER) {
                if (wifiLock == null) {
                    val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val wifiMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                    } else {
                        @Suppress("DEPRECATION")
                        WifiManager.WIFI_MODE_FULL_HIGH_PERF
                    }
                    wifiLock = wifiManager.createWifiLock(
                        wifiMode,
                        "HTTPProxy::WifiLock"
                    ).apply {
                        setReferenceCounted(false)
                        acquire()
                    }
                }
            } else {
                wifiLock?.let { if (it.isHeld) it.release() }
                wifiLock = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null

            wifiLock?.let {
                if (it.isHeld) it.release()
            }
            wifiLock = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun registerNetworkMonitor() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    serverInstance.refreshUpstreamNetwork()
                    checkAndRebindIfInterfaceChanged()
                }

                override fun onLost(network: Network) {
                    serverInstance.refreshUpstreamNetwork()
                    checkAndRebindIfInterfaceChanged()
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    serverInstance.refreshUpstreamNetwork()
                }

                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    checkAndRebindIfInterfaceChanged()
                }
            }
            networkCallback?.let { connectivityManager?.registerNetworkCallback(request, it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var rebindJob: Job? = null

    private fun checkAndRebindIfInterfaceChanged() {
        val currentStatus = serverInstance.status.value
        if (currentStatus != ServerStatus.RUNNING && currentStatus != ServerStatus.ERROR) return
        
        rebindJob?.cancel()
        rebindJob = serviceScope.launch {
            delay(1500) // Allow DHCP and OS interface stack to settle
            val mode = preferences.loadControlMode()
            val freshIp = NetworkUtils.getTargetBindIp(mode)
            val currentBind = serverInstance.boundHost ?: lastBoundIp

            if (serverInstance.isRunning && freshIp != null && freshIp != currentBind) {
                Log.i(TAG, "Network interface IP changed from $currentBind to $freshIp. Auto-rebinding proxy...")
                val config = preferences.loadConfig()
                lastBoundIp = freshIp
                val effectiveConfig = config.copy(host = freshIp)
                serverInstance.stop()
                delay(500)
                serverInstance.start(effectiveConfig, mode, connectivityManager)
            }
        }
    }

    private fun unregisterNetworkMonitor() {
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
            networkCallback = null
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.proxy_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.proxy_service_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ProxyForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.proxy_notification_stop),
                stopPendingIntent
            )
            .build()
    }

    override fun onDestroy() {
        stopProxy()
        serviceScope.cancel()
        super.onDestroy()
    }
}
