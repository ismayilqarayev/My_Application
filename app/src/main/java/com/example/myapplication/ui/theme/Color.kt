package com.example.myapplication.ui.theme

import androidx.compose.ui.graphics.Color

// Bu fayl RƏNG DƏYƏRLƏRİNİ saxlayır. Bunlar birbaşa istifadə olunmur -
// Theme.kt faylında "primary", "secondary" kimi adlara bağlanır, tətbiqin
// qalan hissəsində isə biz həmişə "MaterialTheme.colorScheme.primary" kimi
// istifadə edirik.
//
// Palet Birbank (Kapital Bank-ın rəqəmsal bank tətbiqi, birbank.az) tətbiqinin
// öz sayt CSS dəyişənlərindən (--red, --dark-green, --dark-charcoal və s.)
// çıxarılıb ki, uydurma rəng əvəzinə real brendə uyğun olsun.

// ---- Əsas brend qırmızısı (Birbank-ın düymələrində istifadə olunan rəng) ----
val BirbankRed = Color(0xFFEC3342)         // əsas brend rəngi (əsas düymələr)
val BirbankRedDark = Color(0xFFD42534)     // tünd variant (basılı/hover vəziyyəti üçün)
val BirbankRedLight = Color(0xFFF74454)    // açıq variant (tünd rejimdə primary üçün əlverişlidir)
val BirbankRedContainer = Color(0xFFFDF0F1) // çox açıq qırmızı fon (container rəngi)

// ---- Xəta/təhlükə üçün ayrıca çalar (əsas qırmızıdan fərqləndirmək üçün) ----
val BirbankCrimson = Color(0xFFB11117)     // tünd krimzon - xəta mesajları üçün

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
