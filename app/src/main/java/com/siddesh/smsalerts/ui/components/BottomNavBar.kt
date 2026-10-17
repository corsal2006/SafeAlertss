package com.siddesh.smsalerts.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siddesh.smsalerts.ui.navigation.Screen
import com.siddesh.smsalerts.ui.theme.CardBorder
import com.siddesh.smsalerts.ui.theme.EmergencyRed
import com.siddesh.smsalerts.ui.theme.SurfaceDark
import com.siddesh.smsalerts.ui.theme.TextMuted

data class BottomNavDestination(
    val screen: Screen,
    val title: String,
    val icon: ImageVector
)

val bottomNavDestinations = listOf(
    BottomNavDestination(Screen.Home, "Home", Icons.Default.Home),
    BottomNavDestination(Screen.Contacts, "Contacts", Icons.Default.People),
    BottomNavDestination(Screen.History, "History", Icons.Default.History),
    BottomNavDestination(Screen.Settings, "Settings", Icons.Default.Settings)
)

@Composable
fun SafeAlertBottomNavBar(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = CardBorder.copy(alpha = 0.6f), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
        containerColor = SurfaceDark,
        tonalElevation = 4.dp
    ) {
        bottomNavDestinations.forEach { destination ->
            val isSelected = currentRoute == destination.screen.route

            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(destination.screen) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.title,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text = destination.title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = EmergencyRed,
                    selectedTextColor = EmergencyRed,
                    indicatorColor = EmergencyRed.copy(alpha = 0.12f),
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted
                )
            )
        }
    }
}
