package com.elsoft.whatthewff.ui.features.help

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.io.IOException

// This will be the main entry point for your help system
@Composable
fun HelpSystem(onExit: () -> Unit) { // Added an onExit callback
    val navController = rememberNavController() // Correct: No need for full qualification

    // This will intercept the system back gesture (like swiping)
    BackHandler {
        // Check if the internal NavController can go back
        if (navController.previousBackStackEntry != null) {
            // If it can, pop the stack (navigate back within the help system)
            navController.popBackStack()
        } else {
            // If it can't, call onExit to return to the MainScreen
            onExit()
        }
    }

    NavHost(navController = navController, startDestination = "help/help_introduction.md") { // Correct: Call NavHost directly
        // 'this' is now a NavGraphBuilder, so composable() is available
        composable("help/{topicFile}") { backStackEntry ->
            val topicFile = backStackEntry.arguments?.getString("topicFile") ?: "index.md"
            HelpScreen(
                topicFile = topicFile,
                // Navigate to a new topic when a link is clicked
                onNavigate = { newTopicFile ->
                    navController.navigate("help/$newTopicFile")
                },
                // Go back in the NavHost or exit if at the index
                onBackClicked = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        onExit() // Exit the help system
                    }
                }
            )
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpScreen(
    topicFile: String,
    onNavigate: (String) -> Unit,
    onBackClicked: () -> Unit
) {
    val context = LocalContext.current
    // Load the content of the specified markdown file from the assets/help/ directory
    val markdownContent = remember(topicFile) {
        try {
            context.assets.open("help/$topicFile").bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            "Error: Could not load help file `$topicFile`."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help") },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        MarkdownText(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            markdown = markdownContent,
            // This is the key to making links work!
            onLinkClicked = { link ->
                onNavigate(link)
            }
        )
    }
}
