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
import com.elsoft.whatthewff.logic.Problem
import com.elsoft.whatthewff.ui.features.customproblems.ProblemListScreen
import com.elsoft.whatthewff.ui.features.customproblems.ProblemSetBrowserScreen
import com.elsoft.whatthewff.ui.features.game.GameModeScreen
import com.elsoft.whatthewff.ui.features.help.HelpSystem
import com.elsoft.whatthewff.ui.features.main.MainScreen
import com.elsoft.whatthewff.ui.features.proof.ProofScreen
import com.elsoft.whatthewff.ui.navigation.Screen
import com.elsoft.whatthewff.ui.navigation.Screen.*
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
                    var currentScreen by remember { mutableStateOf<Screen>(Main) }
                    var previousScreen by remember { mutableStateOf<Screen>(Main) }

                    when (val screen = currentScreen) {
                        is Main -> MainScreen(
                            onPracticeClicked = {
                                previousScreen = Main
                                currentScreen = ProblemSetBrowser
                            },
                            onGameClicked = {
                                previousScreen = Main
                                currentScreen = GameModeSelect
                            },
                            onHelpClicked = {
                                previousScreen = Main
                                currentScreen = Help
                            }
                        )
                        is Help -> HelpSystem(
                            onExit = {
                                currentScreen = Main
                            }
                        )
                        is GameModeSelect -> GameModeScreen(
                            onProblemGenerated = { problem ->
                                previousScreen = GameModeSelect
                                currentScreen = Proof(
                                    problem, "Generated Problem",
                                    initialProof = null
                                )
                            },
                            onBackClicked = { currentScreen = Main }
                        )
                        is ProblemSetBrowser -> ProblemSetBrowserScreen(
                            onBackPressed = { currentScreen = Main },
                            onProblemSetSelected = { setTitle ->
                                previousScreen = ProblemSetBrowser
                                currentScreen = ProblemList(setTitle)
                            }
                        )
                        is ProblemList -> ProblemListScreen(
                            problemSetTitle = screen.setTitle,
                            onBackPressed = { currentScreen = ProblemSetBrowser },
                            onProblemSelected = { problem, problemSetTitle ->
                                previousScreen = ProblemList(screen.setTitle)
                                currentScreen = Proof(
                                    problem, problemSetTitle,
                                    initialProof = null
                                )
                            },
                            // Callback for long click
                            onProblemLongClicked = { customProblem, setTitle ->
                                if (customProblem.solvedProof != null) {
                                    val problem = Problem(
                                        id = customProblem.id,
                                        name = "View Solution: ${setTitle}: ${customProblem.id}",
                                        premises = customProblem.premises,
                                        conclusion = customProblem.conclusion,
                                        difficulty = 0
                                    )
                                    previousScreen = ProblemList(screen.setTitle)
                                    currentScreen = Proof(problem, setTitle, customProblem.solvedProof)
                                }
                            }
                        )
                        is Proof -> ProofScreen(
                            problem = screen.problem,
                            problemSetTitle = screen.setTitle,
                            initialProof = screen.initialProof,
                            onBackClicked = { currentScreen = previousScreen }
                        )
                    }
                }
            }
        }
    }
}
