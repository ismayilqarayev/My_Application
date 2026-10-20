
package com.example.myapplication

// Bu fayl tətbiqdə istifadə olunan "məlumat modellərini" (data class) saxlayır.
// Data class-lar sadəcə məlumat daşıyan strukturlardır (bir qutu kimi düşünün) —
// içində məntiq (funksiya) yoxdur, yalnız sahələr (field) var.

/**
 * Bir imtahan sualını təmsil edir. Backend-in JSON cavabından (Gson ilə) yaradılır.
 *
 * DİQQƏT: bütün sahələrin defolt qiyməti olmalıdır ki, Gson boş constructor
 * tapıb obyekti yarada bilsin.
 */
data class Question(
    val id: Long = 0,                             // Sualın serverdəki unikal ID-si
    val text: String = "",                        // Sualın mətni (ekranda göstərilən başlıq)
    val options: List<String> = emptyList(),      // Cavab variantlarının siyahısı (adətən 4 ədəd)
    val correctAnswerIndex: Int = 0,              // "options" siyahısında düzgün cavabın sıra nömrəsi (0-dan başlayır)
    val imageUrl: String? = null                  // Şəklin tam ünvanı (yoxdursa null)
)

/** Daxil olmuş şagirdin ad-soyadı (serverdəki "users" cədvəlindən gəlir). */
data class UserInfo(
    val firstName: String = "",
    val lastName: String = ""
)

/**
 * Bir sınaq. "number" sınağın daimi identifikatorudur (1-3 pulsuz, 4+ ödənişli),
 * "name" isə admin tərəfindən verilmiş istəyə bağlı addır (məs. "Fizika").
 */
data class Category(
    val number: Int = 0,
    val name: String? = null
) {
    val displayName: String get() = name ?: "sınaq $number"
}
