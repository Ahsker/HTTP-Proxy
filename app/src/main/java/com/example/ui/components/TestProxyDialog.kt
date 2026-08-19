package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.TestResult
import com.example.ui.theme.NaturalBackground
import com.example.ui.theme.NaturalBorder
import com.example.ui.theme.NaturalGreenSuccess
import com.example.ui.theme.NaturalGreenTint
import com.example.ui.theme.NaturalHeroContainer
import com.example.ui.theme.NaturalOchrePrimary
import com.example.ui.theme.NaturalRedError
import com.example.ui.theme.NaturalRedTint
import com.example.ui.theme.NaturalSurface
import com.example.ui.theme.NaturalTextPrimary
import com.example.ui.theme.NaturalTextSecondary

@Composable
fun TestProxyDialog(
    isTesting: Boolean,
    testResult: TestResult?,
    port: Int,
    onRunTest: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var testUrl by remember { mutableStateOf("http://connectivitycheck.gstatic.com/generate_204") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = NaturalSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, NaturalBorder),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .testTag("test_proxy_dialog")
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(NaturalHeroContainer, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NetworkCheck,
                                contentDescription = null,
                                tint = NaturalOchrePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Test Proxy Connection",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = NaturalTextPrimary
                            )
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = NaturalTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Sends an HTTP request routed via 127.0.0.1:$port to verify socket server, routing, and DNS resolution.",
                    style = MaterialTheme.typography.bodySmall.copy(color = NaturalTextSecondary)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = testUrl,
                    onValueChange = { testUrl = it },
                    label = { Text("Target URL") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("test_url_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = NaturalTextPrimary,
                        unfocusedTextColor = NaturalTextPrimary,
                        focusedBorderColor = NaturalOchrePrimary,
                        unfocusedBorderColor = NaturalBorder,
                        focusedContainerColor = NaturalHeroContainer.copy(alpha = 0.5f),
                        unfocusedContainerColor = NaturalHeroContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (isTesting) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = NaturalOchrePrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Testing proxy socket connection...", color = NaturalTextSecondary, fontSize = 13.sp)
                        }
                    }
                } else if (testResult != null) {
                    val isSuccess = testResult.success
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSuccess) NaturalGreenTint else NaturalRedTint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (isSuccess) NaturalGreenSuccess.copy(alpha = 0.4f) else NaturalRedError.copy(alpha = 0.4f),
                                RoundedCornerShape(14.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (isSuccess) NaturalGreenSuccess else NaturalRedError,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isSuccess) "Proxy Connection Working!" else "Connection Test Failed",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSuccess) NaturalGreenSuccess else NaturalRedError
                                    )
                                )
                                Text(
                                    text = "${testResult.message} (${testResult.latencyMs}ms)",
                                    style = MaterialTheme.typography.bodySmall.copy(color = NaturalTextPrimary)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { onRunTest(testUrl) },
                        enabled = !isTesting && testUrl.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = NaturalOchrePrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("run_test_button")
                    ) {
                        Text(if (isTesting) "Testing..." else "Run Test", color = NaturalSurface, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
