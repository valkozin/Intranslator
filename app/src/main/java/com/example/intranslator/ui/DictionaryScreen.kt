package com.example.intranslator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.intranslator.models.DictionaryEntry
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.*

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