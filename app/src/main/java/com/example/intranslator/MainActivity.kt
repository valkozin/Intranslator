package com.example.intranslator

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.intranslator.data.PreferencesManager
import com.example.intranslator.data.dataStore
import com.example.intranslator.models.DictionaryEntry
import com.example.intranslator.models.QuizResult
import com.example.intranslator.translation.TranslationManager
import com.example.intranslator.ui.DictionaryScreen
import com.example.intranslator.ui.QuizScreen
import com.example.intranslator.ui.SettingsScreen
import com.example.intranslator.ui.SpeechRecognitionScreen
import com.example.intranslator.ui.theme.IntranslatorTheme
import com.example.intranslator.utils.Constants
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*

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
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var translationManager: TranslationManager

    private var recognizedTextState by mutableStateOf("")
    private var inputTextState by mutableStateOf("")
    private var translatedTextState by mutableStateOf("")
    private var selectedLanguageState by mutableStateOf("English (US)")
    private var targetLanguageState by mutableStateOf("")
    private var apiKeyState by mutableStateOf("")
    private var isTranslating by mutableStateOf(false)

    private var enabledLanguages by mutableStateOf<Set<String>>(setOf("English (US)"))

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

    private fun updateRecognizedText(text: String) {
        recognizedTextState = text
        translatedTextState = ""  // Clear previous translation
    }

    private fun startSpeechRecognition() {
        val languageCode = Constants.AVAILABLE_LANGUAGES[selectedLanguageState] ?: "en-US"
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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSpeechRecognition()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
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

        lifecycleScope.launch {
            try {
                translatedTextState = translationManager.translateText(
                    text = text,
                    targetLanguage = targetLanguageState,
                    onTokenUsageUpdate = { usage ->
                        tokenUsage += usage
                        lifecycleScope.launch {
                            preferencesManager.saveTokenUsage(tokenUsage)
                        }
                    }
                )
                isTranslating = false

                // Speak the translated text
                speakText(translatedTextState)

                // Save to dictionary
                saveDictionaryEntry(
                    DictionaryEntry(
                        originalText = text,
                        translatedText = translatedTextState,
                        fromLanguage = selectedLanguageState,
                        toLanguage = targetLanguageState
                    )
                )
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Translation error: ${e.message}", Toast.LENGTH_LONG).show()
                isTranslating = false
            }
        }
    }

    private fun speakText(text: String) {
        if (readAloudEnabled) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private suspend fun saveDictionaryEntry(entry: DictionaryEntry) {
        val updatedEntries = dictionaryEntries.filter { existingEntry ->
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
        preferencesManager.saveDictionaryEntries(dictionaryEntries)
    }

    private suspend fun deleteDictionaryEntry(entry: DictionaryEntry) {
        val updatedEntries = dictionaryEntries.filter { existingEntry ->
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
        preferencesManager.saveDictionaryEntries(dictionaryEntries)
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

    private fun getEntriesForTimePeriod(period: String): List<DictionaryEntry> {
        val currentTime = System.currentTimeMillis()
        return when (period) {
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferencesManager = PreferencesManager(dataStore)

        // Add back button handling
        onBackPressedDispatcher.addCallback(this) {
            when {
                showSettings -> showSettings = false
                showDictionary -> showDictionary = false
                showQuiz -> showQuiz = false
                else -> finish()
            }
        }

        lifecycleScope.launch {
            // Load preferences
            val prefs = preferencesManager.loadPreferences()
            selectedLanguageState = prefs.selectedLanguage
            targetLanguageState = prefs.targetLanguage
            apiKeyState = prefs.apiKey
            modelType = prefs.modelType
            tokenUsage = prefs.tokenUsage
            readAloudEnabled = prefs.readAloudEnabled
            quizTimePeriod = prefs.quizTimePeriod
            quizMode = prefs.quizMode
            enabledLanguages = prefs.enabledLanguages
            
            // Load dictionary entries and quiz history
            dictionaryEntries = preferencesManager.loadDictionaryEntries()
            quizHistory = preferencesManager.loadQuizHistory()
            quizLanguagePair = preferencesManager.loadQuizLanguagePair()
            historyLanguagePair = preferencesManager.loadHistoryLanguagePair()
            
            // Initialize TranslationManager after loading preferences
            translationManager = TranslationManager(apiKeyState, modelType)
        }

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
                            availableModels = Constants.AVAILABLE_MODELS,
                            tokenUsage = tokenUsage,
                            enabledLanguages = enabledLanguages,
                            allLanguages = Constants.AVAILABLE_LANGUAGES.keys.toList(),
                            onApiKeyChanged = { key ->
                                apiKeyState = key
                                lifecycleScope.launch { preferencesManager.saveApiKey(key) }
                                translationManager = TranslationManager(apiKeyState, modelType)
                            },
                            onModelTypeChanged = { model ->
                                modelType = model
                                lifecycleScope.launch { preferencesManager.saveModelType(model) }
                                translationManager = TranslationManager(apiKeyState, modelType)
                            },
                            onEnabledLanguagesChanged = { languages ->
                                enabledLanguages = languages
                                lifecycleScope.launch { preferencesManager.saveEnabledLanguages(languages) }
                            },
                            onBackClick = { showSettings = false },
                            onExportDictionary = { exportDictionary() },
                            onImportDictionary = { importDictionary() }
                        )
                    }
                    showDictionary -> {
                        DictionaryScreen(
                            entries = dictionaryEntries,
                            languagePairs = getAvailableLanguagePairs(),
                            selectedLanguagePair = historyLanguagePair,
                            onLanguagePairSelected = { pair ->
                                historyLanguagePair = pair
                                lifecycleScope.launch { preferencesManager.saveHistoryLanguagePair(pair) }
                            },
                            onBackClick = { showDictionary = false },
                            onDeleteEntry = { entry ->
                                lifecycleScope.launch {
                                    deleteDictionaryEntry(entry)
                                }
                            },
                            onGetAdditionalInfo = { entry ->
                                translationManager.getAdditionalTranslationInfo(
                                    originalText = entry.originalText,
                                    translatedText = entry.translatedText,
                                    fromLanguage = entry.fromLanguage,
                                    toLanguage = entry.toLanguage,
                                    onTokenUsageUpdate = { usage ->
                                        tokenUsage += usage
                                        lifecycleScope.launch {
                                            preferencesManager.saveTokenUsage(tokenUsage)
                                        }
                                    }
                                )
                            }
                        )
                    }
                    showQuiz -> {
                        QuizScreen(
                            timePeriods = Constants.QUIZ_TIME_PERIODS,
                            selectedPeriod = quizTimePeriod,
                            quizHistory = quizHistory,
                            quizMode = quizMode,
                            languagePairs = getAvailableLanguagePairs(),
                            selectedLanguagePair = quizLanguagePair,
                            onLanguagePairSelected = { pair ->
                                quizLanguagePair = pair
                                lifecycleScope.launch { preferencesManager.saveQuizLanguagePair(pair) }
                            },
                            onQuizModeSelected = { mode ->
                                quizMode = mode
                                lifecycleScope.launch { preferencesManager.saveQuizMode(mode) }
                            },
                            onPeriodSelected = { period ->
                                quizTimePeriod = period
                                lifecycleScope.launch { preferencesManager.saveQuizTimePeriod(period) }
                            },
                            onBackClick = { 
                                showQuiz = false 
                            },
                            onQuizFinished = { score, totalAttempts ->
                                lifecycleScope.launch {
                                    val result = QuizResult(
                                        score = score,
                                        totalAttempts = totalAttempts,
                                        timePeriod = quizTimePeriod,
                                        languagePair = quizLanguagePair
                                    )
                                    quizHistory = quizHistory + listOf(result)
                                    preferencesManager.saveQuizHistory(quizHistory)
                                }
                            },
                            getEntriesForPeriod = { period -> getEntriesForTimePeriod(period) },
                            onValidateAnswer = { userAnswer, correctAnswer, fromLang, toLang ->
                                translationManager.validateQuizAnswer(
                                    userAnswer = userAnswer,
                                    correctAnswer = correctAnswer,
                                    fromLanguage = fromLang,
                                    toLanguage = toLang,
                                    onTokenUsageUpdate = { usage, cost ->
                                        tokenUsage += usage
                                        lifecycleScope.launch {
                                            preferencesManager.saveTokenUsage(tokenUsage)
                                        }
                                    }
                                )
                            }
                        )
                    }
                    else -> {
                        SpeechRecognitionScreen(
                            recognizedText = recognizedTextState,
                            inputText = inputTextState,
                            onInputTextChange = { inputTextState = it },
                            translatedText = translatedTextState,
                            selectedLanguage = selectedLanguageState,
                            targetLanguage = targetLanguageState,
                            availableLanguages = enabledLanguages.toList(),
                            isTranslating = isTranslating,
                            readAloudEnabled = readAloudEnabled,
                            onReadAloudChanged = { enabled ->
                                readAloudEnabled = enabled
                                lifecycleScope.launch { preferencesManager.saveReadAloudEnabled(enabled) }
                            },
                            onLanguageSelected = { language ->
                                selectedLanguageState = language
                                lifecycleScope.launch { preferencesManager.saveSelectedLanguage(language) }
                            },
                            onTargetLanguageSelected = { language ->
                                targetLanguageState = language
                                lifecycleScope.launch { preferencesManager.saveTargetLanguage(language) }
                            },
                            onStartRecognition = { checkPermissionAndStartRecognition() },
                            onTranslateText = { translateText(inputTextState) },
                            onShowDictionary = { showDictionary = true },
                            onShowQuiz = { showQuiz = true },
                            onShowSettings = { showSettings = true },
                            onUpdateRecognizedText = { text -> updateRecognizedText(text) },
                            getAdditionalInfo = { originalText, translatedText, fromLang, toLang ->
                                translationManager.getAdditionalTranslationInfo(
                                    originalText = originalText,
                                    translatedText = translatedText,
                                    fromLanguage = fromLang,
                                    toLanguage = toLang,
                                    onTokenUsageUpdate = { usage ->
                                        tokenUsage += usage
                                        lifecycleScope.launch {
                                            preferencesManager.saveTokenUsage(tokenUsage)
                                        }
                                    }
                                )
                            },
                            onDeleteEntry = { entry ->
                                lifecycleScope.launch {
                                    deleteDictionaryEntry(entry)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.shutdown()
    }

    private fun exportDictionary() {
        val json = Json.encodeToString(dictionaryEntries)
        val fileName = "dictionary_export.json"
        val file = File(getExternalFilesDir(null), fileName)

        lifecycleScope.launch {
            try {
                file.writeText(json)
                Toast.makeText(this@MainActivity, "Dictionary exported to ${file.absolutePath}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun importDictionary() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/json"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, REQUEST_CODE_IMPORT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_IMPORT && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                lifecycleScope.launch {
                    try {
                        val inputStream = contentResolver.openInputStream(uri)
                        val json = inputStream?.bufferedReader().use { it?.readText() }
                        val importedEntries: List<DictionaryEntry> = Json.decodeFromString(json ?: "[]")
                        dictionaryEntries = importedEntries
                        preferencesManager.saveDictionaryEntries(dictionaryEntries)
                        Toast.makeText(this@MainActivity, "Dictionary imported successfully", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private companion object {
        const val REQUEST_CODE_IMPORT = 1001
    }
}