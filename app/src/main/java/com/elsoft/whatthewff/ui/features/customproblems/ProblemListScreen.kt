// File: ui/features/customproblems/ProblemListScreen.kt
// This screen displays the list of individual problems within a selected set.

package com.elsoft.whatthewff.ui.features.customproblems

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
    onProblemSelected: (Problem, String) -> Unit,
    onProblemLongClicked: (CustomProblem, String) -> Unit // New callback for viewing solutions
) {
    val context = LocalContext.current
    val vm: ProblemListViewModel = viewModel(
        factory = ProblemListViewModel.Factory(context.applicationContext as Application, problemSetTitle)
    )
    val problems by vm.problems.collectAsState()

    // Intercept the back gesture and call the onBackClicked lambda,
    // which tells MainActivity to switch the state back to AppScreen.Main.
    BackHandler {
        // Prevent going back while a problem is being generated.
        onBackPressed()
    }

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
        LazyColumn(modifier = Modifier
            .padding(padding)
            .padding(16.dp)) {
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
                    },
                    // --- KEY CHANGE: Trigger the new callback on long click ---
                    onLongClicked = {
                        if (customProblem.solvedProof != null) {
                            onProblemLongClicked(customProblem, problemSetTitle)
                        }
                    }
                )
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            }
        }
    }
}

@Composable
fun ProblemItem(
    problem: CustomProblem,
    onClicked: () -> Unit,
    onLongClicked: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClicked,
                onLongClick = onLongClicked
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically // Center items vertically in the row
    ) {
        Box(
            modifier = Modifier.size(24.dp), // Standard icon size
            contentAlignment = Alignment.Center
        ) {
            if (problem.solvedProof != null) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Solved",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.width(10.dp)) // Space between icon and text

        Column {
            Text(problem.id, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Goal: ${problem.conclusion.stringValue}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Light
            )
        }
    }
}
