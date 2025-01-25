package com.example.intranslator.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.intranslator.models.DictionaryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DictionaryViewModel : ViewModel() {
    private val _entries = MutableStateFlow<List<DictionaryEntry>>(emptyList())
    val entries: StateFlow<List<DictionaryEntry>> = _entries

    fun loadEntries() {
        viewModelScope.launch {
            // Logic to load dictionary entries
        }

    }

    fun deleteEntry(entry: DictionaryEntry) {
        viewModelScope.launch {
            // Logic to delete an entry
        }
    }
} 