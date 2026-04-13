package com.example.brrbox.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = BrrBoxBlue,
    onPrimary = BrrBoxDarkGrey,
    primaryContainer = BrrBoxDeepBlue,
    onPrimaryContainer = Color.White,
    
    secondary = BrrBoxMediumBlue,
    onSecondary = BrrBoxDarkGrey,
    secondaryContainer = BrrBoxBlue.copy(alpha = 0.2f), // Selected tab background
    onSecondaryContainer = BrrBoxBlue, // Selected tab icon/label
    
    tertiary = BrrBoxSkyBlue,
    onTertiary = BrrBoxDarkGrey,
    
    background = BrrBoxDarkGrey,
    onBackground = BrrBoxWhiteBlue,
    
    surface = BrrBoxDarkGrey,
    onSurface = BrrBoxWhiteBlue,
    
    surfaceVariant = Color(0xFF45494C), // Slightly lighter grey for cards/fields
    onSurfaceVariant = BrrBoxBlue,
    
    outline = BrrBoxMediumBlue,
    inverseSurface = BrrBoxWhiteBlue,
    inverseOnSurface = BrrBoxDarkGrey,
    inversePrimary = BrrBoxDeepBlue
)

private val LightColorScheme = lightColorScheme(
    primary = BrrBoxDarkGrey,
    onPrimary = Color.White,
    primaryContainer = BrrBoxBlue,
    onPrimaryContainer = BrrBoxDarkGrey,
    
    secondary = BrrBoxDeepBlue,
    onSecondary = Color.White,
    secondaryContainer = BrrBoxDarkGrey.copy(alpha = 0.1f),
    onSecondaryContainer = BrrBoxDarkGrey,
    
    tertiary = BrrBoxMediumBlue,
    onTertiary = Color.White,
    
    background = BrrBoxWhiteBlue,
    onBackground = BrrBoxDarkGrey,
    
    surface = BrrBoxWhiteBlue,
    onSurface = BrrBoxDarkGrey,
    
    surfaceVariant = BrrBoxBlue.copy(alpha = 0.3f),
    onSurfaceVariant = BrrBoxDarkGrey,
    
    outline = BrrBoxDeepBlue
)

@Composable
fun BRRBOXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is disabled to maintain brand consistency
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
