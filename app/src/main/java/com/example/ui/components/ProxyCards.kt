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
    onToggleServer: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = status == ServerStatus.RUNNING
    val isStarting = status == ServerStatus.STARTING

    // Pulsing animation for running state
    val infiniteTransition = rememberInfiniteTransition(label = "hero_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val heroBgColor by animateColorAsState(
        targetValue = when (status) {
            ServerStatus.RUNNING -> MaterialTheme.colorScheme.primaryContainer
            ServerStatus.STARTING -> NaturalOrangeTint
            ServerStatus.ERROR -> NaturalRedTint
            ServerStatus.STOPPED -> MaterialTheme.colorScheme.primaryContainer
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
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = heroBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (isRunning) androidx.compose.foundation.BorderStroke(2.dp, NaturalGreenSuccess.copy(alpha = 0.4f)) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Status Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = when (status) {
                    ServerStatus.RUNNING -> NaturalGreenTint
                    ServerStatus.STARTING -> NaturalOrangeTint
                    ServerStatus.ERROR -> NaturalRedTint
                    ServerStatus.STOPPED -> MaterialTheme.colorScheme.surfaceVariant
                },
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    when (status) {
                        ServerStatus.RUNNING -> NaturalGreenSuccess.copy(alpha = 0.5f)
                        ServerStatus.STARTING -> NaturalOrangeUpload.copy(alpha = 0.5f)
                        ServerStatus.ERROR -> NaturalRedError.copy(alpha = 0.5f)
                        ServerStatus.STOPPED -> MaterialTheme.colorScheme.outline
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (status) {
                            ServerStatus.RUNNING -> "ONLINE • ROUTING TRAFFIC"
                            ServerStatus.STARTING -> "STARTING SERVER..."
                            ServerStatus.ERROR -> "SERVER ERROR"
                            ServerStatus.STOPPED -> "OFFLINE • PROXY STOPPED"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = when (status) {
                                ServerStatus.RUNNING -> NaturalGreenSuccess
                                ServerStatus.STARTING -> NaturalOrangeUpload
                                ServerStatus.ERROR -> NaturalRedError
                                ServerStatus.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            letterSpacing = 0.8.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Large Circular Power Switch with Obvious Visual States
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp)
            ) {
                // Pulsing glow rings when active
                if (isRunning) {
                    Box(
                        modifier = Modifier
                            .size(116.dp)
                            .scale(pulseScale)
                            .background(NaturalGreenSuccess.copy(alpha = pulseAlpha), CircleShape)
                    )
                }

                // Main power circle button
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(
                            when (status) {
                                ServerStatus.RUNNING -> NaturalGreenSuccess
                                ServerStatus.STARTING -> NaturalOrangeUpload
                                ServerStatus.ERROR -> NaturalRedError
                                ServerStatus.STOPPED -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .border(
                            width = if (isRunning) 3.dp else 1.dp,
                            color = if (isRunning) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable { onToggleServer() }
                        .testTag("hero_power_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isStarting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = if (isRunning) "Stop Proxy" else "Start Proxy",
                            tint = if (isRunning || status == ServerStatus.ERROR) Color.White else NaturalTextSecondary,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Running time or Start instruction
            if (isRunning) {
                Text(
                    text = "Running for $formattedUptime",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp
                    )
                )
                Text(
                    text = "Ready for USB Tethering, Wi-Fi Hotspot & ADB",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )
            } else {
                Text(
                    text = "HTTP & HTTPS Proxy Server",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 17.sp
                    )
                )
                Text(
                    text = "Share phone connection with Windows PC",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Big Obvious START / STOP Action Button
            Button(
                onClick = onToggleServer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("main_toggle_server_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) NaturalRedError else MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRunning) "STOP SERVER" else "START PROXY SERVER",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 15.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Inset Host IP & Port box with quick edit action
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onOpenSettings() }
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "HOST IP ADDRESS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                letterSpacing = 0.8.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (config.host == "0.0.0.0") suggestedIp else config.host,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outline)
                    )

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "PORT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                letterSpacing = 0.8.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${config.port}",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp
                            )
                        )
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Settings",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
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
