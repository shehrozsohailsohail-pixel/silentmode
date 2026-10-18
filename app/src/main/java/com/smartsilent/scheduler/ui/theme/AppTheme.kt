package com.smartsilent.scheduler.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The palette is deliberately narrow: this app's subject is controlled
 * quiet - a calm, precise indigo carries that better than Material's default
 * purple, so it's the one thing overridden. Everything else (surfaces,
 * containers, error roles) inherits Material3's own tested baseline rather
 * than a hand-derived full scheme, which is where a hand-built palette
 * usually goes wrong (containers/on-colors drifting out of sync with a
 * custom seed with no way to visually verify contrast here).
 */
private val IndigoPrimaryLight = Color(0xFF33448C)
private val OnIndigoPrimaryLight = Color(0xFFFFFFFF)
private val SlatePrimaryLight = Color(0xFF5C6B8A)
private val OnSlatePrimaryLight = Color(0xFFFFFFFF)

// Dark scheme uses a lighter tone of the same hue, per Material3 dark-theme
// convention (a saturated deep indigo has poor contrast against a dark
// surface; a lighter tint of the same hue keeps the identity without that
// problem).
private val IndigoPrimaryDark = Color(0xFFB7C1F0)
private val OnIndigoPrimaryDark = Color(0xFF152244)
private val SlatePrimaryDark = Color(0xFFC5CCE3)
private val OnSlatePrimaryDark = Color(0xFF29344A)

/** The one deliberate accent (frontend-design principle: spend boldness in
 * one place) - a warm muted brass, used only for "something is actively
 * silenced right now" indicators, never folded into the general color
 * scheme roles where it would need a matching container/on-color derived
 * by hand. */
val AccentAmber = Color(0xFFC9922B)

private val LightColors = lightColorScheme(
    primary = IndigoPrimaryLight,
    onPrimary = OnIndigoPrimaryLight,
    secondary = SlatePrimaryLight,
    onSecondary = OnSlatePrimaryLight
)

private val DarkColors = darkColorScheme(
    primary = IndigoPrimaryDark,
    onPrimary = OnIndigoPrimaryDark,
    secondary = SlatePrimaryDark,
    onSecondary = OnSlatePrimaryDark
)

/** Only the roles that carry the most visual weight are overridden with a
 * deliberate weight/spacing choice; body/label text otherwise inherits
 * Material3's default type scale. Sentence case throughout - no all-caps
 * labels (a common generic-AI-design tell to avoid, not a style rule
 * without reason: all-caps also reads worse for RTL/Urdu display strings
 * later, per the framework's open localization decision). */
private val AppTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(letterSpacing = 0.2.sp)
    )
}

@Composable
fun SmartSilentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
