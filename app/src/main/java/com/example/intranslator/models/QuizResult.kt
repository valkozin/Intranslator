package com.example.intranslator.models

import kotlinx.serialization.Serializable

@Serializable
data class QuizResult(
    val score: Int,
    val totalAttempts: Int,
    val timePeriod: String,
    val languagePair: Pair<String, String>?,
    val timestamp: Long = System.currentTimeMillis()
) 