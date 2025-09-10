package com.elsoft.whatthewff.ui.navigation

import com.elsoft.whatthewff.logic.Problem

/**
 * A sealed class representing all possible screens in the app.
 * This is used to manage the navigation state.
 */
sealed class Screen {
    object Main : Screen()
    object GameModeSelect : Screen()
    object ProblemSetBrowser : Screen()
    data class ProblemList(val setTitle: String) : Screen()
    data class Proof(val problem: Problem) : Screen()
}

