// File: data/ProblemListViewModel.kt
// This ViewModel is responsible for loading the list of individual
// problems for a specific, user-selected problem set.

package com.elsoft.whatthewff.data

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elsoft.whatthewff.logic.CustomProblem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ProblemListViewModel(application: Application, private val problemSetTitle: String) : ViewModel() {

    private val dao = ProblemDatabase.getDatabase(application).problemDao()

    val problems: StateFlow<List<CustomProblem>> =
        dao.getProblemsForSet(problemSetTitle)
            .map { entities ->
                // This map operation transforms the database entities into our logic-layer models
                entities.map { entity ->
                    CustomProblem(
                        id = entity.id,
                        premises = entity.premises,
                        conclusion = entity.conclusion,
                        solvedProof = entity.solvedProof
                    )
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L), // Keep the flow active for 5s
                initialValue = emptyList()
            )

    // The manual init{} and loadProblems() function are no longer needed.

    // Factory to allow passing the problemSetTitle to the ViewModel
    class Factory(private val application: Application, private val problemSetTitle: String) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ProblemListViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ProblemListViewModel(application, problemSetTitle) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

