// File: data/ProblemListViewModel.kt
// This ViewModel is responsible for loading the list of individual
// problems for a specific, user-selected problem set.

package com.elsoft.whatthewff.data

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elsoft.whatthewff.logic.CustomProblem
import com.elsoft.whatthewff.logic.Problem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProblemListViewModel(application: Application, private val problemSetTitle: String) : ViewModel() {

    private val dao = ProblemDatabase.getDatabase(application).problemDao()

    private val _problems = MutableStateFlow<List<CustomProblem>>(emptyList())
    val problems: StateFlow<List<CustomProblem>> = _problems.asStateFlow()

    init {
        loadProblems()
    }

    private fun loadProblems() {
        viewModelScope.launch {
            val entities = dao.getProblemsForSet(problemSetTitle)
            _problems.value = entities.map { entity ->
                // Convert from the database entity to our logic-layer CustomProblem
                CustomProblem(
                    id = entity.id,
                    premises = entity.premises,
                    conclusion = entity.conclusion,
                    solvedProof = entity.solvedProof
                )
            }
        }
    }

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
