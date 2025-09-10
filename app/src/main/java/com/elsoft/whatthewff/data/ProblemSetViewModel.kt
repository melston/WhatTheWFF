// File: data/ProblemSetViewModel.kt
// This ViewModel acts as the bridge between the UI and the database for
// managing custom problem sets.

package com.elsoft.whatthewff.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elsoft.whatthewff.logic.CustomProblem
import com.elsoft.whatthewff.logic.ProblemFileParser
import com.elsoft.whatthewff.logic.ProblemSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProblemSetViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ProblemDatabase.getDatabase(application)
    private val dao = db.problemDao()

    private val _problemSets = MutableStateFlow<List<ProblemSetEntity>>(emptyList())
    val problemSets: StateFlow<List<ProblemSetEntity>> = _problemSets.asStateFlow()

    init {
        loadProblemSets()
    }

    fun loadProblemSets() {
        viewModelScope.launch {
            _problemSets.value = dao.getAllProblemSets()
        }
    }

    /**
     * Parses the content of a problem set file and saves it to the database.
     */
    fun importProblemSet(fileContent: String) {
        viewModelScope.launch {
            val parsedSets = ProblemFileParser.parse(fileContent)
            parsedSets.forEach { problemSet ->
                dao.insertProblemSet(ProblemSetEntity(problemSet.title))
                val problemEntities = problemSet.problems.map { customProblem ->
                    CustomProblemEntity(
                        id = customProblem.id,
                        problemSetTitle = problemSet.title,
                        premises = customProblem.premises,
                        conclusion = customProblem.conclusion,
                        solvedProof = null // New problems are always unsolved
                    )
                }
                dao.insertProblems(problemEntities)
            }
            // Refresh the list from the database
            loadProblemSets()
        }
    }

    /**
     * Deletes a problem set and all its associated problems from the database.
     */
    fun deleteProblemSet(setTitle: String) {
        viewModelScope.launch {
            dao.deleteProblemSet(setTitle)
            loadProblemSets()
        }
    }
}
