package com.example.intranslator.data

import android.app.Activity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.intranslator.models.DictionaryEntry
import com.example.intranslator.models.QuizResult
import com.example.intranslator.utils.Constants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Activity.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object PreferencesKeys {
    val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
    val TARGET_LANGUAGE = stringPreferencesKey("target_language")
    val API_KEY = stringPreferencesKey("api_key")
    val DICTIONARY_ENTRIES = stringPreferencesKey("dictionary_entries")
    val MODEL_TYPE = stringPreferencesKey("model_type")
    val TOKEN_USAGE = stringPreferencesKey("token_usage")
    val READ_ALOUD_ENABLED = stringPreferencesKey("read_aloud_enabled")
    val QUIZ_TIME_PERIOD = stringPreferencesKey("quiz_time_period")
    val QUIZ_HISTORY = stringPreferencesKey("quiz_history")
    val QUIZ_MODE = stringPreferencesKey("quiz_mode")
    val QUIZ_LANGUAGE_PAIR = stringPreferencesKey("quiz_language_pair")
    val HISTORY_LANGUAGE_PAIR = stringPreferencesKey("history_language_pair")
    val ENABLED_LANGUAGES = stringPreferencesKey("enabled_languages")
}

data class PreferencesData(
    val selectedLanguage: String = Constants.AVAILABLE_LANGUAGES.keys.first(),
    val targetLanguage: String = Constants.AVAILABLE_LANGUAGES.keys.drop(1).first(),
    val apiKey: String = "",
    val modelType: String = "gpt-3.5-turbo",
    val tokenUsage: Int = 0,
    val readAloudEnabled: Boolean = false,
    val quizTimePeriod: String = "All time",
    val quizMode: String = "Mixed",
    val enabledLanguages: Set<String> = Constants.AVAILABLE_LANGUAGES.keys.toSet()
)

class PreferencesManager(private val dataStore: DataStore<Preferences>) {
    suspend fun saveSelectedLanguage(language: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_LANGUAGE] = language
        }
    }

    suspend fun saveTargetLanguage(language: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TARGET_LANGUAGE] = language
        }
    }

    suspend fun saveApiKey(key: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.API_KEY] = key
        }
    }

    suspend fun saveModelType(model: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MODEL_TYPE] = model
        }
    }

    suspend fun saveTokenUsage(usage: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TOKEN_USAGE] = usage.toString()
        }
    }

    suspend fun saveQuizTimePeriod(period: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.QUIZ_TIME_PERIOD] = period
        }
    }

    suspend fun saveQuizResult(quizHistory: List<QuizResult>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.QUIZ_HISTORY] = Json.encodeToString(quizHistory)
        }
    }

    suspend fun saveQuizMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.QUIZ_MODE] = mode
        }
    }

    suspend fun saveQuizLanguagePair(pair: Pair<String, String>?) {
        dataStore.edit { preferences ->
            if (pair != null) {
                preferences[PreferencesKeys.QUIZ_LANGUAGE_PAIR] = "${pair.first}|${pair.second}"
            } else {
                preferences[PreferencesKeys.QUIZ_LANGUAGE_PAIR] = ""
            }
        }
    }

    suspend fun saveHistoryLanguagePair(pair: Pair<String, String>?) {
        dataStore.edit { preferences ->
            if (pair != null) {
                preferences[PreferencesKeys.HISTORY_LANGUAGE_PAIR] = "${pair.first}|${pair.second}"
            } else {
                preferences[PreferencesKeys.HISTORY_LANGUAGE_PAIR] = ""
            }
        }
    }

    suspend fun saveDictionaryEntries(entries: List<DictionaryEntry>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DICTIONARY_ENTRIES] = Json.encodeToString(entries)
        }
    }

    suspend fun saveReadAloudPreference(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.READ_ALOUD_ENABLED] = enabled.toString()
        }
    }

    suspend fun saveReadAloudEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.READ_ALOUD_ENABLED] = enabled.toString()
        }
    }

    suspend fun saveQuizHistory(history: List<QuizResult>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.QUIZ_HISTORY] = Json.encodeToString(history)
        }
    }

    suspend fun saveEnabledLanguages(languages: Set<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ENABLED_LANGUAGES] = Json.encodeToString(languages.toList())
        }
    }

    suspend fun loadPreferences(): PreferencesData {
        val defaultPrefs = PreferencesData()
        return PreferencesData(
            selectedLanguage = dataStore.data
                .map { it[PreferencesKeys.SELECTED_LANGUAGE] ?: defaultPrefs.selectedLanguage }
                .first(),
            targetLanguage = dataStore.data
                .map { it[PreferencesKeys.TARGET_LANGUAGE] ?: defaultPrefs.targetLanguage }
                .first(),
            apiKey = dataStore.data
                .map { it[PreferencesKeys.API_KEY] ?: defaultPrefs.apiKey }
                .first(),
            modelType = dataStore.data
                .map { it[PreferencesKeys.MODEL_TYPE] ?: defaultPrefs.modelType }
                .first(),
            tokenUsage = dataStore.data
                .map { it[PreferencesKeys.TOKEN_USAGE]?.toIntOrNull() ?: defaultPrefs.tokenUsage }
                .first(),
            readAloudEnabled = dataStore.data
                .map { it[PreferencesKeys.READ_ALOUD_ENABLED]?.toBoolean() ?: defaultPrefs.readAloudEnabled }
                .first(),
            quizTimePeriod = dataStore.data
                .map { it[PreferencesKeys.QUIZ_TIME_PERIOD] ?: defaultPrefs.quizTimePeriod }
                .first(),
            quizMode = dataStore.data
                .map { it[PreferencesKeys.QUIZ_MODE] ?: defaultPrefs.quizMode }
                .first(),
            enabledLanguages = dataStore.data
                .map { 
                    val json = it[PreferencesKeys.ENABLED_LANGUAGES]
                    if (json == null) {
                        defaultPrefs.enabledLanguages
                    } else {
                        try {
                            Json.decodeFromString<List<String>>(json).toSet()
                        } catch (e: Exception) {
                            defaultPrefs.enabledLanguages
                        }
                    }
                }
                .first()
        )
    }

    suspend fun loadQuizLanguagePair(): Pair<String, String>? {
        val pairStr = dataStore.data
            .map { it[PreferencesKeys.QUIZ_LANGUAGE_PAIR] ?: "" }
            .first()
        return if (pairStr.isNotEmpty()) {
            val parts = pairStr.split("|")
            if (parts.size == 2) Pair(parts[0], parts[1]) else null
        } else null
    }

    suspend fun loadHistoryLanguagePair(): Pair<String, String>? {
        val pairStr = dataStore.data
            .map { it[PreferencesKeys.HISTORY_LANGUAGE_PAIR] ?: "" }
            .first()
        return if (pairStr.isNotEmpty()) {
            val parts = pairStr.split("|")
            if (parts.size == 2) Pair(parts[0], parts[1]) else null
        } else null
    }

    suspend fun loadDictionaryEntries(): List<DictionaryEntry> {
        val entriesJson = dataStore.data
            .map { it[PreferencesKeys.DICTIONARY_ENTRIES] ?: "[]" }
            .first()
        return try {
            Json.decodeFromString(entriesJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun loadQuizHistory(): List<QuizResult> {
        val historyJson = dataStore.data
            .map { it[PreferencesKeys.QUIZ_HISTORY] ?: "[]" }
            .first()
        return try {
            Json.decodeFromString(historyJson)
        } catch (e: Exception) {
            emptyList()
        }
    }
} 