package com.expenseinterceptor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The shared design tokens (see docs/DESIGN.md): a warm paper and a green-black one, one vermilion
// accent, and the states a number can be in. Roles, not hues.
val PaperLight = Color(0xFFF6F7F3)
val PanelLight = Color(0xFFECEEE8)
val RaisedLight = Color(0xFFFBFBF8)
val InkLight = Color(0xFF17211C)
val InkSoftLight = Color(0xFF434F48)
val EdgeLight = Color(0xFF7A867E)
val LineLight = Color(0xFFD3D8CF)
val SignalLight = Color(0xFFBF3A1B)
val OnSignalLight = Color(0xFFFFFFFF)
val OkLight = Color(0xFF2C6E49)
val BadLight = Color(0xFFA3261C)

val PaperDark = Color(0xFF121815)
val PanelDark = Color(0xFF1A221D)
val RaisedDark = Color(0xFF161D19)
val InkDark = Color(0xFFE6EBE4)
val InkSoftDark = Color(0xFFB4BEB6)
val EdgeDark = Color(0xFF6A786F)
val LineDark = Color(0xFF2B362F)
val SignalDark = Color(0xFFFF7F5F)
val OnSignalDark = Color(0xFF1A0E09)
val OkDark = Color(0xFF6FCF97)
val BadDark = Color(0xFFFF9186)

// Money in and money out follow the theme, so they keep their contrast on both papers.
@Composable fun creditColor(): Color = if (isSystemInDarkTheme()) OkDark else OkLight

@Composable fun debitColor(): Color = if (isSystemInDarkTheme()) BadDark else BadLight

// Category colours are the one place a second hue is allowed: they tell series apart.
val CategoryFood = Color(0xFFC0583F)
val CategoryTravel = Color(0xFF4A86B8)
val CategoryBills = Color(0xFFB08A22)
val CategoryShopping = Color(0xFF8A68A8)
val CategoryEntertainment = Color(0xFFBA5379)
val CategoryHealth = Color(0xFF3F8A5F)
val CategoryOther = Color(0xFF6E7B74)
