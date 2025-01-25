package com.example.intranslator.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.example.intranslator.models.QuizResult
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.*

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
    var usedQuestions by remember { mutableStateOf(mutableSetOf<String>()) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
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

        // Reset used questions if we've used all available questions
        if (usedQuestions.size >= entries.size) {
            usedQuestions.clear()
        }

        // Filter out recently used questions
        val availableEntries = entries.filter { entry ->
            val key = "${entry.originalText}|${entry.translatedText}|${entry.fromLanguage}|${entry.toLanguage}"
            !usedQuestions.contains(key)
        }

        currentEntry = if (availableEntries.isNotEmpty()) {
            val selected = availableEntries.random()
            // Add to used questions
            usedQuestions.add("${selected.originalText}|${selected.translatedText}|${selected.fromLanguage}|${selected.toLanguage}")
            selected
        } else if (entries.isNotEmpty()) {
            // If no unused questions available but we have entries, reset and try again
            usedQuestions.clear()
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

    // Reset used questions when period or language pair changes
    LaunchedEffect(selectedPeriod, selectedLanguagePair) {
        usedQuestions.clear()
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
                    // Handle error
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
                                        Column {
                                            Text(
                                                text = "${result.score}/${result.totalAttempts} (${result.timePeriod})",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            if (result.languagePair != null) {
                                                Text(
                                                    text = "${result.languagePair.first} ↔ ${result.languagePair.second}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
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