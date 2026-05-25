package com.example.novabudget.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val ObsidianColorScheme = darkColorScheme(
    primary = PrimaryEmerald,
    secondary = SecondaryTeal,
    tertiary = TertiaryIndigo,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = SlateWhite,
    onSurface = SlateWhite,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = SlateWhite,
    error = AlertCrimson
)

@Composable
fun NovaBudgetTheme(
  darkTheme: Boolean = true, // Force dark theme by default for premium obsidian aesthetic!
  dynamicColor: Boolean = false, // Use our high-fidelity tailored theme instead of dynamic colors
  content: @Composable () -> Unit,
) {
  val colorScheme = ObsidianColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

