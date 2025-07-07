// File: ui/features/main/MainScreen.kt
// This file defines the main entry screen for the application.

package com.elsoft.whatthewff.ui.features.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.elsoft.whatthewff.ui.theme.WhatTheWFFTheme

/**
 * The main entry screen of the app. It provides navigation to the
 * two primary modes: Practice and Game.
 *
 * @param onPracticeClicked A callback executed when the user chooses Practice Mode.
 * @param onGameClicked A callback executed when the user chooses Game Mode.
 */
@Composable
fun MainScreen(
    onPracticeClicked: () -> Unit,
    onGameClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "What the WFF?",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 48.dp)
        )

        // Practice Mode Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable(onClick = onPracticeClicked),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Practice Mode", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Learn the rules of inference and replacement with curated problems organized by chapter.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Game Mode Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable(onClick = onGameClicked),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Game Mode", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Test your skills by solving procedurally generated proofs of varying difficulty.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    WhatTheWFFTheme {
        MainScreen(onPracticeClicked = {}, onGameClicked = {})
    }
}
