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

/**
 * Ultra-low latency, high-throughput asynchronous HTTP/HTTPS Proxy Server.
 * Optimized with direct byte buffering, TCP_NODELAY, 64KB socket buffers,
 * and single-pass HTTP request line and header parsing.
 */
class HttpProxyServer(val serverName: String = "ProxyServer") {

    private val tag = "HttpProxyServer[$serverName]"

    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverJob: Job? = null
    private var statsJob: Job? = null
    private var serverSocket: ServerSocket? = null

    private val _status = MutableStateFlow(ServerStatus.STOPPED)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

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

    private var currentConfig: ProxyConfig = ProxyConfig()

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
                socket.receiveBufferSize = 64 * 1024
                socket.bind(InetSocketAddress(bindAddr, config.port), 256)
                serverSocket = socket

                _status.value = ServerStatus.RUNNING
                Log.i(tag, "Server listening on ${config.host}:${config.port}")

                startStatsMonitor()

                while (isActive && !socket.isClosed) {
                    try {
                        val clientSocket = socket.accept()
                        // Configure high-performance socket options
                        clientSocket.tcpNoDelay = true
                        clientSocket.sendBufferSize = 64 * 1024
                        clientSocket.receiveBufferSize = 64 * 1024
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
            if (history.isEmpty()) {
                history.addAll(initialHistory())
            }

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
            }
        }
    }

    private fun updateSlotsState() {
        val allTrackedIps = synchronized(clientSlotOrder) {
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
                        remoteSocket.sendBufferSize = 64 * 1024
                        remoteSocket.receiveBufferSize = 64 * 1024
                        remoteSocket.soTimeout = 60000
                        remoteSocket.connect(InetSocketAddress(targetHost, targetPort), 10000)

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
                        remoteSocket.sendBufferSize = 64 * 1024
                        remoteSocket.receiveBufferSize = 64 * 1024
                        remoteSocket.soTimeout = 30000
                        remoteSocket.connect(InetSocketAddress(targetHost, targetPort), 10000)

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

                        var sentReq = reqBytes.size.toLong()
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
                            while (remaining > 0) {
                                val toRead = Math.min(buf.size.toLong(), remaining).toInt()
                                val r = clientIn.read(buf, 0, toRead)
                                if (r == -1) break
                                remoteOut.write(buf, 0, r)
                                remaining -= r
                                sessionReceivedBytes += r
                                totalReceivedBytes.addAndGet(r.toLong())
                                intervalReceivedBytes.addAndGet(r.toLong())
                                trackClientActivity(clientIp, 0L, r.toLong())
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
     * Ultra-fast direct 64KB stream piping without redundant flushing or conversion overhead.
     */
    private fun pipeStreamFast(input: InputStream, output: OutputStream, isClientToRemote: Boolean, clientIp: String): Long {
        val buffer = ByteArray(65536)
        var totalBytes = 0L
        try {
            while (true) {
                val read = input.read(buffer, 0, buffer.size)
                if (read == -1) break
                output.write(buffer, 0, read)
                totalBytes += read

                val count = read.toLong()
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
            output.flush()
        } catch (_: SocketException) {
            // Socket closed normally by client or remote
        } catch (_: SocketTimeoutException) {
            // Idle timeout
        } catch (e: Exception) {
            Log.d(tag, "Pipe stream ended: ${e.message}")
        }
        return totalBytes
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
