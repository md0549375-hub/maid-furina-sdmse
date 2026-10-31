package eu.darken.sdmse.furina.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val FurinaDarkScheme = darkColorScheme(
    primary = FurinaColors.Hydro,
    onPrimary = FurinaColors.Obsidian,
    primaryContainer = FurinaColors.HydroDeep,
    onPrimaryContainer = FurinaColors.Obsidian,
    secondary = FurinaColors.Silver,
    onSecondary = FurinaColors.Obsidian,
    secondaryContainer = FurinaColors.NightBlueRaised,
    onSecondaryContainer = FurinaColors.Silver,
    tertiary = FurinaColors.Gold,
    onTertiary = FurinaColors.GoldOnContainer,
    tertiaryContainer = FurinaColors.GoldContainerDark,
    onTertiaryContainer = FurinaColors.GoldContainerLight,
    background = FurinaColors.Obsidian,
    onBackground = FurinaColors.NobleWhite,
    surface = FurinaColors.NightBlue,
    onSurface = FurinaColors.NobleWhite,
    surfaceVariant = FurinaColors.NightBlueRaised,
    onSurfaceVariant = FurinaColors.NightMutedText,
    outline = FurinaColors.NightOutline,
)

private val FurinaLightScheme = lightColorScheme(
    primary = FurinaColors.HydroDeep,
    onPrimary = FurinaColors.Obsidian,
    primaryContainer = FurinaColors.Hydro,
    onPrimaryContainer = FurinaColors.Obsidian,
    secondary = FurinaColors.Hydro,
    onSecondary = FurinaColors.Obsidian,
    secondaryContainer = FurinaColors.Silver,
    onSecondaryContainer = FurinaColors.Obsidian,
    tertiary = FurinaColors.Gold,
    onTertiary = FurinaColors.GoldOnContainer,
    tertiaryContainer = FurinaColors.GoldContainerLight,
    onTertiaryContainer = FurinaColors.GoldOnContainer,
    background = FurinaColors.NobleWhite,
    onBackground = FurinaColors.Obsidian,
    surface = FurinaColors.Silver,
    onSurface = FurinaColors.Obsidian,
    surfaceVariant = FurinaColors.Silver,
    onSurfaceVariant = FurinaColors.NightBlueRaised,
    outline = FurinaColors.HydroDeep,
)

/** Dark ("cyber obsidian") by default; pass darkTheme = isSystemInDarkTheme() to follow the system. */
@Composable
fun FurinaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) FurinaDarkScheme else FurinaLightScheme,
        content = content,
    )
}
