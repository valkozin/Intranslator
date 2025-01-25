package com.example.intranslator.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.intranslator.data.PreferencesManager
import com.example.intranslator.models.DictionaryEntry
import com.example.intranslator.models.QuizResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val preferencesManager: PreferencesManager) : ViewModel() {
    private val _dictionaryEntries = MutableStateFlow<List<DictionaryEntry>>(emptyList())
    val dictionaryEntries: StateFlow<List<DictionaryEntry>> = _dictionaryEntries

    private val _quizHistory = MutableStateFlow<List<QuizResult>>(emptyList())
    val quizHistory: StateFlow<List<QuizResult>> = _quizHistory

    // Other state variables can be added here

    fun loadPreferences() {
        viewModelScope.launch {
            // Load preferences and update state
        }
    }

    fun updateDictionaryEntries(entries: List<DictionaryEntry>) {
        _dictionaryEntries.value = entries
    }

    fun updateQuizHistory(history: List<QuizResult>) {
        _quizHistory.value = history
    }
} 