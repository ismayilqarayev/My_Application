package com.example.myapplication

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class ScreenState {
    Registration, CategorySelection, Testing, Result, Loading, Admin
}

class TestViewModel : ViewModel() {
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
    private val storage = FirebaseStorage.getInstance()
    private val storageRef = storage.getReference("question_images")

    private val _questions = mutableStateListOf<Question>()
    val questions: List<Question> = _questions
    
    var isLoadingQuestions by mutableStateOf(false)
        private set

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
    }
}
