package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ProxyPreferences
import com.example.model.ProxyConfig
import com.example.model.ServerStatus
import com.example.network.NetworkUtils
import com.example.proxy.HttpProxyServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ProxyForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.example.proxy.ACTION_START"
        const val ACTION_STOP = "com.example.proxy.ACTION_STOP"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "proxy_service_channel"

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

    private lateinit var preferences: ProxyPreferences

    override fun onCreate() {
        super.onCreate()
        preferences = ProxyPreferences(this)
        createNotificationChannel()
    }

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
        val config = preferences.loadConfig()

        if (config.keepCpuAwake) {
            acquireLocks()
        }

        if (config.powerSave) {
            registerNetworkMonitor()
        }

        serverInstance.start(config)

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
                        val title = "HTTP Proxy Active on :${config.port}"
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
        serverInstance.stop()
        releaseLocks()
        unregisterNetworkMonitor()
    }

    private fun acquireLocks() {
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

            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifiLock = wifiManager.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "HTTPProxy::WifiLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
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
                override fun onLost(network: Network) {
                    val config = preferences.loadConfig()
                    if (config.powerSave) {
                        // In power save mode, if network drops, stop server or wait
                    }
                }
            }
            networkCallback?.let { connectivityManager?.registerNetworkCallback(request, it) }
        } catch (e: Exception) {
            e.printStackTrace()
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

    override fun onBind(intent: Intent?): IBinder? = null
}
