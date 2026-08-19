package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.SessionLog
import com.example.network.NetworkUtils
import com.example.ui.theme.NaturalBackground
import com.example.ui.theme.NaturalBorder
import com.example.ui.theme.NaturalGreenSuccess
import com.example.ui.theme.NaturalGreenTint
import com.example.ui.theme.NaturalHeroContainer
import com.example.ui.theme.NaturalOchrePrimary
import com.example.ui.theme.NaturalOrangeUpload
import com.example.ui.theme.NaturalRedError
import com.example.ui.theme.NaturalRedTint
import com.example.ui.theme.NaturalSurface
import com.example.ui.theme.NaturalSurfaceElevated
import com.example.ui.theme.NaturalTextDark
import com.example.ui.theme.NaturalTextMuted
import com.example.ui.theme.NaturalTextPrimary
import com.example.ui.theme.NaturalTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionLogsDialog(
    logs: List<SessionLog>,
    onDismiss: () -> Unit,
    onClearLogs: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedMethodFilter by remember { mutableStateOf("ALL") }
    var selectedLogForDetails by remember { mutableStateOf<SessionLog?>(null) }

    val filteredLogs = remember(logs, searchQuery, selectedMethodFilter) {
        logs.filter { log ->
            val matchesQuery = searchQuery.isBlank() ||
                    log.targetHost.contains(searchQuery, ignoreCase = true) ||
                    log.clientAddress.contains(searchQuery, ignoreCase = true) ||
                    log.method.contains(searchQuery, ignoreCase = true)

            val matchesMethod = when (selectedMethodFilter) {
                "CONNECT" -> log.method == "CONNECT"
                "GET" -> log.method == "GET"
                "POST" -> log.method == "POST"
                "OTHER" -> log.method !in listOf("CONNECT", "GET", "POST")
                else -> true
            }

            matchesQuery && matchesMethod
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(600.dp)
                .padding(vertical = 16.dp)
                .testTag("session_logs_dialog"),
            shape = RoundedCornerShape(28.dp),
            color = NaturalSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, NaturalBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Activity Log",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = NaturalTextPrimary,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "${logs.size} total sessions recorded",
                            style = MaterialTheme.typography.bodySmall.copy(color = NaturalTextSecondary)
                        )
                    }

                    Row {
                        IconButton(
                            onClick = onClearLogs,
                            modifier = Modifier.testTag("clear_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ClearAll,
                                contentDescription = "Clear Logs",
                                tint = NaturalRedError
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = NaturalTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter host, IP, or method...", color = NaturalTextMuted, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = NaturalTextSecondary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = NaturalTextSecondary)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("log_search_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NaturalOchrePrimary,
                        unfocusedBorderColor = NaturalBorder,
                        focusedTextColor = NaturalTextPrimary,
                        unfocusedTextColor = NaturalTextPrimary,
                        focusedContainerColor = NaturalHeroContainer.copy(alpha = 0.5f),
                        unfocusedContainerColor = NaturalHeroContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Method Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ALL", "CONNECT", "GET", "POST", "OTHER").forEach { method ->
                        FilterChip(
                            selected = selectedMethodFilter == method,
                            onClick = { selectedMethodFilter = method },
                            label = { Text(method, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NaturalOchrePrimary,
                                selectedLabelColor = NaturalSurface,
                                containerColor = NaturalHeroContainer,
                                labelColor = NaturalTextDark
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedMethodFilter == method,
                                borderColor = NaturalBorder,
                                selectedBorderColor = NaturalOchrePrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // List of Logs
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (logs.isEmpty()) "No session requests yet.\nConnect your USB PC to see live traffic!" else "No matching requests found",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = NaturalTextMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { log ->
                            SessionLogItem(
                                log = log,
                                onClick = { selectedLogForDetails = log }
                            )
                        }
                    }
                }
            }
        }
    }

    selectedLogForDetails?.let { log ->
        SessionDetailDialog(
            log = log,
            onDismiss = { selectedLogForDetails = null }
        )
    }
}

@Composable
fun SessionLogItem(
    log: SessionLog,
    onClick: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(log.timestamp) { timeFormat.format(Date(log.timestamp)) }

    val (statusBg, statusColor) = when {
        log.statusCode in 200..299 -> Pair(NaturalGreenTint, NaturalGreenSuccess)
        log.statusCode == 407 -> Pair(NaturalHeroContainer, NaturalOchrePrimary)
        else -> Pair(NaturalRedTint, NaturalRedError)
    }

    Surface(
        color = NaturalSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .border(1.dp, NaturalBorder, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Method badge & Host
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (log.isHttpsTunnel) NaturalHeroContainer else NaturalSurfaceElevated
                ) {
                    Text(
                        text = log.method,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (log.isHttpsTunnel) NaturalOchrePrimary else NaturalTextDark,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "${log.targetHost}:${log.targetPort}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = NaturalTextPrimary,
                            fontSize = 13.sp
                        ),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$timeStr • From ${log.clientAddress} • ${log.durationMs}ms",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = NaturalTextSecondary,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Status Code & Bytes
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusBg
                ) {
                    Text(
                        text = "${log.statusCode}",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            fontSize = 11.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${NetworkUtils.formatBytes(log.bytesSent + log.bytesReceived)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = NaturalTextSecondary,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

@Composable
fun SessionDetailDialog(
    log: SessionLog,
    onDismiss: () -> Unit
) {
    val fullDateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()) }
    val fullTimeStr = remember(log.timestamp) { fullDateFormat.format(Date(log.timestamp)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = NaturalSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, NaturalBorder),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Session Detail",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NaturalTextPrimary
                        )
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = NaturalTextSecondary)
                    }
                }

                HorizontalDivider(color = NaturalBorder, modifier = Modifier.padding(vertical = 8.dp))

                DetailItem(label = "Target Host", value = log.targetHost)
                DetailItem(label = "Target Port", value = log.targetPort.toString())
                DetailItem(label = "HTTP Method", value = log.method)
                DetailItem(label = "Status Code", value = log.statusCode.toString())
                DetailItem(label = "Client IP", value = log.clientAddress)
                DetailItem(label = "Timestamp", value = fullTimeStr)
                DetailItem(label = "Duration", value = "${log.durationMs} ms")
                DetailItem(label = "Data Received", value = NetworkUtils.formatBytes(log.bytesReceived))
                DetailItem(label = "Data Sent", value = NetworkUtils.formatBytes(log.bytesSent))

                if (log.error != null) {
                    DetailItem(label = "Error Info", value = log.error, valueColor = NaturalRedError)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NaturalOchrePrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("OK", color = NaturalSurface)
                }
            }
        }
    }
}

@Composable
private fun DetailItem(
    label: String,
    value: String,
    valueColor: Color = NaturalTextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(color = NaturalTextSecondary)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = valueColor
            )
        )
    }
}
