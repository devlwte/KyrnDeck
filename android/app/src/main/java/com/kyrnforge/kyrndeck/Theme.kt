package com.kyrnforge.kyrndeck

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

@Immutable
data class Palette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val raised: Color,
    val select: Color,
    val tile: Color,
    val tileHover: Color,
    val line: Color,
    val lineStrong: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val steel: Color,
    val green: Color,
    val red: Color,
    val redSoft: Color,
    val scrim: Color,
)

val DarkPalette = Palette(
    dark = true,
    bg = Color(0xFF111316),
    surface = Color(0xFF181B1F),
    surface2 = Color(0xFF1F2328),
    raised = Color(0xFF272C32),
    select = Color(0xFF22272C),
    tile = Color(0xFF1C2024),
    tileHover = Color(0xFF23282D),
    line = Color(0xFF2A2F35),
    lineStrong = Color(0xFF3B4249),
    ink = Color(0xFFE6E8EA),
    muted = Color(0xFF9AA1A9),
    faint = Color(0xFF6A7179),
    accent = Color(0xFFD99A4E),
    accentSoft = Color(0x24D99A4E),
    onAccent = Color(0xFF1A1208),
    steel = Color(0xFF8FA3B6),
    green = Color(0xFF5DB58A),
    red = Color(0xFFE0675B),
    redSoft = Color(0x1FE0675B),
    scrim = Color(0xC0080A0C),
)

val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFD6D2CA),
    surface = Color(0xFFE0DCD4),
    surface2 = Color(0xFFD9D4CB),
    raised = Color(0xFFE6E2DB),
    select = Color(0xFFCFC5B3),
    tile = Color(0xFFD9D4CB),
    tileHover = Color(0xFFD0CAC0),
    line = Color(0xFFC2BBAF),
    lineStrong = Color(0xFFA1998B),
    ink = Color(0xFF26231F),
    muted = Color(0xFF555048),
    faint = Color(0xFF7E776C),
    accent = Color(0xFF9A520E),
    accentSoft = Color(0x229A520E),
    onAccent = Color(0xFFF3EEE6),
    steel = Color(0xFF34536D),
    green = Color(0xFF2B7148),
    red = Color(0xFFB3301D),
    redSoft = Color(0x14B3301D),
    scrim = Color(0x8C1D1B18),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

val Body = FontFamily(
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold)
)

val Cond = FontFamily(
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    Font(R.font.barlow_condensed_extrabold, FontWeight.ExtraBold)
)

fun cond(size: TextUnit, color: Color, weight: FontWeight = FontWeight.ExtraBold, spacing: TextUnit = 0.em) =
    TextStyle(fontFamily = Cond, fontWeight = weight, fontSize = size, color = color, letterSpacing = spacing,
        fontFeatureSettings = "tnum", lineHeight = size * 1.12f)

fun body(size: TextUnit = 15.sp, color: Color, weight: FontWeight = FontWeight.SemiBold) =
    TextStyle(fontFamily = Body, fontWeight = weight, fontSize = size, color = color, lineHeight = size * 1.3f)
