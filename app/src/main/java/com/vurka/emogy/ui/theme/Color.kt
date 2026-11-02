package com.vurka.emogy.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// --- Shared Base Palette ---
val VividRed = Color(0xFFE11D48)
val RoyalBlue = Color(0xFF2563EB)
val BrightCyan = Color(0xFF00D2FF)
val GoldStar = Color(0xFFFBBF24)
val EmeraldGreen = Color(0xFF10B981)
val TextWhite = Color(0xFFF8FAFC)
val TextDark = Color(0xFF0F172A)
val TextMuted = Color(0xFF94A3B8)

// --- Default Red & Blue Palette ---
val DefaultBg = Color(0xFF0B132B)
val DefaultSurface = Color(0xFF1C2541)
val DefaultSurfaceVariant = Color(0xFF24325B)

// --- Light Palette ---
val LightBg = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFF1F5F9)
val LightSurfaceVariant = Color(0xFFE2E8F0)

// --- Dark Palette ---
val DarkBg = Color(0xFF0F0C1B)
val DarkSurface = Color(0xFF1E1B38)
val DarkSurfaceVariant = Color(0xFF2A2450)

// --- Gradients ---
val RedBlueGradient = Brush.horizontalGradient(
    colors = listOf(VividRed, RoyalBlue)
)

val SunsetGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFFF5F6D), Color(0xFFFFC371))
)

val OceanGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
)

val MintGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF43E97B), Color(0xFF38F9D7))
)

val CosmicGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF24243E))
)

val AuroraGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF200B1A), Color(0xFF0B132B), Color(0xFF050B18))
)

val SoftGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFFFFEEF2), Color(0xFFEFF6FF), Color(0xFFE0E7FF))
)

val NeonGlowGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF00D2FF), Color(0xFF3A7BD5), Color(0xFF00D2FF))
)
