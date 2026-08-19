package com.example.proxy

import android.util.Base64
import android.util.Log
import com.example.model.ClientSlotStats
import com.example.model.ProxyConfig
import com.example.model.ServerStatus
import com.example.model.SessionLog
import com.example.model.TrafficSample
import com.example.model.TrafficStats
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
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
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

class HttpProxyServer {

    private val tag = "HttpProxyServer"

    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverJob: Job? = null
    private var statsJob: Job? = null
    private var serverSocket: ServerSocket? = null

    private val _status = MutableStateFlow(ServerStatus.STOPPED)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _trafficStats = MutableStateFlow(TrafficStats())
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

    private var currentConfig: ProxyConfig = ProxyConfig()

    private fun defaultSlots(): List<ClientSlotStats> {
        return listOf(
            ClientSlotStats(slotIndex = 1, clientIp = "Not connected", deviceLabel = "User 1 (PC)", isConnected = false),
            ClientSlotStats(slotIndex = 2, clientIp = "Not connected", deviceLabel = "User 2 (Laptop)", isConnected = false),
            ClientSlotStats(slotIndex = 3, clientIp = "Not connected", deviceLabel = "User 3 (Device)", isConnected = false)
        )
    }

    @Synchronized
    fun start(config: ProxyConfig) {
        if (_status.value == ServerStatus.RUNNING || _status.value == ServerStatus.STARTING) {
            return
        }

        currentConfig = config
        _status.value = ServerStatus.STARTING
        _errorMessage.value = null

        serverJob = serverScope.launch {
            try {
                val bindAddr = if (config.host == "0.0.0.0" || config.host.isBlank()) {
                    null // Listen on all interfaces
                } else {
                    InetAddress.getByName(config.host)
                }

                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(bindAddr, config.port), 128)
                serverSocket = socket

                _status.value = ServerStatus.RUNNING
                Log.i(tag, "Proxy server listening on ${config.host}:${config.port}")

                startStatsMonitor()

                while (isActive && !socket.isClosed) {
                    try {
                        val clientSocket = socket.accept()
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
                _errorMessage.value = e.localizedMessage ?: "Failed to start proxy server"
                _status.value = ServerStatus.ERROR
            } finally {
                stopInternal()
            }
        }
    }

    @Synchronized
    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

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

            while (isActive) {
                delay(1000)
                val upBytes = intervalSentBytes.getAndSet(0L)
                val downBytes = intervalReceivedBytes.getAndSet(0L)

                if (upBytes > maxUp) maxUp = upBytes
                if (downBytes > maxDown) maxDown = downBytes

                val now = System.currentTimeMillis()
                history.add(TrafficSample(now, upBytes, downBytes))
                if (history.size > 40) {
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

                // Update 3 Hotspot user slots
                updateSlotsState()
            }
        }
    }

    private fun updateSlotsState() {
        val allTrackedIps = synchronized(clientSlotOrder) {
            // Keep active first, then recent
            val sorted = clientSlotOrder.distinct().sortedWith(
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
                slots.add(
                    ClientSlotStats(
                        slotIndex = i + 1,
                        clientIp = ip,
                        deviceLabel = defaultLabels[i],
                        isConnected = activeSockets > 0 || (System.currentTimeMillis() - (tracker?.lastActiveTimestamp ?: 0L)) < 15000,
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
                        clientIp = "Waiting for User ${i + 1}...",
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
            val clientIp = clientSocket.inetAddress?.hostAddress ?: "Unknown"
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
                clientSocket.soTimeout = 30000
                clientSocket.tcpNoDelay = true

                val clientIn = BufferedInputStream(clientSocket.getInputStream())
                val clientOut = BufferedOutputStream(clientSocket.getOutputStream())

                val requestLine = readLine(clientIn) ?: return@launch
                val parts = requestLine.trim().split(" ")
                if (parts.size < 2) return@launch

                method = parts[0].uppercase()
                val targetUri = parts[1]

                // Read headers
                val headers = mutableListOf<String>()
                var authHeader: String? = null
                var line: String?
                while (true) {
                    line = readLine(clientIn)
                    if (line.isNullOrBlank()) break
                    headers.add(line)
                    if (line.startsWith("Proxy-Authorization:", ignoreCase = true)) {
                        authHeader = line.substringAfter(":").trim()
                    }
                }

                // Check Basic Authentication
                if (currentConfig.authEnabled) {
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
                        remoteSocket.soTimeout = 60000
                        remoteSocket.connect(InetSocketAddress(targetHost, targetPort), 15000)

                        // Respond 200 Connection Established to client
                        val ack = "HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.US_ASCII)
                        clientOut.write(ack)
                        clientOut.flush()
                        sessionSentBytes += ack.size
                        totalSentBytes.addAndGet(ack.size.toLong())
                        intervalSentBytes.addAndGet(ack.size.toLong())
                        trackClientActivity(clientIp, ack.size.toLong(), 0L)

                        // Pipe streams bidirectionally
                        val remoteIn = BufferedInputStream(remoteSocket.getInputStream())
                        val remoteOut = BufferedOutputStream(remoteSocket.getOutputStream())

                        val uploadJob = launch {
                            val up = pipeStream(clientIn, remoteOut, isClientToRemote = true, clientIp = clientIp)
                            sessionReceivedBytes += up
                        }
                        val downloadJob = launch {
                            val down = pipeStream(remoteIn, clientOut, isClientToRemote = false, clientIp = clientIp)
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
                        remoteSocket.soTimeout = 30000
                        remoteSocket.connect(InetSocketAddress(targetHost, targetPort), 15000)

                        val remoteIn = BufferedInputStream(remoteSocket.getInputStream())
                        val remoteOut = BufferedOutputStream(remoteSocket.getOutputStream())

                        // Reconstruct HTTP request line & headers
                        val protocol = if (parts.size > 2) parts[2] else "HTTP/1.1"
                        val newRequestLine = "$method $relativePath $protocol\r\n"
                        val reqBytes = newRequestLine.toByteArray(Charsets.US_ASCII)
                        remoteOut.write(reqBytes)
                        var sentReq = reqBytes.size.toLong()

                        var contentLength = 0L
                        var hasHostHeader = false

                        for (h in headers) {
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
                            val hBytes = "$h\r\n".toByteArray(Charsets.US_ASCII)
                            remoteOut.write(hBytes)
                            sentReq += hBytes.size
                        }

                        if (!hasHostHeader) {
                            val hostHeader = "Host: $targetHost\r\n".toByteArray(Charsets.US_ASCII)
                            remoteOut.write(hostHeader)
                            sentReq += hostHeader.size
                        }

                        remoteOut.write("\r\n".toByteArray(Charsets.US_ASCII))
                        sentReq += 2

                        totalReceivedBytes.addAndGet(sentReq)
                        intervalReceivedBytes.addAndGet(sentReq)
                        sessionReceivedBytes += sentReq
                        trackClientActivity(clientIp, 0L, sentReq)

                        // If request has a body (POST/PUT), forward it
                        if (contentLength > 0) {
                            val buffer = ByteArray(8192)
                            var remaining = contentLength
                            while (remaining > 0) {
                                val toRead = Math.min(buffer.size.toLong(), remaining).toInt()
                                val read = clientIn.read(buffer, 0, toRead)
                                if (read == -1) break
                                remoteOut.write(buffer, 0, read)
                                remaining -= read
                                sessionReceivedBytes += read
                                totalReceivedBytes.addAndGet(read.toLong())
                                intervalReceivedBytes.addAndGet(read.toLong())
                                trackClientActivity(clientIp, 0L, read.toLong())
                            }
                        }
                        remoteOut.flush()

                        // Stream remote response back to client
                        val down = pipeStream(remoteIn, clientOut, isClientToRemote = false, clientIp = clientIp)
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

    private fun pipeStream(input: InputStream, output: OutputStream, isClientToRemote: Boolean, clientIp: String): Long {
        val buffer = ByteArray(32768)
        var totalBytes = 0L
        try {
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                output.flush()
                totalBytes += read

                if (isClientToRemote) {
                    totalReceivedBytes.addAndGet(read.toLong())
                    intervalReceivedBytes.addAndGet(read.toLong())
                    trackClientActivity(clientIp, 0L, read.toLong())
                } else {
                    totalSentBytes.addAndGet(read.toLong())
                    intervalSentBytes.addAndGet(read.toLong())
                    trackClientActivity(clientIp, read.toLong(), 0L)
                }
            }
        } catch (_: SocketException) {
            // Normal connection teardown
        } catch (_: SocketTimeoutException) {
            // Normal idle socket close
        } catch (e: Exception) {
            Log.d(tag, "Pipe stream exception: ${e.message}")
        }
        return totalBytes
    }

    private fun readLine(input: InputStream): String? {
        val baos = ByteArrayOutputStream()
        var prev = -1
        while (true) {
            val curr = input.read()
            if (curr == -1) {
                return if (baos.size() == 0) null else baos.toString("US-ASCII")
            }
            if (curr == '\n'.code) {
                if (prev == '\r'.code) {
                    val bytes = baos.toByteArray()
                    return String(bytes, 0, bytes.size - 1, Charsets.US_ASCII)
                }
                return baos.toString("US-ASCII")
            }
            baos.write(curr)
            prev = curr
            if (baos.size() > 8192) {
                return baos.toString("US-ASCII")
            }
        }
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

        // Relative URI - read from Host header
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
            updated.add(0, log)
            if (updated.size > 200) {
                updated.subList(0, 200)
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
        _trafficStats.value = TrafficStats()
        _clientSlots.value = defaultSlots()
    }
}
