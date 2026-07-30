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

enum class ScreenState {
    Registration, CategorySelection, Testing, Result, Loading, Admin
}

class TestViewModel(application: Application) : AndroidViewModel(application) {
    var screenState by mutableStateOf(ScreenState.Registration)
    var userInfo by mutableStateOf<UserInfo?>(null)
    var currentQuestionIndex by mutableStateOf(0)
    var score by mutableStateOf(0)
    var timeLeft by mutableStateOf(60)
    var selectedCategory by mutableStateOf("")
    private var timerJob: Job? = null

    private val database = FirebaseDatabase.getInstance("https://myapplication-223cacbf-default-rtdb.firebaseio.com")
    private val questionsRef = database.getReference("questions")
    private val resultsRef = database.getReference("results")
    private val purchasesRef = database.getReference("purchases")
    private val storage = FirebaseStorage.getInstance()
    private val storageRef = storage.getReference("question_images")
    private val functions = FirebaseFunctions.getInstance()
    private val prefs = application.getSharedPreferences("exam_prefs", Context.MODE_PRIVATE)

    private val _questions = mutableStateListOf<Question>()
    val questions: List<Question> = _questions

    var isLoadingQuestions by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    // 4-cü sınaqdan etibarən ödənişli açılış üçün
    var phoneNumber by mutableStateOf("")
        private set
    var isUnlocked by mutableStateOf(false)
        private set
    var isProcessingPayment by mutableStateOf(false)
        private set
    var checkoutUrl by mutableStateOf<String?>(null)
        private set
    private var unlockListener: ValueEventListener? = null

    init {
        phoneNumber = prefs.getString(PREF_PHONE_NUMBER, "") ?: ""
        if (sanitizePhone(phoneNumber).length >= 9) {
            startListeningForUnlock(phoneNumber)
        }
    }

    fun clearError() {
        errorMessage = null
    }

    private fun sanitizePhone(phone: String) = phone.filter { it.isDigit() }

    fun updatePhoneNumber(value: String) {
        phoneNumber = value
        prefs.edit().putString(PREF_PHONE_NUMBER, value).apply()
    }

    fun startListeningForUnlock(phone: String) {
        val sanitized = sanitizePhone(phone)
        if (sanitized.length < 9) return

        unlockListener?.let { purchasesRef.removeEventListener(it) }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isUnlocked = snapshot.child("unlocked").getValue(Boolean::class.java) == true
                if (isUnlocked) {
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

    // NOT: Bu, "initiatePayment" adlı bir Firebase Cloud Function çağırır.
    // O funksiya hələ Kapital Bank merchant məlumatları ilə konfiqurasiya
    // olunmayıb (bax: functions/kapitalBankClient.js) - merchant hesabı
    // alındıqdan sonra deploy edilməlidir.
    fun initiatePayment() {
        val sanitized = sanitizePhone(phoneNumber)
        if (sanitized.length < 9) {
            errorMessage = "Zəhmət olmasa düzgün telefon nömrəsi daxil edin."
            return
        }

        isProcessingPayment = true
        functions.getHttpsCallable("initiatePayment")
            .call(mapOf("phone" to sanitized))
            .addOnSuccessListener { result ->
                val url = (result.data as? Map<*, *>)?.get("checkoutUrl") as? String
                if (url != null) {
                    checkoutUrl = url
                    startListeningForUnlock(sanitized)
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

    fun consumeCheckoutUrl() {
        checkoutUrl = null
    }

    fun restorePurchase(phone: String) {
        updatePhoneNumber(phone)
        startListeningForUnlock(phone)
    }

    private companion object {
        const val PREF_PHONE_NUMBER = "phone_number"
    }

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
                
                imageUri?.let { uri ->
                    val fileName = "${System.currentTimeMillis()}.jpg"
                    val fileRef = storageRef.child(fileName)
                    fileRef.putFile(uri).await()
                    downloadUrl = fileRef.downloadUrl.await().toString()
                }

                val questionId = System.currentTimeMillis().toInt()
                val newQuestion = Question(
                    id = questionId,
                    text = text,
                    options = options,
                    correctAnswerIndex = correctIndex,
                    imageUrl = downloadUrl
                )

                database.getReference("questions").child(category).child(questionId.toString())
                    .setValue(newQuestion).await()
                
                isLoadingQuestions = false
                screenState = ScreenState.CategorySelection
            } catch (e: Exception) {
                isLoadingQuestions = false
                errorMessage = "Sual yadda saxlanıla bilmədi: ${e.message}"
            }
        }
    }

    fun register(firstName: String, lastName: String) {
        userInfo = UserInfo(firstName, lastName)
        screenState = ScreenState.CategorySelection
    }

    fun selectCategory(category: String) {
        selectedCategory = category
        loadQuestionsForCategory(category)
    }

    private fun loadQuestionsForCategory(category: String) {
        isLoadingQuestions = true
        screenState = ScreenState.Loading
        
        questionsRef.child(category).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _questions.clear()
                for (postSnapshot in snapshot.children) {
                    val question = postSnapshot.getValue(Question::class.java)
                    question?.let { _questions.add(it) }
                }
                
                if (_questions.isEmpty()) {
                    seedDefaultQuestions(category)
                }
                
                startTest()
                isLoadingQuestions = false
            }

            override fun onCancelled(error: DatabaseError) {
                isLoadingQuestions = false
                errorMessage = "Suallar yüklənə bilmədi: ${error.message}"
                screenState = ScreenState.CategorySelection
            }
        })
    }

    private fun seedDefaultQuestions(category: String) {
        val defaultQuestions = when(category) {
            "sınaq 1" -> getLogicQuestions()
            "sınaq 2" -> getAnimalQuestions()
            "sınaq 3" -> getNatureQuestions()
            else -> emptyList()
        }
        
        _questions.addAll(defaultQuestions)
        
        viewModelScope.launch {
            try {
                defaultQuestions.forEach { questionsRef.child(category).child(it.id.toString()).setValue(it) }
            } catch (e: Exception) {}
        }
    }

    private fun getLogicQuestions(): List<Question> {
        return (1..25).map { Question(it, "Məntiq Sualı $it: ...", listOf("Variant A", "Variant B", "Variant C", "Variant D"), 0) }
    }

    private fun getAnimalQuestions(): List<Question> {
        return (1..25).map { Question(it, "Heyvanlar Sualı $it: ...", listOf("Variant A", "Variant B", "Variant C", "Variant D"), 1) }
    }

    private fun getNatureQuestions(): List<Question> {
        return (1..25).map { Question(it, "Təbiət Sualı $it: ...", listOf("Variant A", "Variant B", "Variant C", "Variant D"), 2) }
    }

    private fun startTest() {
        screenState = ScreenState.Testing
        currentQuestionIndex = 0
        score = 0
        timeLeft = 300 // 5 dəqiqə (25 sual üçün)
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (timeLeft > 0 && screenState == ScreenState.Testing) {
                delay(1000)
                timeLeft--
            }
            if (timeLeft == 0) {
                finishTest()
            }
        }
    }

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

    private fun finishTest() {
        timerJob?.cancel()
        screenState = ScreenState.Result
        saveResultToFirebase()
    }

    private fun saveResultToFirebase() {
        val resultId = resultsRef.push().key ?: return
        val resultData = mapOf(
            "firstName" to (userInfo?.firstName ?: "Anonim"),
            "lastName" to (userInfo?.lastName ?: "İstifadəçi"),
            "score" to score,
            "total" to _questions.size,
            "timestamp" to System.currentTimeMillis()
        )
        resultsRef.child(resultId).setValue(resultData)
    }

    fun restart() {
        screenState = ScreenState.CategorySelection
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        unlockListener?.let { purchasesRef.removeEventListener(it) }
    }
}
