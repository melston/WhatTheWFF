// File: MainActivity.kt
// The main entry point of the app, containing the central navigation logic.

package com.elsoft.whatthewff.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.elsoft.whatthewff.ui.features.customproblems.ProblemListScreen
import com.elsoft.whatthewff.ui.features.customproblems.ProblemSetBrowserScreen
import com.elsoft.whatthewff.ui.features.game.GameModeScreen
import com.elsoft.whatthewff.ui.features.main.MainScreen
import com.elsoft.whatthewff.ui.features.proof.ProofScreen
import com.elsoft.whatthewff.ui.navigation.Screen
import com.elsoft.whatthewff.ui.theme.WhatTheWFFTheme

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WhatTheWFFTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }
                    var previousScreen by remember { mutableStateOf<Screen>(Screen.Main) }

                    when (val screen = currentScreen) {
                        is Screen.Main -> MainScreen(
                            onPracticeClicked = {
                                previousScreen = Screen.Main
                                currentScreen = Screen.ProblemSetBrowser
                            },
                            onGameClicked = {
                                previousScreen = Screen.Main
                                currentScreen = Screen.GameModeSelect
                            }
                        )
                        is Screen.GameModeSelect -> GameModeScreen(
                            onProblemGenerated = { problem ->
                                previousScreen = Screen.GameModeSelect
                                currentScreen = Screen.Proof(problem, "Generated Problem")
                            },
                            onBackClicked = { currentScreen = Screen.Main }
                        )
                        is Screen.ProblemSetBrowser -> ProblemSetBrowserScreen(
                            onBackPressed = { currentScreen = Screen.Main },
                            onProblemSetSelected = { setTitle ->
                                previousScreen = Screen.ProblemSetBrowser
                                currentScreen = Screen.ProblemList(setTitle)
                            }
                        )
                        is Screen.ProblemList -> ProblemListScreen(
                            problemSetTitle = screen.setTitle,
                            onBackPressed = { currentScreen = Screen.ProblemSetBrowser },
                            onProblemSelected = { problem, problemSetTitle ->
                                previousScreen = Screen.ProblemList(screen.setTitle)
                                currentScreen = Screen.Proof(problem, problemSetTitle)
                            }
                        )
                        is Screen.Proof -> ProofScreen(
                            problem = screen.problem,
                            problemSetTitle = screen.setTitle,
                            onBackClicked = { currentScreen = previousScreen }
                        )
                    }
                }
            }
        }
    }
}
