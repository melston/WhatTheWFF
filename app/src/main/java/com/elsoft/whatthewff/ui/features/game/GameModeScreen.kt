// File: ui/features/game/GameModeScreen.kt
// This file defines the screen where users select a difficulty for generated problems.

package com.elsoft.whatthewff.ui.features.game

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Game Mode") },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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

            // Show a progress indicator when loading
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(bottom = 32.dp))
            }

            // Difficulty Buttons
            DifficultyButton(
                text = "Easy",
                difficulty = 2, // e.g., 2 backward steps
                onClick = {
                    isLoading = true
                    scope.launch(Dispatchers.Default) {
                        problemGenerator.generate(2)?.let { problem ->
                            withContext(Dispatchers.Main) {
                                onProblemGenerated(problem)
                                isLoading = false
                            }
                        } ?: run {
                            // Also handle the case where generation fails
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context,
                                               "Failed to generate problem. Please try again.",
                                               Toast.LENGTH_SHORT).show()
                                isLoading = false
                            }
                        }
                    }
                }
            )
            DifficultyButton(
                text = "Medium",
                difficulty = 4, // e.g., 4 backward steps
                onClick = {
                    isLoading = true
                    scope.launch(Dispatchers.Default) {
                        problemGenerator.generate(4)?.let { problem ->
                            withContext(Dispatchers.Main) {
                                onProblemGenerated(problem)
                                isLoading = false
                            }
                        } ?: run {
                            // Also handle the case where generation fails
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context,
                                               "Failed to generate problem. Please try again.",
                                               Toast.LENGTH_SHORT).show()
                                isLoading = false
                            }
                        }
                    }
                }
            )
            DifficultyButton(
                text = "Hard",
                difficulty = 6, // e.g., 6 backward steps
                onClick = {
                    isLoading = true
                    scope.launch(Dispatchers.Default) {
                        problemGenerator.generate(6)?.let { problem ->
                            withContext(Dispatchers.Main) {
                                onProblemGenerated(problem)
                                isLoading = false
                            }
                        } ?: run {
                            // Also handle the case where generation fails
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context,
                                               "Failed to generate problem. Please try again.",
                                               Toast.LENGTH_SHORT).show()
                                isLoading = false
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
