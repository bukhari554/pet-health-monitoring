package com.pethealth.monitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Forest = Color(0xFF1F6F4A)
val Leaf = Color(0xFF3C9A6F)
val Sand = Color(0xFFF3EFE6)
val Ink = Color(0xFF1C2B24)
val Mist = Color(0xFFD9E8DE)
val Amber = Color(0xFFC47B2C)
val Coral = Color(0xFFC44536)
val Sky = Color(0xFF2F6F8F)

private val scheme = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    secondary = Sky,
    onSecondary = Color.White,
    background = Sand,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    error = Coral,
    onError = Color.White
)

@Composable
fun PetHealthTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}
