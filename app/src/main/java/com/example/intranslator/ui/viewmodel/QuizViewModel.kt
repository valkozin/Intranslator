package com.example.intranslator.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.intranslator.models.DictionaryEntry
import com.example.intranslator.models.QuizResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class QuizViewModel : ViewModel() {
    private val _quizMode = MutableStateFlow("Input Language")
    val quizMode: StateFlow<String> = _quizMode

    private val _selectedLanguagePair = MutableStateFlow<Pair<String, String>?>(null)
    val selectedLanguagePair: StateFlow<Pair<String, String>?> = _selectedLanguagePair

    private val _timePeriods = MutableStateFlow<List<String>>(emptyList())
    val timePeriods: StateFlow<List<String>> = _timePeriods

    private val _currentEntry = MutableStateFlow<DictionaryEntry?>(null)
    val currentEntry: StateFlow<DictionaryEntry?> = _currentEntry

    fun loadNewQuestion() {
        viewModelScope.launch {
            // Logic to load a new question
        }
    }

    fun checkAnswer(userAnswer: String) {
        viewModelScope.launch {
            // Logic to check the answer
        }
    }
} 