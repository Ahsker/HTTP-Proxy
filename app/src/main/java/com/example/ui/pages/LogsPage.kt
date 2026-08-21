package com.example.ui.pages

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.data.VolumeCheckpoint
import com.example.model.SessionLog
import com.example.network.NetworkUtils
import com.example.ui.components.SessionDetailDialog
import com.example.ui.components.SessionLogItem
import com.example.ui.theme.NaturalGreenSuccess
import com.example.ui.theme.NaturalGreenTint
import com.example.ui.theme.NaturalOrangeUpload
import com.example.ui.theme.NaturalRedError
import com.example.ui.theme.NaturalRedTint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsPage(
    sessionLogs: List<SessionLog>,
    volumeCheckpoints: List<VolumeCheckpoint>,
    onClearLogs: () -> Unit,
    onCopy: (label: String, text: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedMethodFilter by remember { mutableStateOf("ALL") }
    var selectedLogForDetails by remember { mutableStateOf<SessionLog?>(null) }
    var volumeExpanded by remember { mutableStateOf(false) }

    val filteredLogs = remember(sessionLogs, searchQuery, selectedMethodFilter) {
        sessionLogs.filter { log ->
            val matchesSearch = searchQuery.isBlank() ||
                    log.targetHost.contains(searchQuery, ignoreCase = true) ||
                    log.clientAddress.contains(searchQuery, ignoreCase = true) ||
                    log.method.contains(searchQuery, ignoreCase = true)

            val matchesMethod = when (selectedMethodFilter) {
                "HTTPS" -> log.isHttpsTunnel || log.method == "CONNECT"
                "HTTP" -> !log.isHttpsTunnel && log.method != "CONNECT"
                "ERRORS" -> log.statusCode >= 400 || log.error != null
                else -> true
            }
            matchesSearch && matchesMethod
        }
    }

    // Traffic volume of the currently visible (filtered) sessions.
    // bytesSent = data delivered to the client (download), bytesReceived = data received from the client (upload).
    val totalDownloadBytes = remember(filteredLogs) { filteredLogs.sumOf { it.bytesSent } }
    val totalUploadBytes = remember(filteredLogs) { filteredLogs.sumOf { it.bytesReceived } }
    val totalVolumeBytes = totalDownloadBytes + totalUploadBytes
    val allSessionsTotalBytes = remember(sessionLogs) { sessionLogs.sumOf { it.bytesSent + it.bytesReceived } }

    // Persistent cumulative volume for the current month (from file ledger)
    val cumulativeDownload = remember(volumeCheckpoints) { volumeCheckpoints.sumOf { it.downloadBytes } }
    val cumulativeUpload = remember(volumeCheckpoints) { volumeCheckpoints.sumOf { it.uploadBytes } }
    val cumulativeTotal = cumulativeDownload + cumulativeUpload
    val cumulativeSessions = remember(volumeCheckpoints) { volumeCheckpoints.sumOf { it.sessionCount } }

    val timestampFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // Traffic Volume Summary Header (Clickable for cumulative details)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { volumeExpanded = !volumeExpanded }
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (volumeExpanded) NetworkUtils.formatBytes(cumulativeTotal)
                               else NetworkUtils.formatBytes(totalVolumeBytes),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 17.sp
                        )
                    )
                    Text(
                        text = if (volumeExpanded) "Cumulative this month • tap to collapse"
                               else if (filteredLogs.size < sessionLogs.size) "Filtered volume (${NetworkUtils.formatBytes(allSessionsTotalBytes)} total) • ${filteredLogs.size} of ${sessionLogs.size} req"
                               else "Current volume • ${sessionLogs.size} requests",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (volumeExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (sessionLogs.isNotEmpty()) {
                IconButton(
                    onClick = onClearLogs,
                    modifier = Modifier.testTag("clear_logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Logs",
                        tint = NaturalRedError
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Upload / Download breakdown of the visible sessions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TrafficVolumeChip(
                label = "Upload",
                value = NetworkUtils.formatBytes(totalUploadBytes),
                tint = NaturalOrangeUpload,
                modifier = Modifier.weight(1f)
            )
            TrafficVolumeChip(
                label = "Download",
                value = NetworkUtils.formatBytes(totalDownloadBytes),
                tint = NaturalGreenSuccess,
                modifier = Modifier.weight(1f)
            )
        }

        // Expanded monthly volume details
        if (volumeExpanded) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "↑ ${NetworkUtils.formatBytes(cumulativeUpload)}   ↓ ${NetworkUtils.formatBytes(cumulativeDownload)}   •   $cumulativeSessions requests this month",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(6.dp))
                    if (volumeCheckpoints.isEmpty()) {
                        Text(
                            text = "No checkpoints recorded yet this month",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    } else {
                        volumeCheckpoints.reversed().take(30).forEach { cp ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = timestampFormat.format(Date(cp.timestamp)),
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                )
                                Text(
                                    text = "${NetworkUtils.formatBytes(cp.downloadBytes + cp.uploadBytes)} • ${cp.sessionCount} req",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search host, client IP, or method...", fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Pills Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf("ALL", "HTTPS", "HTTP", "ERRORS")
            filters.forEach { filter ->
                val isSelected = selectedMethodFilter == filter
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { selectedMethodFilter = filter }
                ) {
                    Text(
                        text = filter,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Logs List
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (sessionLogs.isEmpty()) "No proxy traffic recorded yet" else "No matching requests found",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Text(
                        text = if (sessionLogs.isEmpty()) "Start proxy and connect Windows PC to see live requests" else "Try clearing your search filter",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredLogs, key = { it.id }) { log ->
                    SessionLogItem(
                        log = log,
                        onClick = { selectedLogForDetails = log }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(20.dp))
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
private fun TrafficVolumeChip(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(tint, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    )
                )
            }
        }
    }
}
