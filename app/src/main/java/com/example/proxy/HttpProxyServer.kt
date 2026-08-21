package com.example.proxy

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Base64
import android.util.Log
import com.example.model.ClientSlotStats
import com.example.model.ControlMode
import com.example.model.ProxyConfig
import com.example.model.ServerStatus
import com.example.model.SessionLog
import com.example.model.TrafficSample
import com.example.model.TrafficStats
import com.example.network.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private data class DnsCacheEntry(val address: InetAddress, val expiresAt: Long)

/**
 * Ultra-low latency, high-throughput asynchronous HTTP/HTTPS Proxy Server.
 * Optimized with direct byte buffering, TCP_NODELAY, 256KB socket buffers,
 * batched stat tracking, upstream network binding, and single-pass HTTP parsing.
 */
class HttpProxyServer(
    val serverName: String = "ProxyServer",
    var connectivityManager: ConnectivityManager? = null
) {

    private val tag = "HttpProxyServer[$serverName]"

    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverJob: Job? = null
    private var statsJob: Job? = null
    private var serverSocket: ServerSocket? = null

    private val _status = MutableStateFlow(ServerStatus.STOPPED)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()
    val isRunning: Boolean
        get() = _status.value == ServerStatus.RUNNING

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _trafficStats = MutableStateFlow(TrafficStats(history = initialHistory()))
    val trafficStats: StateFlow<TrafficStats> = _trafficStats.asStateFlow()

    private val _activeConnectionsCount = MutableStateFlow(0)
    val activeConnectionsCount: StateFlow<Int> = _activeConnectionsCount.asStateFlow()

    private val _connectedClients = MutableStateFlow<Set<String>>(emptySet())
    val connectedClients: StateFlow<Set<String>> = _connectedClients.asStateFlow()

    private val _clientSlots = MutableStateFlow<List<ClientSlotStats>>(defaultSlots())
    val clientSlots: StateFlow<List<ClientSlotStats>> = _clientSlots.asStateFlow()

    private val _sessionLogs = MutableStateFlow<List<SessionLog>>(emptyList())
    val sessionLogs: StateFlow<List<SessionLog>> = _sessionLogs.asStateFlow()

    private val totalSentBytes = AtomicLong(0L)
    private val totalReceivedBytes = AtomicLong(0L)

    private val intervalSentBytes = AtomicLong(0L)
    private val intervalReceivedBytes = AtomicLong(0L)

    private val activeSocketCount = AtomicInteger(0)
    private val activeClientsMap = ConcurrentHashMap<String, Int>()

    // Per-client statistics tracking
    private class PerClientTracker {
        val totalSent = AtomicLong(0L)
        val totalReceived = AtomicLong(0L)
        val intervalBytes = AtomicLong(0L)
        val totalRequests = AtomicInteger(0)
        var lastActiveTimestamp: Long = System.currentTimeMillis()
    }
    private val perClientMap = ConcurrentHashMap<String, PerClientTracker>()
    private val clientSlotOrder = mutableListOf<String>()

    private val dnsCache = ConcurrentHashMap<String, DnsCacheEntry>()
    private val DNS_TTL_MS = 60_000L // 60 seconds TTL for DNS resolution caching

    private var currentConfig: ProxyConfig = ProxyConfig()

    @Volatile
    private var activeMode: ControlMode = ControlMode.HOTSPOT_MULTI_USER

    @Volatile
    private var activeClientPrefix: String? = null

    // USB mode: skip ConnectivityManager upstream binding entirely.
    // Android's default routing already sends app sockets via cellular;
    // avoiding bindSocket/getUpstreamNetwork removes Binder IPC per connection.
    @Volatile
    private var bypassUpstreamBinding: Boolean = false

    @Volatile
    private var cachedUpstreamNetwork: Network? = null

    private var upstreamNetworkCallback: ConnectivityManager.NetworkCallback? = null

    val boundHost: String?
        get() = currentConfig.host.takeIf { it.isNotBlank() && it != "0.0.0.0" }

    fun setUpstreamNetwork(network: Network?) {
        cachedUpstreamNetwork = network
    }

    fun refreshUpstreamNetwork() {
        cachedUpstreamNetwork = findActiveUpstreamNetwork()
    }

    fun setError(message: String) {
        _errorMessage.value = message
        _status.value = ServerStatus.ERROR
    }

    companion object {
        private fun initialHistory(): List<TrafficSample> {
            val now = System.currentTimeMillis()
            return List(30) { i ->
                TrafficSample(now - (30 - i) * 1000L, 0L, 0L)
            }
        }
    }

    private fun defaultSlots(): List<ClientSlotStats> {
        return listOf(
            ClientSlotStats(slotIndex = 1, clientIp = "Not connected", deviceLabel = "User 1 (PC)", isConnected = false),
            ClientSlotStats(slotIndex = 2, clientIp = "Not connected", deviceLabel = "User 2 (Laptop)", isConnected = false),
            ClientSlotStats(slotIndex = 3, clientIp = "Not connected", deviceLabel = "User 3 (Device)", isConnected = false)
        )
    }

    @Synchronized
    fun start(config: ProxyConfig, mode: ControlMode = ControlMode.HOTSPOT_MULTI_USER, cm: ConnectivityManager? = null) {
        if (cm != null) {
            this.connectivityManager = cm
        }
        if (_status.value == ServerStatus.RUNNING || _status.value == ServerStatus.STARTING) {
            return
        }

        currentConfig = config
        activeMode = mode
        bypassUpstreamBinding = (mode == ControlMode.USB_SINGLE_USER)
        activeClientPrefix = if (mode == ControlMode.HOTSPOT_MULTI_USER) {
            NetworkUtils.hotspotPrefix()
        } else {
            NetworkUtils.usbPrefix()
        }

        // USB mode skips all ConnectivityManager wiring (no upstream bind, no callback).
        // Hotspot mode keeps explicit cellular binding to avoid routing loops.
        if (bypassUpstreamBinding) {
            cachedUpstreamNetwork = null
            unregisterUpstreamNetworkCallback()
        } else {
            cachedUpstreamNetwork = findActiveUpstreamNetwork()

            // Register one-time upstream NetworkCallback to eliminate per-connection Binder IPC scans
            registerUpstreamNetworkCallback()
        }

        _status.value = ServerStatus.STARTING
        _errorMessage.value = null

        serverJob = serverScope.launch {
            try {
                val bindAddr = if (config.host == "0.0.0.0" || config.host.isBlank()) {
                    null // Listen on all interfaces without blocking on interface resolution
                } else {
                    try {
                        InetAddress.getByName(config.host)
                    } catch (_: Exception) {
                        null
                    }
                }

                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.receiveBufferSize = 256 * 1024
                socket.bind(InetSocketAddress(bindAddr, config.port), 256)
                serverSocket = socket

                _status.value = ServerStatus.RUNNING
                Log.i(tag, "Server listening on ${config.host}:${config.port} [Mode: $mode, Prefix: $activeClientPrefix]")

                startStatsMonitor()

                while (isActive && !socket.isClosed) {
                    try {
                        val clientSocket = socket.accept()
                        // Configure high-performance socket options
                        clientSocket.tcpNoDelay = true
                        clientSocket.sendBufferSize = 256 * 1024
                        clientSocket.receiveBufferSize = 256 * 1024
                        try {
                            clientSocket.trafficClass = 0x10 // IPTOS_LOWDELAY
                        } catch (_: Exception) {}
                        clientSocket.soTimeout = 45000

                        handleClientConnection(clientSocket)
                    } catch (e: SocketException) {
                        if (socket.isClosed) break
                        Log.w(tag, "Socket accept error: ${e.message}")
                    } catch (e: Exception) {
                        Log.w(tag, "Client connection accept error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to start proxy server: ${e.message}", e)
                _errorMessage.value = e.localizedMessage ?: "Port ${config.port} unavailable"
                _status.value = ServerStatus.ERROR
            } finally {
                stopInternal()
            }
        }
    }

    private fun registerUpstreamNetworkCallback() {
        try {
            val cm = connectivityManager ?: return
            unregisterUpstreamNetworkCallback()
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()

            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val caps = cm.getNetworkCapabilities(network)
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        cachedUpstreamNetwork = network
                    } else if (cachedUpstreamNetwork == null) {
                        cachedUpstreamNetwork = network
                    }
                }

                override fun onLost(network: Network) {
                    if (cachedUpstreamNetwork == network) {
                        cachedUpstreamNetwork = findActiveUpstreamNetwork()
                    }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        cachedUpstreamNetwork = network
                    }
                }
            }
            upstreamNetworkCallback = cb
            cm.registerNetworkCallback(request, cb)
        } catch (e: Exception) {
            Log.w(tag, "Failed to register upstream NetworkCallback: ${e.message}")
        }
    }

    private fun unregisterUpstreamNetworkCallback() {
        try {
            upstreamNetworkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
            upstreamNetworkCallback = null
        } catch (_: Exception) {}
    }

    @Synchronized
    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        unregisterUpstreamNetworkCallback()
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        cachedUpstreamNetwork = null

        statsJob?.cancel()
        statsJob = null

        serverJob?.cancel()
        serverJob = null

        serverScope.coroutineContext[Job]?.cancelChildren()

        activeSocketCount.set(0)
        activeClientsMap.clear()
        _activeConnectionsCount.value = 0
        _connectedClients.value = emptySet()

        if (_status.value != ServerStatus.ERROR) {
            _status.value = ServerStatus.STOPPED
        }
    }

    private fun startStatsMonitor() {
        statsJob?.cancel()
        statsJob = serverScope.launch {
            var maxUp = _trafficStats.value.maxUpBps
            var maxDown = _trafficStats.value.maxDownBps
            val history = _trafficStats.value.history.toMutableList()
            if (history.isEmpty()) {
                history.addAll(initialHistory())
            }

            var pollCounter = 0
            while (isActive) {
                delay(800) // Smooth refresh rate
                val upBytes = intervalSentBytes.getAndSet(0L)
                val downBytes = intervalReceivedBytes.getAndSet(0L)

                if (upBytes > maxUp) maxUp = upBytes
                if (downBytes > maxDown) maxDown = downBytes

                val now = System.currentTimeMillis()
                history.add(TrafficSample(now, upBytes, downBytes))
                while (history.size > 35) {
                    history.removeAt(0)
                }

                _trafficStats.value = TrafficStats(
                    currentUpBps = upBytes,
                    currentDownBps = downBytes,
                    maxUpBps = maxUp,
                    maxDownBps = maxDown,
                    totalUpBytes = totalSentBytes.get(),
                    totalDownBytes = totalReceivedBytes.get(),
                    history = history.toList()
                )

                _activeConnectionsCount.value = activeSocketCount.get()
                _connectedClients.value = activeClientsMap.keys.toSet()

                updateSlotsState()

                // Periodic check (~every 4.8s) for interface IP change (e.g. Hotspot subnet randomization / USB reconnect)
                pollCounter++
                if (pollCounter >= 6) {
                    pollCounter = 0
                    checkInterfaceAndRebindIfNeeded()
                }
            }
        }
    }

    private fun checkInterfaceAndRebindIfNeeded() {
        if (_status.value != ServerStatus.RUNNING) return
        val currentBind = boundHost ?: return
        val expected = NetworkUtils.getTargetBindIp(activeMode)
        if (expected != null && expected != currentBind) {
            Log.i(tag, "Interface IP changed from $currentBind to $expected. Triggering rebind...")
            val newConfig = currentConfig.copy(host = expected)
            val mode = activeMode
            val cm = connectivityManager
            serverScope.launch {
                stopInternal()
                delay(400)
                start(newConfig, mode, cm)
            }
        }
    }

    private fun updateSlotsState() {
        val prefix = activeClientPrefix
        val allTrackedIps = synchronized(clientSlotOrder) {
            val nonLoopback = clientSlotOrder.filter { ip ->
                ip != "127.0.0.1" && ip != "localhost" && (prefix == null || ip.startsWith(prefix))
            }
            val sorted = nonLoopback.distinct().sortedWith(
                compareByDescending<String> { (activeClientsMap[it] ?: 0) > 0 }
                    .thenByDescending { perClientMap[it]?.lastActiveTimestamp ?: 0L }
            )
            sorted.take(3)
        }

        val slots = mutableListOf<ClientSlotStats>()
        val defaultLabels = listOf("User 1 (PC)", "User 2 (Laptop)", "User 3 (Device)")

        for (i in 0 until 3) {
            val ip = allTrackedIps.getOrNull(i)
            if (ip != null) {
                val tracker = perClientMap[ip]
                val activeSockets = activeClientsMap[ip] ?: 0
                val speed = (tracker?.intervalBytes?.getAndSet(0L) ?: 0L)
                val isRecent = (System.currentTimeMillis() - (tracker?.lastActiveTimestamp ?: 0L)) < 20000
                slots.add(
                    ClientSlotStats(
                        slotIndex = i + 1,
                        clientIp = ip,
                        deviceLabel = defaultLabels[i],
                        isConnected = activeSockets > 0 || isRecent,
                        activeSockets = activeSockets,
                        bytesSent = tracker?.totalSent?.get() ?: 0L,
                        bytesReceived = tracker?.totalReceived?.get() ?: 0L,
                        currentSpeedBps = speed,
                        totalRequests = tracker?.totalRequests?.get() ?: 0,
                        lastActiveTimestamp = tracker?.lastActiveTimestamp ?: 0L
                    )
                )
            } else {
                slots.add(
                    ClientSlotStats(
                        slotIndex = i + 1,
                        clientIp = "Waiting for connection...",
                        deviceLabel = defaultLabels[i],
                        isConnected = false,
                        activeSockets = 0,
                        bytesSent = 0L,
                        bytesReceived = 0L,
                        currentSpeedBps = 0L,
                        totalRequests = 0,
                        lastActiveTimestamp = 0L
                    )
                )
            }
        }

        _clientSlots.value = slots
    }

    private fun trackClientActivity(clientIp: String, sent: Long, received: Long) {
        val tracker = perClientMap.computeIfAbsent(clientIp) { PerClientTracker() }
        tracker.totalSent.addAndGet(sent)
        tracker.totalReceived.addAndGet(received)
        tracker.intervalBytes.addAndGet(sent + received)
        tracker.lastActiveTimestamp = System.currentTimeMillis()

        synchronized(clientSlotOrder) {
            if (!clientSlotOrder.contains(clientIp)) {
                clientSlotOrder.add(clientIp)
            }
        }
    }

    private fun handleClientConnection(clientSocket: Socket) {
        serverScope.launch {
            val clientIp = clientSocket.inetAddress?.hostAddress ?: "127.0.0.1"
            val isLoopback = clientIp == "127.0.0.1" || clientIp == "localhost" || clientSocket.inetAddress?.isLoopbackAddress == true

            // Subnet-level isolation: If the incoming client belongs to a different interface subnet, refuse immediately
            val prefix = activeClientPrefix
            if (!isLoopback && prefix != null && !clientIp.startsWith(prefix)) {
                try {
                    clientSocket.close()
                } catch (_: Exception) {}
                return@launch
            }

            val sessionId = UUID.randomUUID().toString()
            val startTime = System.currentTimeMillis()

            activeSocketCount.incrementAndGet()
            activeClientsMap.compute(clientIp) { _, count -> (count ?: 0) + 1 }
            _activeConnectionsCount.value = activeSocketCount.get()
            _connectedClients.value = activeClientsMap.keys.toSet()

            val tracker = perClientMap.computeIfAbsent(clientIp) { PerClientTracker() }
            tracker.totalRequests.incrementAndGet()
            tracker.lastActiveTimestamp = startTime

            synchronized(clientSlotOrder) {
                if (!clientSlotOrder.contains(clientIp)) {
                    clientSlotOrder.add(clientIp)
                }
            }

            var method = "UNKNOWN"
            var targetHost = ""
            var targetPort = 80
            var statusCode = 200
            var isHttps = false
            var sessionSentBytes = 0L
            var sessionReceivedBytes = 0L
            var errorDetail: String? = null

            try {
                val clientIn = clientSocket.getInputStream()
                val clientOut = clientSocket.getOutputStream()

                // Fast single-pass read for request headers
                val headerBuffer = ByteArray(8192)
                var headerLength = 0
                var headerEnd = -1

                while (headerEnd == -1 && headerLength < headerBuffer.size) {
                    val read = clientIn.read(headerBuffer, headerLength, headerBuffer.size - headerLength)
                    if (read == -1) break
                    headerLength += read
                    headerEnd = findHeaderEnd(headerBuffer, headerLength)
                }

                if (headerLength <= 0 || headerEnd == -1) {
                    return@launch
                }

                val headerString = String(headerBuffer, 0, headerEnd, Charsets.US_ASCII)
                val lines = headerString.split("\r\n")
                if (lines.isEmpty()) return@launch

                val requestLine = lines[0]
                val reqParts = requestLine.trim().split(" ")
                if (reqParts.size < 2) return@launch

                method = reqParts[0].uppercase()
                val targetUri = reqParts[1]
                val protocol = if (reqParts.size > 2) reqParts[2] else "HTTP/1.1"

                val headers = lines.subList(1, lines.size)

                // Check Basic Authentication
                if (currentConfig.authEnabled) {
                    val authHeader = headers.find { it.startsWith("Proxy-Authorization:", ignoreCase = true) }?.substringAfter(":")?.trim()
                    val authorized = verifyAuth(authHeader)
                    if (!authorized) {
                        statusCode = 407
                        val response = "HTTP/1.1 407 Proxy Authentication Required\r\n" +
                                "Proxy-Authenticate: Basic realm=\"HTTP Proxy\"\r\n" +
                                "Content-Type: text/plain\r\n" +
                                "Connection: close\r\n" +
                                "Content-Length: 29\r\n\r\n" +
                                "Proxy authentication required"
                        val bytes = response.toByteArray(Charsets.US_ASCII)
                        clientOut.write(bytes)
                        clientOut.flush()
                        sessionSentBytes += bytes.size
                        totalSentBytes.addAndGet(bytes.size.toLong())
                        intervalSentBytes.addAndGet(bytes.size.toLong())
                        trackClientActivity(clientIp, bytes.size.toLong(), 0L)
                        return@launch
                    }
                }

                if (method == "CONNECT") {
                    isHttps = true
                    // HTTPS Tunnel
                    val hostPort = targetUri.split(":")
                    targetHost = hostPort[0]
                    targetPort = if (hostPort.size > 1) hostPort[1].toIntOrNull() ?: 443 else 443

                    var remoteSocket: Socket? = null
                    try {
                        remoteSocket = Socket()
                        remoteSocket.tcpNoDelay = true
                        remoteSocket.sendBufferSize = 256 * 1024
                        remoteSocket.receiveBufferSize = 256 * 1024
                        try {
                            remoteSocket.trafficClass = 0x10 // IPTOS_LOWDELAY
                        } catch (_: Exception) {}
                        val upstreamNetwork = if (bypassUpstreamBinding) null else getUpstreamNetwork()
                        if (upstreamNetwork != null) {
                            try {
                                upstreamNetwork.bindSocket(remoteSocket)
                                Log.d(tag, "Bound HTTPS socket to upstream network: $upstreamNetwork")
                            } catch (e: Exception) {
                                Log.w(tag, "bindSocket failed: ${e.message}")
                            }
                        }
                        remoteSocket.soTimeout = 30000
                        val targetInetAddress = resolveAddress(targetHost, upstreamNetwork)
                        remoteSocket.connect(InetSocketAddress(targetInetAddress, targetPort), 10000)

                        // Respond 200 Connection Established to client
                        val ack = "HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.US_ASCII)
                        clientOut.write(ack)
                        clientOut.flush()
                        sessionSentBytes += ack.size
                        totalSentBytes.addAndGet(ack.size.toLong())
                        intervalSentBytes.addAndGet(ack.size.toLong())
                        trackClientActivity(clientIp, ack.size.toLong(), 0L)

                        val remoteIn = remoteSocket.getInputStream()
                        val remoteOut = remoteSocket.getOutputStream()

                        // If any excess body bytes were read past the header end
                        val excessBytes = headerLength - headerEnd
                        if (excessBytes > 0) {
                            remoteOut.write(headerBuffer, headerEnd, excessBytes)
                            remoteOut.flush()
                            sessionReceivedBytes += excessBytes
                            totalReceivedBytes.addAndGet(excessBytes.toLong())
                            intervalReceivedBytes.addAndGet(excessBytes.toLong())
                            trackClientActivity(clientIp, 0L, excessBytes.toLong())
                        }

                        val uploadJob = launch(Dispatchers.IO) {
                            val up = pipeStreamFast(clientIn, remoteOut, isClientToRemote = true, clientIp = clientIp)
                            sessionReceivedBytes += up
                        }
                        val downloadJob = launch(Dispatchers.IO) {
                            val down = pipeStreamFast(remoteIn, clientOut, isClientToRemote = false, clientIp = clientIp)
                            sessionSentBytes += down
                        }

                        uploadJob.join()
                        downloadJob.join()
                    } catch (e: Exception) {
                        statusCode = 502
                        errorDetail = e.message
                        try {
                            val errMsg = "HTTP/1.1 502 Bad Gateway\r\nContent-Type: text/plain\r\n\r\n${e.message}".toByteArray()
                            clientOut.write(errMsg)
                            clientOut.flush()
                        } catch (_: Exception) {}
                    } finally {
                        try { remoteSocket?.close() } catch (_: Exception) {}
                    }

                } else {
                    // Standard HTTP request
                    isHttps = false
                    val uriInfo = parseHttpUri(targetUri, headers)
                    targetHost = uriInfo.first
                    targetPort = uriInfo.second
                    val relativePath = uriInfo.third

                    var remoteSocket: Socket? = null
                    try {
                        remoteSocket = Socket()
                        remoteSocket.tcpNoDelay = true
                        remoteSocket.sendBufferSize = 256 * 1024
                        remoteSocket.receiveBufferSize = 256 * 1024
                        try {
                            remoteSocket.trafficClass = 0x10 // IPTOS_LOWDELAY
                        } catch (_: Exception) {}
                        val upstreamNetwork = if (bypassUpstreamBinding) null else getUpstreamNetwork()
                        if (upstreamNetwork != null) {
                            try {
                                upstreamNetwork.bindSocket(remoteSocket)
                                Log.d(tag, "Bound HTTP socket to upstream network: $upstreamNetwork")
                            } catch (e: Exception) {
                                Log.w(tag, "bindSocket failed: ${e.message}")
                            }
                        }
                        remoteSocket.soTimeout = 30000
                        val targetInetAddress = resolveAddress(targetHost, upstreamNetwork)
                        remoteSocket.connect(InetSocketAddress(targetInetAddress, targetPort), 10000)

                        val remoteIn = remoteSocket.getInputStream()
                        val remoteOut = remoteSocket.getOutputStream()

                        // Reconstruct clean HTTP headers
                        val sb = StringBuilder()
                        sb.append("$method $relativePath $protocol\r\n")

                        var contentLength = 0L
                        var hasHostHeader = false

                        for (h in headers) {
                            if (h.isBlank()) continue
                            if (h.startsWith("Proxy-Authorization", ignoreCase = true) ||
                                h.startsWith("Proxy-Connection", ignoreCase = true)
                            ) {
                                continue
                            }
                            if (h.startsWith("Host:", ignoreCase = true)) {
                                hasHostHeader = true
                            }
                            if (h.startsWith("Content-Length:", ignoreCase = true)) {
                                contentLength = h.substringAfter(":").trim().toLongOrNull() ?: 0L
                            }
                            sb.append(h).append("\r\n")
                        }

                        if (!hasHostHeader) {
                            sb.append("Host: ").append(targetHost).append("\r\n")
                        }
                        sb.append("\r\n")

                        val reqBytes = sb.toString().toByteArray(Charsets.US_ASCII)
                        remoteOut.write(reqBytes)

                        val sentReq = reqBytes.size.toLong()
                        totalReceivedBytes.addAndGet(sentReq)
                        intervalReceivedBytes.addAndGet(sentReq)
                        sessionReceivedBytes += sentReq
                        trackClientActivity(clientIp, 0L, sentReq)

                        // Forward request body if present (POST / PUT / PATCH)
                        val excessBytes = (headerLength - headerEnd).toLong()
                        if (excessBytes > 0) {
                            val toForward = Math.min(excessBytes, if (contentLength > 0) contentLength else excessBytes).toInt()
                            remoteOut.write(headerBuffer, headerEnd, toForward)
                            sessionReceivedBytes += toForward
                            totalReceivedBytes.addAndGet(toForward.toLong())
                            intervalReceivedBytes.addAndGet(toForward.toLong())
                            trackClientActivity(clientIp, 0L, toForward.toLong())
                            contentLength -= toForward
                        }

                        if (contentLength > 0) {
                            val buf = ByteArray(65536)
                            var remaining = contentLength
                            var uncommitted = 0L
                            while (remaining > 0) {
                                val toRead = Math.min(buf.size.toLong(), remaining).toInt()
                                val r = clientIn.read(buf, 0, toRead)
                                if (r == -1) break
                                remoteOut.write(buf, 0, r)
                                remaining -= r
                                sessionReceivedBytes += r
                                uncommitted += r
                                if (uncommitted >= 1024 * 1024L) {
                                    totalReceivedBytes.addAndGet(uncommitted)
                                    intervalReceivedBytes.addAndGet(uncommitted)
                                    trackClientActivity(clientIp, 0L, uncommitted)
                                    uncommitted = 0L
                                }
                            }
                            if (uncommitted > 0) {
                                totalReceivedBytes.addAndGet(uncommitted)
                                intervalReceivedBytes.addAndGet(uncommitted)
                                trackClientActivity(clientIp, 0L, uncommitted)
                            }
                        }

                        remoteOut.flush()

                        // Stream remote response back to client directly
                        val down = pipeStreamFast(remoteIn, clientOut, isClientToRemote = false, clientIp = clientIp)
                        sessionSentBytes += down
                    } catch (e: Exception) {
                        statusCode = 502
                        errorDetail = e.message
                        try {
                            val errMsg = "HTTP/1.1 502 Bad Gateway\r\nContent-Type: text/plain\r\n\r\n${e.message}".toByteArray()
                            clientOut.write(errMsg)
                            clientOut.flush()
                        } catch (_: Exception) {}
                    } finally {
                        try { remoteSocket?.close() } catch (_: Exception) {}
                    }
                }

            } catch (e: SocketTimeoutException) {
                statusCode = 504
                errorDetail = "Socket timeout: ${e.message}"
            } catch (e: Exception) {
                statusCode = 500
                errorDetail = e.message
            } finally {
                try {
                    clientSocket.close()
                } catch (_: Exception) {}

                activeSocketCount.decrementAndGet()
                activeClientsMap.compute(clientIp) { _, count ->
                    val newCount = (count ?: 1) - 1
                    if (newCount <= 0) null else newCount
                }
                _activeConnectionsCount.value = activeSocketCount.get().coerceAtLeast(0)
                _connectedClients.value = activeClientsMap.keys.toSet()

                val duration = System.currentTimeMillis() - startTime

                if (targetHost.isNotBlank() || method != "UNKNOWN") {
                    val log = SessionLog(
                        id = sessionId,
                        timestamp = startTime,
                        clientAddress = clientIp,
                        method = method,
                        targetHost = if (targetHost.isBlank()) "Unknown" else targetHost,
                        targetPort = targetPort,
                        statusCode = statusCode,
                        bytesSent = sessionSentBytes,
                        bytesReceived = sessionReceivedBytes,
                        durationMs = duration,
                        isHttpsTunnel = isHttps,
                        error = errorDetail
                    )
                    addSessionLog(log)
                }
            }
        }
    }

    /**
     * Ultra-fast direct 64KB stream piping without redundant intermediate buffer copies.
     * Batches atomic stat tracking and Map lookups every ~1MB to eliminate thread contention and cache bouncing.
     */
    private fun pipeStreamFast(input: InputStream, output: OutputStream, isClientToRemote: Boolean, clientIp: String): Long {
        val buffer = ByteArray(65536)
        var totalBytes = 0L
        var uncommittedBytes = 0L
        val batchThreshold = 1024 * 1024L // 1MB batch window
        try {
            while (true) {
                val read = input.read(buffer, 0, buffer.size)
                if (read == -1) break
                output.write(buffer, 0, read)
                totalBytes += read
                uncommittedBytes += read

                if (uncommittedBytes >= batchThreshold) {
                    flushStats(isClientToRemote, clientIp, uncommittedBytes)
                    uncommittedBytes = 0L
                }
            }
            output.flush()
        } catch (_: SocketException) {
            // Socket closed normally by client or remote
        } catch (_: SocketTimeoutException) {
            // Idle timeout
        } catch (e: Exception) {
            Log.d(tag, "Pipe stream ended: ${e.message}")
        } finally {
            if (uncommittedBytes > 0) {
                flushStats(isClientToRemote, clientIp, uncommittedBytes)
            }
        }
        return totalBytes
    }

    private fun flushStats(isClientToRemote: Boolean, clientIp: String, count: Long) {
        if (count <= 0) return
        if (isClientToRemote) {
            totalReceivedBytes.addAndGet(count)
            intervalReceivedBytes.addAndGet(count)
            trackClientActivity(clientIp, 0L, count)
        } else {
            totalSentBytes.addAndGet(count)
            intervalSentBytes.addAndGet(count)
            trackClientActivity(clientIp, count, 0L)
        }
    }

    private fun findActiveUpstreamNetwork(): Network? {
        val cm = connectivityManager ?: return null
        return try {
            val allNetworks = cm.allNetworks
            // 1. Prioritize Cellular / Mobile Data network with Internet capability
            val mobileNetwork = allNetworks.firstOrNull { network ->
                val caps = cm.getNetworkCapabilities(network) ?: return@firstOrNull false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            }

            // 2. Fallback to active default network with Internet capability
            mobileNetwork
                ?: cm.activeNetwork?.takeIf { network ->
                    val caps = cm.getNetworkCapabilities(network) ?: return@takeIf false
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
                ?: allNetworks.firstOrNull { network ->
                    val caps = cm.getNetworkCapabilities(network) ?: return@firstOrNull false
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
        } catch (e: Exception) {
            Log.w(tag, "Failed to find active upstream network: ${e.message}")
            null
        }
    }

    /**
     * Finds the upstream mobile data network or active internet connection.
     * Uses cached @Volatile Network instance to eliminate per-connection Binder IPC overhead.
     */
    private fun getUpstreamNetwork(): Network? {
        val cached = cachedUpstreamNetwork
        if (cached != null) return cached
        val fresh = findActiveUpstreamNetwork()
        cachedUpstreamNetwork = fresh
        return fresh
    }

    /**
     * Resolves domain names (DNS) through the upstream network with a high-performance
     * in-memory TTL cache to eliminate per-connection carrier DNS latency bottlenecks.
     */
    private fun resolveAddress(host: String, network: Network?): InetAddress {
        // Fast path for raw IPv4 or IPv6 literals without triggering DNS lookups
        if (host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")) || host.contains(":")) {
            return InetAddress.getByName(host)
        }

        // Check DNS cache first (0ms hit)
        val now = System.currentTimeMillis()
        val cached = dnsCache[host]
        if (cached != null && now < cached.expiresAt) {
            return cached.address
        }

        // Cache miss — resolve and store
        return try {
            val addresses = if (network != null) {
                network.getAllByName(host)
            } else {
                InetAddress.getAllByName(host)
            }
            // Prefer IPv4 address to eliminate carrier IPv6 AAAA timeout stalls
            val result = addresses.firstOrNull { it is Inet4Address }
                ?: addresses.firstOrNull()
                ?: throw java.net.UnknownHostException(host)

            dnsCache[host] = DnsCacheEntry(result, now + DNS_TTL_MS)
            result
        } catch (e: Exception) {
            // Fallback to default resolver
            try {
                val result = InetAddress.getByName(host)
                dnsCache[host] = DnsCacheEntry(result, now + DNS_TTL_MS)
                result
            } catch (fallbackEx: Exception) {
                Log.e(tag, "All DNS resolution failed for $host: ${fallbackEx.message}")
                throw fallbackEx
            }
        }
    }

    private fun findHeaderEnd(buf: ByteArray, length: Int): Int {
        for (i in 0 until length - 3) {
            if (buf[i] == '\r'.code.toByte() &&
                buf[i + 1] == '\n'.code.toByte() &&
                buf[i + 2] == '\r'.code.toByte() &&
                buf[i + 3] == '\n'.code.toByte()
            ) {
                return i + 4
            }
        }
        return -1
    }

    private fun verifyAuth(authHeader: String?): Boolean {
        if (authHeader == null || !authHeader.startsWith("Basic ", ignoreCase = true)) {
            return false
        }
        return try {
            val encoded = authHeader.substring(6).trim()
            val decoded = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
            val parts = decoded.split(":", limit = 2)
            if (parts.size == 2) {
                parts[0] == currentConfig.username && parts[1] == currentConfig.password
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun parseHttpUri(uri: String, headers: List<String>): Triple<String, Int, String> {
        if (uri.startsWith("http://", ignoreCase = true)) {
            val noPrefix = uri.substring(7)
            val slashIdx = noPrefix.indexOf('/')
            val hostPortStr = if (slashIdx != -1) noPrefix.substring(0, slashIdx) else noPrefix
            val path = if (slashIdx != -1) noPrefix.substring(slashIdx) else "/"

            val hp = hostPortStr.split(":")
            val host = hp[0]
            val port = if (hp.size > 1) hp[1].toIntOrNull() ?: 80 else 80
            return Triple(host, port, path)
        }

        val hostHeader = headers.find { it.startsWith("Host:", ignoreCase = true) }
        if (hostHeader != null) {
            val hostValue = hostHeader.substringAfter(":").trim()
            val hp = hostValue.split(":")
            val host = hp[0]
            val port = if (hp.size > 1) hp[1].toIntOrNull() ?: 80 else 80
            return Triple(host, port, uri)
        }

        return Triple("127.0.0.1", 80, uri)
    }

    private fun addSessionLog(log: SessionLog) {
        _sessionLogs.update { currentList ->
            val updated = ArrayList<SessionLog>(currentList.size + 1)
            updated.add(log)
            updated.addAll(currentList)
            if (updated.size > 200) {
                updated.subList(0, 200).toList()
            } else {
                updated
            }
        }
    }

    fun clearSessionLogs() {
        _sessionLogs.value = emptyList()
    }

    fun resetStats() {
        totalSentBytes.set(0L)
        totalReceivedBytes.set(0L)
        intervalSentBytes.set(0L)
        intervalReceivedBytes.set(0L)
        perClientMap.clear()
        synchronized(clientSlotOrder) {
            clientSlotOrder.clear()
        }
        _trafficStats.value = TrafficStats(history = initialHistory())
        _clientSlots.value = defaultSlots()
    }
}
