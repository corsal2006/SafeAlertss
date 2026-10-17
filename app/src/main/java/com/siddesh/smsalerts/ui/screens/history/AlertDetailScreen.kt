package com.siddesh.smsalerts.ui.screens.history

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siddesh.smsalerts.ui.components.GlassCard
import com.siddesh.smsalerts.ui.components.SafeAlertTopBar
import com.siddesh.smsalerts.ui.theme.*
import com.siddesh.smsalerts.ui.viewmodel.HistoryViewModel
import com.siddesh.smsalerts.utils.DateUtils

@Composable
fun AlertDetailScreen(
    alertId: String,
    viewModel: HistoryViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedAlert by viewModel.selectedAlert.collectAsState()

    LaunchedEffect(alertId) {
        viewModel.selectAlertById(alertId)
    }

    Scaffold(
        topBar = {
            SafeAlertTopBar(
                title = "Alert Details",
                showBackButton = true,
                onBackClick = onBackClick
            )
        },
        containerColor = BackgroundDark,
        modifier = modifier
    ) { paddingValues ->
        val alert = selectedAlert
        if (alert == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentRed)
            }
        } else {
            val statusColor = when (alert.status.lowercase()) {
                "sent" -> SuccessGreen
                "failed" -> AccentRed
                "cancelled" -> WarningAmber
                else -> AccentRed
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status Header Card
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(statusColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (alert.status.lowercase()) {
                                    "sent" -> Icons.Default.CheckCircle
                                    "failed" -> Icons.Default.Error
                                    "cancelled" -> Icons.Default.Cancel
                                    else -> Icons.Default.Warning
                                },
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = alert.emergencyType,
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Status: ${alert.status.uppercase()}",
                                color = statusColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Trigger: ${alert.triggerSource.uppercase()}",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = DateUtils.formatTimestamp(alert.timestamp),
                                color = TextTertiary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (alert.failureReason != null && alert.failureReason.isNotBlank()) {
                    GlassCard(modifier = Modifier.fillMaxWidth(), borderColor = AccentRed.copy(alpha = 0.4f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Error: ${alert.failureReason}",
                                color = AccentRed,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Message Card
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "Dispatched SMS Message",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = alert.message,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }

                // Location Card
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "Location Coordinates",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (alert.latitude != null && alert.longitude != null) {
                            Text(
                                text = "Lat: ${alert.latitude}, Long: ${alert.longitude}",
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if (!alert.mapsUrl.isNullOrBlank()) {
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(alert.mapsUrl))
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentRed.copy(alpha = 0.2f),
                                        contentColor = AccentRed
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open in Google Maps", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else {
                            Text(
                                text = "Location unavailable at the time of SOS broadcast",
                                color = TextTertiary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Recipients Card
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "Recipients (${alert.recipientCount})",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (alert.recipients.isEmpty()) {
                            Text(
                                text = "No recipient phone numbers recorded",
                                color = TextTertiary,
                                fontSize = 13.sp
                            )
                        } else {
                            alert.recipients.forEach { recipient ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = AccentRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = recipient,
                                        color = TextPrimary,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
