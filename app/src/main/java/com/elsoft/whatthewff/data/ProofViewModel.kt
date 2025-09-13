// File: data/ProofViewModel.kt
// This ViewModel is responsible for saving a solved proof for a specific
// custom problem back to the database.

package com.elsoft.whatthewff.data

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elsoft.whatthewff.logic.Proof
import kotlinx.coroutines.launch

class ProofViewModel(
    application: Application,
    private val problemSetTitle: String,
    private val problemId: String
) : ViewModel() {

    private val dao = ProblemDatabase.getDatabase(application).problemDao()

    fun saveProof(proof: Proof) {
        viewModelScope.launch {
            // Find the specific problem entity in the database
            val problemToUpdate = dao.getProblemEntity(problemSetTitle, problemId)

            if (problemToUpdate != null) {
                // Create an updated version of the entity with the new proof
                val updatedEntity = problemToUpdate.copy(solvedProof = proof)
                dao.updateProblem(updatedEntity)
            }
        }
    }

    // Factory to allow passing parameters to the ViewModel
    class Factory(
        private val application: Application,
        private val problemSetTitle: String,
        private val problemId: String
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ProofViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ProofViewModel(application, problemSetTitle, problemId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
