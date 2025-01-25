package com.example.intranslator.translation

import android.app.Activity
import android.speech.RecognizerIntent
import android.widget.Toast
import com.aallam.openai.api.chat.*
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TranslationManager(private val activity: Activity, private val openAI: OpenAI) {
    fun translateText(text: String, apiKeyState: String, targetLanguageState: String, onTranslation: (String) -> Unit) {
        if (apiKeyState.isEmpty()) {
            Toast.makeText(activity, "Please enter OpenAI API key in settings", Toast.LENGTH_LONG).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val completion = openAI.chatCompletion(
                    ChatCompletionRequest(
                        model = ModelId("gpt-3.5-turbo"),
                        messages = listOf(
                            ChatMessage(role = ChatRole.System, content = "You are a translator. Translate the following text to $targetLanguageState. Only respond with the translation, nothing else."),
                            ChatMessage(role = ChatRole.User, content = text)
                        )
                    )
                )

                val translatedText = completion.choices.first().message.content ?: "Translation failed"
                withContext(Dispatchers.Main) {
                    onTranslation(translatedText)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(activity, "Translation error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
} 