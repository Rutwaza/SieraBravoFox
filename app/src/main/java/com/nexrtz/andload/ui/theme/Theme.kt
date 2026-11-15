package com.nexrtz.andload.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CyberColorScheme = darkColorScheme(
    primary = CyberGreen,
    secondary = CyberBlue,
    tertiary = NeonPurple,
    background = CyberDark,
    surface = CyberGrey,
    onPrimary = CyberDark,
    onSecondary = CyberDark,
    onBackground = CyberGreen,
    onSurface = CyberGreen
)

@Composable
fun CyberTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CyberColorScheme,
        content = content
    )
}
