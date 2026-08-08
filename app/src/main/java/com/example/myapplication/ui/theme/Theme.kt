package com.example.myapplication.ui.theme

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

// Tünd rejim üçün rəng sxemi - Birbank-ın brend qırmızısı əsas rəng olaraq
// saxlanılır, tünd fonda daha yaxşı görünsün deyə açıq tonu (BirbankRedLight) işlədilir
private val DarkColorScheme = darkColorScheme(
    primary = BirbankRedLight,
    onPrimary = Color(0xFF3A0A0E),
    primaryContainer = BirbankRedDark,
    onPrimaryContainer = BirbankWhite,
    secondary = BirbankGreenContainer,
    onSecondary = BirbankGreen,
    background = BirbankDarkBackground,
    onBackground = BirbankWhite,
    surface = BirbankDarkSurface,
    onSurface = BirbankWhite,
    surfaceVariant = Color(0xFF2A2B2D),
    onSurfaceVariant = Color(0xFFBDC3C7),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3A0A0E),
    errorContainer = BirbankCrimson,
    onErrorContainer = BirbankWhite,
    outline = BirbankDarkBorder,
    outlineVariant = Color(0xFF3A3C3F)
)

// Açıq rejim üçün rəng sxemi - Birbank saytının öz CSS dəyişənlərindən
// (--red, --dark-green, --dark-charcoal, --soft-gray və s.) götürülüb
private val LightColorScheme = lightColorScheme(
    primary = BirbankRed,
    onPrimary = BirbankWhite,
    primaryContainer = BirbankRedContainer,
    onPrimaryContainer = BirbankRedDark,
    secondary = BirbankGreen,
    onSecondary = BirbankWhite,
    secondaryContainer = BirbankGreenContainer,
    onSecondaryContainer = BirbankGreen,
    background = BirbankSoftGray,
    onBackground = BirbankCharcoal,
    surface = BirbankWhite,
    onSurface = BirbankCharcoal,
    surfaceVariant = BirbankHazeGray,
    onSurfaceVariant = BirbankMediumGray,
    error = BirbankCrimson,
    onError = BirbankWhite,
    errorContainer = BirbankRedContainer,
    onErrorContainer = BirbankCrimson,
    outline = BirbankBorderGray,
    outlineVariant = BirbankPaleGray
)

/**
 * Bütün tətbiqi bu funksiya "bükür" (bax: MainActivity.kt-də
 * "MyApplicationTheme { TestApp() }"). Bu sayədə tətbiqin HƏR YERİNDƏ
 * "MaterialTheme.colorScheme.primary" kimi istifadə etdiyimiz rənglər
 * avtomatik olaraq düzgün (tünd/açıq) seçilir.
 */
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),   // telefonun sistem tənzimləməsinə görə tünd/açıq avtomatik seçilir
    // "Dynamic color" - Android 12+ telefonlarda divar kağızından (wallpaper)
    // rəng çıxarma imkanı. BİLƏRƏKDƏN "false" edilib: Birbank kimi sabit brend
    // rəngi (qırmızı) göstərmək istəyiriksə, bu, telefondan-telefona
    // dəyişməməlidir - istifadəçinin divar kağızından asılı olmamalıdır.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Android 12+ (S) və dinamik rəng aktivdirsə - telefonun öz rənglərini istifadə et
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Əks halda, yuxarıda təyin etdiyimiz sabit (Birbank qırmızısı əsaslı) rəngləri istifadə et
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
