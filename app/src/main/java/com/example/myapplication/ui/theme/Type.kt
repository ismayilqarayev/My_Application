package com.example.myapplication.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Bu fayl tətbiqin ŞRİFT (font) tənzimləmələrini saxlayır.
// Material Design 3-də mətn stilləri əvvəlcədən adlandırılıb (bodyLarge,
// titleLarge, labelSmall və s.) - biz "Text(...)" yazanda Compose avtomatik
// düzgün ölçü/qalınlıq seçir, biz hər dəfə fontSize yazmaq məcburiyyətində qalmırıq.
//
// Hazırda yalnız "bodyLarge" (əsas mətn üçün) fərdiləşdirilib, qalanları
// Material-ın standart dəyərlərini istifadə edir.
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Digər standart mətn stillərini burada dəyişmək olar (hazırda deaktivdir):
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)
