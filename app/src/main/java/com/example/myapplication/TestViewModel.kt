package com.example.myapplication

import android.app.Activity
import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

// ==========================================================================
// BU FAYL TƏTBIQIN "BEYNİ"DİR.
// Ekranda görünən heç nə (düymə, mətn, rəng) burada yoxdur - o, MainActivity.kt-dədir.
// Burada YALNIZ məntiq var: Firebase-ə qoşulma, sualları yükləmə, cavabları
// yoxlama, xal hesablama, taймer, ödəniş axını və s.
//
// Bu, "ViewModel" adlanan bir Android arxitektura nümunəsidir: ekran fırlanıb
// yenidən çəkilsə belə (rotation), bu sinifin içindəki məlumatlar itmir,
// çünki ViewModel ekrandan ayrı yaşayır.
// ==========================================================================

/**
 * Tətbiqin hansı ekranda olduğunu göstərən vəziyyətlər (state).
 * MainActivity.kt-dəki "when (state)" bloku bu siyahıya əsasən
 * müvafiq Composable ekranı göstərir.
 */
enum class ScreenState {
    RoleSelection,        // "Şagird" / "Müəllim" seçimi - başlanğıc ekran
    PhoneEntry,           // Telefon nömrəsi daxil etmə (giriş)
    CodeEntry,            // SMS ilə gələn təsdiq kodunu daxil etmə ekranı
    NameEntry,            // (yalnız YENİ hesab üçün) ad-soyad daxil etmə ekranı
    CategorySelection,    // 25 sınaqdan birini seçmə ekranı
    Testing,              // Sualların göstərildiyi, cavab verilən ekran
    Result,               // İmtahan bitəndə nəticənin göstərildiyi ekran
    Loading,              // Suallar Firebase-dən yüklənərkən göstərilən spinner ekranı
    Admin                 // Yeni sual əlavə etmək üçün admin paneli
}

/**
 * "AndroidViewModel" - normal ViewModel-dən fərqli olaraq, Application (tətbiqin
 * özü) obyektinə çıxışı var. Bizə bu, Firebase Phone Auth üçün lazımdır.
 */
class TestViewModel(application: Application) : AndroidViewModel(application) {

    // ---------------------------------------------------------------------
    // EKRAN VƏ İMTAHAN VƏZİYYƏTİ
    // "by mutableStateOf(...)" - bu, Compose-a deyir ki, "bu dəyər dəyişəndə
    // ekranı avtomatik yenidən çək". Adi "var" olsaydı, dəyər dəyişəndə
    // ekran YENİLƏNMƏZDİ.
    // ---------------------------------------------------------------------
    var screenState by mutableStateOf(ScreenState.RoleSelection)  // hazırda hansı ekrandayıq
    var userInfo by mutableStateOf<UserInfo?>(null)                // qeydiyyatdan keçən şagirdin ad-soyadı
    var currentQuestionIndex by mutableStateOf(0)                  // hazırkı sualın sıra nömrəsi (0-dan başlayır)
    var score by mutableStateOf(0)                                 // düzgün cavabların sayı
    var timeLeft by mutableStateOf(60)                             // qalan vaxt (saniyə ilə)
    var selectedCategory by mutableStateOf("")                     // seçilmiş sınaq (məs. "sınaq 1")
    private var timerJob: Job? = null                              // taймerin arxa fonda işləyən coroutine-i (dayandırmaq üçün saxlanılır)

    // ---------------------------------------------------------------------
    // FIREBASE BAĞLANTILARI
    // "Ref" (reference) - Firebase-in içindəki konkret bir "qovluğa" işarədir.
    // Məsələn, questionsRef = Firebase-dəki "questions" adlı qovluq.
    // ---------------------------------------------------------------------
    private val database = FirebaseDatabase.getInstance("https://myapplication-223cacbf-default-rtdb.firebaseio.com")
    private val questionsRef = database.getReference("questions")   // bütün sınaqların sualları burada saxlanılır
    private val resultsRef = database.getReference("results")       // hər imtahanın nəticəsi burada saxlanılır
    private val purchasesRef = database.getReference("purchases")   // kim ödəniş edib (telefon nömrəsi -> açıq/bağlı)
    private val usersRef = database.getReference("users")           // telefon nömrəsinə bağlı hesab (ad-soyad) məlumatı
    private val storage = FirebaseStorage.getInstance()
    private val storageRef = storage.getReference("question_images") // sual şəkilləri burada saxlanılır

    // Firebase Phone Auth - telefon nömrəsini SMS kodu ilə təsdiqləyir.
    // Uğurlu girişdən sonra bu sessiya telefonda avtomatik yadda saxlanılır
    // (FirebaseAuth SDK-nın öz işidir) - yəni eyni cihazda tətbiqi bağlayıb
    // açsanız, yenidən kod daxil etməyə ehtiyac qalmır.
    private val auth = FirebaseAuth.getInstance()

    // "_questions" gizli (private), xaricdən dəyişdirilə bilməz.
    // "questions" isə ona sadəcə BAXMAQ üçün açıq versiyadır (List, MutableList deyil).
    // Bu, "encapsulation" adlanır - başqa fayllar səhvən sualları poza bilməsin deyə.
    private val _questions = mutableStateListOf<Question>()
    val questions: List<Question> = _questions

    var isLoadingQuestions by mutableStateOf(false)
        private set   // yalnız bu fayl daxilində dəyişdirilə bilər, xaricdən oxuna bilər

    var errorMessage by mutableStateOf<String?>(null)
        private set   // xəta olanda burada mətn görünür, MainActivity.kt bunu AlertDialog kimi göstərir

    // ---------------------------------------------------------------------
    // GİRİŞ: TELEFON NÖMRƏSİ + SMS TƏSDİQİ (Firebase Phone Auth)
    //
    // Bu, tətbiqin "hesab" sistemidir: şagird telefon nömrəsini yazır, SMS ilə
    // gələn kodu təsdiqləyir. Bu nömrə onun daimi hesab ID-si olur - başqa
    // cihazdan eyni nömrə ilə giriş etsə, ad-soyadı və açılış statusu
    // (aşağıda) avtomatik bərpa olunur.
    // ---------------------------------------------------------------------
    var phoneNumber by mutableStateOf("")   // PhoneEntry ekranında yazılan (ölkə kodu olmadan, məs. "501234567")
        private set
    var isSendingCode by mutableStateOf(false)      // SMS kodu göndərilərkən true (spinner üçün)
        private set
    var isVerifyingCode by mutableStateOf(false)    // kod təsdiqlənərkən true (spinner üçün)
        private set
    private var verificationId: String? = null      // Firebase-in verdiyi, kodu təsdiqləmək üçün lazım olan ID

    // ---------------------------------------------------------------------
    // GİRİŞ: NÖMRƏ + PAROL (SMS-siz, qayıdan istifadəçilər üçün)
    //
    // Yalnız İLK qeydiyyatda SMS tələb olunur. Həmin zaman istifadəçi bir
    // parol da təyin edir (bax: register()) - bu parol Firebase Auth-un öz
    // Email/Parol provayderinə "bağlanır" (linkWithCredential), telefon
    // nömrəsindən düzəldilən süni (sintetik) email ünvanı ilə. Bu sayədə
    // parolun özü heç vaxt bizim kodumuzda və ya Realtime Database-də
    // saxlanılmır - onu Firebase öz təhlükəsiz (hash-lənmiş) formada saxlayır.
    // Növbəti girişlərdə isə artıq SMS göndərilmir, sadəcə nömrə+parol
    // Firebase-ə göndərilib yoxlanılır (signInWithEmailAndPassword).
    // ---------------------------------------------------------------------
    var isCheckingPhone by mutableStateOf(false)         // nömrənin qeydiyyatlı olub-olmadığı yoxlanılarkən true
        private set
    var isReturningUser by mutableStateOf(false)         // true olanda ekranda SMS əvəzinə parol sahəsi göstərilir
        private set
    var isLoggingInWithPassword by mutableStateOf(false) // parolla giriş edilərkən true (spinner üçün)
        private set

    /**
     * Telefon nömrəsindən Firebase Auth üçün "sintetik" (real olmayan, amma
     * düzgün formatlı) email ünvanı düzəldir. Firebase-in Email/Parol
     * provayderi işləmək üçün email formatı tələb edir, amma bu ünvana heç
     * vaxt real məktub getmir - sadəcə hər istifadəçi üçün unikal açar rolunu oynayır.
     */
    private fun fakeEmailFor(phoneKey: String) = "$phoneKey@myapplication-223cacbf.local"

    // ---------------------------------------------------------------------
    // ÖDƏNİŞ / AÇILIŞ MƏNTİQİ (4-cü sınaqdan yuxarı ödənişli hissə)
    //
    // QEYD: Bank API-si (Kapital Bank merchant hesabı) əvəzinə sadə kart-karta
    // köçürmə istifadə olunur: şagird admininin kart nömrəsinə köçürmə edir,
    // admin isə Admin panelindən əl ilə həmin telefon nömrəsini "açır".
    // Bu, tamamilə PULSUZ (Spark plan) işləyir, backend/bank inteqrasiyası lazım deyil.
    // ---------------------------------------------------------------------
    var isUnlocked by mutableStateOf(false)      // true olanda BÜTÜN sınaqlar açılır
        private set

    // Firebase-dəki "purchases/<telefon>/unlocked" sahəsini CANLI (real-time) izləyən dinləyici.
    // Bu sayədə ödəniş təsdiqlənən kimi, tətbiqi yenidən açmadan, ekran avtomatik dəyişir.
    private var unlockListener: ValueEventListener? = null

    /**
     * ViewModel ilk dəfə yaradılanda (tətbiq açılanda) işə düşür.
     * Əgər bu cihazda artıq giriş edilibsə (FirebaseAuth sessiyası saxlanılıb),
     * telefon nömrəsi yenidən soruşulmadan birbaşa hesabı yükləyirik.
     *
     * "onlyIfStillOnRoleSelection = true" - bu YOXLAMA vacibdir: Firebase-ə
     * sorğu (usersRef.get()) ASINXRON işlədiyi üçün, cavab gələnə qədər
     * istifadəçi artıq "Şagird" və ya "Müəllim" düyməsinə basıb irəli
     * keçmiş ola bilər. Bu yoxlama olmasaydı, avtomatik giriş cavabı
     * gecikəndə istifadəçinin öz seçimini "əzib" onu gözlənilmədən başqa
     * ekrana apara bilərdi (bax: RoleSelectionScreen-dən sonrakı "gözlənilməz
     * tullanma" problemi).
     */
    init {
        auth.currentUser?.phoneNumber?.let { phone ->
            loadUserOrAskName(phone, onlyIfStillOnRoleSelection = true)
        }
    }

    /** Xəta mesajını bağlayır (istifadəçi "Tamam" düyməsinə basanda çağırılır). */
    fun clearError() {
        errorMessage = null
    }

    /**
     * Telefon nömrəsindən rəqəm olmayan hər şeyi (boşluq, "+", "-") təmizləyir.
     * Məsələn: "+994 55 123-45-67" -> "994551234567"
     * Bu lazımdır, çünki Firebase-in açar (key) adlarında bəzi simvollar
     * (".", "#", "$", "/") işlədilə bilməz.
     */
    private fun sanitizePhone(phone: String) = phone.filter { it.isDigit() }

    /**
     * Bir telefon nömrəsini Firebase açarı kimi İSTİFADƏ OLUNAN, SABİT formata
     * gətirir: ölkə kodu (994) daxil, cəmi rəqəmlər. Giriş edən şagirdlərin
     * açarı (loadUserOrAskName vasitəsilə) HƏMİŞƏ bu formatdadır (Firebase-in
     * "+994..." E.164 nömrəsindən sanitizasiya olunur). Admin isə "purchases"
     * düyününə əl ilə yazarkən (manualUnlock) ölkə kodunu yazmaya da bilər -
     * bu funksiya hər iki halı (9 rəqəmli yerli, ya da 12 rəqəmli tam) eyni
     * açara gətirərək uyğunsuzluğun qarşısını alır.
     */
    private fun normalizePhoneKey(phone: String): String {
        val digits = sanitizePhone(phone)
        return if (digits.length == 9) "994$digits" else digits
    }

    /** PhoneEntry ekranında istifadəçi nömrə yazdıqca çağırılır. */
    fun updatePhoneNumber(value: String) {
        phoneNumber = value
        // Nömrəni dəyişəndə əvvəlki "qayıdan istifadəçi" (parol sahəsi) vəziyyətini
        // sıfırlayırıq - yeni nömrə üçün yenidən yoxlama aparılmalıdır.
        if (isReturningUser) isReturningUser = false
    }

    /**
     * PhoneEntry ekranında "Davam et" düyməsinə basılanda çağırılır.
     * Əvvəlcə bu nömrənin ARTIQ qeydiyyatdan keçib-keçmədiyini yoxlayır:
     *  - Qeydiyyatlıdırsa (qayıdan istifadəçi) -> parol sahəsini göstəririk,
     *    SMS göndərilmir.
     *  - Qeydiyyatlı deyilsə (yeni istifadəçi) -> köhnə SMS axını başlayır.
     */
    fun checkPhoneAndProceed(activity: Activity) {
        val sanitized = sanitizePhone(phoneNumber)
        if (sanitized.length < 9) {
            errorMessage = "Zəhmət olmasa düzgün telefon nömrəsi daxil edin."
            return
        }
        val fullKey = normalizePhoneKey(sanitized)

        isCheckingPhone = true
        errorMessage = null
        usersRef.child(fullKey).get()
            .addOnSuccessListener { snapshot ->
                isCheckingPhone = false
                if (snapshot.exists()) {
                    isReturningUser = true
                } else {
                    sendVerificationCode(activity)
                }
            }
            .addOnFailureListener {
                // Yoxlama uğursuz olsa belə istifadəçini bloklamayaq - SMS axını ilə davam edək
                isCheckingPhone = false
                sendVerificationCode(activity)
            }
    }

    /**
     * Qayıdan istifadəçi (isReturningUser = true) parolu yazıb "Daxil ol"
     * düyməsinə basanda çağırılır. SMS-siz, birbaşa nömrə+parol ilə Firebase
     * Auth-a daxil olur.
     */
    fun loginWithPassword(password: String) {
        if (password.isBlank()) {
            errorMessage = "Zəhmət olmasa parolu daxil edin."
            return
        }
        val fullKey = normalizePhoneKey(phoneNumber)
        isLoggingInWithPassword = true
        errorMessage = null

        auth.signInWithEmailAndPassword(fakeEmailFor(fullKey), password)
            .addOnSuccessListener {
                isLoggingInWithPassword = false
                loadUserOrAskName(fullKey)
            }
            .addOnFailureListener {
                isLoggingInWithPassword = false
                errorMessage = "Giriş uğursuz oldu: nömrə və ya parol yanlışdır."
            }
    }

    /**
     * "Kod göndər" düyməsinə basılanda çağırılır. Azərbaycan ölkə kodunu
     * (+994) avtomatik əlavə edib Firebase-ə SMS göndərməyi tapşırır.
     *
     * "activity" lazımdır, çünki Firebase bəzən (cihaz etibarlı deyilsə)
     * reCAPTCHA təsdiqini ekранда göstərməli olur - bunun üçün Activity tələb edir.
     */
    fun sendVerificationCode(activity: Activity) {
        val fullPhone = "+994${sanitizePhone(phoneNumber)}"
        if (sanitizePhone(phoneNumber).length < 9) {
            errorMessage = "Zəhmət olmasa düzgün telefon nömrəsi daxil edin."
            return
        }

        isSendingCode = true
        errorMessage = null

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(fullPhone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                // Bəzi cihazlarda Google Play Services kodu avtomatik tutub təsdiqləyir -
                // bu halda istifadəçi kodu əl ilə yazmır, birbaşa giriş edilir.
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signInWithCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    isSendingCode = false
                    errorMessage = "Kod göndərilə bilmədi: ${e.message}"
                }

                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    isSendingCode = false
                    verificationId = id
                    screenState = ScreenState.CodeEntry
                }
            })
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    /** İstifadəçi SMS ilə gələn 6 rəqəmli kodu yazıb "Təsdiqlə" basanda çağırılır. */
    fun verifyCode(code: String) {
        val id = verificationId
        if (id == null) {
            errorMessage = "Əvvəlcə kod tələb edin."
            return
        }
        isVerifyingCode = true
        signInWithCredential(PhoneAuthProvider.getCredential(id, code))
    }

    /** Həm avtomatik (onVerificationCompleted), həm də əl ilə (verifyCode) təsdiqdən sonra çağırılır. */
    private fun signInWithCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                isSendingCode = false
                isVerifyingCode = false
                result.user?.phoneNumber?.let { phone -> loadUserOrAskName(phone) }
            }
            .addOnFailureListener { e ->
                isSendingCode = false
                isVerifyingCode = false
                errorMessage = "Kod yanlışdır: ${e.message}"
            }
    }

    /**
     * Telefon təsdiqləndikdən sonra çağırılır: "users/<telefon>" düyünündə
     * bu nömrə üçün əvvəlcədən qeydiyyat varmı yoxlayır.
     *  - Varsa (qayıdan istifadəçi) -> ad-soyadı bərpa edib birbaşa kateqoriya ekranına keçir
     *  - Yoxdursa (yeni istifadəçi) -> ad-soyad soruşan ekrana keçir
     * Hər iki halda açılış statusunu (purchases) canlı dinləməyə başlayır.
     */
    private fun loadUserOrAskName(fullPhone: String, onlyIfStillOnRoleSelection: Boolean = false) {
        val sanitized = sanitizePhone(fullPhone)
        phoneNumber = sanitized
        startListeningForUnlock(sanitized)

        usersRef.child(sanitized).get()
            .addOnSuccessListener { snapshot ->
                // Bax yuxarıdakı "init" bloku qeydinə: istifadəçi artıq özü
                // naviqasiya edibsə, bu gecikmiş cavabın onu "əzməsinin" qarşısını alırıq
                if (onlyIfStillOnRoleSelection && screenState != ScreenState.RoleSelection) return@addOnSuccessListener

                val existing = snapshot.getValue(UserInfo::class.java)
                if (existing != null) {
                    userInfo = existing
                    screenState = ScreenState.CategorySelection
                } else {
                    screenState = ScreenState.NameEntry
                }
            }
            .addOnFailureListener {
                if (onlyIfStillOnRoleSelection && screenState != ScreenState.RoleSelection) return@addOnFailureListener
                // Şəbəkə xətası olsa belə, istifadəçini bloklamayaq - ad soruşub davam edək
                screenState = ScreenState.NameEntry
            }
    }

    /**
     * Firebase-də "purchases/<telefon>/unlocked" sahəsinə CANLI dinləyici qoşur.
     * "addValueEventListener" (addListenerForSingleValueEvent-dən fərqli olaraq)
     * BİR DƏFƏ deyil, hər dəfə həmin sahə dəyişəndə avtomatik işə düşür.
     * Məhz buna görə ödəniş edilən kimi tətbiq özü açılır - biz heç nəyi
     * yenidən yükləməli olmuruq, Firebase bizə xəbər verir.
     */
    fun startListeningForUnlock(phone: String) {
        val sanitized = sanitizePhone(phone)
        if (sanitized.length < 9) return   // düzgün olmayan nömrə ilə boş yerə dinləməyə başlamayaq

        // Əvvəlki dinləyici varsa, onu ləğv edirik - əks halda iki dəfə dinləmiş olarıq
        unlockListener?.let { purchasesRef.removeEventListener(it) }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Firebase-dən "unlocked: true/false" oxuyuruq, yoxdursa false sayırıq
                isUnlocked = snapshot.child("unlocked").getValue(Boolean::class.java) == true
            }

            override fun onCancelled(error: DatabaseError) {
                errorMessage = "Açılış statusu yoxlanıla bilmədi: ${error.message}"
            }
        }
        purchasesRef.child(sanitized).addValueEventListener(listener)
        unlockListener = listener
    }

    /**
     * YALNIZ ADMİN PANELİNDƏN çağırılır. Şagird kart-karta köçürməni etdikdən
     * sonra, admin bu funksiya ilə həmin telefon nömrəsini əl ilə "açır".
     *
     * Bu, Firebase Realtime Database-ə BİRBAŞA (client-dən) yazır - heç bir
     * bank inteqrasiyası/backend lazım deyil, ona görə tamamilə PULSUZ (Spark
     * plan) işləyir.
     *
     * TƏHLÜKƏSİZLİK QEYDİ: Admin paneli artıq parolla qorunur (bax:
     * MainActivity.kt-də admin girişi), amma "purchases" düyünü Firebase
     * qaydalarında client-dən yazıla bilən edilib (bax: database.rules.json).
     * Bu, kiçik/şəxsi layihə üçün kifayət edən sadə bir modeldir.
     */
    fun manualUnlock(phone: String) {
        val sanitized = normalizePhoneKey(phone)
        if (sanitized.length < 9) {
            errorMessage = "Zəhmət olmasa düzgün telefon nömrəsi daxil edin."
            return
        }

        purchasesRef.child(sanitized).setValue(
            mapOf("unlocked" to true, "updatedAt" to ServerValue.TIMESTAMP)
        ).addOnFailureListener { e ->
            errorMessage = "Açılış qeyd edilə bilmədi: ${e.message}"
        }
    }

    // ---------------------------------------------------------------------
    // ADMIN: YENİ SUAL ƏLAVƏ ETMƏ
    // ---------------------------------------------------------------------

    /**
     * Admin panelindən yeni sual əlavə edəndə çağırılır.
     * Əgər şəkil seçilibsə, əvvəlcə onu Firebase Storage-a yükləyir, sonra
     * sualın özünü (mətn + variantlar + şəklin linki) Realtime Database-ə yazır.
     *
     * "viewModelScope.launch" - bu işi arxa fonda (background thread) başladır
     * ki, şəkil yüklənərkən ekran "donmasın".
     */
    fun uploadQuestion(
        category: String,
        text: String,
        options: List<String>,
        correctIndex: Int,
        imageUri: Uri?
    ) {
        viewModelScope.launch {
            try {
                isLoadingQuestions = true
                var downloadUrl: String? = null

                // Əgər admin şəkil seçibsə, əvvəlcə onu Storage-a yükləyirik
                imageUri?.let { uri ->
                    val fileName = "${System.currentTimeMillis()}.jpg"   // hər şəklə unikal ad (vaxt möhürü)
                    val fileRef = storageRef.child(fileName)
                    fileRef.putFile(uri).await()                         // "await()" - nəticə gələnə qədər gözləyir
                    downloadUrl = fileRef.downloadUrl.await().toString()  // yüklənmiş şəklin ictimai linkini alırıq
                }

                // Sualın unikal ID-si kimi hazırkı vaxtı istifadə edirik (praktik həll)
                val questionId = System.currentTimeMillis().toInt()
                val newQuestion = Question(
                    id = questionId,
                    text = text,
                    options = options,
                    correctAnswerIndex = correctIndex,
                    imageUrl = downloadUrl
                )

                // Firebase-ə yazırıq: questions/<kateqoriya>/<sualın id-si> = sualın özü
                database.getReference("questions").child(category).child(questionId.toString())
                    .setValue(newQuestion).await()

                isLoadingQuestions = false
                screenState = ScreenState.CategorySelection   // uğurlu olanda kateqoriya ekranına qayıdırıq
            } catch (e: Exception) {
                isLoadingQuestions = false
                errorMessage = "Sual yadda saxlanıla bilmədi: ${e.message}"
            }
        }
    }

    /**
     * Admin panelindən BİR DƏFƏYƏ çoxlu sual əlavə etmək üçün. Hər sualı
     * tək-tək admin panelindən yazmaq əvəzinə, admin bütün sualları xüsusi
     * formatda bir mətn qutusuna yapışdırır, bu funksiya onları ayırıb
     * Firebase-ə yazır.
     *
     * GÖZLƏNİLƏN FORMAT (hər sual boş sətirlə ayrılır):
     *   Sualın mətni?
     *   Səhv variant
     *   *Düzgün variant
     *   Səhv variant
     *
     * Yəni: bloqun birinci sətri - sualın mətni, qalan sətirlər - variantlar,
     * düzgün variantın əvvəlinə "*" qoyulur.
     */
    fun uploadQuestionsBulk(category: String, rawText: String) {
        // Sualları boş sətir(lər)ə görə ayırırıq (hər blok = bir sual)
        val blocks = rawText.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotBlank() }
        if (blocks.isEmpty()) {
            errorMessage = "Heç bir sual tapılmadı. Zəhmət olmasa formatı yoxlayın."
            return
        }

        val baseId = System.currentTimeMillis().toInt()
        val questions = blocks.mapIndexedNotNull { index, block ->
            val lines = block.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.size < 2) return@mapIndexedNotNull null   // sual + ən azı 1 variant lazımdır

            val questionText = lines.first()
            val optionLines = lines.drop(1)
            // "*" ilə başlayan variantın sırasını tapırıq (yoxdursa ilk variant düzgün sayılır)
            val correctIndex = optionLines.indexOfFirst { it.startsWith("*") }.let { if (it == -1) 0 else it }
            val options = optionLines.map { it.removePrefix("*").trim() }

            Question(id = baseId + index, text = questionText, options = options, correctAnswerIndex = correctIndex)
        }

        if (questions.isEmpty()) {
            errorMessage = "Düzgün formatlı heç bir sual tapılmadı (hər sualın ən azı 1 variantı olmalıdır)."
            return
        }

        viewModelScope.launch {
            try {
                isLoadingQuestions = true
                // "updateChildren" bir sorğuda BİRDƏN ÇOX yeri eyni anda yazır -
                // hər sual üçün ayrıca setValue çağırmaqdan daha səmərəlidir.
                val updates: Map<String, Question> = questions.associateBy { it.id.toString() }
                questionsRef.child(category).updateChildren(updates).await()
                isLoadingQuestions = false
                screenState = ScreenState.CategorySelection
            } catch (e: Exception) {
                isLoadingQuestions = false
                errorMessage = "Toplu sual saxlanıla bilmədi: ${e.message}"
            }
        }
    }

    // ---------------------------------------------------------------------
    // QEYDİYYAT VƏ KATEQORİYA SEÇİMİ
    // ---------------------------------------------------------------------

    /**
     * NameEntry ekranında (yalnız YENİ hesab üçün, telefon artıq təsdiqləndikdən
     * sonra) "Davam et" düyməsinə basılanda çağırılır. Ad-soyadı "users/<telefon>"
     * düyününə yazır ki, növbəti girişdə (bu və ya başqa cihazda) bərpa olunsun.
     *
     * Həmçinin bu addımda təyin edilən PAROLU Firebase Auth hesabına bağlayır
     * (linkWithCredential) ki, növbəti girişlərdə artıq SMS lazım olmasın -
     * bax: loginWithPassword().
     */
    fun register(firstName: String, lastName: String, password: String) {
        val info = UserInfo(firstName, lastName)
        userInfo = info
        if (phoneNumber.isNotBlank()) {
            usersRef.child(phoneNumber).setValue(info)
        }

        if (password.length >= 4) {
            val credential = EmailAuthProvider.getCredential(fakeEmailFor(phoneNumber), password)
            auth.currentUser?.linkWithCredential(credential)
                ?.addOnFailureListener { e ->
                    errorMessage = "Parol saxlanıla bilmədi: ${e.message}"
                }
        }

        screenState = ScreenState.CategorySelection
    }

    /** İstifadəçi bir sınaq (kateqoriya) seçəndə çağırılır. */
    fun selectCategory(category: String) {
        selectedCategory = category
        loadQuestionsForCategory(category)
    }

    /**
     * Seçilmiş kateqoriyanın suallarını Firebase-dən yükləyir.
     * "addListenerForSingleValueEvent" - "startListeningForUnlock"-dan fərqli
     * olaraq YALNIZ BİR DƏFƏ məlumat oxuyur, sonra dinləməyi dayandırır
     * (çünki suallar dəyişəndə ekranı canlı yeniləməyə ehtiyac yoxdur).
     */
    private fun loadQuestionsForCategory(category: String) {
        isLoadingQuestions = true
        screenState = ScreenState.Loading   // "Yüklənir..." spinner ekranı göstərilir

        questionsRef.child(category).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _questions.clear()
                // Firebase-dən gələn hər alt-elementi Question obyektinə çeviririk
                for (postSnapshot in snapshot.children) {
                    val question = postSnapshot.getValue(Question::class.java)
                    question?.let { _questions.add(it) }
                }

                // Əgər bu kateqoriyada heç bir sual yoxdursa (Firebase boşdursa),
                // hazır (default) sualları yaradıb yükləyirik
                if (_questions.isEmpty()) {
                    seedDefaultQuestions(category)
                }

                startTest()
                isLoadingQuestions = false
            }

            override fun onCancelled(error: DatabaseError) {
                // İnternet yoxdursa və ya Firebase-də xəta olsa, istifadəçini
                // əbədi "Yüklənir..." ekranında saxlamaq əvəzinə geri qaytarırıq
                isLoadingQuestions = false
                errorMessage = "Suallar yüklənə bilmədi: ${error.message}"
                screenState = ScreenState.CategorySelection
            }
        })
    }

    /**
     * Yalnız 1-ci, 2-ci və 3-cü sınaqlar üçün "hazır" (nümunə) suallar yaradır.
     * Bu, Firebase-i əl ilə doldurmadan tətbiqi test etmək üçün rahat üsuldur.
     * 4-cü sınaqdan yuxarı üçün hazır sual dəsti YOXDUR (ona görə kilidlidir).
     */
    private fun seedDefaultQuestions(category: String) {
        val defaultQuestions = when (category) {
            "sınaq 1" -> getLogicQuestions()
            "sınaq 2" -> getAnimalQuestions()
            "sınaq 3" -> getNatureQuestions()
            else -> emptyList()
        }

        _questions.addAll(defaultQuestions)

        // Yaradılan sualları Firebase-ə də yazırıq ki, növbəti dəfə eyni işi
        // təkrar etməyə ehtiyac qalmasın
        viewModelScope.launch {
            try {
                defaultQuestions.forEach { questionsRef.child(category).child(it.id.toString()).setValue(it) }
            } catch (e: Exception) {
                // Bura qəsdən boş qalıb: bu, arxa fonda edilən "ehtiyat" yazısıdır -
                // uğursuz olsa belə, suallar artıq yaddaşda (_questions) var və
                // imtahan davam edə bilər, ona görə istifadəçiyə xəta göstərmirik.
            }
        }
    }

    /** 1-ci sınaq üçün 25 ədəd nümunə "məntiq" sualı yaradır. */
    private fun getLogicQuestions(): List<Question> {
        return (1..25).map { Question(it, "Məntiq Sualı $it: ...", listOf("Variant A", "Variant B", "Variant C", "Variant D"), 0) }
    }

    /** 2-ci sınaq üçün 25 ədəd nümunə "heyvanlar" sualı yaradır. */
    private fun getAnimalQuestions(): List<Question> {
        return (1..25).map { Question(it, "Heyvanlar Sualı $it: ...", listOf("Variant A", "Variant B", "Variant C", "Variant D"), 1) }
    }

    /** 3-cü sınaq üçün 25 ədəd nümunə "təbiət" sualı yaradır. */
    private fun getNatureQuestions(): List<Question> {
        return (1..25).map { Question(it, "Təbiət Sualı $it: ...", listOf("Variant A", "Variant B", "Variant C", "Variant D"), 2) }
    }

    // ---------------------------------------------------------------------
    // İMTAHAN AXINI: BAŞLAMA, TAYMER, CAVABLANDIRMA, BİTİRMƏ
    // ---------------------------------------------------------------------

    /** Suallar yüklənib qurtaranda imtahanı sıfırdan başladır. */
    private fun startTest() {
        screenState = ScreenState.Testing
        currentQuestionIndex = 0
        score = 0
        timeLeft = 300 // 5 dəqiqə (25 sual üçün)
        startTimer()
    }

    /**
     * Hər saniyə geri sayan taймer. "viewModelScope.launch" - bu, arxa fonda
     * işləyən sonsuz (while) dövrdür, amma "screenState == Testing" şərti
     * sayəsində imtahan bitəndə özü dayanır.
     */
    private fun startTimer() {
        timerJob?.cancel()   // əvvəlki taймer varsa (məs. yenidən başlasaq), onu ləğv edirik ki, iki taймer birdən işləməsin
        timerJob = viewModelScope.launch {
            while (timeLeft > 0 && screenState == ScreenState.Testing) {
                delay(1000)   // 1 saniyə gözlə
                timeLeft--
            }
            if (timeLeft == 0) {
                finishTest()   // vaxt bitdi - imtahan avtomatik bitirilir
            }
        }
    }

    /**
     * İmtahan zamanı "Çıx" düyməsi ilə (təsdiqdən sonra) çağırılır.
     * "finishTest()"-dən fərqli olaraq, nəticəni Firebase-ə YAZMIR - çünki
     * imtahan tamamlanmayıb, sadəcə istifadəçi özü ləğv edib.
     */
    fun exitTest() {
        timerJob?.cancel()
        screenState = ScreenState.CategorySelection
    }

    /**
     * İstifadəçi bir cavab seçəndə çağırılır.
     * Düzgün cavabdırsa xalı artırır, sonra ya növbəti suala keçir, ya da
     * (bu son sualdırsa) imtahanı bitirir.
     */
    fun answerQuestion(selectedIndex: Int) {
        if (selectedIndex == _questions[currentQuestionIndex].correctAnswerIndex) {
            score++
        }

        if (currentQuestionIndex < _questions.size - 1) {
            currentQuestionIndex++
        } else {
            finishTest()
        }
    }

    /** İmtahan bitəndə (bütün suallar cavablanıb və ya vaxt tükənib) çağırılır. */
    private fun finishTest() {
        timerJob?.cancel()
        screenState = ScreenState.Result
        saveResultToFirebase()
    }

    /** Nəticəni (ad, soyad, xal, tarix) Firebase-in "results" qovluğuna yazır. */
    private fun saveResultToFirebase() {
        val resultId = resultsRef.push().key ?: return   // Firebase-dən unikal ID alırıq ("push" hər dəfə fərqli ID yaradır)
        val resultData = mapOf(
            "firstName" to (userInfo?.firstName ?: "Anonim"),
            "lastName" to (userInfo?.lastName ?: "İstifadəçi"),
            "score" to score,
            "total" to _questions.size,
            "timestamp" to System.currentTimeMillis()
        )
        resultsRef.child(resultId).setValue(resultData)
    }

    /** Nəticə ekranında "Yenidən Başla" düyməsinə basılanda kateqoriya seçiminə qaytarır. */
    fun restart() {
        screenState = ScreenState.CategorySelection
    }

    /**
     * ViewModel tamamilə məhv ediləndə (məs. tətbiq bağlananda) çağırılır.
     * Burada açıq qalan bütün "dinləyiciləri" (taймer, Firebase listener)
     * təmizləyirik ki, yaddaş sızması (memory leak) olmasın.
     */
    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        unlockListener?.let { purchasesRef.removeEventListener(it) }
    }
}
