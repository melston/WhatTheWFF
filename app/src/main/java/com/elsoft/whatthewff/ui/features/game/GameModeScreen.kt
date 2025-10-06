// File: ui/features/game/GameModeScreen.kt
// This file defines the screen where users select a difficulty for generated problems.

package com.elsoft.whatthewff.ui.features.game

import android.os.Build
import androidx.activity.result.launch
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.elsoft.whatthewff.logic.PlannedProblemGenerator
import com.elsoft.whatthewff.logic.Problem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val problemGenerator: PlannedProblemGenerator = PlannedProblemGenerator()

/**
 * A screen that allows users to select a difficulty level for a generated proof.
 *
 * @param onProblemGenerated A callback executed with the newly generated problem.
 * @param onBackClicked A callback to navigate back to the main screen.
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameModeScreen(
    onProblemGenerated: (Problem) -> Unit,
    onBackClicked: () -> Unit
) {
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Game Mode") },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Select a Difficulty",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Difficulty Buttons
            DifficultyButton(
                text = "Easy",
                difficulty = 2, // e.g., 2 backward steps
                onClick = {
                    // Call generate() directly on the PlannedProblemGenerator object
                    // and handle the nullable result.
                    scope.launch(Dispatchers.Default) {
                        problemGenerator.generate(2)?.let { problem ->
                            withContext(Dispatchers.Main) {
                                onProblemGenerated(problem)
                            }
                        }
                    }
                }
            )
            DifficultyButton(
                text = "Medium",
                difficulty = 4, // e.g., 4 backward steps
                onClick = {
                    // Call generate() directly on the PlannedProblemGenerator object
                    // and handle the nullable result.
                    scope.launch(Dispatchers.Default) {
                        problemGenerator.generate(4)?.let { problem ->
                            withContext(Dispatchers.Main) {
                                onProblemGenerated(problem)
                            }
                        }
                    }
                }
            )
            DifficultyButton(
                text = "Hard",
                difficulty = 6, // e.g., 6 backward steps
                onClick = {
                    // Call generate() directly on the PlannedProblemGenerator object
                    // and handle the nullable result.
                    scope.launch(Dispatchers.Default) {
                        problemGenerator.generate(6)?.let { problem ->
                            withContext(Dispatchers.Main) {
                                onProblemGenerated(problem)
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun DifficultyButton(text: String, difficulty: Int, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(56.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}
