package com.elsoft.whatthewff.ui.navigation

import com.elsoft.whatthewff.logic.Problem

sealed class Screen {
    object Main : Screen()
    object PracticeSelect : Screen()
    object GameModeSelect : Screen()
    data class Proof(val problem: Problem) : Screen()
}
