package com.example.intranslator.data

import android.app.Activity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.example.intranslator.models.QuizResult

class PreferencesManager(private val dataStore: DataStore<Preferences>) {
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

    suspend fun saveSelectedLanguage(language: String) {
        dataStore.edit { preferences -> preferences[SELECTED_LANGUAGE] = language }
    }

    suspend fun saveTargetLanguage(language: String) {
        dataStore.edit { preferences -> preferences[TARGET_LANGUAGE] = language }
    }

    suspend fun saveApiKey(key: String) {
        dataStore.edit { preferences -> preferences[API_KEY] = key }
    }

    suspend fun saveModelType(model: String) {
        dataStore.edit { preferences -> preferences[MODEL_TYPE] = model }
    }

    suspend fun saveTokenUsage(usage: Int) {
        dataStore.edit { preferences -> preferences[TOKEN_USAGE] = usage.toString() }
    }

    suspend fun saveQuizTimePeriod(period: String) {
        dataStore.edit { preferences -> preferences[QUIZ_TIME_PERIOD] = period }
    }

    suspend fun saveQuizResult(result: QuizResult) {
        // Implement saving quiz result logic
    }

    suspend fun loadPreferences(): Map<String, String> {
        return dataStore.data.map { preferences ->
            mapOf(
                "selected_language" to (preferences[SELECTED_LANGUAGE] ?: "English (US)"),
                "target_language" to (preferences[TARGET_LANGUAGE] ?: ""),
                "api_key" to (preferences[API_KEY] ?: ""),
                "model_type" to (preferences[MODEL_TYPE] ?: "gpt-3.5-turbo"),
                "token_usage" to (preferences[TOKEN_USAGE]?.toIntOrNull()?.toString() ?: "0"),
                "read_aloud_enabled" to (preferences[READ_ALOUD_ENABLED]?.toBoolean().toString() ?: "false"),
                "quiz_time_period" to (preferences[QUIZ_TIME_PERIOD] ?: "All time"),
                "quiz_mode" to (preferences[QUIZ_MODE] ?: "Mixed"),
                "quiz_language_pair" to (preferences[QUIZ_LANGUAGE_PAIR] ?: ""),
                "history_language_pair" to (preferences[HISTORY_LANGUAGE_PAIR] ?: "")
            )
        }.first()
    }
} 