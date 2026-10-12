package com.example.myapplication

import android.app.Application
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
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
    PhoneEntry,           // Telefon nömrəsi (və qayıdan istifadəçi üçün parol) daxil etmə
    NameEntry,            // (yalnız YENİ hesab üçün) ad-soyad + parol təyin etmə ekranı
    CategorySelection,    // 25 sınaqdan birini seçmə ekranı
    Testing,              // Sualların göstərildiyi, cavab verilən ekran
    Result,               // İmtahan bitəndə nəticənin göstərildiyi ekran
    Loading,              // Suallar Firebase-dən yüklənərkən göstərilən spinner ekranı
    Admin                 // Yeni sual əlavə etmək üçün admin paneli
}

/**
 * "AndroidViewModel" - normal ViewModel-dən fərqli olaraq, Application (tətbiqin
 * özü) obyektinə çıxışı var.
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
    private val metaRef = database.getReference("meta")             // tətbiqin ümumi tənzimləmələri (məs. sınaqların sayı)
    private val storage = FirebaseStorage.getInstance()
    private val storageRef = storage.getReference("question_images") // sual şəkilləri burada saxlanılır

    // Firebase Auth - hesab telefon nömrəsindən düzəldilən sintetik email +
    // istifadəçinin təyin etdiyi parol ilə idarə olunur (bax: fakeEmailFor).
    // Uğurlu girişdən sonra bu sessiya telefonda avtomatik yadda saxlanılır
    // (FirebaseAuth SDK-nın öz işidir) - yəni eyni cihazda tətbiqi bağlayıb
    // açsanız, yenidən daxil olmağa ehtiyac qalmır.
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

    // Admin panelində seçilmiş kateqoriyada HAZIRDA olan bütün sualların siyahısı.
    // Bu sayədə admin sınağın İÇİNƏ baxıb (nə var, nə yoxdur görüb) elə həmin
    // yerdən yeni sual əlavə edə bilir. Sual sayına heç bir sərt limit YOXDUR
    // (25 sadəcə tövsiyə olunan hədəfdir) - imtahan vaxtı da buna uyğun uzanır
    // (bax: startTest()).
    private val _selectedCategoryQuestions = mutableStateListOf<Question>()
    val selectedCategoryQuestions: List<Question> = _selectedCategoryQuestions

    /** Admin panelində kateqoriya seçimi dəyişəndə (və hər sual yükləməsindən sonra) çağırılır. */
    fun refreshSelectedCategoryQuestions(category: String) {
        questionsRef.child(category).get()
            .addOnSuccessListener { snapshot ->
                _selectedCategoryQuestions.clear()
                for (child in snapshot.children) {
                    child.getValue(Question::class.java)?.let { _selectedCategoryQuestions.add(it) }
                }
            }
            .addOnFailureListener { _selectedCategoryQuestions.clear() }
    }

    // ---------------------------------------------------------------------
    // GİRİŞ: TELEFON NÖMRƏSİ + PAROL (SMS TƏSDİQİ YOXDUR)
    //
    // Bu, tətbiqin "hesab" sistemidir: şagird telefon nömrəsini yazır.
    //  - Nömrə YENİDİRSƏ -> ad-soyad + parol təyin edib qeydiyyatdan keçir.
    //  - Nömrə ARTIQ QEYDİYYATLIDIRSA -> yalnız parolu yazıb daxil olur.
    // Hesab, telefon nömrəsindən düzəldilən sintetik email ünvanı ilə Firebase
    // Auth-un Email/Parol provayderində saxlanılır (bax: fakeEmailFor) - parolun
    // özü heç vaxt bizim kodumuzda və ya Realtime Database-də saxlanılmır,
    // Firebase onu öz təhlükəsiz (hash-lənmiş) formada saxlayır.
    // ---------------------------------------------------------------------
    var phoneNumber by mutableStateOf("")   // PhoneEntry ekranında yazılan (ölkə kodu olmadan, məs. "501234567")
        private set
    var isCheckingPhone by mutableStateOf(false)         // nömrənin qeydiyyatlı olub-olmadığı yoxlanılarkən true
        private set
    var isReturningUser by mutableStateOf(false)         // true olanda ekranda parol sahəsi göstərilir
        private set
    var isLoggingInWithPassword by mutableStateOf(false) // parolla giriş edilərkən true (spinner üçün)
        private set
    var isRegistering by mutableStateOf(false)           // yeni hesab yaradılarkən true (spinner üçün)
        private set

    /**
     * Telefon nömrəsindən Firebase Auth üçün "sintetik" (real olmayan, amma
     * düzgün formatlı) email ünvanı düzəldir. Firebase-in Email/Parol
     * provayderi işləmək üçün email formatı tələb edir, amma bu ünvana heç
     * vaxt real məktub getmir - sadəcə hər istifadəçi üçün unikal açar rolunu oynayır.
     */
    private fun fakeEmailFor(phoneKey: String) = "$phoneKey@myapplication-223cacbf.local"

    companion object {
        /**
         * Admin panelinin sabit Firebase Auth email ünvanı. Əvvəllər admin girişi
         * yalnız kodun İÇİNDƏ (BuildConfig.ADMIN_PASSWORD) müqayisə olunurdu - bu,
         * Realtime Database qaydaları üçün TƏHLÜKƏLİ idi, çünki server tərəfində
         * "bu, əsl admindir" sübutu yox idi (bax: database.rules.json-da köhnə
         * ".read/.write": true qaydaları). İndi admin parolu bu sabit hesabla
         * Firebase Auth-a göndərilir - uğurlu olarsa, "auth.token.email == ADMIN_EMAIL"
         * server tərəfində yoxlanıla bilən bir sübuta çevrilir (bax: adminLogin()).
         *
         * BİR DƏFƏLİK QURULUM: Firebase Console -> Authentication -> Add user
         * bölməsindən bu email ilə əl ilə bir hesab yaradılmalıdır - admin
         * panelinin parolu elə o hesabın parolu olacaq.
         */
        const val ADMIN_EMAIL = "admin@myapplication-223cacbf.local"
    }

    /**
     * Bu cihaza xas, sabit bir ID qaytarır (Android-in "Settings.Secure.ANDROID_ID"
     * dəyəri). Hesabların telefonlar arasında paylaşılmasının qarşısını almaq
     * üçün istifadə olunur - bax: register() və loadUserOrAskName().
     */
    private fun currentDeviceId(): String =
        Settings.Secure.getString(getApplication<Application>().contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown-device"

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

    // ---------------------------------------------------------------------
    // SINAQLARIN SAYI (Admin panelindən artırıla bilər)
    //
    // Əvvəllər "1..25" kodda sabit (hardcoded) idi. İndi Firebase-in
    // "meta/categoryCount" sahəsində saxlanılır və CANLI dinlənilir - admin
    // "Yeni sınaq əlavə et" düyməsinə basanda bu ədəd 1 artır, bütün
    // istifadəçilərin ekranı (kod dəyişmədən, tətbiqi yenidən yükləmədən)
    // avtomatik yenilənir.
    // ---------------------------------------------------------------------
    var categoryCount by mutableStateOf(25)
        private set
    private var categoryCountListener: ValueEventListener? = null

    private fun startListeningForCategoryCount() {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                categoryCount = snapshot.getValue(Int::class.java) ?: 25
            }

            override fun onCancelled(error: DatabaseError) {
                // Sükutla keçirik - sınaqların sayı ikinci dərəcəli məlumatdır,
                // xəta göstərib istifadəçini narahat etməyə dəyməz
            }
        }
        metaRef.child("categoryCount").addValueEventListener(listener)
        categoryCountListener = listener
    }

    /**
     * YALNIZ ADMİN PANELİNDƏN çağırılır. Yeni bir sınaq yaradır, istəyə bağlı
     * öz adı ilə (məs. "Fizika").
     *
     * DİQQƏT: Sınağın DAXİLİ açarı (Firebase-dəki "questions/sınaq N" yolu,
     * pulsuz/ödənişli məntiqi, silinmə/sıra izlənməsi) HƏMİŞƏ "sınaq N"
     * formatında qalır - bu, mövcud koda toxunmadan işləməyə davam etsin
     * deyə qəsdən belədir. "name" isə YALNIZ "meta/categoryNames/N" düyününə
     * yazılan, ekranda "sınaq N" əvəzinə göstərilən bir etikettir.
     *
     * Nömrələmə: əvvəlcə silinmiş (boş qalmış) bir nömrə axtarır - varsa, ONU
     * geri "canlandırır" (məs. hamısı silinibsə, yenidən "sınaq 1"-dən
     * başlayır). Yoxdursa, sayğacı 1 artırıb təzə bir nömrə yaradır.
     */
    fun addNewCategory(name: String = "") {
        val freedNumber = (1..categoryCount).firstOrNull { it in deletedCategories }
        val targetNumber = freedNumber ?: (categoryCount + 1)

        if (freedNumber != null) {
            metaRef.child("deletedCategories").child(freedNumber.toString()).removeValue()
                .addOnFailureListener { e ->
                    errorMessage = "Yeni sınaq əlavə edilə bilmədi: ${e.message}"
                }
        } else {
            metaRef.child("categoryCount").setValue(categoryCount + 1)
                .addOnFailureListener { e ->
                    errorMessage = "Yeni sınaq əlavə edilə bilmədi: ${e.message}"
                }
        }

        val trimmedName = name.trim()
        val nameRef = metaRef.child("categoryNames").child(targetNumber.toString())
        if (trimmedName.isNotBlank()) nameRef.setValue(trimmedName) else nameRef.removeValue()
    }

    // "meta/deletedCategories/<N>" düyünündə silinmiş sınaq nömrələri saxlanılır.
    // Sınaqlar "1..categoryCount" ardıcıllığı ilə nömrələndiyi üçün, silinmiş bir
    // sınağın nömrəsini sonrakıların üstünə "sürüşdürmək" (renumbering) əvəzinə,
    // sadəcə onu bu siyahıda "gizlədirik" - kod sadə qalır, məlumat itkisi riski olmur.
    var deletedCategories by mutableStateOf<Set<Int>>(emptySet())
        private set
    private var deletedCategoriesListener: ValueEventListener? = null

    private fun startListeningForDeletedCategories() {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                deletedCategories = snapshot.children.mapNotNull { it.key?.toIntOrNull() }.toSet()
            }

            override fun onCancelled(error: DatabaseError) {
                // Sükutla keçirik - eyni səbəbdən (bax: startListeningForCategoryCount)
            }
        }
        metaRef.child("deletedCategories").addValueEventListener(listener)
        deletedCategoriesListener = listener
    }

    // "meta/categoryNames/<N>" düyünündə sınaqların öz (admin tərəfindən verilmiş)
    // adları saxlanılır - məs. {1: "Fizika"}. Daxili açar həmişə "sınaq N" olaraq
    // qalır (bax: addNewCategory), bu YALNIZ ekranda göstərilən etikətdir.
    var categoryNames by mutableStateOf<Map<Int, String>>(emptyMap())
        private set
    private var categoryNamesListener: ValueEventListener? = null

    private fun startListeningForCategoryNames() {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                categoryNames = snapshot.children.mapNotNull { child ->
                    val number = child.key?.toIntOrNull() ?: return@mapNotNull null
                    val name = child.getValue(String::class.java) ?: return@mapNotNull null
                    number to name
                }.toMap()
            }

            override fun onCancelled(error: DatabaseError) {
                // Sükutla keçirik - eyni səbəbdən (bax: startListeningForCategoryCount)
            }
        }
        metaRef.child("categoryNames").addValueEventListener(listener)
        categoryNamesListener = listener
    }

    /**
     * YALNIZ ADMİN PANELİNDƏN çağırılır. Bir sınağı - içindəki BÜTÜN suallarla
     * birlikdə - tamamilə silir. Sınaq nömrəsi "silinmiş" kimi işarələnir ki,
     * nə şagirdlərin siyahısında, nə də admin panelində bir daha görünməsin.
     * Əvvəllər verilmiş xüsusi ad da silinir (nömrə sonra yenidən istifadə
     * olunanda köhnə adı "miras" olaraq daşımasın deyə).
     */
    fun deleteCategory(category: String) {
        val number = category.filter { it.isDigit() }.toIntOrNull() ?: return
        questionsRef.child(category).removeValue()
        metaRef.child("deletedCategories").child(number.toString()).setValue(true)
            .addOnFailureListener { e ->
                errorMessage = "Sınaq silinə bilmədi: ${e.message}"
            }
        metaRef.child("categoryNames").child(number.toString()).removeValue()
        _selectedCategoryQuestions.clear()
    }

    /**
     * YALNIZ ADMİN PANELİNDƏN çağırılır. Bir sınağın içindən TƏK bir sualı silir
     * (bütün sınağı deyil). Silindikdən sonra siyahı avtomatik təzələnir.
     */
    fun deleteQuestion(category: String, questionId: Int) {
        questionsRef.child(category).child(questionId.toString()).removeValue()
            .addOnSuccessListener { refreshSelectedCategoryQuestions(category) }
            .addOnFailureListener { e ->
                errorMessage = "Sual silinə bilmədi: ${e.message}"
            }
    }

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
        startListeningForCategoryCount()
        startListeningForDeletedCategories()
        startListeningForCategoryNames()
        // Hesab email/parol provayderi ilə saxlanıldığı üçün telefon nömrəsi
        // Firebase-in "phoneNumber" sahəsində deyil, sintetik emailin özündə
        // gizlənib ("<nömrə>@..." - bax: fakeEmailFor) - ordan geri çıxarırıq.
        // ADMIN_EMAIL-i istisna edirik - əks halda admin sessiyası qapanmadan
        // tətbiq yenidən açılsa, "admin" bir telefon nömrəsi kimi şərh olunardı.
        auth.currentUser?.email?.let { email ->
            if (email != ADMIN_EMAIL) {
                loadUserOrAskName(email.substringBefore("@"), onlyIfStillOnRoleSelection = true)
            }
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
     *
     * QEYD: İstifadəçilər çox vaxt yerli vərdişlə nömrənin əvvəlinə "0" yazır
     * (məs. "0501234567" - yerli zəng vərdişi). Bu "0" ölkə kodu ilə birləşəndə
     * ("+9940501234567") YANLIŞ/mövcud olmayan nömrə yaranır və SMS heç vaxt
     * çatmır. Ona görə, dəqiq 10 rəqəm və "0" ilə başlayırsa, həmin "0"-ı atırıq.
     */
    private fun sanitizePhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.length == 10 && digits.startsWith("0")) digits.drop(1) else digits
    }

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
     * Bu nömrənin ARTIQ qeydiyyatdan keçib-keçmədiyini yoxlayır:
     *  - Qeydiyyatlıdırsa (qayıdan istifadəçi) -> parol sahəsini göstəririk.
     *  - Qeydiyyatlı deyilsə (yeni istifadəçi) -> ad-soyad+parol ekranına keçir.
     */
    fun checkPhoneAndProceed() {
        val sanitized = sanitizePhone(phoneNumber)
        if (sanitized.length < 9) {
            errorMessage = "Zəhmət olmasa düzgün telefon nömrəsi daxil edin."
            return
        }
        val fullKey = normalizePhoneKey(sanitized)

        // DİQQƏT: "phoneNumber"-i burada tam açara ("994...") DƏYİŞMİRİK - bu ekranda
        // (PhoneEntryScreen) hələ "+994 " prefiksi ilə göstərilir, əgər phoneNumber
        // artıq "994" ilə başlasaydı, "+994 994..." kimi İKİQAT görünərdi. register()
        // və loginWithPassword() öz aralarında normalizePhoneKey(phoneNumber) çağırıb
        // tam açarı özləri hesabladığı üçün buna ehtiyac da yoxdur.
        isCheckingPhone = true
        errorMessage = null
        usersRef.child(fullKey).get()
            .addOnSuccessListener { snapshot ->
                isCheckingPhone = false
                if (snapshot.exists()) {
                    isReturningUser = true
                } else {
                    screenState = ScreenState.NameEntry
                }
            }
            .addOnFailureListener {
                // Yoxlama uğursuz olsa belə istifadəçini bloklamayaq - qeydiyyat ekranı ilə davam edək
                isCheckingPhone = false
                screenState = ScreenState.NameEntry
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
                    val thisDevice = currentDeviceId()
                    if (existing.deviceId.isNotBlank() && existing.deviceId != thisDevice) {
                        // Hesab ARTIQ başqa bir cihaza bağlıdır - paylaşılmanın qarşısını
                        // almaq üçün bu cihazdan girişi rədd edirik (bax: Models.kt-də deviceId qeydi)
                        auth.signOut()
                        errorMessage = "Bu hesab artıq başqa bir telefonda istifadə olunur. Əgər bu sizin öz telefonunuzdursa, müəllimlə əlaqə saxlayıb hesabı sıfırlatdırın."
                        screenState = ScreenState.RoleSelection
                        return@addOnSuccessListener
                    }
                    if (existing.deviceId.isBlank()) {
                        // Bu funksiya əlavə olunmazdan əvvəl qeydiyyatdan keçmiş köhnə hesab -
                        // indi ilk daxil olan cihaza bağlayırıq
                        usersRef.child(sanitized).child("deviceId").setValue(thisDevice)
                    }
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

    /**
     * YALNIZ ADMİN PANELİNDƏN çağırılır. Hesabın bağlı olduğu cihaz ID-sini
     * silir (bax: Models.kt-də deviceId qeydi) - beləliklə növbəti dəfə
     * istənilən telefon bu nömrə+parolla daxil olanda, həmin cihaz "ilk"
     * sayılıb yenidən bağlanır. Şagirdin telefonu dəyişəndə/sındıqda istifadə
     * olunur.
     */
    fun resetDevice(phone: String) {
        val sanitized = normalizePhoneKey(phone)
        if (sanitized.length < 9) {
            errorMessage = "Zəhmət olmasa düzgün telefon nömrəsi daxil edin."
            return
        }

        usersRef.child(sanitized).child("deviceId").setValue("")
            .addOnFailureListener { e ->
                errorMessage = "Cihaz sıfırlana bilmədi: ${e.message}"
            }
    }

    // ---------------------------------------------------------------------
    // ADMIN GİRİŞİ
    // ---------------------------------------------------------------------

    var isAdminLoggingIn by mutableStateOf(false)
        private set

    /**
     * Admin parol dialoqunda "Daxil Ol" düyməsinə basılanda çağırılır. Parolu
     * birbaşa sabit ADMIN_EMAIL hesabı ilə Firebase Auth-a göndərir - uğurlu
     * olarsa bu sessiya server tərəfində "admindir" kimi sübut oluna bilir
     * (bax: ADMIN_EMAIL qeydi və database.rules.json).
     */
    fun adminLogin(password: String, onResult: (success: Boolean) -> Unit) {
        isAdminLoggingIn = true
        auth.signInWithEmailAndPassword(ADMIN_EMAIL, password)
            .addOnSuccessListener {
                isAdminLoggingIn = false
                onResult(true)
            }
            .addOnFailureListener {
                isAdminLoggingIn = false
                onResult(false)
            }
    }

    /**
     * Admin panelindən geri qayıdanda çağırılır. Əgər admin panelinə heç bir
     * şagird kimi giriş etmədən (birbaşa "Müəllim" düyməsi ilə) daxil olunubsa,
     * Firebase Auth sessiyasını bağlayır ki, cihaz həmişəlik "admin kimi
     * qeydiyyatlı" qalmasın (əks halda tətbiq növbəti dəfə açılanda "admin"
     * bir telefon nömrəsi kimi şərh oluna bilərdi - bax: init bloku).
     * Artıq şagird kimi giriş edilibsə (userInfo doludursa), toxunmuruq.
     */
    fun signOutAdminIfNeeded() {
        if (userInfo == null && auth.currentUser?.email == ADMIN_EMAIL) {
            auth.signOut()
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
        imageUri: Uri?,
        // Yadda saxlama UĞURLA bitəndə çağırılır - MainActivity.kt bunu formu
        // təmizləmək və təsdiq mesajı göstərmək üçün istifadə edir ki, admin
        // ardıcıl bir neçə sual əlavə edərkən əlləri ilə sahələri silməli olmasın
        onSuccess: () -> Unit = {}
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

                isLoadingQuestions = false   // admin panelində qalırıq (MainActivity.kt-dəki AdminScreen isLoading dəyişəndə siyahını özü təzələyir)
                onSuccess()
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
    fun uploadQuestionsBulk(category: String, rawText: String, onSuccess: () -> Unit = {}) {
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
                isLoadingQuestions = false   // admin panelində qalırıq (MainActivity.kt-dəki AdminScreen isLoading dəyişəndə siyahını özü təzələyir)
                onSuccess()
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
     * NameEntry ekranında (yalnız YENİ hesab üçün) "İmtahana Başla" düyməsinə
     * basılanda çağırılır. Telefon nömrəsindən düzəldilən sintetik email +
     * təyin edilən parolla Firebase Auth-da yeni hesab yaradır, sonra
     * ad-soyadı "users/<telefon>" düyününə yazır ki, növbəti girişdə (bu və
     * ya başqa cihazda) bərpa olunsun.
     *
     * Hesab elə bu andaca bu cihaza "bağlanır" (deviceId) - başqa telefon eyni
     * nömrə+parolla giriş etməyə çalışsa, rədd ediləcək (bax: loadUserOrAskName).
     */
    fun register(firstName: String, lastName: String, password: String) {
        if (password.length < 4) {
            errorMessage = "Parol ən azı 4 simvol olmalıdır."
            return
        }
        val fullKey = normalizePhoneKey(phoneNumber)

        isRegistering = true
        errorMessage = null
        auth.createUserWithEmailAndPassword(fakeEmailFor(fullKey), password)
            .addOnSuccessListener {
                isRegistering = false
                val info = UserInfo(firstName, lastName, currentDeviceId())
                userInfo = info
                phoneNumber = fullKey
                usersRef.child(fullKey).setValue(info)
                startListeningForUnlock(fullKey)
                screenState = ScreenState.CategorySelection
            }
            .addOnFailureListener { e ->
                isRegistering = false
                errorMessage = "Qeydiyyat uğursuz oldu: ${e.message}"
            }
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

    /**
     * Suallar yüklənib qurtaranda imtahanı sıfırdan başladır.
     * Vaxt sual sayına mütənasibdir (hər sual üçün 12 saniyə - 25 sualda 5
     * dəqiqəyə bərabərdir), ona görə admin bir kateqoriyaya 25-dən çox sual
     * əlavə etsə belə, imtahan vaxtı avtomatik uzanır.
     */
    private fun startTest() {
        screenState = ScreenState.Testing
        currentQuestionIndex = 0
        score = 0
        timeLeft = _questions.size * 12
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
     * İstifadəçi bir cavab seçəndə çağırılır (QuestionScreen düzgün/səhv
     * animasiyasını göstərdikdən bir qədər sonra).
     * Düzgün cavabdırsa xalı artırır, sonra ya növbəti suala keçir, ya da
     * (bu son sualdırsa) imtahanı bitirir.
     *
     * "screenState != Testing" yoxlaması: əgər vaxt məhz düzgün/səhv
     * animasiyası göstərilən qısa gecikmə zamanı bitibsə, taймer artıq
     * finishTest()-i çağırmış ola bilər - bu halda bu funksiyanın gecikmiş
     * çağırışı xal/nəticəni ikinci dəfə pozmasın deyə erkən çıxırıq.
     */
    fun answerQuestion(selectedIndex: Int) {
        if (screenState != ScreenState.Testing) return
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
        categoryCountListener?.let { metaRef.child("categoryCount").removeEventListener(it) }
        deletedCategoriesListener?.let { metaRef.child("deletedCategories").removeEventListener(it) }
        categoryNamesListener?.let { metaRef.child("categoryNames").removeEventListener(it) }
    }
}
