package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.NetworkInterfaceInfo
import com.example.model.ProxyConfig
import com.example.ui.theme.NaturalBackground
import com.example.ui.theme.NaturalBorder
import com.example.ui.theme.NaturalGreenSuccess
import com.example.ui.theme.NaturalHeroContainer
import com.example.ui.theme.NaturalOchrePrimary
import com.example.ui.theme.NaturalSurface
import com.example.ui.theme.NaturalSurfaceElevated
import com.example.ui.theme.NaturalTextPrimary
import com.example.ui.theme.NaturalTextSecondary

@Composable
fun ProxySettingsDialog(
    initialConfig: ProxyConfig,
    availableInterfaces: List<NetworkInterfaceInfo>,
    onDismiss: () -> Unit,
    onSave: (ProxyConfig) -> Unit,
    onTestClick: () -> Unit
) {
    var host by remember { mutableStateOf(initialConfig.host) }
    var portText by remember { mutableStateOf(initialConfig.port.toString()) }
    var authEnabled by remember { mutableStateOf(initialConfig.authEnabled) }
    var username by remember { mutableStateOf(initialConfig.username) }
    var password by remember { mutableStateOf(initialConfig.password) }
    var passwordVisible by remember { mutableStateOf(false) }
    var powerSave by remember { mutableStateOf(initialConfig.powerSave) }
    var autoStart by remember { mutableStateOf(initialConfig.autoStartOnBoot) }
    var keepCpuAwake by remember { mutableStateOf(initialConfig.keepCpuAwake) }

    var hostDropdownExpanded by remember { mutableStateOf(false) }

    val parsedPort = portText.toIntOrNull()
    val isPortValid = parsedPort != null && parsedPort in 1..65535

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
                .testTag("proxy_settings_dialog"),
            shape = RoundedCornerShape(28.dp),
            color = NaturalSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, NaturalBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Text(
                    text = "Proxy Configuration",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = NaturalTextPrimary,
                        fontSize = 20.sp
                    ),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // IP Address dropdown field
                Text(
                    text = "IP Address",
                    style = MaterialTheme.typography.labelMedium.copy(color = NaturalTextSecondary)
                )
                Spacer(modifier = Modifier.height(4.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setting_host_input"),
                        trailingIcon = {
                            IconButton(onClick = { hostDropdownExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select IP",
                                    tint = NaturalOchrePrimary
                                )
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NaturalOchrePrimary,
                            unfocusedBorderColor = NaturalBorder,
                            focusedTextColor = NaturalTextPrimary,
                            unfocusedTextColor = NaturalTextPrimary,
                            focusedContainerColor = NaturalHeroContainer.copy(alpha = 0.5f),
                            unfocusedContainerColor = NaturalHeroContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )

                    DropdownMenu(
                        expanded = hostDropdownExpanded,
                        onDismissRequest = { hostDropdownExpanded = false },
                        modifier = Modifier
                            .background(NaturalSurface)
                            .fillMaxWidth(0.8f)
                    ) {
                        DropdownMenuItem(
                            text = { Text("0.0.0.0 (All interfaces)", color = NaturalTextPrimary) },
                            onClick = {
                                host = "0.0.0.0"
                                hostDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("127.0.0.1 (Loopback / ADB reverse)", color = NaturalTextPrimary) },
                            onClick = {
                                host = "127.0.0.1"
                                hostDropdownExpanded = false
                            }
                        )
                        availableInterfaces.forEach {
                            if (it.ipAddress != "127.0.0.1") {
                                DropdownMenuItem(
                                    text = { Text("${it.ipAddress} - ${it.displayName}", color = NaturalTextPrimary) },
                                    onClick = {
                                        host = it.ipAddress
                                        hostDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Port field
                Text(
                    text = "Port",
                    style = MaterialTheme.typography.labelMedium.copy(color = NaturalTextSecondary)
                )
                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setting_port_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = !isPortValid,
                    supportingText = {
                        if (!isPortValid) {
                            Text("Enter a valid port (1 - 65535)", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NaturalOchrePrimary,
                        unfocusedBorderColor = NaturalBorder,
                        focusedTextColor = NaturalTextPrimary,
                        unfocusedTextColor = NaturalTextPrimary,
                        focusedContainerColor = NaturalHeroContainer.copy(alpha = 0.5f),
                        unfocusedContainerColor = NaturalHeroContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Power Save option
                CheckboxOptionRow(
                    title = "Power save",
                    subtitle = "Switch off proxy when the IP Address is no longer available",
                    checked = powerSave,
                    onCheckedChange = { powerSave = it },
                    tag = "setting_powersave_checkbox"
                )

                // Auto Start option
                CheckboxOptionRow(
                    title = "Auto Start",
                    subtitle = "Start proxy when the device starts (Boot completed)",
                    checked = autoStart,
                    onCheckedChange = { autoStart = it },
                    tag = "setting_autostart_checkbox"
                )

                // Keep CPU Awake / Wakelock option
                CheckboxOptionRow(
                    title = "Keep CPU Awake (Wakelock)",
                    subtitle = "Prevents Android from pausing proxy when screen is locked or idle",
                    checked = keepCpuAwake,
                    onCheckedChange = { keepCpuAwake = it },
                    tag = "setting_wakelock_checkbox"
                )

                // Basic Authentication option
                CheckboxOptionRow(
                    title = "Basic authentication",
                    subtitle = "Enable HTTP Basic Authentication for proxy clients",
                    checked = authEnabled,
                    onCheckedChange = { authEnabled = it },
                    tag = "setting_auth_checkbox"
                )

                if (authEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NaturalHeroContainer, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setting_username_input"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = NaturalTextPrimary,
                                unfocusedTextColor = NaturalTextPrimary,
                                focusedBorderColor = NaturalOchrePrimary,
                                unfocusedBorderColor = NaturalBorder,
                                focusedContainerColor = NaturalSurface,
                                unfocusedContainerColor = NaturalSurface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setting_password_input"),
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password visibility",
                                        tint = NaturalTextSecondary
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = NaturalTextPrimary,
                                unfocusedTextColor = NaturalTextPrimary,
                                focusedBorderColor = NaturalOchrePrimary,
                                unfocusedBorderColor = NaturalBorder,
                                focusedContainerColor = NaturalSurface,
                                unfocusedContainerColor = NaturalSurface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions: Cancel, Test, Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("setting_cancel_button")
                    ) {
                        Text("Cancel", color = NaturalTextSecondary, fontWeight = FontWeight.Medium)
                    }

                    Row {
                        TextButton(
                            onClick = onTestClick,
                            modifier = Modifier.testTag("setting_test_button")
                        ) {
                            Text("Test", color = NaturalGreenSuccess, fontWeight = FontWeight.Medium)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (isPortValid) {
                                    onSave(
                                        ProxyConfig(
                                            host = host.trim(),
                                            port = parsedPort ?: 8080,
                                            authEnabled = authEnabled,
                                            username = username.trim(),
                                            password = password,
                                            powerSave = powerSave,
                                            autoStartOnBoot = autoStart,
                                            keepCpuAwake = keepCpuAwake
                                        )
                                    )
                                }
                            },
                            enabled = isPortValid,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NaturalOchrePrimary,
                                contentColor = NaturalSurface
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("setting_save_button")
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckboxOptionRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = NaturalTextPrimary
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = NaturalTextSecondary,
                    fontSize = 11.sp
                )
            )
        }

        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
            colors = CheckboxDefaults.colors(
                checkedColor = NaturalOchrePrimary,
                uncheckedColor = NaturalBorder,
                checkmarkColor = NaturalSurface
            )
        )
    }
}
