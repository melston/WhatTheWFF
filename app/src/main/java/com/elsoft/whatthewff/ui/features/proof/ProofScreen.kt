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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.elsoft.whatthewff.R
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
fun ProofLineView(line: ProofLine, currentScale: Float, onDelete: () -> Unit) {
    // Define your base font sizes (these could come from MaterialTheme or be custom)
    val baseNumberFontSize: TextUnit = MaterialTheme.typography.bodyLarge.fontSize
    val baseFormulaFontSize: TextUnit = MaterialTheme.typography.bodyLarge.fontSize
    val baseJustificationFontSize: TextUnit = MaterialTheme.typography.bodyMedium.fontSize

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Line Number Text
        Text(
            text = "${line.lineNumber}.",
            fontWeight = FontWeight.Bold,
            fontSize = baseNumberFontSize * currentScale, // <--- ADJUST FONT SIZE
            modifier = Modifier.padding(end = 8.dp)
        )

        // Formula Text
        Text(
            text = line.formula.stringValue,
            fontSize = baseFormulaFontSize * currentScale, // <--- ADJUST FONT SIZE
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            // consider maxLines and overflow if text can get very long
        )

        // Justification Text (Example, adjust as per your actual UI)
        Text(
            text = line.justification.displayText(),
            fontSize = baseJustificationFontSize * currentScale, // <--- ADJUST FONT SIZE
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )

        // Delete Icon (Icons usually scale differently, often you scale the Modifier.size())
        Box(
            modifier = Modifier
                .size(32.dp) // A smaller, more reasonable touch target.
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete line ${line.lineNumber}",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(15.dp * currentScale) // The visible size of the icon itself.
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

    val addJustificationLabel = stringResource(id = R.string.add_justification_label)
    val cancelLabel = stringResource(id = R.string.cancel_label)
    val confirmLabel = stringResource(id = R.string.confirm_label)
    val inferenceLabel = stringResource(id = R.string.inference_label)
    val referenceLineLabel = stringResource(id = R.string.reference_line_label)
    val referenceLinesLabel = stringResource(id = R.string.reference_lines_label)
    val replacementLabel = stringResource(id = R.string.replacement_label)

    Dialog(onDismissRequest = onDismissRequest) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp),
                   horizontalAlignment = Alignment.CenterHorizontally) {
                Text(addJustificationLabel,
                     style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(inferenceLabel) })
                    Tab(selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(replacementLabel) })
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

                OutlinedTextField(value = lineRefs,
                                  onValueChange = { lineRefs = it.filter { char -> char.isDigit() || char == ',' } },
                                  label = { Text(if (selectedTab == 0) referenceLinesLabel else referenceLineLabel) },
                                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissRequest) { Text(cancelLabel) }
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
                    }) { Text(confirmLabel) }
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
    val addLineMessage = stringResource(id = R.string.add_line_message)
    val proofClearedMessage = stringResource(id = R.string.proof_cleared_message)
    val proofCompleteMessage = stringResource(id = R.string.proof_complete_message)
    val proofIncompleteMessage = stringResource(id = R.string.proof_incomplete_message)
    val proofUpdatedMessage = stringResource(id = R.string.proof_updated_message)
    val proofValidMessage = stringResource(id = R.string.proof_valid_message)

    val addLineLabel = stringResource(id = R.string.add_line_label)
    val addPremiseLabel = stringResource(id = R.string.add_premise_label)
    val validateProofLabel = stringResource(id = R.string.validate_proof_label)
    val clearProofLabel = stringResource(id = R.string.clear_proof_label)
    val goalLabel = stringResource(id = R.string.goal_label)

    var proof by remember { mutableStateOf(Proof(lines = initialLines)) }
    var currentFormula by remember { mutableStateOf(Formula(emptyList())) }
    var showAddLineDialog by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf(addLineMessage) }
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
                text = goalLabel + " ${problem.conclusion.stringValue}",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            // --- Proof Display Area ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .then(zoomModifier),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                LazyColumn(modifier = Modifier.padding(16.dp),
                           verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    items(proof.lines) { line ->
                        ProofLineView(
                            line = line,
                            currentScale = scale.value,
                            onDelete = {
                                proof = Proof(proof.lines.take(line.lineNumber - 1))
                                feedbackMessage = proofUpdatedMessage
                            }
                        )
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
                    Text(addLineLabel)
                }
                Button(onClick = { currentFormula = Formula(emptyList()) }) {
                    Text("Clear Input")
                }
                Button(
                    onClick = {
                        val result = ProofValidator.validate(proof)
                        if (!result.isValid) {
                            feedbackMessage = result.errorMessage ?: proofValidMessage
                        } else {
                            // Check if the last line matches the conclusion
                            if (proof.lines.lastOrNull()?.formula == problem.conclusion) {
                                feedbackMessage = proofCompleteMessage
                            } else {
                                feedbackMessage = proofIncompleteMessage
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(validateProofLabel)
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
                        feedbackMessage = proofUpdatedMessage
                    },
                    enabled = currentFormula.tiles.isNotEmpty()
                ) {
                    Text(addPremiseLabel)
                }
                Button(onClick = {
                    proof = Proof(emptyList())
                    currentFormula = Formula(emptyList())
                    feedbackMessage = proofClearedMessage
                }) {
                    Text(clearProofLabel)
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- FEEDBACK AREA ---
            Text(
                text = feedbackMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = if (feedbackMessage == proofCompleteMessage ||
                            feedbackMessage == proofValidMessage) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
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
