package com.example.novabudget.theme

import androidx.compose.ui.graphics.Color

// Obsidian & Emerald/Teal Theme Colors
val DarkBackground = Color(0xFF0B0D13)
val DarkSurface = Color(0xFF121622)
val DarkSurfaceVariant = Color(0xFF1A1F30)

val PrimaryEmerald = Color(0xFF10B981)      // Main brand emerald accent
val SecondaryTeal = Color(0xFF06B6D4)       // Secondary cyan/teal details
val TertiaryIndigo = Color(0xFF6366F1)      // Highlight/bluetooth indigo
val SlateWhite = Color(0xFFF8FAFC)           // Text primary
val SlateGray = Color(0xFF64748B)            // Text muted

val AlertCrimson = Color(0xFFEF4444)         // Budget exceeded alert
val AlertWarning = Color(0xFFF59E0B)         // Approaching limit warning

// Gradients
val PrimaryGradient = listOf(PrimaryEmerald, SecondaryTeal)
val WarningGradient = listOf(AlertWarning, AlertCrimson)
val SurfaceGradient = listOf(DarkSurface, DarkSurfaceVariant)
