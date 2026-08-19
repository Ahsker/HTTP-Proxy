package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.InterfaceType
import com.example.model.NetworkInterfaceInfo
import com.example.model.ProxyConfig
import com.example.model.ServerStatus
import com.example.model.TrafficStats
import com.example.network.NetworkUtils
import com.example.ui.theme.DarkNaturalOchre
import com.example.ui.theme.NaturalBorder
import com.example.ui.theme.NaturalGreenBright
import com.example.ui.theme.NaturalGreenSuccess
import com.example.ui.theme.NaturalGreenTint
import com.example.ui.theme.NaturalHeroContainer
import com.example.ui.theme.NaturalOchreLight
import com.example.ui.theme.NaturalOchrePrimary
import com.example.ui.theme.NaturalOrangeTint
import com.example.ui.theme.NaturalOrangeUpload
import com.example.ui.theme.NaturalRedError
import com.example.ui.theme.NaturalRedTint
import com.example.ui.theme.NaturalSurface
import com.example.ui.theme.NaturalSurfaceElevated
import com.example.ui.theme.NaturalTextDark
import com.example.ui.theme.NaturalTextMuted
import com.example.ui.theme.NaturalTextPrimary
import com.example.ui.theme.NaturalTextSecondary

@Composable
fun NaturalHeroCard(
    status: ServerStatus,
    config: ProxyConfig,
    suggestedIp: String,
    uptimeSeconds: Long,
    stats: TrafficStats,
    onToggleServer: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = status == ServerStatus.RUNNING
    val isStarting = status == ServerStatus.STARTING

    val heroBgColor by animateColorAsState(
        targetValue = when (status) {
            ServerStatus.RUNNING -> MaterialTheme.colorScheme.surface
            ServerStatus.STARTING -> NaturalOrangeTint
            ServerStatus.ERROR -> NaturalRedTint
            ServerStatus.STOPPED -> MaterialTheme.colorScheme.surface
        },
        label = "heroBg"
    )

    val formattedUptime = remember(uptimeSeconds) {
        val hrs = uptimeSeconds / 3600
        val mins = (uptimeSeconds % 3600) / 60
        val secs = uptimeSeconds % 60
        if (hrs > 0) {
            String.format("%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("natural_hero_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = heroBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (isRunning) androidx.compose.foundation.BorderStroke(1.5.dp, NaturalGreenSuccess.copy(alpha = 0.5f)) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Status Bar with Status Pill and Compact Start/Stop Toggle Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = when (status) {
                        ServerStatus.RUNNING -> NaturalGreenTint
                        ServerStatus.STARTING -> NaturalOrangeTint
                        ServerStatus.ERROR -> NaturalRedTint
                        ServerStatus.STOPPED -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when (status) {
                            ServerStatus.RUNNING -> NaturalGreenSuccess.copy(alpha = 0.4f)
                            ServerStatus.STARTING -> NaturalOrangeUpload.copy(alpha = 0.4f)
                            ServerStatus.ERROR -> NaturalRedError.copy(alpha = 0.4f)
                            ServerStatus.STOPPED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(
                                    when (status) {
                                        ServerStatus.RUNNING -> NaturalGreenSuccess
                                        ServerStatus.STARTING -> NaturalOrangeUpload
                                        ServerStatus.ERROR -> NaturalRedError
                                        ServerStatus.STOPPED -> NaturalTextMuted
                                    },
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (status) {
                                ServerStatus.RUNNING -> "ONLINE • $formattedUptime"
                                ServerStatus.STARTING -> "STARTING..."
                                ServerStatus.ERROR -> "ERROR"
                                ServerStatus.STOPPED -> "OFFLINE"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = when (status) {
                                    ServerStatus.RUNNING -> NaturalGreenSuccess
                                    ServerStatus.STARTING -> NaturalOrangeUpload
                                    ServerStatus.ERROR -> NaturalRedError
                                    ServerStatus.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                letterSpacing = 0.5.sp,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // Compact Toggle Server Button
                Button(
                    onClick = onToggleServer,
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("main_toggle_server_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) NaturalRedError else MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isStarting) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Starting",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        } else {
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isRunning) "STOP" else "START",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Embedded Traffic Waveform Graph replacing the old on/off button UI
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hero_traffic_waveform")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    TrafficGraph(
                        history = stats.history,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(82.dp)
                    )

                    // Compact Live Speed overlay tags
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "▲ ${NetworkUtils.formatSpeed(stats.currentUpBps)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = NaturalOrangeUpload,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        Text(
                            text = "▼ ${NetworkUtils.formatSpeed(stats.currentDownBps)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = NaturalGreenSuccess,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Inset Box containing IP, Port, and Data Sent / Received directly underneath (no upload/download icons)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // Row 1: IP Address and Port with Edit Action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // IP Address
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenSettings() }
                        ) {
                            Text(
                                text = "HOST IP",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.6.sp
                                )
                            )
                            Text(
                                text = if (config.host == "0.0.0.0") suggestedIp else config.host,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp
                                ),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        Box(
                            modifier = Modifier
                                .height(26.dp)
                                .width(1.dp)
                                .padding(horizontal = 4.dp)
                                .background(MaterialTheme.colorScheme.outline)
                        )

                        // Port
                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clickable { onOpenSettings() }
                        ) {
                            Text(
                                text = "PORT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.6.sp
                                )
                            )
                            Text(
                                text = "${config.port}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 14.sp
                                )
                            )
                        }

                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .size(28.dp)
                                .padding(start = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Settings",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 0.8.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )

                    // Row 2: Data Sent and Data Received shifted directly under IP & Port (no icons, compact)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Data Sent
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DATA SENT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NaturalOrangeUpload,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.6.sp
                                )
                            )
                            Text(
                                text = NetworkUtils.formatBytes(stats.totalUpBytes),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp
                                ),
                                maxLines = 1
                            )
                        }

                        Box(
                            modifier = Modifier
                                .height(24.dp)
                                .width(1.dp)
                                .padding(horizontal = 4.dp)
                                .background(MaterialTheme.colorScheme.outline)
                        )

                        // Data Received
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                        ) {
                            Text(
                                text = "DATA RECEIVED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NaturalGreenSuccess,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.6.sp
                                )
                            )
                            Text(
                                text = NetworkUtils.formatBytes(stats.totalDownBytes),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NaturalStatsGrid(
    stats: TrafficStats,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Data Sent Card
        Card(
            modifier = Modifier
                .weight(1f)
                .testTag("stats_data_sent_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = "Data Sent",
                        tint = NaturalOrangeUpload,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Data Sent",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )

                Text(
                    text = NetworkUtils.formatBytes(stats.totalUpBytes),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp
                    )
                )

                Text(
                    text = "Speed: ${NetworkUtils.formatSpeed(stats.currentUpBps)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = NaturalTextMuted,
                        fontSize = 11.sp
                    )
                )
            }
        }

        // Data Received Card
        Card(
            modifier = Modifier
                .weight(1f)
                .testTag("stats_data_received_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(NaturalGreenTint, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Data Received",
                        tint = NaturalGreenSuccess,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Data Received",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )

                Text(
                    text = NetworkUtils.formatBytes(stats.totalDownBytes),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp
                    )
                )

                Text(
                    text = "Speed: ${NetworkUtils.formatSpeed(stats.currentDownBps)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = NaturalTextMuted,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
fun NaturalTrafficGraphCard(
    stats: TrafficStats,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("traffic_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Traffic Waveform",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(NaturalOrangeUpload, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sent", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.size(8.dp).background(NaturalGreenSuccess, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Received", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            TrafficGraph(
                history = stats.history,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            )
        }
    }
}

@Composable
fun IpAddressesCard(
    interfaces: List<NetworkInterfaceInfo>,
    port: Int,
    onCopy: (label: String, text: String) -> Unit,
    onOpenHotspotSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ip_addresses_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lan,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Available IP Addresses",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${interfaces.size} found",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (interfaces.isEmpty()) {
                Text(
                    text = "No active network interfaces detected",
                    style = MaterialTheme.typography.bodySmall.copy(color = NaturalTextMuted),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                interfaces.forEachIndexed { index, intf ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                    IpAddressItem(
                        item = intf,
                        port = port,
                        onCopy = onCopy
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Hotspot shortcut button inside IP card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenHotspotSettings() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WifiTethering,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sharing Wi-Fi Hotspot to PC? Open Hotspot settings",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IpAddressItem(
    item: NetworkInterfaceInfo,
    port: Int,
    onCopy: (label: String, text: String) -> Unit
) {
    val (icon, badgeBg, badgeColor) = when (item.type) {
        InterfaceType.USB_TETHERING -> Triple(Icons.Default.Usb, NaturalHeroContainer, NaturalOchrePrimary)
        InterfaceType.WIFI_HOTSPOT -> Triple(Icons.Default.WifiTethering, NaturalGreenTint, NaturalGreenSuccess)
        InterfaceType.WIFI -> Triple(Icons.Default.Wifi, NaturalGreenTint, NaturalGreenSuccess)
        InterfaceType.LOOPBACK -> Triple(Icons.Default.PhoneAndroid, NaturalSurfaceElevated, NaturalTextDark)
        InterfaceType.MOBILE -> Triple(Icons.Default.Router, NaturalHeroContainer, NaturalOchreLight)
        else -> Triple(Icons.Default.Lan, NaturalSurfaceElevated, NaturalTextSecondary)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCopy("${item.ipAddress}:$port", item.ipAddress) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(badgeBg, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = item.ipAddress,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    )
                )
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
            }
        }

        IconButton(
            onClick = { onCopy("${item.ipAddress}:$port", item.ipAddress) },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy IP",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun NaturalRecentActivityCard(
    sessionLogsCount: Int,
    activeConnections: Int,
    connectedDevicesCount: Int,
    onOpenSessionLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable { onOpenSessionLogs() }
            .testTag("recent_activity_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT ACTIVITY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 1.sp,
                        fontSize = 11.sp
                    )
                )

                Text(
                    text = "$activeConnections Active",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = if (activeConnections > 0) NaturalGreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Total Recorded Requests:", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                Text(
                    "$sessionLogsCount",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Connected Device IPs:", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                Text(
                    "$connectedDevicesCount",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    }
}
