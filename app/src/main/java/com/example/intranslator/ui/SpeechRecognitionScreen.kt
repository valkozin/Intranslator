package com.example.intranslator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SpeechRecognitionScreen(
    recognizedText: String,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onTranslateText: () -> Unit,
    onStartRecognition: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // UI components for speech recognition
    }
} 