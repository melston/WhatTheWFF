package com.elsoft.whatthewff

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.elsoft.whatthewff.logic.ProblemGenerator
import com.elsoft.whatthewff.ui.features.game.GameModeScreen
import com.elsoft.whatthewff.ui.features.main.MainScreen
import com.elsoft.whatthewff.ui.features.practice.PracticeSelectScreen
import com.elsoft.whatthewff.ui.features.proof.ProofScreen

import com.elsoft.whatthewff.ui.theme.WhatTheWFFTheme
import com.elsoft.whatthewff.ui.navigation.Screen

// In MainActivity.kt
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WhatTheWFFTheme {
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }

                Surface(modifier = Modifier.fillMaxSize()) {
                    when (val screen = currentScreen) {
                        is Screen.Main -> MainScreen(
                            onPracticeClicked = { currentScreen = Screen.PracticeSelect },
                            onGameClicked = { currentScreen = Screen.GameModeSelect } // Change this line
                        )
                        is Screen.PracticeSelect -> PracticeSelectScreen(
                            onProblemSelected = { problem -> currentScreen = Screen.Proof(problem) },
                            onBackClicked = { currentScreen = Screen.Main }
                        )
                        // Add this new case
                        is Screen.GameModeSelect -> GameModeScreen(
                            onProblemGenerated = { problem -> currentScreen = Screen.Proof(problem) },
                            onBackClicked = { currentScreen = Screen.Main }
                        )
                        is Screen.Proof -> ProofScreen(
                            problem = screen.problem,
                            onBackClicked = { currentScreen = Screen.Main }
                        )
                    }
                }
            }
        }
    }
}