package com.worstalarm.clock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFFFF6B6B),
    secondary = Color(0xFFFFB86B),
    background = Color(0xFF101018),
    surface = Color(0xFF1B1B24)
)
private val Light = lightColorScheme(
    primary = Color(0xFFC84343),
    secondary = Color(0xFFCC7F3E)
)

@Composable
fun WorstAlarmTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) Dark else Light
    MaterialTheme(colorScheme = scheme, content = content)
}
