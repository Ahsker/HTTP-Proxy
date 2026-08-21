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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ClientSlotStats
import com.example.model.InterfaceType
import com.example.model.NetworkInterfaceInfo
import com.example.model.ProxyConfig
import com.example.model.ServerStatus
import com.example.model.SessionLog
import com.example.model.TrafficStats
import com.example.ui.components.HotspotMultiUserCard
import com.example.ui.components.IpAddressesCard
import com.example.ui.components.NaturalHeroCard
import com.example.ui.components.NaturalRecentActivityCard
import com.example.ui.components.NaturalStatsGrid
import com.example.ui.components.NaturalTrafficGraphCard
import com.example.ui.theme.NaturalGreenSuccess
import com.example.ui.theme.NaturalGreenTint
import com.example.ui.theme.NaturalRedError
import com.example.ui.theme.NaturalRedTint

@Composable
fun HotspotMultiUserPage(
    serverStatus: ServerStatus,
    errorMessage: String?,
    config: ProxyConfig,
    uptimeSeconds: Long,
    trafficStats: TrafficStats,
    activeConnections: Int,
    connectedClients: Set<String>,
    clientSlots: List<ClientSlotStats>,
    sessionLogs: List<SessionLog>,
    networkInterfaces: List<NetworkInterfaceInfo>,
    onToggleServer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHotspotSettings: () -> Unit,
    onOpenSessionLogs: () -> Unit,
    onOpenWindowsGuide: () -> Unit,
    onCopy: (label: String, text: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val hotspotIntf = remember(networkInterfaces) {
        networkInterfaces.find { it.type == InterfaceType.WIFI_HOTSPOT }
    }
    val isHotspotActive = hotspotIntf != null
    val hotspotGatewayIp = hotspotIntf?.ipAddress ?: "192.168.43.1"
    val activeHotspotCount = remember(clientSlots) {
        clientSlots.count { it.isConnected }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(2.dp))

        // Hotspot Status Notice Banner
        if (!isHotspotActive) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenHotspotSettings() }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiTethering,
                        contentDescription = "Hotspot Inactive",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mobile Hotspot is OFF",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tap here to turn ON Mobile Hotspot in Android Settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = NaturalGreenTint,
                border = androidx.compose.foundation.BorderStroke(1.dp, NaturalGreenSuccess.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Hotspot Active",
                        tint = NaturalGreenSuccess,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Hotspot Active on ${hotspotIntf.name} ($hotspotGatewayIp)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = NaturalGreenSuccess
                    )
                }
            }
        }

        // Error message if any
        if (serverStatus == ServerStatus.ERROR && errorMessage != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = NaturalRedTint,
                border = androidx.compose.foundation.BorderStroke(1.dp, NaturalRedError.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Error: $errorMessage",
                    color = NaturalRedError,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        // 1. Hero Active State Card with integrated Waveform Graph, IP/Port & Sent/Received stats
        NaturalHeroCard(
            status = serverStatus,
            config = config,
            suggestedIp = hotspotGatewayIp,
            uptimeSeconds = uptimeSeconds,
            stats = trafficStats,
            onToggleServer = onToggleServer,
            onOpenSettings = onOpenSettings
        )

        // 2. Dedicated Multi-User Hotspot Card with 3 Sub-Tabs
        HotspotMultiUserCard(
            clientSlots = clientSlots,
            gatewayIp = hotspotGatewayIp,
            port = config.port,
            onCopy = onCopy,
            onOpenHotspotSettings = onOpenHotspotSettings
        )

        // 3. Available IP Addresses Card
        IpAddressesCard(
            interfaces = networkInterfaces,
            port = config.port,
            onCopy = onCopy,
            onOpenHotspotSettings = onOpenHotspotSettings
        )

        // 6. Recent Activity Card
        NaturalRecentActivityCard(
            sessionLogsCount = sessionLogs.size,
            activeConnections = activeConnections,
            connectedDevicesCount = connectedClients.size,
            onOpenSessionLogs = onOpenSessionLogs
        )

        // 7. Windows PC Wi-Fi Hotspot Setup Guide Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable { onOpenWindowsGuide() },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DesktopWindows,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Hotspot PC Configuration Guide",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            )
                        )
                        Text(
                            text = "Connect up to 3 PCs/devices via Wi-Fi Hotspot",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
