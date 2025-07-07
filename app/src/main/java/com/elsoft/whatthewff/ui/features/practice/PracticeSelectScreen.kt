// File: ui/features/practice/PracticeSelectScreen.kt
// This file defines the screen where users can select a chapter of practice problems.

package com.elsoft.whatthewff.ui.features.practice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.elsoft.whatthewff.logic.Problem
import com.elsoft.whatthewff.logic.ProblemSets

/**
 * A screen that lists available chapters from the ProblemSets.
 *
 * @param onProblemSelected A callback executed when a user selects a problem.
 * @param onBackClicked A callback to navigate back to the main screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeSelectScreen(
    onProblemSelected: (Problem) -> Unit,
    onBackClicked: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select a Chapter") },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Chapter 1
            item {
                ChapterCard(
                    title = "Chapter 1: Modus Ponens",
                    problems = ProblemSets.chapter1_ModusPonens,
                    onProblemSelected = onProblemSelected
                )
            }
            // Chapter 2
            item {
                ChapterCard(
                    title = "Chapter 2: Modus Tollens",
                    problems = ProblemSets.chapter2_ModusTollens,
                    onProblemSelected = onProblemSelected
                )
            }
        }
    }
}

/**
 * A card that displays a chapter title and a list of its problems.
 */
@Composable
fun ChapterCard(
    title: String,
    problems: List<Problem>,
    onProblemSelected: (Problem) -> Unit
) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            problems.forEach { problem ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProblemSelected(problem) }
                        .padding(vertical = 8.dp)
                ) {
                    Text(text = problem.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
