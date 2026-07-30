package com.example.myapplication

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

// Bu da Android Studio-nun avtomatik yaratdığı NÜMUNƏ testdir. Yuxarıdakı
// ExampleUnitTest-dən fərqi budur: bu, HƏQİQİ Android telefonda və ya
// emulyatorda işləməlidir (ona görə "instrumented" adlanır), çünki
// "InstrumentationRegistry" kimi Android-ə məxsus alətlərdən istifadə edir.
/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Test edilən tətbiqin "context"i (Android-in tətbiq haqqında məlumat obyekti)
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        // Yoxlayır ki, tətbiqin paket adı düzgündür (build.gradle.kts-dəki "applicationId" ilə eyni olmalıdır)
        assertEquals("com.example.myapplication", appContext.packageName)
    }
}