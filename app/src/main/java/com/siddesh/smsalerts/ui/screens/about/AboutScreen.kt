package com.siddesh.smsalerts.ui.screens.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siddesh.smsalerts.ui.components.GlassCard
import com.siddesh.smsalerts.ui.components.SafeAlertTopBar
import com.siddesh.smsalerts.ui.theme.*

@Composable
fun AboutScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            SafeAlertTopBar(
                title = "About SafeAlert",
                showBackButton = true,
                onBackClick = onBackClick
            )
        },
        containerColor = BackgroundDark,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Badge
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(AccentRed.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = AccentRed,
                    modifier = Modifier.size(36.dp)
                )
            }

            Text(
                text = "SafeAlert SOS",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Version 1.0.0 (Build 1)",
                color = TextTertiary,
                fontSize = 12.sp
            )

            // Academic Project Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "Wireless & Mobile Communication",
                        color = AccentRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Major Academic Project",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "SafeAlert is designed to address emergency response latency in scenarios with zero or low data connectivity. By leveraging GSM cellular short message service (SMS) and device GPS telemetry, vital distress signals and real-time coordinates are transmitted directly without relying on data availability.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            // Architecture Stack
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "Technical Architecture",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    TechItem(title = "Telephony Subsystem", desc = "Android SmsManager with multi-part PDU splitting & SIM state verification")
                    TechItem(title = "Geolocation Engine", desc = "Google Play FusedLocationProviderClient with PRIORITY_HIGH_ACCURACY GPS fixes")
                    TechItem(title = "Cloud Infrastructure", desc = "Firebase Authentication (Email, Passwordless Link, Google) & Cloud Firestore persistence")
                    TechItem(title = "User Interface", desc = "100% Jetpack Compose with Material 3, Dark Modern glassmorphism & coroutine flows")
                }
            }

            // Safety Disclaimer
            GlassCard(modifier = Modifier.fillMaxWidth(), borderColor = WarningAmber.copy(alpha = 0.4f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Safety & Carrier Notice",
                            color = WarningAmber,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Standard cellular SMS carrier charges may apply depending on your plan. Ensure active SIM card and network coverage. In severe life-threatening emergencies, also dial local emergency dispatch services (such as 112 or 911).",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TechItem(title: String, desc: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "• $title",
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = desc,
            color = TextTertiary,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
        )
    }
}
