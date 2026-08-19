package com.example.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.InterfaceType
import com.example.model.NetworkInterfaceInfo
import com.example.ui.theme.NaturalGreenSuccess
import com.example.ui.theme.NaturalGreenTint
import com.example.ui.theme.NaturalHeroContainer
import com.example.ui.theme.NaturalOchrePrimary
import com.example.ui.theme.NaturalSurface
import com.example.ui.theme.NaturalTextPrimary
import com.example.ui.theme.NaturalTextSecondary

@Composable
fun WindowsSetupGuideDialog(
    port: Int,
    interfaces: List<NetworkInterfaceInfo>,
    onDismiss: () -> Unit,
    onCopy: (label: String, text: String) -> Unit,
    onOpenTetherSettings: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("🔌 USB Tethering", "📡 Wi-Fi Hotspot", "💻 Windows Settings", "🛠️ CLI & Git")

    val hotspotIp = remember(interfaces) {
        interfaces.find { it.type == InterfaceType.WIFI_HOTSPOT }?.ipAddress ?: "192.168.43.1"
    }

    val usbIp = remember(interfaces) {
        interfaces.find { it.type == InterfaceType.USB_TETHERING }?.ipAddress
            ?: interfaces.find { it.type == InterfaceType.WIFI }?.ipAddress
            ?: "192.168.42.129"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 16.dp)
                .testTag("windows_setup_guide_dialog"),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
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
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Windows PC Proxy Setup",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp
                            )
                        )
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Selector
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    divider = {},
                    edgePadding = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (selectedTab) {
                        0 -> UsbTetheringGuide(
                            suggestedIp = usbIp,
                            port = port,
                            onCopy = onCopy,
                            onOpenTetherSettings = onOpenTetherSettings
                        )
                        1 -> WifiHotspotGuide(
                            hotspotIp = hotspotIp,
                            port = port,
                            onCopy = onCopy,
                            onOpenHotspotSettings = onOpenTetherSettings
                        )
                        2 -> WindowsSettingsGuide(
                            suggestedIp = usbIp,
                            port = port,
                            onCopy = onCopy
                        )
                        3 -> CliAndGitGuide(
                            suggestedIp = usbIp,
                            port = port,
                            onCopy = onCopy
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WifiHotspotGuide(
    hotspotIp: String,
    port: Int,
    onCopy: (label: String, text: String) -> Unit,
    onOpenHotspotSettings: () -> Unit
) {
    val curlCmd = "curl -x http://$hotspotIp:$port https://ipinfo.io"

    Column {
        Surface(
            color = NaturalGreenTint,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WifiTethering,
                        contentDescription = null,
                        tint = NaturalGreenSuccess,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Wireless Proxy over Wi-Fi Hotspot",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NaturalGreenSuccess
                        )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Share mobile data with your Windows PC over Wi-Fi and route PC traffic directly through this proxy server.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        StepHeader(step = 1, title = "Turn ON Mobile Hotspot on Android:")
        Text(
            text = "Enable Mobile Hotspot in Android settings and connect your Windows PC to this phone's Wi-Fi network.",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onOpenHotspotSettings,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(imageVector = Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Open Hotspot & Tethering Settings", fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))

        StepHeader(step = 2, title = "Phone's Hotspot Gateway IP & Port:")
        Text(
            text = "When connected to this phone's Hotspot, configure Windows proxy to:",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Spacer(modifier = Modifier.height(6.dp))
        CodeSnippetBox(code = "$hotspotIp:$port", onCopy = { onCopy("Hotspot Proxy Address", "$hotspotIp:$port") })

        Spacer(modifier = Modifier.height(14.dp))

        StepHeader(step = 3, title = "Set Windows Proxy:")
        Text(
            text = "Win + I > Network & Internet > Proxy > Manual proxy setup > Address: $hotspotIp, Port: $port.",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )

        Spacer(modifier = Modifier.height(14.dp))

        StepHeader(step = 4, title = "Quick Verification Command on Windows:")
        CodeSnippetBox(code = curlCmd, onCopy = { onCopy("Curl Command", curlCmd) })
    }
}

@Composable
private fun UsbTetheringGuide(
    suggestedIp: String,
    port: Int,
    onCopy: (label: String, text: String) -> Unit,
    onOpenTetherSettings: () -> Unit
) {
    Column {
        StepHeader(step = 1, title = "Enable USB Tethering on Android:")
        Text(
            text = "Connect phone to Windows PC via USB cable, then turn on USB Tethering.",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onOpenTetherSettings,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Open Android Tethering Settings", fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))

        StepHeader(step = 2, title = "Phone's IP Address:")
        Text(
            text = "Use this IP address in Windows Proxy configuration:",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Spacer(modifier = Modifier.height(6.dp))
        CodeSnippetBox(code = "$suggestedIp:$port", onCopy = { onCopy("Proxy Address", "$suggestedIp:$port") })

        Spacer(modifier = Modifier.height(14.dp))

        StepHeader(step = 3, title = "Configure Windows Proxy Settings:")
        Text(
            text = "On Windows: Press Win + I > Network & Internet > Proxy > Manual proxy setup > Address: $suggestedIp, Port: $port.",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
    }
}

@Composable
private fun WindowsSettingsGuide(
    suggestedIp: String,
    port: Int,
    onCopy: (label: String, text: String) -> Unit
) {
    Column {
        Text(
            text = "Step-by-Step for Windows 10 & Windows 11",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        StepHeader(step = 1, title = "Open Windows Proxy Settings")
        Text(
            text = "1. Press Windows Key + I to open Settings.\n2. Click on 'Network & Internet' in the left sidebar.\n3. Click on 'Proxy'.",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )

        Spacer(modifier = Modifier.height(12.dp))

        StepHeader(step = 2, title = "Turn ON Manual Proxy")
        Text(
            text = "Under 'Manual proxy setup', click 'Set up' or toggle 'Use a proxy server' to ON.",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )

        Spacer(modifier = Modifier.height(12.dp))

        StepHeader(step = 3, title = "Enter IP Address & Port")
        CodeSnippetBox(code = "Proxy IP: $suggestedIp\nPort: $port", onCopy = { onCopy("IP & Port", "$suggestedIp:$port") })

        Spacer(modifier = Modifier.height(12.dp))

        StepHeader(step = 4, title = "Click Save")
        Text(
            text = "All browsers (Chrome, Edge, Firefox) and Windows apps will now route their traffic through this proxy.",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
    }
}

@Composable
private fun CliAndGitGuide(
    suggestedIp: String,
    port: Int,
    onCopy: (label: String, text: String) -> Unit
) {
    val psCmd = "\$env:HTTP_PROXY=\"http://$suggestedIp:$port\"; \$env:HTTPS_PROXY=\"http://$suggestedIp:$port\""
    val cmdCmd = "set HTTP_PROXY=http://$suggestedIp:$port & set HTTPS_PROXY=http://$suggestedIp:$port"
    val gitCmd = "git config --global http.proxy http://$suggestedIp:$port"
    val gitUnsetCmd = "git config --global --unset http.proxy"

    Column {
        StepHeader(step = 1, title = "PowerShell Environment Variable:")
        CodeSnippetBox(code = psCmd, onCopy = { onCopy("PowerShell Proxy Env", psCmd) })

        Spacer(modifier = Modifier.height(12.dp))

        StepHeader(step = 2, title = "Command Prompt (CMD):")
        CodeSnippetBox(code = cmdCmd, onCopy = { onCopy("CMD Proxy Env", cmdCmd) })

        Spacer(modifier = Modifier.height(12.dp))

        StepHeader(step = 3, title = "Git Global Proxy:")
        CodeSnippetBox(code = gitCmd, onCopy = { onCopy("Git Proxy", gitCmd) })

        Spacer(modifier = Modifier.height(12.dp))

        StepHeader(step = 4, title = "Unset Git Proxy When Done:")
        CodeSnippetBox(code = gitUnsetCmd, onCopy = { onCopy("Git Unset", gitUnsetCmd) })
    }
}

@Composable
private fun StepHeader(step: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$step",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

@Composable
private fun CodeSnippetBox(
    code: String,
    onCopy: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable { onCopy() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
