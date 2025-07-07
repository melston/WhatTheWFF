// File: ui/features/proof/ProofScreen.kt
// This file defines the UI for the proof construction screen.

package com.elsoft.whatthewff.ui.features.proof

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.elsoft.whatthewff.logic.*
import com.elsoft.whatthewff.ui.features.game.ConstructionArea
import com.elsoft.whatthewff.ui.features.game.SymbolPalette
import com.elsoft.whatthewff.ui.theme.WhatTheWFFTheme

/**
 * Displays a single, formatted line of a proof.
 *
 * @param line The ProofLine to display.
 */
@Composable
fun ProofLineView(line: ProofLine, scale: Float, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "${line.lineNumber}.",
             modifier = Modifier.width(30.dp),
             fontWeight = FontWeight.Bold,
             fontSize = 16.sp * scale
        )
        Text(text = line.formula.stringValue,
             modifier = Modifier.weight(1f),
             fontSize = 16.sp * scale)
        Text(text = line.justification.displayText(),
             modifier = Modifier.width(100.dp),
             fontSize = 12.sp * scale,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
        // MODIFIED: Replaced IconButton with a clickable Box for precise size control.
        Box(
            modifier = Modifier
                .size(32.dp) // A smaller, more reasonable touch target.
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete line ${line.lineNumber}",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp) // The visible size of the icon itself.
            )
        }
    }
}

/**
 * A dialog for adding a new line to the proof by specifying its justification.
 *
 * @param onDismissRequest Called when the user wants to close the dialog.
 * @param onConfirm Called when the user confirms the new line, passing the justification.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLineDialog(onDismissRequest: () -> Unit, onConfirm: (Justification) -> Unit) {
    var selectedTab by remember { mutableStateOf(0) } // 0 for Inference, 1 for Replacement
    var selectedInferenceRule by remember { mutableStateOf(InferenceRule.MODUS_PONENS) }
    var selectedReplacementRule by remember { mutableStateOf(ReplacementRule.DEMORGANS_THEOREM) }
    var lineRefs by remember { mutableStateOf("") }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Add Justification", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Inference") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Replacement") })
                }
                Spacer(Modifier.height(24.dp))

                // Dropdown Menu
                ExposedDropdownMenuBox(expanded = isDropdownExpanded,
                                       onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }) {
                    val currentRuleName =
                        if (selectedTab == 0) selectedInferenceRule.ruleName
                        else selectedReplacementRule.ruleName

                    OutlinedTextField(
                        value = currentRuleName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Rule") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                    )

                    ExposedDropdownMenu(expanded = isDropdownExpanded,
                                        onDismissRequest = { isDropdownExpanded = false }) {
                        if (selectedTab == 0) {
                            InferenceRule.entries.forEach { rule ->
                                DropdownMenuItem(
                                    text = { Text(rule.ruleName) },
                                    onClick = {
                                        selectedInferenceRule = rule
                                        isDropdownExpanded = false
                                    }
                                )
                            }
                        } else {
                            ReplacementRule.entries.forEach { rule ->
                                DropdownMenuItem(
                                    text = { Text(rule.ruleName) },
                                    onClick = {
                                        selectedReplacementRule = rule
                                        isDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(value = lineRefs, onValueChange = { lineRefs = it.filter { char -> char.isDigit() || char == ',' } }, label = { Text(if (selectedTab == 0) "Reference Lines (e.g., 1,2)" else "Reference Line (e.g., 1)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissRequest) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val justification = if (selectedTab == 0) {
                            val refs = lineRefs.split(',').mapNotNull { it.trim().toIntOrNull() }
                            if (refs.isNotEmpty()) Justification.Inference(selectedInferenceRule, refs) else null
                        } else {
                            val ref = lineRefs.trim().toIntOrNull()
                            if (ref != null) Justification.Replacement(selectedReplacementRule, ref) else null
                        }
                        justification?.let { onConfirm(it) }
                    }) { Text("Confirm") }
                }
            }
        }
    }
}

/**
 * The main screen for building and validating proofs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProofScreen(
    problem: Problem,
    onBackClicked: () -> Unit
) {

    // --- STATE MANAGEMENT ---
    // Initialize the proof with the problem's premises.
    val initialLines = problem.premises.mapIndexed { index, formula ->
        ProofLine(index + 1, formula, Justification.Premise)
    }
    var proof by remember { mutableStateOf(Proof(lines = initialLines)) }
    var currentFormula by remember { mutableStateOf(Formula(emptyList())) }
    var showAddLineDialog by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf("Add a line to complete the proof, then validate.") }
    val scale = remember { mutableStateOf(1f) }

    val zoomModifier = Modifier.pointerInput(Unit) {
        detectTransformGestures { _, _, zoom, _ ->
            scale.value = (scale.value * zoom).coerceIn(0.5f, 3f)  // limit scale range
        }
    }

    // --- DIALOG HANDLER ---
    if (showAddLineDialog) {
        AddLineDialog(
            onDismissRequest = { showAddLineDialog = false },
            onConfirm = { justification ->
                val newProofLine = ProofLine(
                    lineNumber = proof.lines.size + 1,
                    formula = currentFormula,
                    justification = justification
                )
                proof = Proof(proof.lines + newProofLine)
                currentFormula = Formula(emptyList()) // Clear input field
                showAddLineDialog = false // Close dialog
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(problem.name) },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // --- GOAL DISPLAY ---
            Text(
                text = "Goal: ${problem.conclusion.stringValue}",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            // --- Proof Display Area ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                LazyColumn(modifier = Modifier.padding(16.dp),
                           verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    items(proof.lines) { line ->
                        ProofLineView(line = line, scale = scale.value) {
                            proof = Proof(proof.lines.take(line.lineNumber - 1))
                            feedbackMessage = "Proof updated. Continue building."
                        }
                    }
                }
            }

            // --- FORMULA CONSTRUCTION AREA ---
            SymbolPalette {
                    tile -> currentFormula = Formula(currentFormula.tiles + tile)
            }
            ConstructionArea(formula = currentFormula)
            Spacer(Modifier.height(16.dp))

            // --- ACTION BUTTONS ---
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                Button(
                    onClick = {
                        // Only show dialog if the user has constructed a formula.
                        if (currentFormula.tiles.isNotEmpty()) {
                            showAddLineDialog = true
                        }
                    },
                    // Disable button if there's no formula to add
                    enabled = currentFormula.tiles.isNotEmpty()
                ) {
                    Text("Add Line")
                }
                Button(onClick = { currentFormula = Formula(emptyList()) }) {
                    Text("Clear Input")
                }
                Button(
                    onClick = {
                        val result = ProofValidator.validate(proof)
                        if (!result.isValid) {
                            feedbackMessage = result.errorMessage ?: "Proof is valid!"
                        } else {
                            // Check if the last line matches the conclusion
                            if (proof.lines.lastOrNull()?.formula == problem.conclusion) {
                                feedbackMessage = "Congratulations! Proof complete!"
                            } else {
                                feedbackMessage = "The proof is valid, but you haven't reached the goal yet."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Validate Proof")
                }

            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                Button(
                    onClick = {
                        val newProofLine = ProofLine(
                            lineNumber = proof.lines.size + 1,
                            formula = currentFormula,
                            justification = Justification.Premise
                        )
                        proof = Proof(proof.lines + newProofLine)
                        currentFormula = Formula(emptyList())
                        feedbackMessage = "Proof updated. Continue building."
                    },
                    enabled = currentFormula.tiles.isNotEmpty()
                ) {
                    Text("Add Premise")
                }
                Button(onClick = {
                    proof = Proof(emptyList())
                    currentFormula = Formula(emptyList())
                    feedbackMessage = "Proof cleared. Start over."
                }) {
                    Text("Clear Proof")
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- FEEDBACK AREA ---
            Text(
                text = feedbackMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = if (feedbackMessage == "Proof is valid!") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }

    }
}

@Preview(showBackground = true)
@Composable
fun ProofScreenPreview() {
    WhatTheWFFTheme {
        ProofScreen(problem = ProblemSets.chapter1_ModusPonens.get(0),
                    onBackClicked = { /*TODO*/ } )
    }
}
