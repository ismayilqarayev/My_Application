package com.example.myapplication.ui.theme

import androidx.compose.ui.graphics.Color

// Bu fayl RƏNG DƏYƏRLƏRİNİ saxlayır. Bunlar birbaşa istifadə olunmur -
// Theme.kt faylında "primary", "secondary" kimi adlara bağlanır, tətbiqin
// qalan hissəsində isə biz həmişə "MaterialTheme.colorScheme.primary" kimi
// istifadə edirik.
//
// Neytral/yaşıl/qırmızı (xəta) çalarlar əvvəlcə Birbank (Kapital Bank-ın rəqəmsal
// bank tətbiqi, birbank.az) saytının CSS dəyişənlərindən götürülmüşdü. Əsas brend
// rəngi isə qırmızıdan "sakit mavi"yə dəyişdirilib - imtahan kontekstində qırmızı
// (xəbərdarlıq/səhv ilə assosiasiya olunan rəng) əvəzinə mavi daha rahat/sakit
// hiss yaradır, diqqəti yayındırmır.

// ---- Əsas brend rəngi: "sakit mavi" (əsas düymələr, başlıqlar, aktiv vəziyyət) ----
val CalmBlue = Color(0xFF2B6CB0)           // əsas brend rəngi (əsas düymələr)
val CalmBlueDark = Color(0xFF1A4971)       // tünd variant (basılı/hover vəziyyəti üçün)
val CalmBlueLight = Color(0xFF6FA8DC)      // açıq variant (tünd rejimdə primary üçün əlverişlidir)
val CalmBlueContainer = Color(0xFFE6F0FA)  // çox açıq mavi fon (container rəngi)

// ---- Xəta/təhlükə üçün ayrıca çalar (əsas rəngdən - indi mavi - asılı deyil) ----
val BirbankCrimson = Color(0xFFB11117)     // tünd krimzon - xəta mesajları üçün
val BirbankCrimsonContainer = Color(0xFFFDF0F1) // çox açıq qırmızı fon (xəta container rəngi)

// ---- Aksent yaşıl (uğur, depozit/əmanət kimi "müsbət" əməliyyatlar üçün) ----
val BirbankGreen = Color(0xFF066A3A)
val BirbankGreenContainer = Color(0xFFE1F0E7)

// ---- Neytral rənglər (mətn, fon) ----
val BirbankCharcoal = Color(0xFF242424)        // əsas mətn rəngi
val BirbankCharcoalBlue = Color(0xFF353A3E)    // ikinci dərəcəli mətn
val BirbankMediumGray = Color(0xFF6F6F6F)      // üçüncü dərəcəli mətn / ikonlar
val BirbankBorderGray = Color(0xFFD4D6DB)      // xətt/haşiyə rəngi
val BirbankPaleGray = Color(0xFFE8E8E8)        // açıq haşiyə/ayırıcı xətt
val BirbankSoftGray = Color(0xFFF9F9FA)        // ekranın ümumi fon rəngi
val BirbankHazeGray = Color(0xFFF2F4F7)        // kartların/inputların içi (bir az fərqli fon)
val BirbankWhite = Color(0xFFFFFFFF)

// ---- Tünd rejim üçün neytral rənglər ----
val BirbankDarkBackground = Color(0xFF121314)  // Birbank saytının "charcoal-black-deep" dəyişəni
val BirbankDarkSurface = Color(0xFF1E1F21)
val BirbankDarkBorder = Color(0xFF55595D)
