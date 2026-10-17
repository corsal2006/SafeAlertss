package com.siddesh.smsalerts.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val EmergencyRed = Color(0xFFFF2A2A)
val EmergencyRedDark = Color(0xFFD31D1D)
val EmergencyRedLight = Color(0xFFFF6B6B)
val EmergencyRedGlow = Color(0x33FF2A2A)

// Raw Color Palettes for Theme Configuration
val RawBackgroundDark = Color(0xFF090B0E)
val RawSurfaceDark = Color(0xFF11141A)
val RawSurfaceCardDark = Color(0xFF181C24)
val RawSurfaceCardElevatedDark = Color(0xFF202632)
val RawCardBorderDark = Color(0xFF283040)
val RawTextPrimaryDark = Color(0xFFF8FAFC)
val RawTextSecondaryDark = Color(0xFF94A3B8)
val RawTextMutedDark = Color(0xFF64748B)

val RawBackgroundLight = Color(0xFFF8FAFC)
val RawSurfaceLight = Color(0xFFFFFFFF)
val RawSurfaceCardLight = Color(0xFFF1F5F9)
val RawSurfaceCardElevatedLight = Color(0xFFE2E8F0)
val RawCardBorderLight = Color(0xFFCBD5E1)
val RawTextPrimaryLight = Color(0xFF0F172A)
val RawTextSecondaryLight = Color(0xFF475569)
val RawTextMutedLight = Color(0xFF94A3B8)

// Accent Status Colors
val StatusSuccess = Color(0xFF10B981)
val StatusWarning = Color(0xFFF59E0B)
val StatusInfo = Color(0xFF3B82F6)
val StatusError = Color(0xFFEF4444)

// Color Token Aliases for Unified UI Design
val AccentRed = EmergencyRed
val SuccessGreen = StatusSuccess
val WarningAmber = StatusWarning

// Centralized Dynamic Theme Color Accessors (Seamless Light & Dark Mode)
val TextPrimary: Color
    @Composable get() = MaterialTheme.colorScheme.onBackground

val TextSecondary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

val TextMuted: Color
    @Composable get() = MaterialTheme.colorScheme.outline

val TextTertiary: Color
    @Composable get() = MaterialTheme.colorScheme.outline

val BackgroundDark: Color
    @Composable get() = MaterialTheme.colorScheme.background

val SurfaceDark: Color
    @Composable get() = MaterialTheme.colorScheme.surface

val SurfaceCard: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant

val SurfaceCardElevated: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant

val CardBorder: Color
    @Composable get() = MaterialTheme.colorScheme.outline

val CardBorderHighlight: Color
    @Composable get() = MaterialTheme.colorScheme.outline

val BorderSubtle: Color
    @Composable get() = MaterialTheme.colorScheme.outline

val CardBackgroundLight: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant
