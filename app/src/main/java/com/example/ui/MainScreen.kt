package com.example.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ControlMode
import com.example.model.InterfaceType
import com.example.model.ServerStatus
import com.example.model.ThemeMode
import com.example.ui.components.ControlModeSegmentedSwitch
import com.example.ui.components.HotspotMultiUserCard
import com.example.ui.components.IpAddressesCard
import com.example.ui.components.NaturalHeroCard
import com.example.ui.components.NaturalRecentActivityCard
import com.example.ui.components.NaturalStatsGrid
import com.example.ui.components.NaturalTrafficGraphCard
import com.example.ui.components.ProxySettingsDialog
import com.example.ui.components.SessionLogsDialog
import com.example.ui.components.TestProxyDialog
import com.example.ui.components.UsbSingleUserCard
import com.example.ui.components.WindowsSetupGuideDialog
import com.example.ui.theme.NaturalGreenSuccess
import com.example.ui.theme.NaturalRedError
import com.example.ui.theme.NaturalRedTint
import com.example.ui.theme.NaturalTextMuted
import com.example.ui.theme.NaturalTextPrimary
import com.example.ui.theme.NaturalTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: ProxyViewModel) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val controlMode by viewModel.controlMode.collectAsStateWithLifecycle()
    val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val trafficStats by viewModel.trafficStats.collectAsStateWithLifecycle()
    val activeConnections by viewModel.activeConnectionsCount.collectAsStateWithLifecycle()
    val connectedClients by viewModel.connectedClients.collectAsStateWithLifecycle()
    val clientSlots by viewModel.clientSlots.collectAsStateWithLifecycle()
    val sessionLogs by viewModel.sessionLogs.collectAsStateWithLifecycle()
    val networkInterfaces by viewModel.networkInterfaces.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTesting.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val uptimeSeconds by viewModel.uptimeSeconds.collectAsStateWithLifecycle()

    var currentNavTab by remember { mutableIntStateOf(0) } // 0: Control, 1: Logs, 2: Setup

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showWindowsGuideDialog by remember { mutableStateOf(false) }
    var showSessionLogsDialog by remember { mutableStateOf(false) }
    var showTestDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    // Request notification permission for Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = {}
        )
        LaunchedEffect(Unit) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val suggestedIp = remember(networkInterfaces) {
        networkInterfaces.find { it.type == InterfaceType.USB_TETHERING }?.ipAddress
            ?: networkInterfaces.find { it.type == InterfaceType.WIFI_HOTSPOT }?.ipAddress
            ?: networkInterfaces.find { it.type == InterfaceType.WIFI }?.ipAddress
            ?: "192.168.42.129"
    }

    val hotspotGatewayIp = remember(networkInterfaces) {
        networkInterfaces.find { it.type == InterfaceType.WIFI_HOTSPOT }?.ipAddress ?: "192.168.43.1"
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Router,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Relay Proxy",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 20.sp,
                                letterSpacing = (-0.5).sp
                            )
                        )
                    }
                },
                actions = {
                    // Day / Night Display Toggle Button
                    IconButton(
                        onClick = { viewModel.toggleDayNight() },
                        modifier = Modifier.testTag("day_night_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (themeMode == ThemeMode.DARK) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (themeMode == ThemeMode.DARK) "Switch to Day Mode" else "Switch to Night Mode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Setup & Info Button
                    IconButton(
                        onClick = { showWindowsGuideDialog = true },
                        modifier = Modifier.testTag("open_guide_icon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "PC Setup Guide",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Refresh Interfaces Button
                    IconButton(
                        onClick = { viewModel.refreshNetworkInterfaces() },
                        modifier = Modifier.testTag("refresh_interfaces_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Interfaces",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // More Options Dropdown
                    Box {
                        IconButton(
                            onClick = { showOptionsMenu = true },
                            modifier = Modifier.testTag("more_options_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Proxy Settings", color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = {
                                    Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    showSettingsDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Test Connection", color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = {
                                    Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = NaturalGreenSuccess)
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    showTestDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Reset Traffic Stats", color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = {
                                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    viewModel.resetStats()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Open Wi-Fi Hotspot Settings", color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = {
                                    Icon(Icons.Default.WifiTethering, contentDescription = null, tint = NaturalGreenSuccess)
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    viewModel.openHotspotSettings()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Open USB Tethering Settings", color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = {
                                    Icon(Icons.Default.Cable, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    viewModel.openTetheringSettings()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NaturalBottomNavBar(
                selectedTab = currentNavTab,
                onTabSelect = { tabIndex ->
                    currentNavTab = tabIndex
                    when (tabIndex) {
                        1 -> showSessionLogsDialog = true
                        2 -> showWindowsGuideDialog = true
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // Error banner if any
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

            // 1. Hero Active State Card with Obvious Start / Stop Button & Indicator
            NaturalHeroCard(
                status = serverStatus,
                config = config,
                suggestedIp = suggestedIp,
                uptimeSeconds = uptimeSeconds,
                onToggleServer = { viewModel.toggleServer() },
                onOpenSettings = { showSettingsDialog = true }
            )

            // 2. Control Mode Switcher: USB (Single PC) vs Hotspot (3 Users)
            ControlModeSegmentedSwitch(
                currentMode = controlMode,
                onModeChange = { viewModel.setControlMode(it) },
                activeHotspotUsersCount = clientSlots.count { it.isConnected }
            )

            // 3. Conditional Mode Views:
            if (controlMode == ControlMode.HOTSPOT_MULTI_USER) {
                // Multi-User Hotspot Card with 3 compact sub-tabs and traffic info per user
                HotspotMultiUserCard(
                    clientSlots = clientSlots,
                    gatewayIp = hotspotGatewayIp,
                    port = config.port,
                    onCopy = { label, text -> viewModel.copyToClipboard(label, text) },
                    onOpenHotspotSettings = { viewModel.openHotspotSettings() }
                )
            } else {
                // USB Single User Card with ADB Reverse and USB Tethering IP
                UsbSingleUserCard(
                    suggestedIp = suggestedIp,
                    port = config.port,
                    onCopy = { label, text -> viewModel.copyToClipboard(label, text) },
                    onOpenTetherSettings = { viewModel.openTetheringSettings() }
                )
            }

            // 4. Data Sent & Data Received 2-Column Stats Cards
            NaturalStatsGrid(stats = trafficStats)

            // 5. Traffic Waveform Canvas
            NaturalTrafficGraphCard(stats = trafficStats)

            // 6. IP Addresses Card (USB, Wi-Fi Hotspot, Wi-Fi, ADB)
            IpAddressesCard(
                interfaces = networkInterfaces,
                port = config.port,
                onCopy = { label, text -> viewModel.copyToClipboard(label, text) },
                onOpenHotspotSettings = { viewModel.openHotspotSettings() }
            )

            // 7. Recent Activity Card
            NaturalRecentActivityCard(
                sessionLogsCount = sessionLogs.size,
                activeConnections = activeConnections,
                connectedDevicesCount = connectedClients.size,
                onOpenSessionLogs = { showSessionLogsDialog = true }
            )

            // 8. Windows PC USB & Wi-Fi Hotspot Setup Quick Banner
            WindowsSetupBannerNatural(
                onClick = { showWindowsGuideDialog = true }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Dialogs
    if (showSettingsDialog) {
        ProxySettingsDialog(
            initialConfig = config,
            availableInterfaces = networkInterfaces,
            onDismiss = { showSettingsDialog = false },
            onSave = { newConfig ->
                viewModel.saveConfig(newConfig)
                showSettingsDialog = false
            },
            onTestClick = {
                showSettingsDialog = false
                showTestDialog = true
            }
        )
    }

    if (showWindowsGuideDialog) {
        WindowsSetupGuideDialog(
            port = config.port,
            interfaces = networkInterfaces,
            onDismiss = {
                showWindowsGuideDialog = false
                if (currentNavTab == 2) currentNavTab = 0
            },
            onCopy = { label, text -> viewModel.copyToClipboard(label, text) },
            onOpenTetherSettings = { viewModel.openHotspotSettings() }
        )
    }

    if (showSessionLogsDialog) {
        SessionLogsDialog(
            logs = sessionLogs,
            onDismiss = {
                showSessionLogsDialog = false
                if (currentNavTab == 1) currentNavTab = 0
            },
            onClearLogs = { viewModel.clearLogs() }
        )
    }

    if (showTestDialog) {
        TestProxyDialog(
            isTesting = isTesting,
            testResult = testResult,
            port = config.port,
            onRunTest = { url -> viewModel.runProxySelfTest(url) },
            onDismiss = {
                viewModel.clearTestResult()
                showTestDialog = false
            }
        )
    }
}

@Composable
fun NaturalBottomNavBar(
    selectedTab: Int,
    onTabSelect: (Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavTabItem(
                title = "Control",
                icon = Icons.Default.Home,
                isSelected = selectedTab == 0,
                onClick = { onTabSelect(0) }
            )
            NavTabItem(
                title = "Logs",
                icon = Icons.Default.History,
                isSelected = selectedTab == 1,
                onClick = { onTabSelect(1) }
            )
            NavTabItem(
                title = "Setup",
                icon = Icons.Default.Settings,
                isSelected = selectedTab == 2,
                onClick = { onTabSelect(2) }
            )
        }
    }
}

@Composable
fun NavTabItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 18.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
fun WindowsSetupBannerNatural(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .testTag("windows_setup_banner"),
        shape = RoundedCornerShape(24.dp),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
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

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "Windows PC Proxy Setup",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                    )
                    Text(
                        text = "USB Tethering • Wi-Fi Hotspot • ADB Reverse",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
