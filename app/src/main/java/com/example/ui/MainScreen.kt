package com.example.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.ui.components.ProxySettingsDialog
import com.example.ui.components.SessionLogsDialog
import com.example.ui.components.TestProxyDialog
import com.example.ui.components.WindowsSetupGuideDialog
import com.example.ui.pages.HotspotMultiUserPage
import com.example.ui.pages.LogsPage
import com.example.ui.pages.UsbTetheringPage
import com.example.ui.pages.WindowsSetupPage
import com.example.ui.theme.NaturalGreenSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: ProxyViewModel) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
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
    val activeControlMode by viewModel.controlMode.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentNavTab by remember(activeControlMode) {
        mutableIntStateOf(if (activeControlMode == ControlMode.HOTSPOT_MULTI_USER) 1 else 0)
    }

    val modeLocked = serverStatus == ServerStatus.RUNNING || serverStatus == ServerStatus.STARTING
    val isUsbTabEnabled = !(modeLocked && activeControlMode != ControlMode.USB_SINGLE_USER)
    val isHotspotTabEnabled = !(modeLocked && activeControlMode != ControlMode.HOTSPOT_MULTI_USER)

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showWindowsGuideDialog by remember { mutableStateOf(false) }
    var showSessionLogsDialog by remember { mutableStateOf(false) }
    var showTestDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val refreshRotationAngle by animateFloatAsState(
        targetValue = if (isRefreshing) 360f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "refresh_rotation"
    )

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

    val suggestedUsbIp = remember(networkInterfaces) {
        networkInterfaces.find { it.type == InterfaceType.USB_TETHERING }?.ipAddress
            ?: networkInterfaces.find { it.type == InterfaceType.WIFI }?.ipAddress
            ?: networkInterfaces.firstOrNull { it.type != InterfaceType.LOOPBACK && it.type != InterfaceType.MOBILE }?.ipAddress
            ?: "192.168.42.129"
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                                imageVector = when (currentNavTab) {
                                    0 -> Icons.Default.Usb
                                    1 -> Icons.Default.WifiTethering
                                    2 -> Icons.Default.History
                                    else -> Icons.Default.DesktopWindows
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = when (currentNavTab) {
                                    0 -> "USB Tethering"
                                    1 -> "Wi-Fi Hotspot"
                                    2 -> "Traffic Logs"
                                    else -> "Setup Guide"
                                },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 17.sp,
                                    letterSpacing = (-0.3).sp
                                ),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = when (currentNavTab) {
                                    0 -> "Direct PC connection"
                                    1 -> "Up to 3 Hotspot Users"
                                    2 -> "${sessionLogs.size} requests"
                                    else -> "PC & CLI configs"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
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
                        onClick = { currentNavTab = 3 },
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
                        onClick = {
                            coroutineScope.launch {
                                isRefreshing = true
                                viewModel.refreshNetworkInterfaces()
                                com.example.service.ProxyForegroundService.serverInstance.refreshUpstreamNetwork()
                                delay(600)
                                isRefreshing = false
                                val count = networkInterfaces.size
                                snackbarHostState.showSnackbar("Network interfaces & routing refreshed ($count detected)")
                            }
                        },
                        modifier = Modifier.testTag("refresh_interfaces_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Interfaces",
                            tint = if (isRefreshing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rotate(refreshRotationAngle)
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
                                text = { Text("Open USB Tethering Settings", color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = {
                                    Icon(Icons.Default.Cable, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    viewModel.openTetheringSettings()
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
            FourTabBottomNavBar(
                selectedTab = currentNavTab,
                onTabSelect = { tabIndex ->
                    currentNavTab = tabIndex
                    if (tabIndex == 0) {
                        viewModel.setControlMode(ControlMode.USB_SINGLE_USER)
                    } else if (tabIndex == 1) {
                        viewModel.setControlMode(ControlMode.HOTSPOT_MULTI_USER)
                    }
                },
                // Only show the hotspot user badge when hotspot mode is active,
                // so USB mode never displays stale hotspot client counts.
                hotspotActiveCount = if (activeControlMode == ControlMode.HOTSPOT_MULTI_USER) {
                    clientSlots.count { it.isConnected }
                } else {
                    0
                },
                isUsbTabEnabled = isUsbTabEnabled,
                isHotspotTabEnabled = isHotspotTabEnabled,
                onDisabledTabClick = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Stop proxy server first to switch between USB and Hotspot modes.")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentNavTab) {
                0 -> UsbTetheringPage(
                    serverStatus = serverStatus,
                    errorMessage = errorMessage,
                    config = config,
                    suggestedIp = suggestedUsbIp,
                    uptimeSeconds = uptimeSeconds,
                    trafficStats = trafficStats,
                    activeConnections = activeConnections,
                    connectedClients = connectedClients,
                    sessionLogs = sessionLogs,
                    networkInterfaces = networkInterfaces,
                    onToggleServer = { viewModel.toggleServer(ControlMode.USB_SINGLE_USER) },
                    onOpenSettings = { showSettingsDialog = true },
                    onOpenTetherSettings = { viewModel.openTetheringSettings() },
                    onOpenSessionLogs = { currentNavTab = 2 },
                    onOpenWindowsGuide = { currentNavTab = 3 },
                    onCopy = { label, text -> viewModel.copyToClipboard(label, text) }
                )

                1 -> HotspotMultiUserPage(
                    serverStatus = serverStatus,
                    errorMessage = errorMessage,
                    config = config,
                    uptimeSeconds = uptimeSeconds,
                    trafficStats = trafficStats,
                    activeConnections = activeConnections,
                    connectedClients = connectedClients,
                    clientSlots = clientSlots,
                    sessionLogs = sessionLogs,
                    networkInterfaces = networkInterfaces,
                    onToggleServer = { viewModel.toggleServer(ControlMode.HOTSPOT_MULTI_USER) },
                    onOpenSettings = { showSettingsDialog = true },
                    onOpenHotspotSettings = { viewModel.openHotspotSettings() },
                    onOpenSessionLogs = { currentNavTab = 2 },
                    onOpenWindowsGuide = { currentNavTab = 3 },
                    onCopy = { label, text -> viewModel.copyToClipboard(label, text) }
                )

                2 -> LogsPage(
                    sessionLogs = sessionLogs,
                    onClearLogs = { viewModel.clearLogs() },
                    onCopy = { label, text -> viewModel.copyToClipboard(label, text) }
                )

                3 -> WindowsSetupPage(
                    port = config.port,
                    interfaces = networkInterfaces,
                    onCopy = { label, text -> viewModel.copyToClipboard(label, text) },
                    onOpenTetherSettings = { viewModel.openTetheringSettings() },
                    onOpenHotspotSettings = { viewModel.openHotspotSettings() }
                )
            }
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
            onDismiss = { showWindowsGuideDialog = false },
            onCopy = { label, text -> viewModel.copyToClipboard(label, text) },
            onOpenTetherSettings = { viewModel.openHotspotSettings() }
        )
    }

    if (showSessionLogsDialog) {
        SessionLogsDialog(
            logs = sessionLogs,
            onDismiss = { showSessionLogsDialog = false },
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
fun FourTabBottomNavBar(
    selectedTab: Int,
    onTabSelect: (Int) -> Unit,
    hotspotActiveCount: Int,
    isUsbTabEnabled: Boolean = true,
    isHotspotTabEnabled: Boolean = true,
    onDisabledTabClick: () -> Unit = {}
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding() // Lift above the system gesture bar on edge-to-edge displays
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FourNavTabItem(
                title = "USB",
                icon = Icons.Default.Usb,
                badgeText = null,
                isSelected = selectedTab == 0,
                isEnabled = isUsbTabEnabled,
                onClick = {
                    if (isUsbTabEnabled) onTabSelect(0) else onDisabledTabClick()
                }
            )
            FourNavTabItem(
                title = "Hotspot (3)",
                icon = Icons.Default.WifiTethering,
                badgeText = if (hotspotActiveCount > 0) "$hotspotActiveCount" else null,
                isSelected = selectedTab == 1,
                isEnabled = isHotspotTabEnabled,
                onClick = {
                    if (isHotspotTabEnabled) onTabSelect(1) else onDisabledTabClick()
                }
            )
            FourNavTabItem(
                title = "Logs",
                icon = Icons.Default.History,
                badgeText = null,
                isSelected = selectedTab == 2,
                isEnabled = true,
                onClick = { onTabSelect(2) }
            )
            FourNavTabItem(
                title = "Setup",
                icon = Icons.Default.DesktopWindows,
                badgeText = null,
                isSelected = selectedTab == 3,
                isEnabled = true,
                onClick = { onTabSelect(3) }
            )
        }
    }
}

@Composable
fun FourNavTabItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeText: String?,
    isSelected: Boolean,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (isEnabled) 1f else 0.38f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 14.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = (if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = alpha),
                    modifier = Modifier.size(20.dp)
                )
                if (badgeText != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .background(NaturalGreenSuccess.copy(alpha = alpha), CircleShape)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badgeText,
                            color = Color.White.copy(alpha = alpha),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = (if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = alpha)
            )
        )
    }
}
