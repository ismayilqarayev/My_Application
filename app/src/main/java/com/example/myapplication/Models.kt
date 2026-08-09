package com.example.myapplication

// Bu fayl tətbiqdə istifadə olunan "məlumat modellərini" (data class) saxlayır.
// Data class-lar sadəcə məlumat daşıyan strukturlardır (bir qutu kimi düşünün) —
// içində məntiq (funksiya) yoxdur, yalnız sahələr (field) var.

/**
 * Bir imtahan sualını təmsil edir.
 * Firebase Realtime Database-dən oxunanda/yazılanda bu struktur istifadə olunur.
 *
 * DİQQƏT: Firebase-in avtomatik çevirmə (deserialization) mexanizmi işləməsi üçün
 * bütün sahələrin defolt (default) qiyməti olmalıdır - əks halda Firebase
 * "boş constructor" tapa bilməz və proqram xəta verər.
 */
data class Question(
    val id: Int = 0,                              // Sualın unikal nömrəsi (vaxt möhürü əsasında yaradılır)
    val text: String = "",                        // Sualın mətni (ekranda göstərilən başlıq)
    val options: List<String> = emptyList(),      // Cavab variantlarının siyahısı (adətən 4 ədəd)
    val correctAnswerIndex: Int = 0,              // "options" siyahısında düzgün cavabın sıra nömrəsi (0-dan başlayır)
    val imageUrl: String? = null                  // Əgər sualın şəkli varsa, Firebase Storage-dəki linki (yoxdursa null)
)

/**
 * Telefon nömrəsi + parolla təsdiqlənmiş şagirdin hesab məlumatını saxlayır.
 * "users/<telefon>" düyünündə saxlanılır ki, eyni nömrə ilə giriş edəndə
 * ad-soyad avtomatik bərpa olunsun.
 *
 * "deviceId" - hesabın PAYLAŞILMASININ QARŞISINI ALMAQ üçündür: hesab ilk
 * dəfə hansı cihazda aktivləşdirilirsə, o cihazın ID-si burada saxlanılır.
 * Başqa bir cihaz eyni nömrə+parolla daxil olmağa çalışsa, bu ID uyğun
 * gəlmədiyi üçün giriş rədd edilir (bax: TestViewModel.loadUserOrAskName).
 *
 * DİQQƏT: bütün sahələrin defolt qiyməti olmalıdır ki, Firebase-in
 * avtomatik çevirmə (deserialization) mexanizmi işləsin (bax: Question-dakı qeyd).
 */
data class UserInfo(
    val firstName: String = "",   // Ad
    val lastName: String = "",    // Soyad
    val deviceId: String = ""     // Hesabın bağlı olduğu cihazın ID-si (boşdursa hələ heç bir cihaza bağlanmayıb)
)
