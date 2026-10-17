package com.siddesh.smsalerts.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siddesh.smsalerts.ui.theme.CardBorder
import com.siddesh.smsalerts.ui.theme.StatusError
import com.siddesh.smsalerts.ui.theme.StatusSuccess
import com.siddesh.smsalerts.ui.theme.SurfaceCard
import com.siddesh.smsalerts.ui.theme.TextMuted
import com.siddesh.smsalerts.ui.theme.TextPrimary
import com.siddesh.smsalerts.ui.theme.TextSecondary

@Composable
fun StatusDashboardGrid(
    locationReady: Boolean,
    locationLabel: String,
    networkReady: Boolean,
    networkLabel: String,
    contactsCount: Int,
    onLocationClick: () -> Unit,
    onNetworkClick: () -> Unit,
    onContactsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatusItemCard(
            modifier = Modifier.weight(1f),
            title = "LOCATION",
            subtitle = if (locationReady) "GPS Ready" else "GPS Off",
            detail = locationLabel,
            isHealthy = locationReady,
            icon = Icons.Default.LocationOn,
            onClick = onLocationClick
        )

        StatusItemCard(
            modifier = Modifier.weight(1f),
            title = "NETWORK",
            subtitle = if (networkReady) "Mobile Net" else "No Cellular",
            detail = networkLabel,
            isHealthy = networkReady,
            icon = Icons.Default.CellTower,
            onClick = onNetworkClick
        )

        StatusItemCard(
            modifier = Modifier.weight(1f),
            title = "CONTACTS",
            subtitle = "$contactsCount Saved",
            detail = if (contactsCount > 0) "Active" else "Add Now",
            isHealthy = contactsCount > 0,
            icon = Icons.Default.People,
            onClick = onContactsClick
        )
    }
}

@Composable
fun StatusItemCard(
    title: String,
    subtitle: String,
    detail: String,
    isHealthy: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = if (isHealthy) StatusSuccess else StatusError

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 0.8.sp
            )

            Text(
                text = subtitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Text(
                text = detail,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = TextSecondary,
                maxLines = 1
            )
        }
    }
}
