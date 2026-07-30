package com.example.myapplication.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Tünd rejim üçün rəng "sxemi" - Color.kt-dəki rəngləri Material-ın gözlədiyi
// rollara (primary, secondary, tertiary) bağlayır
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

// Açıq rejim üçün rəng sxemi
private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Digər standart rəngləri burada dəyişmək olar (hazırda deaktivdir):
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

/**
 * Bütün tətbiqi bu funksiya "bükür" (bax: MainActivity.kt-də
 * "MyApplicationTheme { TestApp() }"). Bu sayədə tətbiqin HƏR YERİNDƏ
 * "MaterialTheme.colorScheme.primary" kimi istifadə etdiyimiz rənglər
 * avtomatik olaraq düzgün (tünd/açıq, telefonun öz rənginə uyğun) seçilir.
 */
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),   // telefonun sistem tənzimləməsinə görə tünd/açıq avtomatik seçilir
    // "Dynamic color" - Android 12+ telefonlarda, telefonun divar kağızından
    // (wallpaper) çıxarılan rəngləri istifadə etmə imkanı
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Android 12+ (S) və dinamik rəng aktivdirsə - telefonun öz rənglərini istifadə et
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Əks halda, yuxarıda təyin etdiyimiz sabit (Purple əsaslı) rəngləri istifadə et
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
