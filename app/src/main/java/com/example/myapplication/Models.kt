package com.example.myapplication

data class Question(
    val id: Int = 0,
    val text: String = "",
    val options: List<String> = emptyList(),
    val correctAnswerIndex: Int = 0,
    val imageUrl: String? = null // Şəklin internet ünvanı
)

data class UserInfo(
    val firstName: String,
    val lastName: String
)
