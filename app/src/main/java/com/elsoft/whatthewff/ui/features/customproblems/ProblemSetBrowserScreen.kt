// File: ui/features/customproblems/ProblemSetBrowserScreen.kt
// This screen allows the user to browse their imported problem sets and
// import new ones from a text file.

package com.elsoft.whatthewff.ui.features.customproblems

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elsoft.whatthewff.data.ProblemSetEntity
import com.elsoft.whatthewff.data.ProblemSetViewModel
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProblemSetBrowserScreen(
    onBackPressed: () -> Unit,
    onProblemSetSelected: (String) -> Unit, // Callback with the set title
    vm: ProblemSetViewModel = viewModel()
) {
    val problemSets by vm.problemSets.collectAsState()
    val context = LocalContext.current

    // This is the modern way to handle the file picker result
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                // Read the file content and import it
                val contentResolver = context.contentResolver
                contentResolver.openInputStream(it)?.use { inputStream ->
                    val reader = InputStreamReader(inputStream)
                    val fileContent = reader.readText()
                    vm.importProblemSet(fileContent)
                }
            }
        }
    )

    BackHandler {
        // Prevent going back while a problem is being generated.
        onBackPressed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Problem Sets") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { filePickerLauncher.launch("text/plain") }) {
                Icon(Icons.Default.Add, contentDescription = "Import Problem Set")
            }
        }
    ) { padding ->
        if (problemSets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No problem sets imported yet.\nClick the '+' to add one.",
                     style = MaterialTheme.typography.bodyLarge,
                     color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier
                .padding(padding)
                .padding(16.dp)) {
                items(problemSets) { set ->
                    ProblemSetItem(
                        set = set,
                        onClicked = { onProblemSetSelected(set.title) },
                        onDelete = { vm.deleteProblemSet(set.title) }
                    )
                    Divider()
                }
            }
        }
    }
}

@Composable
fun ProblemSetItem(
    set: ProblemSetEntity,
    onClicked: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClicked)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(set.title, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete Set", tint = MaterialTheme.colorScheme.error)
        }
    }
}
