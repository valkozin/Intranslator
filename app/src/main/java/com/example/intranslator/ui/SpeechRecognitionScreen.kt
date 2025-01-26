package com.example.intranslator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.intranslator.models.DictionaryEntry
import kotlinx.coroutines.launch

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
    onShowSettings: () -> Unit,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI translator with voice input, quiz and history") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { onShowSettings() }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            )
        }
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
                .padding(padding)
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
                                    availableLanguages.filter { it != targetLanguage }.forEach { language ->
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
                                    availableLanguages.filter { it != selectedLanguage }.forEach { language ->
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
} 