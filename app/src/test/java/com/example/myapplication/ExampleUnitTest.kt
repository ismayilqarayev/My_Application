package com.example.myapplication

import org.junit.Test

import org.junit.Assert.*

// Bu, Android Studio-nun layihə yaradanda avtomatik əlavə etdiyi NÜMUNƏ testdir.
// Tətbiqin öz məntiqinə aid heç nə yoxdur - "unit test" necə yazılır göstərmək
// üçün nümunədir. Bu cür testlər telefon/emulyator olmadan, birbaşa
// kompüterdə (host) işləyir - çünki 2+2 kimi sadə hesablamalar Android-ə
// ehtiyac duymur.
/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)   // sadəcə 2+2=4 olduğunu yoxlayır - real test deyil, nümunədir
    }
}