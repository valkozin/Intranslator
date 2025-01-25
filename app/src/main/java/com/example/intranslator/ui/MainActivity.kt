package com.example.intranslator.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.intranslator.data.PreferencesManager
import com.example.intranslator.models.DictionaryEntry
import com.example.intranslator.models.QuizResult
import com.example.intranslator.translation.TranslationManager
import com.example.intranslator.ui.theme.IntranslatorTheme
import kotlinx.coroutines.runBlocking
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.aallam.openai.client.OpenAI
import com.aallam.openai.api.http.Timeout
import kotlin.time.Duration.Companion.seconds

private val Activity.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var translationManager: TranslationManager
    private lateinit var openAI: OpenAI

    private var dictionaryEntries by mutableStateOf<List<DictionaryEntry>>(emptyList())
    private var quizHistory by mutableStateOf<List<QuizResult>>(emptyList())
    private var showDictionary by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var showQuiz by mutableStateOf(false)
    private var apiKeyState by mutableStateOf("")
    private var targetLanguageState by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferencesManager = PreferencesManager(dataStore)
        openAI = OpenAI(token = apiKeyState, timeout = Timeout(socket = 60.seconds))
        translationManager = TranslationManager(this, openAI)

        loadPreferences()

        setContent {
            IntranslatorTheme {
                // UI content goes here
            }
        }
    }

    private fun loadPreferences() {
        runBlocking {
            val preferences = preferencesManager.loadPreferences()
            apiKeyState = preferences["api_key"] ?: ""
            targetLanguageState = preferences["target_language"] ?: ""
            // Load other preferences as needed
        }
    }

    // Other methods for handling speech recognition, etc.
} 