package com.buttery.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val RecipeColorScheme = lightColorScheme(
    primary = Paprika,
    secondary = Herb,
    background = Cream,
    surface = Cream,
    onPrimary = Cream,
    onSecondary = Cream,
    onBackground = Charcoal,
    onSurface = Charcoal
)

@Composable
fun RecipeAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RecipeColorScheme,
        typography = Typography,
        content = content
    )
}
