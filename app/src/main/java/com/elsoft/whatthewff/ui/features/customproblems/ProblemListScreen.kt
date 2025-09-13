// File: ui/features/customproblems/ProblemListScreen.kt
// This screen displays the list of individual problems within a selected set.

package com.elsoft.whatthewff.ui.features.customproblems

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elsoft.whatthewff.data.ProblemListViewModel
import com.elsoft.whatthewff.logic.CustomProblem
import com.elsoft.whatthewff.logic.Problem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProblemListScreen(
    problemSetTitle: String,
    onBackPressed: () -> Unit,
    onProblemSelected: (Problem, String) -> Unit
) {
    val context = LocalContext.current
    val vm: ProblemListViewModel = viewModel(
        factory = ProblemListViewModel.Factory(context.applicationContext as Application, problemSetTitle)
    )
    val problems by vm.problems.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(problemSetTitle) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            items(problems) { customProblem ->
                ProblemItem(
                    problem = customProblem,
                    onClicked = {
                        // Convert the CustomProblem to the generic Problem the ProofScreen expects
                        val problem = Problem(
                            id = customProblem.id,
                            name = "${problemSetTitle}: ${customProblem.id}",
                            premises = customProblem.premises,
                            conclusion = customProblem.conclusion,
                            difficulty = 0 // Difficulty isn't relevant for custom problems
                        )
                        onProblemSelected(problem, problemSetTitle)
                    }
                )
                Divider()
            }
        }
    }
}

@Composable
fun ProblemItem(
    problem: CustomProblem,
    onClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClicked)
            .padding(vertical = 16.dp)
    ) {
        Text(problem.id, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Goal: ${problem.conclusion.stringValue}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Light
        )
    }
}
