package com.expenseinterceptor.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColorScheme =
  lightColorScheme(
    primary = SignalLight,
    onPrimary = OnSignalLight,
    primaryContainer = PanelLight,
    onPrimaryContainer = InkLight,
    secondary = InkSoftLight,
    onSecondary = OnSignalLight,
    secondaryContainer = PanelLight,
    onSecondaryContainer = InkLight,
    background = PaperLight,
    onBackground = InkLight,
    surface = RaisedLight,
    onSurface = InkLight,
    surfaceVariant = PanelLight,
    onSurfaceVariant = InkSoftLight,
    outline = EdgeLight,
    outlineVariant = LineLight,
    error = BadLight,
    onError = OnSignalLight
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = SignalDark,
    onPrimary = OnSignalDark,
    primaryContainer = PanelDark,
    onPrimaryContainer = InkDark,
    secondary = InkSoftDark,
    onSecondary = OnSignalDark,
    secondaryContainer = PanelDark,
    onSecondaryContainer = InkDark,
    background = PaperDark,
    onBackground = InkDark,
    surface = RaisedDark,
    onSurface = InkDark,
    surfaceVariant = PanelDark,
    onSurfaceVariant = InkSoftDark,
    outline = EdgeDark,
    outlineVariant = LineDark,
    error = BadDark,
    onError = OnSignalDark
  )

// Small radii: this is a tool, not a toy.
private val AppShapes =
  Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(6.dp)
  )

@Composable
fun ExpenseInterceptorTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as Activity).window
      window.statusBarColor = colorScheme.background.toArgb()
      WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
    }
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = AppShapes,
    content = content
  )
}
