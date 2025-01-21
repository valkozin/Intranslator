package com.example.intranslator

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.activity.compose.BackHandler
import com.aallam.openai.api.chat.*
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.example.intranslator.ui.theme.IntranslatorTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.DateFormat
import java.util.*
import kotlin.time.Duration.Companion.seconds
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import android.speech.tts.TextToSpeech

@Serializable
data class DictionaryEntry(
    val originalText: String,
    val translatedText: String,
    val fromLanguage: String,
    val toLanguage: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class QuizResult(
    val score: Int,
    val totalAttempts: Int,
    val timePeriod: String,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
val Activity.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
private val TARGET_LANGUAGE = stringPreferencesKey("target_language")
private val API_KEY = stringPreferencesKey("api_key")
private val DICTIONARY_ENTRIES = stringPreferencesKey("dictionary_entries")
private val MODEL_TYPE = stringPreferencesKey("model_type")
private val TOKEN_USAGE = stringPreferencesKey("token_usage")
private val READ_ALOUD_ENABLED = stringPreferencesKey("read_aloud_enabled")
private val QUIZ_TIME_PERIOD = stringPreferencesKey("quiz_time_period")
private val QUIZ_HISTORY = stringPreferencesKey("quiz_history")
private val QUIZ_MODE = stringPreferencesKey("quiz_mode")
private val QUIZ_LANGUAGE_PAIR = stringPreferencesKey("quiz_language_pair")
private val HISTORY_LANGUAGE_PAIR = stringPreferencesKey("history_language_pair")

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private var dictionaryEntries by mutableStateOf<List<DictionaryEntry>>(emptyList())
    private var quizHistory by mutableStateOf<List<QuizResult>>(emptyList())
    private var showDictionary by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var showQuiz by mutableStateOf(false)
    private var tokenUsage by mutableStateOf(0)
    private var modelType by mutableStateOf("gpt-3.5-turbo")
    private var readAloudEnabled by mutableStateOf(false)
    private var quizTimePeriod by mutableStateOf("All time")
    private var quizMode by mutableStateOf("Mixed")
    private var quizLanguagePair by mutableStateOf<Pair<String, String>?>(null)
    private var historyLanguagePair by mutableStateOf<Pair<String, String>?>(null)
    private lateinit var textToSpeech: TextToSpeech
    
    private val availableModels = listOf(
        "gpt-3.5-turbo",
        "gpt-4",
        "gpt-4-turbo-preview"
    )

    private val availableLanguages = mapOf(
        "English" to "en-US",
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

    private var recognizedTextState by mutableStateOf("")
    private var inputTextState by mutableStateOf("")
    private var translatedTextState by mutableStateOf("")
    private var selectedLanguageState by mutableStateOf("")
    private var targetLanguageState by mutableStateOf("")
    private var apiKeyState by mutableStateOf("")
    private var isTranslating by mutableStateOf(false)

    private val openAI by lazy {
        OpenAI(
            token = apiKeyState,
            timeout = Timeout(socket = 60.seconds)
        )
    }

    private val speechRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                updateRecognizedText(results[0])
                if (targetLanguageState.isNotEmpty() && apiKeyState.isNotEmpty()) {
                    translateText(results[0])
                }
            }
        }
    }

    private fun translateText(text: String) {
        if (apiKeyState.isEmpty()) {
            Toast.makeText(this, "Please enter OpenAI API key in settings", Toast.LENGTH_LONG).show()
            return
        }

        // Set up TextToSpeech language
        if (readAloudEnabled) {
            val locale = when (targetLanguageState) {
                "English (US)" -> Locale.US
                "English (UK)" -> Locale.UK
                "Spanish" -> Locale("es")
                "French" -> Locale.FRENCH
                "German" -> Locale.GERMAN
                "Italian" -> Locale.ITALIAN
                "Japanese" -> Locale.JAPANESE
                "Korean" -> Locale.KOREAN
                "Chinese (Simplified)" -> Locale.SIMPLIFIED_CHINESE
                "Russian" -> Locale("ru")
                "Portuguese" -> Locale("pt")
                "Hindi" -> Locale("hi")
                else -> Locale.US
            }
            textToSpeech.language = locale
        }

        updateRecognizedText(text)
        isTranslating = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val completion = openAI.chatCompletion(
                    ChatCompletionRequest(
                        model = ModelId(modelType),
                        messages = listOf(
                            ChatMessage(
                                role = ChatRole.System,
                                content = "You are a translator. Translate the following text to $targetLanguageState. Only respond with the translation, nothing else."
                            ),
                            ChatMessage(
                                role = ChatRole.User,
                                content = text
                            )
                        )
                    )
                )
                
                val translatedText = completion.choices.first().message.content ?: "Translation failed"
                val usage = completion.usage?.totalTokens ?: 0
                
                withContext(Dispatchers.Main) {
                    translatedTextState = translatedText
                    isTranslating = false
                    tokenUsage += usage
                    saveTokenUsage(tokenUsage)
                    
                    // Speak the translated text
                    speakText(translatedText)
                    
                    // Save to dictionary
                    runBlocking {
                        saveDictionaryEntry(
                            DictionaryEntry(
                                originalText = text,
                                translatedText = translatedText,
                                fromLanguage = selectedLanguageState,
                                toLanguage = targetLanguageState
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Translation error: ${e.message}", Toast.LENGTH_LONG).show()
                    isTranslating = false
                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSpeechRecognition()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateRecognizedText(text: String) {
        recognizedTextState = text
        translatedTextState = ""  // Clear previous translation
    }

    private fun startSpeechRecognition() {
        val languageCode = availableLanguages[selectedLanguageState] ?: "en-US"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now in ${selectedLanguageState}...")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Speech recognition not available for ${selectedLanguageState}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun checkPermissionAndStartRecognition() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startSpeechRecognition()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private suspend fun saveSelectedLanguage(language: String) {
        dataStore.edit { preferences ->
            preferences[SELECTED_LANGUAGE] = language
        }
    }

    private suspend fun saveTargetLanguage(language: String) {
        dataStore.edit { preferences ->
            preferences[TARGET_LANGUAGE] = language
        }
    }

    private suspend fun saveApiKey(key: String) {
        dataStore.edit { preferences ->
            preferences[API_KEY] = key
        }
    }

    private suspend fun saveModelType(model: String) {
        dataStore.edit { preferences ->
            preferences[MODEL_TYPE] = model
        }
    }

    private suspend fun saveTokenUsage(usage: Int) {
        dataStore.edit { preferences ->
            preferences[TOKEN_USAGE] = usage.toString()
        }
    }

    private suspend fun saveQuizTimePeriod(period: String) {
        dataStore.edit { preferences ->
            preferences[QUIZ_TIME_PERIOD] = period
        }
    }

    private suspend fun saveQuizResult(result: QuizResult) {
        val updatedHistory = quizHistory + result
        quizHistory = updatedHistory
        dataStore.edit { preferences ->
            preferences[QUIZ_HISTORY] = Json.encodeToString(updatedHistory)
        }
    }

    private suspend fun saveQuizMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[QUIZ_MODE] = mode
        }
    }

    private suspend fun saveQuizLanguagePair(pair: Pair<String, String>?) {
        dataStore.edit { preferences ->
            if (pair != null) {
                preferences[QUIZ_LANGUAGE_PAIR] = "${pair.first}|${pair.second}"
            } else {
                preferences[QUIZ_LANGUAGE_PAIR] = ""
            }
        }
    }

    private suspend fun saveHistoryLanguagePair(pair: Pair<String, String>?) {
        dataStore.edit { preferences ->
            if (pair != null) {
                preferences[HISTORY_LANGUAGE_PAIR] = "${pair.first}|${pair.second}"
            } else {
                preferences[HISTORY_LANGUAGE_PAIR] = ""
            }
        }
    }

    private fun getAvailableLanguagePairs(): List<Pair<String, String>> {
        return dictionaryEntries
            .map { entry -> 
                if (entry.fromLanguage < entry.toLanguage)
                    Pair(entry.fromLanguage, entry.toLanguage)
                else
                    Pair(entry.toLanguage, entry.fromLanguage)
            }
            .distinct()
            .sortedBy { "${it.first}${it.second}" }
    }

    private fun loadPreferences() {
        runBlocking {
            selectedLanguageState = dataStore.data
                .map { preferences ->
                    preferences[SELECTED_LANGUAGE] ?: "English (US)"
                }
                .first()

            targetLanguageState = dataStore.data
                .map { preferences ->
                    preferences[TARGET_LANGUAGE] ?: ""
                }
                .first()

            apiKeyState = dataStore.data
                .map { preferences ->
                    preferences[API_KEY] ?: ""
                }
                .first()

            modelType = dataStore.data
                .map { preferences ->
                    preferences[MODEL_TYPE] ?: "gpt-3.5-turbo"
                }
                .first()
                
            tokenUsage = dataStore.data
                .map { preferences ->
                    preferences[TOKEN_USAGE]?.toIntOrNull() ?: 0
                }
                .first()

            readAloudEnabled = dataStore.data
                .map { preferences ->
                    preferences[READ_ALOUD_ENABLED]?.toBoolean() ?: false
                }
                .first()

            quizTimePeriod = dataStore.data
                .map { preferences ->
                    preferences[QUIZ_TIME_PERIOD] ?: "All time"
                }
                .first()

            quizMode = dataStore.data
                .map { preferences ->
                    preferences[QUIZ_MODE] ?: "Mixed"
                }
                .first()

            val languagePairStr = dataStore.data
                .map { preferences ->
                    preferences[QUIZ_LANGUAGE_PAIR] ?: ""
                }
                .first()
            
            quizLanguagePair = if (languagePairStr.isNotEmpty()) {
                val parts = languagePairStr.split("|")
                if (parts.size == 2) Pair(parts[0], parts[1]) else null
            } else null

            val historyPairStr = dataStore.data
                .map { preferences ->
                    preferences[HISTORY_LANGUAGE_PAIR] ?: ""
                }
                .first()
            
            historyLanguagePair = if (historyPairStr.isNotEmpty()) {
                val parts = historyPairStr.split("|")
                if (parts.size == 2) Pair(parts[0], parts[1]) else null
            } else null
        }
    }

    private suspend fun saveDictionaryEntry(entry: DictionaryEntry) {
        val updatedEntries = dictionaryEntries.filter { existingEntry ->
            // Don't keep entries that match (case-insensitive)
            !((existingEntry.originalText.equals(entry.originalText, ignoreCase = true) &&
               existingEntry.translatedText.equals(entry.translatedText, ignoreCase = true) &&
               existingEntry.fromLanguage == entry.fromLanguage &&
               existingEntry.toLanguage == entry.toLanguage) ||
              (existingEntry.originalText.equals(entry.translatedText, ignoreCase = true) &&
               existingEntry.translatedText.equals(entry.originalText, ignoreCase = true) &&
               existingEntry.fromLanguage == entry.toLanguage &&
               existingEntry.toLanguage == entry.fromLanguage))
        }
        dictionaryEntries = updatedEntries + entry
        dataStore.edit { preferences ->
            preferences[DICTIONARY_ENTRIES] = Json.encodeToString(updatedEntries + entry)
        }
    }

    private suspend fun deleteDictionaryEntry(entry: DictionaryEntry) {
        val updatedEntries = dictionaryEntries.filter { existingEntry ->
            // Don't keep entries that match (case-insensitive)
            !((existingEntry.originalText.equals(entry.originalText, ignoreCase = true) &&
               existingEntry.translatedText.equals(entry.translatedText, ignoreCase = true) &&
               existingEntry.fromLanguage == entry.fromLanguage &&
               existingEntry.toLanguage == entry.toLanguage) ||
              (existingEntry.originalText.equals(entry.translatedText, ignoreCase = true) &&
               existingEntry.translatedText.equals(entry.originalText, ignoreCase = true) &&
               existingEntry.fromLanguage == entry.toLanguage &&
               existingEntry.toLanguage == entry.fromLanguage))
        }
        dictionaryEntries = updatedEntries
        dataStore.edit { preferences ->
            preferences[DICTIONARY_ENTRIES] = Json.encodeToString(updatedEntries)
        }
    }

    private fun loadDictionaryEntries() {
        runBlocking {
            val entriesJson = dataStore.data
                .map { preferences ->
                    preferences[DICTIONARY_ENTRIES] ?: "[]"
                }
                .first()
            try {
                dictionaryEntries = Json.decodeFromString(entriesJson)
            } catch (e: Exception) {
                dictionaryEntries = emptyList()
            }
        }
    }

    private fun loadQuizHistory() {
        runBlocking {
            val historyJson = dataStore.data
                .map { preferences ->
                    preferences[QUIZ_HISTORY] ?: "[]"
                }
                .first()
            try {
                quizHistory = Json.decodeFromString(historyJson)
            } catch (e: Exception) {
                quizHistory = emptyList()
            }
        }
    }

    private suspend fun validateQuizAnswer(
        userAnswer: String,
        correctAnswer: String,
        fromLanguage: String,
        toLanguage: String
    ): Boolean {
        return try {
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
            
            // Update token usage
            val usage = completion.usage?.totalTokens ?: 0
            withContext(Dispatchers.Main) {
                tokenUsage += usage
                saveTokenUsage(tokenUsage)
            }
            
            completion.choices.first().message.content?.trim().equals("true", ignoreCase = true)
        } catch (e: Exception) {
            // Fallback to exact match if API call fails
            userAnswer.trim().equals(correctAnswer.trim(), ignoreCase = true)
        }
    }

    private suspend fun getAdditionalTranslationInfo(
        originalText: String,
        translatedText: String,
        fromLanguage: String,
        toLanguage: String
    ): String {
        return try {
            val completion = openAI.chatCompletion(
                ChatCompletionRequest(
                    model = ModelId(modelType),
                    messages = listOf(
                        ChatMessage(
                            role = ChatRole.System,
                            content = """
                                You are a helpful AI assistant analyzing translations. FOLLOW STRICTLY THE FOLLOWING FORMAT:
                                1. All your response must be in "$toLanguage".
                                2. If the translation is only one noun: (a) provide its gender (when applicable) and (b) provide its plural form (when applicable). Use the format (m/f/n/pl). Ignore these two clauses for translations from English.
                                3. If possible, list 2-3 alternative meanings or translations. 
                                4. When translating individual words or short phrases, provide one brief example of usage in "$fromLanguage" and its translation to "$toLanguage".
                                5. Don't insert any other text or phrases in your response.
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
            
            // Update token usage
            val usage = completion.usage?.totalTokens ?: 0
            withContext(Dispatchers.Main) {
                tokenUsage += usage
                saveTokenUsage(tokenUsage)
            }
            
            completion.choices.first().message.content ?: "Failed to get additional information"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadPreferences()
        loadDictionaryEntries()
        loadQuizHistory()
        enableEdgeToEdge()
        
        // Initialize TextToSpeech
        textToSpeech = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Toast.makeText(this, "Text to speech initialization failed", Toast.LENGTH_LONG).show()
            }
        }

        setContent {
            IntranslatorTheme {
                when {
                    showSettings -> {
                        SettingsScreen(
                            apiKey = apiKeyState,
                            modelType = modelType,
                            availableModels = availableModels,
                            tokenUsage = tokenUsage,
                            onApiKeyChanged = { key ->
                                apiKeyState = key
                                runBlocking { saveApiKey(key) }
                            },
                            onModelTypeChanged = { model ->
                                modelType = model
                                runBlocking { saveModelType(model) }
                            },
                            onBackClick = { showSettings = false }
                        )
                    }
                    showDictionary -> {
                        DictionaryScreen(
                            entries = dictionaryEntries,
                            languagePairs = getAvailableLanguagePairs(),
                            selectedLanguagePair = historyLanguagePair,
                            onLanguagePairSelected = { pair ->
                                historyLanguagePair = pair
                                runBlocking { saveHistoryLanguagePair(pair) }
                            },
                            onBackClick = { showDictionary = false },
                            onDeleteEntry = { entry ->
                                runBlocking {
                                    deleteDictionaryEntry(entry)
                                }
                            },
                            onGetAdditionalInfo = { entry ->
                                getAdditionalTranslationInfo(
                                    entry.originalText,
                                    entry.translatedText,
                                    entry.fromLanguage,
                                    entry.toLanguage
                                )
                            }
                        )
                    }
                    showQuiz -> {
                        QuizScreen(
                            timePeriods = quizTimePeriods,
                            selectedPeriod = quizTimePeriod,
                            quizHistory = quizHistory,
                            quizMode = quizMode,
                            languagePairs = getAvailableLanguagePairs(),
                            selectedLanguagePair = quizLanguagePair,
                            onLanguagePairSelected = { pair ->
                                quizLanguagePair = pair
                                runBlocking { saveQuizLanguagePair(pair) }
                            },
                            onQuizModeSelected = { mode ->
                                quizMode = mode
                                runBlocking { saveQuizMode(mode) }
                            },
                            onPeriodSelected = { period ->
                                quizTimePeriod = period
                                runBlocking { saveQuizTimePeriod(period) }
                            },
                            onBackClick = { 
                                showQuiz = false 
                            },
                            onQuizFinished = { score, totalAttempts ->
                                runBlocking {
                                    saveQuizResult(
                                        QuizResult(
                                            score = score,
                                            totalAttempts = totalAttempts,
                                            timePeriod = quizTimePeriod
                                        )
                                    )
                                }
                            },
                            getEntriesForPeriod = { period -> getEntriesForTimePeriod(period) },
                            onValidateAnswer = { userAnswer, correctAnswer, fromLang, toLang ->
                                validateQuizAnswer(userAnswer, correctAnswer, fromLang, toLang)
                            }
                        )
                    }
                    else -> {
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = { Text("InTranslator: Instant Translator with AI") },
                                    actions = {
                                        IconButton(onClick = { showSettings = true }) {
                                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        ) { innerPadding ->
                            SpeechRecognitionScreen(
                                recognizedText = recognizedTextState,
                                inputText = inputTextState,
                                onInputTextChange = { inputTextState = it },
                                translatedText = translatedTextState,
                                selectedLanguage = selectedLanguageState,
                                targetLanguage = targetLanguageState,
                                availableLanguages = availableLanguages.keys.toList(),
                                isTranslating = isTranslating,
                                readAloudEnabled = readAloudEnabled,
                                onReadAloudChanged = { enabled ->
                                    readAloudEnabled = enabled
                                    runBlocking { saveReadAloudPreference(enabled) }
                                },
                                onLanguageSelected = { language ->
                                    selectedLanguageState = language
                                    runBlocking { saveSelectedLanguage(language) }
                                },
                                onTargetLanguageSelected = { language ->
                                    targetLanguageState = language
                                    runBlocking { saveTargetLanguage(language) }
                                },
                                onStartRecognition = { checkPermissionAndStartRecognition() },
                                onTranslateText = { translateText(inputTextState) },
                                onShowDictionary = { showDictionary = true },
                                onShowQuiz = { showQuiz = true },
                                onUpdateRecognizedText = { text -> updateRecognizedText(text) },
                                getAdditionalInfo = { originalText, translatedText, fromLang, toLang ->
                                    getAdditionalTranslationInfo(originalText, translatedText, fromLang, toLang)
                                },
                                onDeleteEntry = { entry ->
                                    runBlocking {
                                        deleteDictionaryEntry(entry)
                                    }
                                },
                        modifier = Modifier.padding(innerPadding)
                    )
                        }
                }
            }
        }
    }
}

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.shutdown()
    }

    private fun speakText(text: String) {
        if (readAloudEnabled) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private suspend fun saveReadAloudPreference(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[READ_ALOUD_ENABLED] = enabled.toString()
        }
    }

    private fun getEntriesForTimePeriod(period: String): List<DictionaryEntry> {
        val currentTime = System.currentTimeMillis()
        val filteredEntries = when (period) {
            "Last 24 hours" -> dictionaryEntries.filter { 
                currentTime - it.timestamp <= 24 * 60 * 60 * 1000 
            }
            "Last 7 days" -> dictionaryEntries.filter { 
                currentTime - it.timestamp <= 7 * 24 * 60 * 60 * 1000 
            }
            "Last 30 days" -> dictionaryEntries.filter { 
                currentTime - it.timestamp <= 30L * 24 * 60 * 60 * 1000 
            }
            "Last 365 days" -> dictionaryEntries.filter { 
                currentTime - it.timestamp <= 365L * 24 * 60 * 60 * 1000 
            }
            else -> dictionaryEntries
        }
        return filteredEntries
    }

    companion object {
        private val quizTimePeriods = listOf(
            "Last 24 hours",
            "Last 7 days",
            "Last 30 days",
            "Last 365 days",
            "All time"
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(
    entries: List<DictionaryEntry>,
    languagePairs: List<Pair<String, String>>,
    selectedLanguagePair: Pair<String, String>?,
    onLanguagePairSelected: (Pair<String, String>?) -> Unit,
    onBackClick: () -> Unit,
    onDeleteEntry: (DictionaryEntry) -> Unit,
    onGetAdditionalInfo: suspend (DictionaryEntry) -> String
) {
    var languagePairExpanded by remember { mutableStateOf(false) }
    var expandedEntryId by remember { mutableStateOf<Long?>(null) }
    var additionalInfo by remember { mutableStateOf<String?>(null) }
    var isLoadingInfo by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val filteredEntries = entries.filter { entry ->
        val matchesQuery = searchQuery.isEmpty() ||
            entry.originalText.contains(searchQuery, ignoreCase = true) ||
            entry.translatedText.contains(searchQuery, ignoreCase = true)
        if (selectedLanguagePair == null) matchesQuery
        else {
            val pair = if (entry.fromLanguage < entry.toLanguage)
                Pair(entry.fromLanguage, entry.toLanguage)
            else
                Pair(entry.toLanguage, entry.fromLanguage)
            matchesQuery && pair == selectedLanguagePair
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Translation History") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search Translations") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            )

            // Language Pair Selector
            if (languagePairs.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = languagePairExpanded,
                    onExpandedChange = { languagePairExpanded = it }
                ) {
                    OutlinedTextField(
                        value = if (selectedLanguagePair != null)
                            "${selectedLanguagePair.first} ↔ ${selectedLanguagePair.second}"
                        else
                            "All Language Pairs",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Language Pair") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languagePairExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = languagePairExpanded,
                        onDismissRequest = { languagePairExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Language Pairs") },
                            onClick = {
                                onLanguagePairSelected(null)
                                languagePairExpanded = false
                            }
                        )
                        languagePairs.forEach { pair ->
                            DropdownMenuItem(
                                text = { Text("${pair.first} ↔ ${pair.second}") },
                                onClick = {
                                    onLanguagePairSelected(pair)
                                    languagePairExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
    Text(
                        text = if (entries.isEmpty())
                            "No translations yet.\nStart translating to build your history!"
                        else
                            "No translations found for selected language pair",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredEntries.sortedByDescending { it.timestamp }) { entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "${entry.fromLanguage} → ${entry.toLanguage}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = entry.originalText,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = entry.translatedText,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                                .format(Date(entry.timestamp)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        TextButton(
                                            onClick = {
                                                if (expandedEntryId == entry.timestamp) {
                                                    expandedEntryId = null
                                                    additionalInfo = null
                                                } else {
                                                    expandedEntryId = entry.timestamp
                                                    isLoadingInfo = true
                                                    scope.launch {
                                                        additionalInfo = onGetAdditionalInfo(entry)
                                                        isLoadingInfo = false
                                                    }
                                                }
                                            }
                                        ) {
                                            Text(if (expandedEntryId == entry.timestamp) "Less" else "More")
                                        }
                                        IconButton(
                                            onClick = { onDeleteEntry(entry) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete translation",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }

                                if (expandedEntryId == entry.timestamp) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                        ) {
                                            if (isLoadingInfo) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .align(Alignment.Center)
                                                )
                                            } else {
                                                Text(
                                                    text = additionalInfo ?: "",
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechRecognitionScreen(
    recognizedText: String,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    translatedText: String,
    selectedLanguage: String,
    targetLanguage: String,
    availableLanguages: List<String>,
    isTranslating: Boolean,
    readAloudEnabled: Boolean,
    onReadAloudChanged: (Boolean) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onTargetLanguageSelected: (String) -> Unit,
    onStartRecognition: () -> Unit,
    onTranslateText: () -> Unit,
    onShowDictionary: () -> Unit,
    onShowQuiz: () -> Unit,
    onUpdateRecognizedText: (String) -> Unit,
    getAdditionalInfo: suspend (String, String, String, String) -> String,
    onDeleteEntry: (DictionaryEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    var sourceExpanded by remember { mutableStateOf(false) }
    var targetExpanded by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    fun handleTranslation() {
        if (inputText.isNotEmpty()) {
            keyboardController?.hide()
            focusManager.clearFocus()
            onUpdateRecognizedText(inputText)
            onTranslateText()
            onInputTextChange("")  // Clear input field
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // History Button (keep this outside of scrollable area)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            FilledTonalButton(
                onClick = { onShowQuiz() },
                modifier = Modifier.height(40.dp)
            ) {
                Icon(
                    Icons.Default.Quiz,
                    contentDescription = "Quiz",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Quiz")
            }
            FilledTonalButton(
                onClick = onShowDictionary,
                modifier = Modifier.height(40.dp)
            ) {
                Icon(
                    Icons.Default.Book,
                    contentDescription = "Dictionary",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("History")
            }
        }

        // Make the rest of the content scrollable
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Language Selection Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Source Language Selector
                        ExposedDropdownMenuBox(
                            expanded = sourceExpanded,
                            onExpandedChange = { sourceExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedLanguage,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("From") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = sourceExpanded,
                                onDismissRequest = { sourceExpanded = false }
                            ) {
                                availableLanguages.forEach { language ->
                                    DropdownMenuItem(
                                        text = { Text(language) },
                                        onClick = {
                                            onLanguageSelected(language)
                                            sourceExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Switch Languages Button
                        IconButton(
                            onClick = {
                                val tempLang = selectedLanguage
                                onLanguageSelected(targetLanguage)
                                onTargetLanguageSelected(tempLang)
                                // Clear both input and translation fields
                                onUpdateRecognizedText("")
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Switch Languages",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Target Language Selector
                        ExposedDropdownMenuBox(
                            expanded = targetExpanded,
                            onExpandedChange = { targetExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = targetLanguage,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("To") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = targetExpanded,
                                onDismissRequest = { targetExpanded = false }
                            ) {
                                availableLanguages.forEach { language ->
                                    DropdownMenuItem(
                                        text = { Text(language) },
                                        onClick = {
                                            onTargetLanguageSelected(language)
                                            targetExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Read Aloud Checkbox
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = readAloudEnabled,
                                onCheckedChange = onReadAloudChanged
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Read translations aloud",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            item {
                // Results Card
                if (translatedText.isNotEmpty() || isTranslating) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (isTranslating) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }

                            if (translatedText.isNotEmpty()) {
                                var showAdditionalInfo by remember { mutableStateOf(false) }
                                var additionalInfo by remember { mutableStateOf<String?>(null) }
                                var isLoadingInfo by remember { mutableStateOf(false) }
                                val scope = rememberCoroutineScope()

                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Translation",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = translatedText,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    if (showAdditionalInfo) {
                                                        showAdditionalInfo = false
                                                        additionalInfo = null
                                                    } else {
                                                        showAdditionalInfo = true
                                                        isLoadingInfo = true
                                                        scope.launch {
                                                            additionalInfo = getAdditionalInfo(
                                                                recognizedText,
                                                                translatedText,
                                                                selectedLanguage,
                                                                targetLanguage
                                                            )
                                                            isLoadingInfo = false
                                                        }
                                                    }
                                                }
                                            ) {
                                                Text(if (showAdditionalInfo) "Less" else "More")
                                            }
                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        // Create an entry that matches the one in history
                                                        val entry = DictionaryEntry(
                                                            originalText = recognizedText,
                                                            translatedText = translatedText,
                                                            fromLanguage = selectedLanguage,
                                                            toLanguage = targetLanguage
                                                        )
                                                        onDeleteEntry(entry)
                                                        // Clear both the input and translation
                                                        onUpdateRecognizedText("")
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete translation",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }

                                    if (showAdditionalInfo) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp)
                                            ) {
                                                if (isLoadingInfo) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .align(Alignment.Center)
                                                    )
                                                } else {
                                                    Text(
                                                        text = additionalInfo ?: "",
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                // Input Methods Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Voice Input Button
                        Button(
                            onClick = onStartRecognition,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Microphone",
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Voice Input",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }

                        // Show recognized text right after voice input if available
                        if (recognizedText.isNotEmpty()) {
                            Column {
                                Text(
                                    text = "Input text",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = recognizedText,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }

                        // Text Input with Translate Button
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = onInputTextChange,
                            label = { Text("Or type text here") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Send
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = { handleTranslation() }
                            )
                        )

                        // Translate Button
                        Button(
                            onClick = { handleTranslation() },
                            enabled = inputText.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Translate Text")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    apiKey: String,
    modelType: String,
    availableModels: List<String>,
    tokenUsage: Int,
    onApiKeyChanged: (String) -> Unit,
    onModelTypeChanged: (String) -> Unit,
    onBackClick: () -> Unit
) {
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // API Key Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "API Key",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showApiKeyDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (apiKey.isEmpty()) "Set OpenAI API Key" else "Change API Key")
                    }
                }
            }

            // Model Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Model Selection",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = modelType,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            availableModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        onModelTypeChanged(model)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Usage Statistics
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Usage Statistics",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Total tokens used: $tokenUsage",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Estimated cost: $${String.format("%.3f", tokenUsage * 0.000002)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showApiKeyDialog) {
        var apiKeyInput by remember { mutableStateOf(apiKey) }
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Enter OpenAI API Key") },
            text = {
                Column {
                    Text(
                        "Your API key will be securely stored on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onApiKeyChanged(apiKeyInput)
                        showApiKeyDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showApiKeyDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    timePeriods: List<String>,
    selectedPeriod: String,
    quizHistory: List<QuizResult>,
    quizMode: String,
    languagePairs: List<Pair<String, String>>,
    selectedLanguagePair: Pair<String, String>?,
    onLanguagePairSelected: (Pair<String, String>?) -> Unit,
    onQuizModeSelected: (String) -> Unit,
    onPeriodSelected: (String) -> Unit,
    onBackClick: () -> Unit,
    onQuizFinished: (score: Int, totalAttempts: Int) -> Unit,
    getEntriesForPeriod: (String) -> List<DictionaryEntry>,
    onValidateAnswer: suspend (String, String, String, String) -> Boolean
) {
    var currentEntry by remember { mutableStateOf<DictionaryEntry?>(null) }
    var userAnswer by remember { mutableStateOf("") }
    var score by remember { mutableStateOf(0) }
    var totalAttempts by remember { mutableStateOf(0) }
    var periodExpanded by remember { mutableStateOf(false) }
    var modeExpanded by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }
    var questionText by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var languagePairExpanded by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val quizModes = listOf("Input Language", "Translated Language", "Mixed")

    // Handle back button to save quiz result
    BackHandler {
        if (totalAttempts > 0) {
            onQuizFinished(score, totalAttempts)
        }
        onBackClick()
    }

    fun loadNewQuestion() {
        val entries = getEntriesForPeriod(selectedPeriod).filter { entry ->
            if (selectedLanguagePair == null) true
            else {
                val pair = if (entry.fromLanguage < entry.toLanguage)
                    Pair(entry.fromLanguage, entry.toLanguage)
                else
                    Pair(entry.toLanguage, entry.fromLanguage)
                pair == selectedLanguagePair
            }
        }
        currentEntry = if (entries.isNotEmpty()) {
            entries.random()
        } else {
            null
        }
        
        // Set the question text based on mode
        questionText = if (currentEntry != null) {
            when (quizMode) {
                "Input Language" -> currentEntry!!.translatedText  // Show translated, expect original
                "Translated Language" -> currentEntry!!.originalText  // Show original, expect translated
                else -> if (Random().nextBoolean()) currentEntry!!.originalText else currentEntry!!.translatedText
            }
        } else ""
        
        userAnswer = ""
        showResult = false
    }

    fun checkAnswer() {
        if (currentEntry != null && !isChecking) {
            isChecking = true
            val (correctAnswer, fromLang, toLang) = when (quizMode) {
                "Input Language" -> Triple(
                    currentEntry!!.originalText,
                    currentEntry!!.toLanguage,
                    currentEntry!!.fromLanguage
                )
                "Translated Language" -> Triple(
                    currentEntry!!.translatedText,
                    currentEntry!!.fromLanguage,
                    currentEntry!!.toLanguage
                )
                else -> {
                    val isShowingOriginal = currentEntry!!.originalText == questionText
                    if (isShowingOriginal) {
                        Triple(
                            currentEntry!!.translatedText,
                            currentEntry!!.fromLanguage,
                            currentEntry!!.toLanguage
                        )
                    } else {
                        Triple(
                            currentEntry!!.originalText,
                            currentEntry!!.toLanguage,
                            currentEntry!!.fromLanguage
                        )
                    }
                }
            }

            scope.launch {
                try {
                    isCorrect = onValidateAnswer(userAnswer, correctAnswer, fromLang, toLang)
                    if (isCorrect) score++
                    totalAttempts++
                    showResult = true
                    keyboardController?.hide()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Error checking answer: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    isChecking = false
                }
            }
        }
    }

    LaunchedEffect(selectedPeriod, quizMode, selectedLanguagePair) {
        loadNewQuestion()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Translation Quiz") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (totalAttempts > 0) {
                                onQuizFinished(score, totalAttempts)
                            }
                            onBackClick()
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Score display
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Score: $score / $totalAttempts",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            item {
                // Settings Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Language Pair Selector
                        if (languagePairs.isNotEmpty()) {
                            ExposedDropdownMenuBox(
                                expanded = languagePairExpanded,
                                onExpandedChange = { languagePairExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = if (selectedLanguagePair != null)
                                        "${selectedLanguagePair.first} ↔ ${selectedLanguagePair.second}"
                                    else
                                        "All Language Pairs",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Language Pair") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languagePairExpanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )

                                ExposedDropdownMenu(
                                    expanded = languagePairExpanded,
                                    onDismissRequest = { languagePairExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("All Language Pairs") },
                                        onClick = {
                                            onLanguagePairSelected(null)
                                            languagePairExpanded = false
                                        }
                                    )
                                    languagePairs.forEach { pair ->
                                        DropdownMenuItem(
                                            text = { Text("${pair.first} ↔ ${pair.second}") },
                                            onClick = {
                                                onLanguagePairSelected(pair)
                                                languagePairExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Quiz Mode Selector
                        ExposedDropdownMenuBox(
                            expanded = modeExpanded,
                            onExpandedChange = { modeExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = quizMode,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Quiz Mode") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = modeExpanded,
                                onDismissRequest = { modeExpanded = false }
                            ) {
                                quizModes.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode) },
                                        onClick = {
                                            onQuizModeSelected(mode)
                                            modeExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Time period selector
                        ExposedDropdownMenuBox(
                            expanded = periodExpanded,
                            onExpandedChange = { periodExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedPeriod,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Time Period") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = periodExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = periodExpanded,
                                onDismissRequest = { periodExpanded = false }
                            ) {
                                timePeriods.forEach { period ->
                                    DropdownMenuItem(
                                        text = { Text(period) },
                                        onClick = {
                                            onPeriodSelected(period)
                                            periodExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                if (currentEntry == null) {
                    Text(
                        text = "No translations available for the selected period",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Question card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        ) {
                            Column {
                                val (fromLang, toLang) = when (quizMode) {
                                    "Input Language" -> Pair(currentEntry!!.toLanguage, currentEntry!!.fromLanguage)
                                    "Translated Language" -> Pair(currentEntry!!.fromLanguage, currentEntry!!.toLanguage)
                                    else -> if (currentEntry!!.originalText == questionText) 
                                        Pair(currentEntry!!.fromLanguage, currentEntry!!.toLanguage)
                                    else 
                                        Pair(currentEntry!!.toLanguage, currentEntry!!.fromLanguage)
                                }

                                Text(
                                    text = "Translate from $fromLang to $toLang:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = questionText,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }

                    // Answer input
                    OutlinedTextField(
                        value = userAnswer,
                        onValueChange = { userAnswer = it },
                        label = { Text("Your translation") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !showResult,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = { if (userAnswer.isNotEmpty() && !isChecking) checkAnswer() }
                        )
                    )

                    if (showResult) {
                        // Result display
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCorrect) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isCorrect) "Correct!" else "Incorrect",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isCorrect) 
                                        MaterialTheme.colorScheme.onPrimaryContainer 
                                    else 
                                        MaterialTheme.colorScheme.onErrorContainer
                                )
                                if (!isCorrect) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Expected answer: ${
                                            when (quizMode) {
                                                "Input Language" -> currentEntry!!.originalText
                                                "Translated Language" -> currentEntry!!.translatedText
                                                else -> {
                                                    val isShowingOriginal = currentEntry!!.originalText == questionText
                                                    if (isShowingOriginal) currentEntry!!.translatedText else currentEntry!!.originalText
                                                }
                                            }
                                        }",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        // Next question button
                        Button(
                            onClick = { loadNewQuestion() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Next Question")
                        }
                    } else {
                        // Check answer button
                        Button(
                            onClick = { checkAnswer() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = userAnswer.isNotEmpty() && !isChecking
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Check Answer")
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))

                // Quiz history section at the bottom
                if (quizHistory.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = "Previous Results",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 200.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(quizHistory.sortedByDescending { it.timestamp }.take(5)) { result ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${result.score}/${result.totalAttempts} (${result.timePeriod})",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                                .format(Date(result.timestamp)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}