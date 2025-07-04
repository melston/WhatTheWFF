// File: ui/features/rulebook/RulebookScreen.kt
// This file defines the UI for the screen that shows the rules for WFFs.

package com.elsoft.whatthewff.ui.features.rulebook

// Import necessary components from Jetpack Compose
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.elsoft.whatthewff.ui.theme.WhatTheWFFTheme // Assuming you have a theme file

/**
 * A Composable function that displays a single rule.
 * We make this a separate component to keep our code clean and reusable.
 *
 * @param title The title of the rule (e.g., "Rule 1: Atomic Propositions").
 * @param description The detailed explanation of the rule.
 * @param examples A list of strings showing examples of the rule in action.
 */
@Composable
fun RuleCard(title: String, description: String, examples: List<String>) {
    // Card provides a nice, elevated container for our content.
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        // Column arranges its children vertically.
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp)) // Adds vertical space
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Examples:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            // Display each example on a new line
            examples.forEach { example ->
                Text(
                    text = "• $example",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/**
 * The main Composable for the Rulebook screen. It lays out all the rules
 * in a scrollable list.
 *
 * @param modifier The modifier to be applied to the layout. This is a best practice
 * for making composables reusable and adaptable.
 */
@Composable
fun RulebookScreen(modifier: Modifier = Modifier) {
    // LazyColumn is an efficient way to display a scrollable list of items.
    // We apply the incoming modifier to the LazyColumn.
    LazyColumn(
        modifier = modifier
            .fillMaxSize() // Take up the whole screen
            .padding(horizontal = 16.dp), // We apply horizontal padding here
        verticalArrangement = Arrangement.spacedBy(8.dp) // Space between items
    ) {
        item {
            Text(
                text = "Rules of Well-Formed Formulas (WFFs)",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 16.dp) // Adjust padding here
            )
        }

        item {
            RuleCard(
                title = "Rule 1: Variables",
                description = "Any propositional variable (a single lowercase letter) is a well-formed formula.",
                examples = listOf("p", "q", "r")
            )
        }

        item {
            RuleCard(
                title = "Rule 2: Negation (¬)",
                description = "If 'p' is a well-formed formula, then '(¬p)' is also a well-formed formula.",
                examples = listOf("(¬p)", "(¬q)")
            )
        }

        item {
            RuleCard(
                title = "Rule 3: Binary Connectives (∧, ∨, →, ↔)",
                description = "If 'p' and 'q' are well-formed formulas, then formulas using a binary connective are also WFFs.",
                examples = listOf("(p ∧ q)", "(q ∨ r)", "(p → q)")
            )
        }

        item {
            RuleCard(
                title = "Rule 4: Closure",
                description = "Nothing else is a well-formed formula. Only formulas that can be constructed by applying the above rules are WFFs.",
                examples = listOf("p ∧ q (missing parentheses) is not a WFF.", "p¬q is not a WFF.")
            )
        }
    }
}

/**
 * A Preview function allows us to see what our Composable looks like
 * in the Android Studio editor without having to run the app on a device.
 */
@Preview(showBackground = true)
@Composable
fun RulebookScreenPreview() {
    // It's good practice to wrap previews in your app's theme.
    WhatTheWFFTheme {
        RulebookScreen()
    }
}
