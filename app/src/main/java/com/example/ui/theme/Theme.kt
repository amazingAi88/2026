package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SwarDubDarkColorScheme = darkColorScheme(
    primary = SaffronAmber,
    onPrimary = Color(0xFF1F1200),
    primaryContainer = DeepAmberContainer,
    onPrimaryContainer = SaffronAmberLight,
    secondary = ElectricCoral,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = DeepCoralContainer,
    onSecondaryContainer = Color(0xFFFFD9DF),
    tertiary = CinemaTeal,
    onTertiary = Color(0xFF00201D),
    tertiaryContainer = DeepTealContainer,
    onTertiaryContainer = Color(0xFFA8F5EC),
    background = ObsidianBg,
    onBackground = SoftIvory,
    surface = StudioSurface,
    onSurface = SoftIvory,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = MutedLavender,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceElevated,
    surfaceContainerHigh = StudioSurfaceHighest,
    outline = StudioBorder,
    outlineVariant = Color(0xFF2B253E),
    error = ErrorCrimson,
    onError = Color(0xFF3A0000),
    errorContainer = ErrorContainerDark,
    onErrorContainer = Color(0xFFFFDADA)
)

private val SwarDubLightColorScheme = lightColorScheme(
    primary = SaffronAmberDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE5C2),
    onPrimaryContainer = Color(0xFF361B00),
    secondary = ElectricCoralDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF3E0014),
    tertiary = CinemaTealDark,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC5F7F1),
    onTertiaryContainer = Color(0xFF00201C),
    background = LightStudioBg,
    onBackground = LightInk,
    surface = LightStudioSurface,
    onSurface = LightInk,
    surfaceVariant = LightStudioSurfaceVariant,
    onSurfaceVariant = LightMutedInk,
    outline = Color(0xFFD5CCDF)
)

val SwarDubShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SwarDubTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SwarDubDarkColorScheme else SwarDubLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SwarDubTypography,
        shapes = SwarDubShapes,
        content = content
    )
}
