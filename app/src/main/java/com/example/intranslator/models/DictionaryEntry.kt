package com.example.intranslator.models

import kotlinx.serialization.Serializable

@Serializable
data class DictionaryEntry(
    val originalText: String,
    val translatedText: String,
    val fromLanguage: String,
    val toLanguage: String,
    val timestamp: Long = System.currentTimeMillis()
) 