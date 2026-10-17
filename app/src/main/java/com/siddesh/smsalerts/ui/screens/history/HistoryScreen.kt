package com.siddesh.smsalerts.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siddesh.smsalerts.data.model.EmergencyAlert
import com.siddesh.smsalerts.ui.components.GlassCard
import com.siddesh.smsalerts.ui.components.SafeAlertTopBar
import com.siddesh.smsalerts.ui.theme.BackgroundDark
import com.siddesh.smsalerts.ui.theme.CardBorder
import com.siddesh.smsalerts.ui.theme.EmergencyRed
import com.siddesh.smsalerts.ui.theme.StatusError
import com.siddesh.smsalerts.ui.theme.StatusSuccess
import com.siddesh.smsalerts.ui.theme.StatusWarning
import com.siddesh.smsalerts.ui.theme.SurfaceCard
import com.siddesh.smsalerts.ui.theme.SurfaceCardElevated
import com.siddesh.smsalerts.ui.theme.TextMuted
import com.siddesh.smsalerts.ui.theme.TextPrimary
import com.siddesh.smsalerts.ui.theme.TextSecondary
import com.siddesh.smsalerts.ui.viewmodel.HistoryViewModel
import com.siddesh.smsalerts.utils.DateUtils

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onAlertClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val alerts by viewModel.alerts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            SafeAlertTopBar(
                title = "Alert History"
            )
        },
        containerColor = BackgroundDark,
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading && alerts.isEmpty()) {
                CircularProgressIndicator(
                    color = EmergencyRed,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (alerts.isEmpty()) {
                // Systematic Empty State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(SurfaceCard)
                            .border(1.dp, CardBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsNone,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "No Emergency Alerts",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your emergency history will appear here.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 88.dp // Ensures content is never obscured by bottom navigation bar
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(alerts, key = { it.alertId }) { alert ->
                        AlertHistoryItem(
                            alert = alert,
                            onClick = { onAlertClick(alert.alertId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlertHistoryItem(
    alert: EmergencyAlert,
    onClick: () -> Unit
) {
    val isSuccess = alert.status.contains("sent", ignoreCase = true) || alert.status.contains("dispatched", ignoreCase = true)
    val isFailed = alert.status.contains("fail", ignoreCase = true) || alert.status.contains("error", ignoreCase = true)

    val statusColor = when {
        isSuccess -> StatusSuccess
        isFailed -> StatusError
        else -> StatusWarning
    }

    val statusBg = statusColor.copy(alpha = 0.12f)
    val statusBorder = statusColor.copy(alpha = 0.35f)

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            // Header Row: [Emergency Icon] + [Title (weight 1f)] + [Status Badge]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(EmergencyRed.copy(alpha = 0.15f))
                        .border(1.dp, EmergencyRed.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isSuccess -> Icons.Default.CheckCircle
                            isFailed -> Icons.Default.Error
                            else -> Icons.Default.Warning
                        },
                        contentDescription = null,
                        tint = if (isSuccess) StatusSuccess else EmergencyRed,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title Section - Takes remaining space and wraps naturally without pushing out status
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = alert.emergencyType.ifBlank { "Personal Emergency" }.uppercase(),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Badge has dedicated space on the right (never overlaps title or message)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusBg)
                        .border(1.dp, statusBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatStatus(alert.status),
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Message Preview Box - clean container with 2 lines max and ellipsis
            val cleanMessage = formatMessagePreview(alert.message)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceCardElevated.copy(alpha = 0.6f))
                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = cleanMessage,
                    color = TextPrimary.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata Row: Trigger Source & Formatted Timestamp
            val triggerText = when (alert.triggerSource.uppercase()) {
                "SHAKE" -> "Shake"
                "VOICE" -> "Voice"
                "BUTTON" -> "Manual Button"
                else -> alert.triggerSource
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Triggered by: $triggerText",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = DateUtils.formatTimestamp(alert.timestamp),
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer Row: Location indicator & View Details action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (alert.locationAvailable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "GPS Attached",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Text(
                        text = "No GPS Data",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onClick)
                ) {
                    Text(
                        text = "View Details",
                        color = EmergencyRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "View Details",
                        tint = EmergencyRed,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

private fun formatStatus(status: String): String {
    return when {
        status.contains("sent", ignoreCase = true) -> "SMS SENT"
        status.contains("composer", ignoreCase = true) || status.contains("dispatched", ignoreCase = true) -> "DISPATCHED"
        status.contains("fail", ignoreCase = true) -> "FAILED"
        status.contains("cancel", ignoreCase = true) -> "CANCELLED"
        else -> status.uppercase().take(12)
    }
}

private fun formatMessagePreview(raw: String): String {
    if (raw.isBlank()) return "🚨 Emergency alert triggered."
    val firstLine = raw.lines().firstOrNull { it.isNotBlank() } ?: raw
    return if (firstLine.startsWith("🚨")) firstLine else "🚨 $firstLine"
}
