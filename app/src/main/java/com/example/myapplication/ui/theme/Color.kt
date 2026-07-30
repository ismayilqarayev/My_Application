package com.example.myapplication.ui.theme

import androidx.compose.ui.graphics.Color

// Bu fayl sadəcə RƏNG DƏYƏRLƏRİNİ saxlayır (Android Studio-nun standart
// şablonundan gəlir). Bunlar birbaşa istifadə olunmur - Theme.kt faylında
// "primary", "secondary" kimi adlara bağlanır, tətbiqin qalan hissəsində isə
// biz həmişə "MaterialTheme.colorScheme.primary" kimi istifadə edirik (yəni
// "Purple80" adını birbaşa yazmırıq - bu, tünd/açıq rejimə görə avtomatik
// düzgün rəngi seçməyə imkan verir).

// Tünd rejim (dark theme) üçün rənglər
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

// Açıq rejim (light theme) üçün rənglər
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
