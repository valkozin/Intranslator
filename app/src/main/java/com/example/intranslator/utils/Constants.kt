package com.example.intranslator.utils

object Constants {
    val AVAILABLE_MODELS = listOf(
        "gpt-3.5-turbo",
        "gpt-4",
        "gpt-4-turbo-preview",
        "gpt-4o",
        "gpt-4o-mini"
    )

    val MODEL_PRICING = mapOf(
        "gpt-3.5-turbo" to Pair(0.000002, 0.000002),
        "gpt-4" to Pair(0.00003, 0.00006),
        "gpt-4-turbo-preview" to Pair(0.000015, 0.00003),
        "gpt-4o" to Pair(0.00004, 0.00008),
        "gpt-4o-mini" to Pair(0.00002, 0.00004)
    )

    val AVAILABLE_LANGUAGES = mapOf(
        "English (US)" to "en-US",
        "Spanish" to "es-ES",
        "French" to "fr-FR",
        "German" to "de-DE",
        "Italian" to "it-IT",
        "Japanese" to "ja-JP",
        "Korean" to "ko-KR",
        "Chinese (Simplified)" to "zh-CN",
        "Russian" to "ru-RU",
        "Portuguese" to "pt-PT",
        "Hindi" to "hi-IN"
    )

    val QUIZ_TIME_PERIODS = listOf(
        "Last 24 hours",
        "Last 7 days",
        "Last 30 days",
        "Last 365 days",
        "All time"
    )

    val QUIZ_MODES = listOf(
        "Input Language",
        "Translated Language",
        "Mixed"
    )
} 