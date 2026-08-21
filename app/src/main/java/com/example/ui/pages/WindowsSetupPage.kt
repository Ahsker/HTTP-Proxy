package com.example.ui.pages

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.model.InterfaceType
import com.example.model.NetworkInterfaceInfo
import com.example.network.NetworkUtils
import com.example.ui.components.CodeSnippetBox
import com.example.ui.components.StepHeader
import com.example.ui.theme.NaturalGreenSuccess
import com.example.ui.theme.NaturalGreenTint
import java.util.Calendar

@Composable
fun WindowsSetupPage(
    port: Int,
    interfaces: List<NetworkInterfaceInfo>,
    volumeCheckpoints: List<VolumeCheckpoint>,
    onCopy: (label: String, text: String) -> Unit,
    onOpenTetherSettings: () -> Unit,
    onOpenHotspotSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var guideExpanded by remember { mutableStateOf(true) }
    val tabs = listOf("🔌 USB", "📡 Wi-Fi", "💻 PC", "🛠️ CLI")

    val hotspotIp = remember(interfaces) {
        interfaces.find { it.type == InterfaceType.WIFI_HOTSPOT }?.ipAddress
            ?: interfaces.find { it.type == InterfaceType.WIFI }?.ipAddress
            ?: interfaces.firstOrNull { it.type != InterfaceType.LOOPBACK && it.type != InterfaceType.MOBILE }?.ipAddress
            ?: "192.168.43.1"
    }

    val usbIp = remember(interfaces) {
        interfaces.find { it.type == InterfaceType.USB_TETHERING }?.ipAddress
            ?: interfaces.find { it.type == InterfaceType.WIFI }?.ipAddress
            ?: interfaces.firstOrNull { it.type != InterfaceType.LOOPBACK && it.type != InterfaceType.MOBILE }?.ipAddress
            ?: "192.168.42.129"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Title Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DesktopWindows,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Windows Setup Guide & Report",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp
                    )
                )
                Text(
                    text = "PC proxy setup & monthly data usage",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )
            }
        }

        // Quick System Settings Buttons (Both USB & Wi-Fi Hotspot)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onOpenTetherSettings,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("setup_open_usb_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Cable,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "USB Tethering",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick = onOpenHotspotSettings,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("setup_open_wifi_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WifiTethering,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Wi-Fi Hotspot",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Tabs (collapsible)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { guideExpanded = !guideExpanded }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Windows Setup Guide",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = if (guideExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (guideExpanded) "Collapse guide" else "Expand guide",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (guideExpanded) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = MaterialTheme.colorScheme.primary,
                                height = 3.dp
                            )
                        },
                        divider = {}
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        ),
                                        maxLines = 1
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Tab Content
        if (guideExpanded) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    when (selectedTab) {
                        0 -> UsbGuide(usbIp = usbIp, port = port, onCopy = onCopy, onOpenTetherSettings = onOpenTetherSettings)
                        1 -> HotspotGuide(hotspotIp = hotspotIp, port = port, onCopy = onCopy, onOpenHotspotSettings = onOpenHotspotSettings)
                        2 -> WindowsProxySettingsGuide(suggestedIp = usbIp, port = port, onCopy = onCopy)
                        3 -> CliGuide(suggestedIp = usbIp, port = port, onCopy = onCopy)
                    }
                }
            }
        }

        // Monthly Usage Report (collapsible, below the setup guide)
        MonthlyUsageReportSection(checkpoints = volumeCheckpoints)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun MonthlyUsageReportSection(checkpoints: List<VolumeCheckpoint>) {
    var expanded by remember { mutableStateOf(false) }

    val monthTotal = remember(checkpoints) {
        checkpoints.sumOf { it.downloadBytes + it.uploadBytes }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Monthly Usage Report",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${NetworkUtils.formatBytes(monthTotal)} recorded this month",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse report" else "Expand report",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                MonthlyCalendarGrid(checkpoints = checkpoints)
            }
        }
    }
}

@Composable
private fun MonthlyCalendarGrid(checkpoints: List<VolumeCheckpoint>) {
    val cal = Calendar.getInstance()
    val today = cal.get(Calendar.DAY_OF_MONTH)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    // Aggregate checkpoints per day-of-month
    val dailyTotals: Map<Int, Long> = remember(checkpoints) {
        val c = Calendar.getInstance()
        checkpoints.groupBy { cp ->
            c.timeInMillis = cp.timestamp
            c.get(Calendar.DAY_OF_MONTH)
        }.mapValues { (_, list) -> list.sumOf { it.downloadBytes + it.uploadBytes } }
    }

    // Leading empty cells so day 1 lands on its weekday column (Sunday-first)
    cal.set(Calendar.DAY_OF_MONTH, 1)
    val leadingBlanks = cal.get(Calendar.DAY_OF_WEEK) - 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Weekday header
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { label ->
                Box(
                    modifier = Modifier.weight(1f).padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Day cells
        var dayCounter = 1
        val totalCells = leadingBlanks + daysInMonth
        val rowCount = (totalCells + 6) / 7
        for (row in 0 until rowCount) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    if (cellIndex < leadingBlanks || dayCounter > daysInMonth) {
                        Box(modifier = Modifier.weight(1f).height(54.dp))
                    } else {
                        val day = dayCounter
                        CalendarDayCell(
                            day = day,
                            bytes = dailyTotals[day] ?: 0L,
                            isToday = day == today,
                            modifier = Modifier.weight(1f)
                        )
                        dayCounter++
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: Int,
    bytes: Long,
    isToday: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isToday) NaturalGreenTint else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isToday) NaturalGreenSuccess else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        modifier = modifier.padding(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$day",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    color = if (isToday) NaturalGreenSuccess else MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            if (isToday) {
                // Today's figure is not final — checkpoints are batched
                Text(
                    text = "Pending",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = NaturalGreenSuccess
                    )
                )
            } else if (bytes > 0) {
                Text(
                    text = formatTrafficMb(bytes),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            } else {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}

private fun formatTrafficMb(bytes: Long): String {
    return if (bytes >= 1_048_576L) {
        String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
    } else {
        String.format(java.util.Locale.US, "%d KB", bytes / 1024)
    }
}

@Composable
private fun UsbGuide(
    usbIp: String,
    port: Int,
    onCopy: (label: String, text: String) -> Unit,
    onOpenTetherSettings: () -> Unit
) {
    Column {
        StepHeader(step = 1, title = "Plug Phone into Windows PC via USB Cable")
        Text(
            text = "Connect a standard USB-C or Micro-USB cable between your phone and your Windows PC.",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )

        Spacer(modifier = Modifier.height(14.dp))

        StepHeader(step = 2, title = "Enable USB Tethering on Phone")
        Text(
            text = "Turn on 'USB Tethering' in Android Network & Internet settings.",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Button(
            onClick = onOpenTetherSettings,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Cable, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open USB Tethering Settings", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))

        StepHeader(step = 3, title = "Configure Windows Proxy")
        Text(
            text = "Open Windows Settings > Network & Internet > Proxy > Manual proxy setup. Enter:",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Spacer(modifier = Modifier.height(6.dp))
        CodeSnippetBox(code = "$usbIp:$port", onCopy = { onCopy("USB Proxy Address", "$usbIp:$port") })
    }
}

@Composable
private fun HotspotGuide(
    hotspotIp: String,
    port: Int,
    onCopy: (label: String, text: String) -> Unit,
    onOpenHotspotSettings: () -> Unit
) {
    Column {
        StepHeader(step = 1, title = "Turn on Wi-Fi Hotspot on this Phone")
        Text(
            text = "Enable portable Wi-Fi Hotspot and note your Wi-Fi name and password.",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Button(
            onClick = onOpenHotspotSettings,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.WifiTethering, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open Wi-Fi Hotspot Settings", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))

        StepHeader(step = 2, title = "Connect up to 3 PCs/Devices to Hotspot")
        Text(
            text = "On your Windows PC (or User 1, User 2, User 3 laptops), connect to the phone's Wi-Fi network.",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )

        Spacer(modifier = Modifier.height(14.dp))

        StepHeader(step = 3, title = "Set Hotspot Proxy on Each PC:")
        Text(
            text = "In Windows Proxy Settings on each connected PC, enter this gateway address:",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Spacer(modifier = Modifier.height(6.dp))
        CodeSnippetBox(code = "$hotspotIp:$port", onCopy = { onCopy("Hotspot Proxy Address", "$hotspotIp:$port") })
    }
}

@Composable
private fun WindowsProxySettingsGuide(
    suggestedIp: String,
    port: Int,
    onCopy: (label: String, text: String) -> Unit
) {
    Column {
        StepHeader(step = 1, title = "Open Windows Settings")
        Text(
            text = "Press Win + I or open Windows Settings > Network & Internet > Proxy.",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )

        Spacer(modifier = Modifier.height(12.dp))

        StepHeader(step = 2, title = "Turn ON 'Use a proxy server'")
        Text(
            text = "Under Manual proxy setup, click 'Set up' or toggle 'Use a proxy server' to ON.",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )

        Spacer(modifier = Modifier.height(12.dp))

        StepHeader(step = 3, title = "Enter Proxy IP & Port")
        CodeSnippetBox(code = "IP: $suggestedIp\nPort: $port", onCopy = { onCopy("Proxy Endpoint", "$suggestedIp:$port") })

        Spacer(modifier = Modifier.height(12.dp))

        StepHeader(step = 4, title = "Save & Browse")
        Text(
            text = "Click Save. All Windows browser traffic (Edge, Chrome, Firefox) will now route through Relay Proxy!",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
    }
}

@Composable
private fun CliGuide(
    suggestedIp: String,
    port: Int,
    onCopy: (label: String, text: String) -> Unit
) {
    val psCmd = "\$env:HTTP_PROXY=\"http://$suggestedIp:$port\"; \$env:HTTPS_PROXY=\"http://$suggestedIp:$port\""
    val cmdCmd = "set HTTP_PROXY=http://$suggestedIp:$port & set HTTPS_PROXY=http://$suggestedIp:$port"
    val gitCmd = "git config --global http.proxy http://$suggestedIp:$port"
    val gitUnsetCmd = "git config --global --unset http.proxy"

    Column {
        StepHeader(step = 1, title = "PowerShell Environment Variable")
        CodeSnippetBox(code = psCmd, onCopy = { onCopy("PowerShell Command", psCmd) })

        Spacer(modifier = Modifier.height(14.dp))

        StepHeader(step = 2, title = "Command Prompt (CMD)")
        CodeSnippetBox(code = cmdCmd, onCopy = { onCopy("CMD Command", cmdCmd) })

        Spacer(modifier = Modifier.height(14.dp))

        StepHeader(step = 3, title = "Git Proxy Configuration")
        CodeSnippetBox(code = gitCmd, onCopy = { onCopy("Git Proxy Command", gitCmd) })

        Spacer(modifier = Modifier.height(10.dp))
        Text(text = "To disable Git proxy later:", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
        Spacer(modifier = Modifier.height(4.dp))
        CodeSnippetBox(code = gitUnsetCmd, onCopy = { onCopy("Git Unset Command", gitUnsetCmd) })
    }
}
