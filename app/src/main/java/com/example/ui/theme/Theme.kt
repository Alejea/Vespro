package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val TwilightDarkColorScheme = darkColorScheme(
    primary = CandlelightAmber,
    onPrimary = CandlelightDeep,
    primaryContainer = CandlelightContainer,
    onPrimaryContainer = CandlelightSoft,
    secondary = DuskLavender,
    onSecondary = DuskLavenderDark,
    secondaryContainer = DuskLavenderContainer,
    onSecondaryContainer = IvoryInk,
    tertiary = SageMist,
    onTertiary = SageDeep,
    tertiaryContainer = SageContainer,
    onTertiaryContainer = IvoryInk,
    background = TwilightObsidian,
    onBackground = IvoryInk,
    surface = TwilightSurface,
    onSurface = IvoryInk,
    surfaceVariant = TwilightSurfaceHigh,
    onSurfaceVariant = MutedDuskInk,
    surfaceContainerLowest = TwilightObsidian,
    surfaceContainerLow = TwilightSurface,
    surfaceContainer = TwilightSurfaceHigh,
    surfaceContainerHigh = TwilightSurfaceHighest,
    surfaceContainerHighest = TwilightSurfaceHighest,
    outline = TwilightOutline,
    outlineVariant = TwilightOutlineVariant,
    error = SoftCoralError,
    onError = SoftCoralErrorDark
)

private val WarmParchmentColorScheme = lightColorScheme(
    primary = AmberInk,
    onPrimary = ParchmentCream,
    primaryContainer = AmberLightContainer,
    onPrimaryContainer = CharcoalInk,
    secondary = PlumSlate,
    onSecondary = ParchmentCream,
    secondaryContainer = PlumLightContainer,
    onSecondaryContainer = CharcoalInk,
    tertiary = ForestSage,
    onTertiary = ParchmentCream,
    tertiaryContainer = ForestLightContainer,
    onTertiaryContainer = CharcoalInk,
    background = ParchmentCream,
    onBackground = CharcoalInk,
    surface = ParchmentSurface,
    onSurface = CharcoalInk,
    surfaceVariant = ParchmentSurfaceHigh,
    onSurfaceVariant = WarmTaupeInk,
    surfaceContainerLowest = ParchmentCream,
    surfaceContainerLow = ParchmentSurface,
    surfaceContainer = ParchmentSurfaceHigh,
    surfaceContainerHigh = ParchmentSurfaceHigh,
    outline = ParchmentOutline,
    outlineVariant = ParchmentOutline,
    error = LightCoralError,
    onError = ParchmentCream
)

val VesproShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun VesproTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TwilightDarkColorScheme else WarmParchmentColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = VesproTypography,
        shapes = VesproShapes,
        content = content
    )
}
