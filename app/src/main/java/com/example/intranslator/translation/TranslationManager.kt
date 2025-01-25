package com.example.intranslator.translation

import android.app.Activity
import android.speech.RecognizerIntent
import android.widget.Toast
import com.aallam.openai.api.chat.*
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

class TranslationManager(private val apiKey: String, private val modelType: String) {
    private val openAI by lazy {
        OpenAI(
            token = apiKey,
            timeout = Timeout(socket = 60.seconds)
        )
    }

    private val modelPricing = mapOf(
        "gpt-3.5-turbo" to Pair(0.000002, 0.000002),
        "gpt-4" to Pair(0.00003, 0.00006),
        "gpt-4-turbo-preview" to Pair(0.000015, 0.00003),
        "gpt-4o" to Pair(0.00004, 0.00008),
        "gpt-4o-mini" to Pair(0.00002, 0.00004)
    )

    suspend fun translateText(
        text: String,
        targetLanguage: String,
        onTokenUsageUpdate: (Int) -> Unit
    ): String {
        val completion = openAI.chatCompletion(
            ChatCompletionRequest(
                model = ModelId(modelType),
                messages = listOf(
                    ChatMessage(
                        role = ChatRole.System,
                        content = "You are a translator. Translate the following text to $targetLanguage. Only respond with the translation, nothing else."
                    ),
                    ChatMessage(
                        role = ChatRole.User,
                        content = text
                    )
                )
            )
        )

        val usage = completion.usage?.totalTokens ?: 0
        onTokenUsageUpdate(usage)

        return completion.choices.first().message.content ?: "Translation failed"
    }

    suspend fun validateQuizAnswer(
        userAnswer: String,
        correctAnswer: String,
        fromLanguage: String,
        toLanguage: String,
        onTokenUsageUpdate: (Int, Double) -> Unit
    ): Boolean {
        val completion = openAI.chatCompletion(
            ChatCompletionRequest(
                model = ModelId(modelType),
                messages = listOf(
                    ChatMessage(
                        role = ChatRole.System,
                        content = """You are a language expert validating translations. 
                            |Consider the following aspects when validating:
                            |1. The core meaning should be the same
                            |2. Common spelling variations are acceptable (e.g., ss vs ß in German)
                            |3. Minor typos that don't change the meaning are acceptable
                            |4. Different valid translations of the same concept are acceptable
                            |5. Different word orders that are grammatically correct are acceptable
                            |
                            |Respond with ONLY "true" if the translation is acceptable, or "false" if it's incorrect.
                            |Do not provide any other text in your response.""".trimMargin()
                    ),
                    ChatMessage(
                        role = ChatRole.User,
                        content = """From $fromLanguage to $toLanguage:
                            |Expected translation: $correctAnswer
                            |User's translation: $userAnswer""".trimMargin()
                    )
                )
            )
        )

        // Update token usage and cost
        val inputTokens = completion.usage?.promptTokens ?: 0
        val outputTokens = completion.usage?.completionTokens ?: 0
        val (inputPrice, outputPrice) = modelPricing[modelType] ?: Pair(0.0, 0.0)
        val cost = inputTokens * inputPrice + outputTokens * outputPrice
        onTokenUsageUpdate(inputTokens + outputTokens, cost)

        return completion.choices.first().message.content?.trim().equals("true", ignoreCase = true)
    }

    suspend fun getAdditionalTranslationInfo(
        originalText: String,
        translatedText: String,
        fromLanguage: String,
        toLanguage: String,
        onTokenUsageUpdate: (Int) -> Unit
    ): String {
        val completion = openAI.chatCompletion(
            ChatCompletionRequest(
                model = ModelId(modelType),
                messages = listOf(
                    ChatMessage(
                        role = ChatRole.System,
                        content = """
                            You are a helpful AI assistant specializing in analyzing translations. Please follow these instructions exactly:

                            1. Write your entire response in "$toLanguage" only.
                            2. Use the following template for your response (include only the sections that apply):
                            
                            [Gender/Plural]
                            Provide gender (m/f/n) and the plural form if:
                            - The translation is a single noun,
                            - The source language is not English.
                            
                            [Alternative Meanings]
                            Provide 2–3 alternative meanings or translations, if possible.
                            
                            [Example Usage]
                            For individual words or short phrases, include:
                            - A brief usage example in "$fromLanguage",
                            - Its translation in "$toLanguage".
                            
                            [Correction]
                            If there are grammatical errors in the original or the translated text, provide the corrected version (marked with "*").
                            
                            [Conjugations]
                            If the translation is a single verb, provide relevant tense conjugations.

                            3. Do not include any additional text or commentary beyond these template sections.
                        """.trimIndent()
                    ),
                    ChatMessage(
                        role = ChatRole.User,
                        content = """
                            From $fromLanguage to $toLanguage:
                            Original: $originalText
                            Translation: $translatedText
                        """.trimIndent()
                    )
                )
            )
        )

        val usage = completion.usage?.totalTokens ?: 0
        onTokenUsageUpdate(usage)

        return completion.choices.first().message.content ?: "Failed to get additional information"
    }
} 