package com.example.myapplication.ui.theme

import androidx.compose.ui.graphics.Color

// Bu fayl RƏNG DƏYƏRLƏRİNİ saxlayır. Bunlar birbaşa istifadə olunmur -
// Theme.kt faylında "primary", "secondary" kimi adlara bağlanır, tətbiqin
// qalan hissəsində isə biz həmişə "MaterialTheme.colorScheme.primary" kimi
// istifadə edirik.
//
// Neytral/yaşıl/qırmızı (xəta) çalarlar əvvəlcə Birbank (Kapital Bank-ın rəqəmsal
// bank tətbiqi, birbank.az) saytının CSS dəyişənlərindən götürülmüşdü. Əsas brend
// rəngi (BrandPrimary*) bir neçə dəfə dəyişdirilib (qırmızı -> sakit mavi -> yenidən
// qırmızı) - ona görə "neutral" adlandırılıb ki, növbəti dəyişiklik yalnız bu 4
// dəyərin özünü yeniləməklə olsun, adı il rəngi uyğunsuz qalmasın.

// ---- Əsas brend rəngi (əsas düymələr, başlıqlar, aktiv vəziyyət) ----
val BrandPrimary = Color(0xFFEC3342)           // əsas brend rəngi (əsas düymələr)
val BrandPrimaryDark = Color(0xFFD42534)       // tünd variant (basılı/hover vəziyyəti üçün)
val BrandPrimaryLight = Color(0xFFF74454)      // açıq variant (tünd rejimdə primary üçün əlverişlidir)
val BrandPrimaryContainer = Color(0xFFFDF0F1)  // çox açıq fon (container rəngi)

// ---- Xəta/təhlükə üçün ayrıca çalar (əsas rəngdən asılı deyil) ----
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

// ---- "Dərs rəfi" aksentləri: hər sınaq kartına fərqli fənn ikonu+rəngi verməkçün ----
// (CategorySelectionScreen-də dövri olaraq istifadə olunur ki, kartlar eyni görünməsin,
// məktəb dərsliyi/dəftər hissi versin). Hər rəngin öz açıq konteyner fonu + tünd mətn/ikon
// tonu var - kontrast üçün.
val LessonBlue = Color(0xFF2B6CB0)
val LessonBlueContainer = Color(0xFFE6F0FA)
val LessonOrange = Color(0xFFB5650C)
val LessonOrangeContainer = Color(0xFFFCE9D2)
val LessonGreen = Color(0xFF2E7D4F)
val LessonGreenContainer = Color(0xFFE1F0E7)
val LessonPurple = Color(0xFF6B4FA0)
val LessonPurpleContainer = Color(0xFFEDE7F8)
val LessonPink = Color(0xFFB5467E)
val LessonPinkContainer = Color(0xFFFBE7F0)
val LessonTeal = Color(0xFF127D82)
val LessonTealContainer = Color(0xFFDFF3F4)
