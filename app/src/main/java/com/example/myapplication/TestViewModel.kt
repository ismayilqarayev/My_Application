package com.example.myapplication

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
    Registration,        // Ad-soyad daxil etmə ekranı (başlanğıc ekran)
    CategorySelection,    // 25 sınaqdan birini seçmə ekranı
    Testing,              // Sualların göstərildiyi, cavab verilən ekran
    Result,               // İmtahan bitəndə nəticənin göstərildiyi ekran
    Loading,              // Suallar Firebase-dən yüklənərkən göstərilən spinner ekranı
    Admin                 // Yeni sual əlavə etmək üçün admin paneli
}

/**
 * "AndroidViewModel" - normal ViewModel-dən fərqli olaraq, Application (tətbiqin
 * özü) obyektinə çıxışı var. Bizə bu lazımdır ki, SharedPreferences (telefonda
 * kiçik məlumat saxlamaq üçün yaddaş) istifadə edə bilək - telefon nömrəsini
 * yadda saxlamaq üçün.
 */
class TestViewModel(application: Application) : AndroidViewModel(application) {

    // ---------------------------------------------------------------------
    // EKRAN VƏ İMTAHAN VƏZİYYƏTİ
    // "by mutableStateOf(...)" - bu, Compose-a deyir ki, "bu dəyər dəyişəndə
    // ekranı avtomatik yenidən çək". Adi "var" olsaydı, dəyər dəyişəndə
    // ekran YENİLƏNMƏZDİ.
    // ---------------------------------------------------------------------
    var screenState by mutableStateOf(ScreenState.Registration)   // hazırda hansı ekrandayıq
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
    private val storage = FirebaseStorage.getInstance()
    private val storageRef = storage.getReference("question_images") // sual şəkilləri burada saxlanılır
    private val functions = FirebaseFunctions.getInstance()          // Cloud Functions (backend) çağırmaq üçün

    // Telefonun öz yaddaşı (SharedPreferences) - tətbiq bağlanıb açılsa belə
    // telefon nömrəsi burada saxlanılıb qalır.
    private val prefs = application.getSharedPreferences("exam_prefs", Context.MODE_PRIVATE)

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
    // ÖDƏNİŞ / AÇILIŞ MƏNTİQİ (4-cü sınaqdan yuxarı ödənişli hissə)
    // ---------------------------------------------------------------------
    var phoneNumber by mutableStateOf("")
        private set
    var isUnlocked by mutableStateOf(false)      // true olanda BÜTÜN sınaqlar açılır
        private set
    var isProcessingPayment by mutableStateOf(false)   // ödəniş linki gözlənilirkən true olur (spinner göstərmək üçün)
        private set
    var checkoutUrl by mutableStateOf<String?>(null)   // Kapital Bank-ın ödəniş səhifəsinin linki (alınan kimi brauzerdə açılır)
        private set

    // Firebase-dəki "purchases/<telefon>/unlocked" sahəsini CANLI (real-time) izləyən dinləyici.
    // Bu sayədə ödəniş təsdiqlənən kimi, tətbiqi yenidən açmadan, ekran avtomatik dəyişir.
    private var unlockListener: ValueEventListener? = null

    /**
     * ViewModel ilk dəfə yaradılanda (tətbiq açılanda) işə düşür.
     * Əvvəllər yadda saxlanmış telefon nömrəsi varsa, açılış statusunu yoxlayır.
     */
    init {
        phoneNumber = prefs.getString(PREF_PHONE_NUMBER, "") ?: ""
        if (sanitizePhone(phoneNumber).length >= 9) {
            startListeningForUnlock(phoneNumber)
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

    /** İstifadəçi ödəniş dialoqunda telefon nömrəsi yazdıqca çağırılır, həm də telefonun yaddaşına yazır. */
    fun updatePhoneNumber(value: String) {
        phoneNumber = value
        prefs.edit().putString(PREF_PHONE_NUMBER, value).apply()
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
                if (isUnlocked) {
                    // Açılış təsdiqləndi - ödəniş prosesi bitdi, checkout linkinə artıq ehtiyac yoxdur
                    isProcessingPayment = false
                    checkoutUrl = null
                }
            }

            override fun onCancelled(error: DatabaseError) {
                errorMessage = "Açılış statusu yoxlanıla bilmədi: ${error.message}"
            }
        }
        purchasesRef.child(sanitized).addValueEventListener(listener)
        unlockListener = listener
    }

    /**
     * Ödəniş prosesini BAŞLADIR.
     * Bu funksiya birbaşa Kapital Bank ilə DANIŞMIR - əvəzinə bizim Firebase
     * Cloud Function-umuzu ("initiatePayment") çağırır, o da öz növbəsində
     * Kapital Bank ilə server tərəfindən (təhlükəsiz şəkildə) danışır.
     *
     * NİYƏ BELƏ? Çünki bank sirr açarını (secret/sertifikat) heç vaxt telefon
     * tətbiqinin içində saxlamaq olmaz - kimsə tətbiqi "açıb" onu oğurlaya bilər.
     * Ona görə bu həssas iş yalnız serverdə (functions/ qovluğunda) edilir.
     *
     * QEYD: "initiatePayment" Cloud Function-u hələ Kapital Bank-ın həqiqi
     * merchant məlumatları ilə konfiqurasiya olunmayıb (bax: functions/kapitalBankClient.js).
     * Merchant hesabı alındıqdan sonra tamamlanıb deploy edilməlidir.
     */
    fun initiatePayment() {
        val sanitized = sanitizePhone(phoneNumber)
        if (sanitized.length < 9) {
            errorMessage = "Zəhmət olmasa düzgün telefon nömrəsi daxil edin."
            return
        }

        isProcessingPayment = true
        // "getHttpsCallable" - Firebase-ə "bu adlı funksiyanı çağır" deyir.
        // "call(...)" - funksiyaya telefon nömrəsini göndərir və serverdən cavab gözləyir.
        functions.getHttpsCallable("initiatePayment")
            .call(mapOf("phone" to sanitized))
            .addOnSuccessListener { result ->
                // Server bizə {"checkoutUrl": "...", "internalOrderId": "..."} formasında cavab qaytarır
                val url = (result.data as? Map<*, *>)?.get("checkoutUrl") as? String
                if (url != null) {
                    checkoutUrl = url   // MainActivity.kt bunu görüb brauzerdə (Custom Tabs) açacaq
                    startListeningForUnlock(sanitized)   // ödəniş bitəndə xəbər tutmaq üçün dinləməyə başlayırıq
                } else {
                    isProcessingPayment = false
                    errorMessage = "Ödəniş linki alına bilmədi."
                }
            }
            .addOnFailureListener { e ->
                isProcessingPayment = false
                errorMessage = "Ödəniş başladıla bilmədi: ${e.message}"
            }
    }

    /** Checkout linki bir dəfə açıldıqdan sonra "istifadə olunub" kimi işarələnir ki, yenidən açılmasın. */
    fun consumeCheckoutUrl() {
        checkoutUrl = null
    }

    /**
     * "Artıq ödəniş etmişəm" deyən istifadəçi üçün - telefon nömrəsini yenidən
     * daxil edəndə, əvvəlki ödənişin statusunu Firebase-dən yenidən yoxlayır.
     * Başqa cihazda ödəniş edilmişsə, bu telefonda da açılışı "bərpa edir".
     */
    fun restorePurchase(phone: String) {
        updatePhoneNumber(phone)
        startListeningForUnlock(phone)
    }

    // SharedPreferences-də istifadə olunan açarın adı bir yerdə saxlanılır ki,
    // yazı səhvi (typo) riski olmasın.
    private companion object {
        const val PREF_PHONE_NUMBER = "phone_number"
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

    // ---------------------------------------------------------------------
    // QEYDİYYAT VƏ KATEQORİYA SEÇİMİ
    // ---------------------------------------------------------------------

    /** Qeydiyyat ekranında "İmtahana Başla" düyməsinə basılanda çağırılır. */
    fun register(firstName: String, lastName: String) {
        userInfo = UserInfo(firstName, lastName)
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
