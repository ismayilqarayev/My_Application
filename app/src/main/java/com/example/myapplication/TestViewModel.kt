package com.example.myapplication

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

// ==========================================================================
// BU FAYL TƏTBIQIN "BEYNİ"DİR.
// Ekranda görünən heç nə (düymə, mətn, rəng) burada yoxdur - o, MainActivity.kt-dədir.
// Burada YALNIZ məntiq var: serverə (backend API) sorğular, sualları yükləmə,
// cavabları yoxlama, xal hesablama, taймer, ödəniş axını və s.
//
// Bu, "ViewModel" adlanan bir Android arxitektura nümunəsidir: ekran fırlanıb
// yenidən çəkilsə belə (rotation), bu sinifin içindəki məlumatlar itmir.
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
    CategorySelection,    // Sınaqlardan birini seçmə ekranı
    Testing,              // Sualların göstərildiyi, cavab verilən ekran
    Result,               // İmtahan bitəndə nəticənin göstərildiyi ekran
    Loading,              // Suallar serverdən yüklənərkən göstərilən spinner ekranı
    Admin                 // Müəllim (admin) paneli
}

class TestViewModel(application: Application) : AndroidViewModel(application) {

    // ---------------------------------------------------------------------
    // SESSİYA VƏ SERVER BAĞLANTISI
    //
    // Şagird daxil olanda server ona "token" (giriş açarı) verir. Token telefonda
    // (SharedPreferences) saxlanılır - tətbiq bağlanıb açılsa belə yenidən daxil
    // olmağa ehtiyac qalmır. Admin token-i isə YALNIZ yaddaşda saxlanılır: admin
    // panelindən çıxanda silinir, hər dəfə parol yenidən soruşulur.
    // ---------------------------------------------------------------------
    private val prefs = application.getSharedPreferences("session", Context.MODE_PRIVATE)
    private var studentToken: String? = prefs.getString(KEY_TOKEN, null)
    private var adminToken: String? = null
    private val api = ApiClient(studentToken = { studentToken }, adminToken = { adminToken })

    // ---------------------------------------------------------------------
    // EKRAN VƏ İMTAHAN VƏZİYYƏTİ
    // "by mutableStateOf(...)" - Compose-a deyir: "bu dəyər dəyişəndə ekranı yenidən çək".
    // ---------------------------------------------------------------------
    var screenState by mutableStateOf(ScreenState.RoleSelection)
    var userInfo by mutableStateOf<UserInfo?>(null)
        private set
    var currentQuestionIndex by mutableStateOf(0)
        private set
    var score by mutableStateOf(0)
        private set
    var timeLeft by mutableStateOf(60)
        private set
    private var selectedCategory = 0
    private var timerJob: Job? = null

    private val _questions = mutableStateListOf<Question>()
    val questions: List<Question> = _questions

    var isLoadingQuestions by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** Bütün (silinməmiş) sınaqlar, nömrəyə görə sıralanmış. */
    var categories by mutableStateOf<List<Category>>(emptyList())
        private set

    /** Admin panelində seçilmiş sınağın içindəki suallar. */
    private val _selectedCategoryQuestions = mutableStateListOf<Question>()
    val selectedCategoryQuestions: List<Question> = _selectedCategoryQuestions

    // ---------------------------------------------------------------------
    // GİRİŞ: TELEFON NÖMRƏSİ + PAROL
    //  - Nömrə YENİDİRSƏ -> ad-soyad + parol təyin edib qeydiyyatdan keçir.
    //  - Nömrə ARTIQ QEYDİYYATLIDIRSA -> yalnız parolu yazıb daxil olur.
    // Parol serverdə yalnız hash (bcrypt) şəklində saxlanılır.
    // ---------------------------------------------------------------------
    var phoneNumber by mutableStateOf("")   // PhoneEntry ekranında yazılan (ölkə kodu olmadan, məs. "501234567")
        private set
    /** Daxil olmuş hesabın tam nömrəsi (məs. "994501234567") - ödəniş dialoqunda göstərilir. */
    var accountPhone by mutableStateOf("")
        private set
    var isCheckingPhone by mutableStateOf(false)
        private set
    var isReturningUser by mutableStateOf(false)
        private set
    var isLoggingInWithPassword by mutableStateOf(false)
        private set
    var isRegistering by mutableStateOf(false)
        private set
    var isAdminLoggingIn by mutableStateOf(false)
        private set

    // ---------------------------------------------------------------------
    // ÖDƏNİŞ / AÇILIŞ (4-cü sınaqdan yuxarı ödənişli hissə)
    // Şagird admininin kartına köçürmə edir, admin panelindən həmin nömrəni əl
    // ilə "açır". Server ödənişli sınaqların suallarını açılmamış hesaba VERMİR.
    // ---------------------------------------------------------------------
    var isUnlocked by mutableStateOf(false)
        private set

    init {
        // Sınaqlar ekranında olduğumuz müddətdə hər 15 saniyədən bir hesab
        // (ödəniş açılıbmı?) və sınaq siyahısı serverdən yenilənir - ödəniş
        // təsdiqlənən kimi tətbiqi yenidən açmadan kilidlər götürülür.
        // "collectLatest" ekran dəyişəndə köhnə dövrü avtomatik dayandırır.
        viewModelScope.launch {
            snapshotFlow { screenState }.collectLatest { state ->
                when (state) {
                    ScreenState.CategorySelection -> while (true) {
                        refreshCategories()
                        refreshAccount()
                        delay(REFRESH_INTERVAL_MS)
                    }
                    ScreenState.Admin -> refreshCategories()
                    else -> Unit
                }
            }
        }
        if (studentToken != null) restoreSession()
    }

    /** Bu cihazda əvvəllər giriş edilibsə, telefon nömrəsini soruşmadan hesabı yükləyir. */
    private fun restoreSession() {
        viewModelScope.launch {
            try {
                val me = api.service.me()
                // İstifadəçi cavab gələnə qədər özü başqa ekrana keçibsə, onu "tullamırıq"
                if (screenState == ScreenState.RoleSelection) {
                    applyAccount(me)
                    screenState = ScreenState.CategorySelection
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.httpCode == 401) clearStudentSession()
            }
        }
    }

    private suspend fun refreshCategories() {
        try {
            categories = api.service.categories()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sükutla keçirik - növbəti yenilənmədə yenidən cəhd olunacaq
        }
    }

    private suspend fun refreshAccount() {
        if (studentToken == null) return
        try {
            applyAccount(api.service.me())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 401 = token etibarsızdır (məs. admin cihazı sıfırlayıb, hesab başqa telefona bağlanıb)
            if (e.httpCode == 401) {
                errorMessage = e.userMessage("Sessiyanın vaxtı bitib. Yenidən daxil olun.")
                clearStudentSession()
            }
        }
    }

    private fun applyAccount(me: MeResponse) {
        userInfo = UserInfo(me.firstName, me.lastName)
        accountPhone = me.phone
        isUnlocked = me.unlocked
    }

    private fun startStudentSession(auth: AuthResponse) {
        studentToken = auth.token
        prefs.edit { putString(KEY_TOKEN, auth.token) }
        applyAccount(auth.user)
        screenState = ScreenState.CategorySelection
    }

    private fun clearStudentSession() {
        studentToken = null
        prefs.edit { remove(KEY_TOKEN) }
        userInfo = null
        accountPhone = ""
        isUnlocked = false
        if (screenState != ScreenState.Admin) screenState = ScreenState.RoleSelection
    }

    /**
     * Server sorğusunu icra edir; xəta olarsa serverin mesajını (yoxdursa "fallback"-i)
     * ekranda göstərib null qaytarır.
     */
    private suspend fun <T> apiCall(fallback: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        errorMessage = e.userMessage(fallback)
        null
    }

    /**
     * Bu cihaza xas, sabit ID (Android-in "ANDROID_ID" dəyəri). Server hesabı ilk
     * daxil olan cihaza bağlayır - başqa telefondan eyni nömrə+parolla girişi rədd edir.
     */
    private fun currentDeviceId(): String =
        Settings.Secure.getString(getApplication<Application>().contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown-device"

    fun clearError() {
        errorMessage = null
    }

    fun updatePhoneNumber(value: String) {
        phoneNumber = value
        if (isReturningUser) isReturningUser = false
    }

    /** PhoneEntry ekranında "Davam et": nömrə qeydiyyatlıdırsa parol sahəsini, deyilsə qeydiyyat ekranını göstərir. */
    fun checkPhoneAndProceed() {
        if (phoneNumber.count { it.isDigit() } < 9) {
            errorMessage = "Zəhmət olmasa düzgün telefon nömrəsi daxil edin."
            return
        }
        isCheckingPhone = true
        errorMessage = null
        viewModelScope.launch {
            val result = apiCall("Nömrə yoxlanıla bilmədi.") { api.service.checkPhone(PhoneRequest(phoneNumber)) }
            isCheckingPhone = false
            when {
                result == null -> Unit
                result.registered -> isReturningUser = true
                else -> screenState = ScreenState.NameEntry
            }
        }
    }

    fun loginWithPassword(password: String) {
        if (password.isBlank()) {
            errorMessage = "Zəhmət olmasa parolu daxil edin."
            return
        }
        isLoggingInWithPassword = true
        errorMessage = null
        viewModelScope.launch {
            val auth = apiCall("Giriş uğursuz oldu.") {
                api.service.login(LoginRequest(phoneNumber, password, currentDeviceId()))
            }
            isLoggingInWithPassword = false
            auth?.let(::startStudentSession)
        }
    }

    /** NameEntry ekranında (yalnız YENİ hesab üçün) qeydiyyat. Hesab elə bu cihaza bağlanır. */
    fun register(firstName: String, lastName: String, password: String) {
        if (password.length < 4) {
            errorMessage = "Parol ən azı 4 simvol olmalıdır."
            return
        }
        isRegistering = true
        errorMessage = null
        viewModelScope.launch {
            val auth = apiCall("Qeydiyyat uğursuz oldu.") {
                api.service.register(RegisterRequest(phoneNumber, firstName, lastName, password, currentDeviceId()))
            }
            isRegistering = false
            auth?.let(::startStudentSession)
        }
    }

    // ---------------------------------------------------------------------
    // ADMIN
    // ---------------------------------------------------------------------

    /** Admin parol dialoqunda "Daxil Ol". onResult(false) dialoqda "Yanlış parol!" göstərir. */
    fun adminLogin(password: String, onResult: (success: Boolean) -> Unit) {
        isAdminLoggingIn = true
        viewModelScope.launch {
            try {
                adminToken = api.service.adminLogin(AdminLoginRequest(password)).token
                onResult(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.httpCode != 401) errorMessage = e.userMessage("Admin girişi uğursuz oldu.")
                onResult(false)
            } finally {
                isAdminLoggingIn = false
            }
        }
    }

    /** Admin panelindən çıxanda admin token-i unudulur. */
    fun adminLogout() {
        adminToken = null
        _selectedCategoryQuestions.clear()
    }

    fun refreshSelectedCategoryQuestions(categoryNumber: Int) {
        viewModelScope.launch {
            try {
                val list = api.service.adminQuestions(categoryNumber).map(::withAbsoluteImage)
                _selectedCategoryQuestions.clear()
                _selectedCategoryQuestions.addAll(list)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _selectedCategoryQuestions.clear()
            }
        }
    }

    /** Yeni sınaq yaradır; server silinmiş nömrəni yenidən istifadə edir, yoxdursa yenisini verir. */
    fun addNewCategory(name: String, onCreated: (Int) -> Unit) {
        viewModelScope.launch {
            val created = apiCall("Yeni sınaq əlavə edilə bilmədi.") {
                api.service.createCategory(NewCategoryRequest(name.trim().ifBlank { null }))
            } ?: return@launch
            categories = (categories + created).sortedBy { it.number }
            onCreated(created.number)
        }
    }

    /** Sınağı içindəki BÜTÜN suallarla birlikdə silir. */
    fun deleteCategory(categoryNumber: Int) {
        viewModelScope.launch {
            apiCall("Sınaq silinə bilmədi.") { api.service.deleteCategory(categoryNumber) } ?: return@launch
            categories = categories.filter { it.number != categoryNumber }
            _selectedCategoryQuestions.clear()
        }
    }

    fun deleteQuestion(categoryNumber: Int, questionId: Long) {
        viewModelScope.launch {
            apiCall("Sual silinə bilmədi.") { api.service.deleteQuestion(questionId) } ?: return@launch
            refreshSelectedCategoryQuestions(categoryNumber)
        }
    }

    fun manualUnlock(phone: String, onDone: () -> Unit) {
        if (phone.count { it.isDigit() } < 9) {
            errorMessage = "Zəhmət olmasa düzgün telefon nömrəsi daxil edin."
            return
        }
        viewModelScope.launch {
            apiCall("Açılış qeyd edilə bilmədi.") { api.service.unlock(PhoneRequest(phone)) } ?: return@launch
            onDone()
        }
    }

    /** Hesabın cihaz bağlantısını silir - növbəti dəfə daxil olan telefon ona bağlanır. */
    fun resetDevice(phone: String, onDone: () -> Unit) {
        if (phone.count { it.isDigit() } < 9) {
            errorMessage = "Zəhmət olmasa düzgün telefon nömrəsi daxil edin."
            return
        }
        viewModelScope.launch {
            apiCall("Cihaz sıfırlana bilmədi.") { api.service.resetDevice(PhoneRequest(phone)) } ?: return@launch
            onDone()
        }
    }

    /**
     * Admin panelindən tək sual əlavə edir. Boş buraxılmış variantlar atılır (məs.
     * yalnız 3 variant yazılıbsa). Şəkil seçilibsə, əvvəlcə kiçildilib serverə yüklənir.
     */
    fun uploadQuestion(
        categoryNumber: Int,
        text: String,
        options: List<String>,
        correctIndex: Int,
        imageUri: Uri?,
        onSuccess: () -> Unit = {}
    ) {
        val filled = options.mapIndexed { i, option -> i to option.trim() }.filter { it.second.isNotEmpty() }
        val newCorrectIndex = filled.indexOfFirst { it.first == correctIndex }
        if (newCorrectIndex == -1) {
            errorMessage = "Düzgün kimi seçilən variant boş ola bilməz."
            return
        }

        viewModelScope.launch {
            isLoadingQuestions = true
            val saved = apiCall("Sual yadda saxlanıla bilmədi.") {
                val imageUrl = imageUri?.let { uploadImage(it) }
                api.service.addQuestions(
                    categoryNumber,
                    listOf(NewQuestion(text.trim(), filled.map { it.second }, newCorrectIndex, imageUrl))
                )
            }
            isLoadingQuestions = false   // AdminScreen isLoading dəyişəndə siyahını özü təzələyir
            if (saved != null) onSuccess()
        }
    }

    /**
     * Şəkli maks. 1600px-ə kiçildib JPEG kimi serverə yükləyir və onun ünvanını
     * qaytarır. Telefon kamerasının şəkilləri çox vaxt 5 MB-dan böyük olur.
     */
    private suspend fun uploadImage(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        val bytes = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_IMAGE_SIZE) sampleSize *= 2

            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            } ?: throw IllegalStateException("Şəkil oxuna bilmədi")
            val scale = MAX_IMAGE_SIZE.toFloat() / maxOf(decoded.width, decoded.height)
            val bitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
            } else decoded

            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.toByteArray()
            }
        }
        val part = MultipartBody.Part.createFormData("file", "image.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
        return api.service.uploadImage(part).url
    }

    /**
     * Bir dəfəyə çoxlu sual əlavə edir. Format (hər sual boş sətirlə ayrılır):
     *   Sualın mətni?
     *   Səhv variant
     *   *Düzgün variant
     */
    fun uploadQuestionsBulk(categoryNumber: Int, rawText: String, onSuccess: () -> Unit = {}) {
        val blocks = rawText.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotBlank() }
        val parsed = blocks.mapNotNull { block ->
            val lines = block.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.size < 3) return@mapNotNull null   // sual + ən azı 2 variant lazımdır
            val optionLines = lines.drop(1)
            val correctIndex = optionLines.indexOfFirst { it.startsWith("*") }.coerceAtLeast(0)
            NewQuestion(lines.first(), optionLines.map { it.removePrefix("*").trim() }, correctIndex)
        }
        if (parsed.isEmpty()) {
            errorMessage = "Düzgün formatlı heç bir sual tapılmadı (hər sualın ən azı 2 variantı olmalıdır)."
            return
        }

        viewModelScope.launch {
            isLoadingQuestions = true
            val saved = apiCall("Toplu sual saxlanıla bilmədi.") { api.service.addQuestions(categoryNumber, parsed) }
            isLoadingQuestions = false
            if (saved != null) onSuccess()
        }
    }

    private fun withAbsoluteImage(q: Question) = q.copy(imageUrl = api.absoluteUrl(q.imageUrl))

    // ---------------------------------------------------------------------
    // İMTAHAN AXINI: BAŞLAMA, TAYMER, CAVABLANDIRMA, BİTİRMƏ
    // ---------------------------------------------------------------------

    fun selectCategory(categoryNumber: Int) {
        selectedCategory = categoryNumber
        isLoadingQuestions = true
        screenState = ScreenState.Loading

        viewModelScope.launch {
            try {
                val loaded = api.service.questions(categoryNumber).map(::withAbsoluteImage)
                if (loaded.isEmpty()) {
                    errorMessage = "Bu sınaqda hələ sual yoxdur."
                    screenState = ScreenState.CategorySelection
                } else {
                    _questions.clear()
                    _questions.addAll(loaded)
                    startTest()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.userMessage("Suallar yüklənə bilmədi.")
                if (e.httpCode == 401) clearStudentSession() else screenState = ScreenState.CategorySelection
            } finally {
                isLoadingQuestions = false
            }
        }
    }

    /** Vaxt sual sayına mütənasibdir: hər sual üçün 12 saniyə (25 sualda 5 dəqiqə). */
    private fun startTest() {
        screenState = ScreenState.Testing
        currentQuestionIndex = 0
        score = 0
        timeLeft = _questions.size * SECONDS_PER_QUESTION
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (timeLeft > 0 && screenState == ScreenState.Testing) {
                delay(1000)
                timeLeft--
            }
            if (timeLeft == 0) finishTest()
        }
    }

    /** İmtahandan (təsdiqdən sonra) çıxış - nəticə yadda saxlanılmır. */
    fun exitTest() {
        timerJob?.cancel()
        screenState = ScreenState.CategorySelection
    }

    /**
     * Cavab seçiləndə (düzgün/səhv animasiyasından sonra) çağırılır.
     * "screenState != Testing" yoxlaması: vaxt məhz animasiya zamanı bitibsə,
     * gecikmiş çağırış nəticəni ikinci dəfə pozmasın.
     */
    fun answerQuestion(selectedIndex: Int) {
        if (screenState != ScreenState.Testing) return
        if (selectedIndex == _questions[currentQuestionIndex].correctAnswerIndex) score++

        if (currentQuestionIndex < _questions.size - 1) currentQuestionIndex++ else finishTest()
    }

    private fun finishTest() {
        timerJob?.cancel()
        screenState = ScreenState.Result
        val result = ResultRequest(selectedCategory, score, _questions.size)
        viewModelScope.launch {
            try {
                api.service.saveResult(result)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Nəticə ekranı onsuz da göstərilir - saxlanma xətası şagirdə mane olmasın
            }
        }
    }

    fun restart() {
        screenState = ScreenState.CategorySelection
    }

    private companion object {
        const val KEY_TOKEN = "student_token"
        const val REFRESH_INTERVAL_MS = 15_000L
        const val SECONDS_PER_QUESTION = 12
        const val MAX_IMAGE_SIZE = 1600
    }
}
