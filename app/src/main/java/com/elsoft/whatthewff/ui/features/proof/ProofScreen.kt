// File: ui/features/proof/ProofScreen.kt
// This file defines the UI for the proof construction screen.

package com.elsoft.whatthewff.ui.features.proof

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.elsoft.whatthewff.R
import com.elsoft.whatthewff.logic.*
import com.elsoft.whatthewff.ui.features.game.ConstructionArea
import com.elsoft.whatthewff.ui.features.game.SymbolPalette
import com.elsoft.whatthewff.ui.theme.WhatTheWFFTheme

import com.elsoft.whatthewff.logic.AvailableTiles.implies
import com.elsoft.whatthewff.logic.AvailableTiles.leftParen
import com.elsoft.whatthewff.logic.AvailableTiles.rightParen
//import com.elsoft.whatthewff.logic.AvailableTiles.iff
//import com.elsoft.whatthewff.logic.AvailableTiles.or
//import com.elsoft.whatthewff.logic.AvailableTiles.and
//import com.elsoft.whatthewff.logic.AvailableTiles.not

/**
 * Displays a single, formatted line of a proof.
 *
 * @param line The ProofLine to display.
 */
@Composable
fun ProofLineView(
    line: ProofLine,
    isSelected: Boolean,
    onLineClicked: () -> Unit,
    currentScale: Float,
    onDelete: () -> Unit
) {
    // Define your base font sizes (these could come from MaterialTheme or be custom)
    val baseNumberFontSize: TextUnit = MaterialTheme.typography.bodyLarge.fontSize
    val baseFormulaFontSize: TextUnit = MaterialTheme.typography.bodyLarge.fontSize
    val baseJustificationFontSize: TextUnit = MaterialTheme.typography.bodyMedium.fontSize

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onLineClicked)
            .then(
                if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                else Modifier
            )
            .padding(start = (line.depth * 24).dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vertical line to indicate sub-proof scope
        if (line.depth > 0) {
            Box(modifier =
                    Modifier.width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .padding(end = 4.dp))
        }
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
                // TODO: Create string resource for this
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(15.dp * currentScale) // The visible size of the icon itself.
            )
        }
    }
}

/**
 * A dialog to confirm the deletion of a proof line.
 */
@Composable
fun DeleteConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Confirm Deletion") },
        // TODO: Create string resource for this
        text = { Text("Are you sure you want to delete this line and all subsequent lines? This action cannot be undone.") },
        // TODO: Create string resource for this
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
                // TODO: Create string resource for this
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
                // TODO: Create string resource for this
            }
        }
    )
}

/**
 * A dialog for adding a new line to the proof by specifying its justification.
 *
 * @param onDismissRequest Called when the user wants to close the dialog.
 * @param onConfirm Called when the user confirms the new line, passing the justification.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLineDialog(
    preSelectedLines: Set<Int>,
    onDismissRequest: () -> Unit,
    onConfirm: (Justification) -> Unit
) {
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

    // Use LaunchedEffect to pre-fill the text field when the dialog opens with selections.
    LaunchedEffect(preSelectedLines) {
        if (preSelectedLines.isNotEmpty()) {
            lineRefs = preSelectedLines.sorted().joinToString(separator = ",")
        }
    }

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
 * A single action item in the expanding Floating Action Button (FAB) menu.
 */
@Composable
fun FabAction(
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Text(text,
                 modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                 style = MaterialTheme.typography.labelLarge)
        }
        Spacer(modifier = Modifier.width(12.dp))
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp),
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Icon(icon, contentDescription = text)
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
    val clearProofLabel = stringResource(id = R.string.clear_proof_label)
    val clearInputLabel = stringResource(id = R.string.clear_input_label)
    val goalLabel = stringResource(id = R.string.goal_label)
    val validateProofLabel = stringResource(id = R.string.validate_proof_label)

    var proof by remember { mutableStateOf(Proof(lines = initialLines)) }
    var currentFormula by remember { mutableStateOf(Formula(emptyList())) }
    var showAddLineDialog by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf(addLineMessage) }
    var selectedLines by remember { mutableStateOf(emptySet<Int>()) }
    var dialogPreSelectedLines by remember { mutableStateOf(emptySet<Int>()) }
    var currentDepth by remember { mutableStateOf(0) }
    var subproofStartLines by remember { mutableStateOf(listOf<Int>()) }
    var isFabMenuExpanded by remember { mutableStateOf(false) }
    var lineToDelete by remember { mutableStateOf<ProofLine?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val scale = remember { mutableStateOf(1f) }

    val zoomModifier = Modifier.pointerInput(Unit) {
        detectTransformGestures { _, _, zoom, _ ->
            scale.value = (scale.value * zoom).coerceIn(0.5f, 3f)  // limit scale range
        }
    }

    // --- DIALOG HANDLER ---
    if (showAddLineDialog) {
        AddLineDialog(
            preSelectedLines = dialogPreSelectedLines,
            onDismissRequest = { showAddLineDialog = false },
            onConfirm = { justification ->
                val newProofLine = ProofLine(
                    lineNumber = proof.lines.size + 1,
                    formula = currentFormula,
                    justification = justification,
                    depth = currentDepth
                )
                proof = Proof(proof.lines + newProofLine)
                currentFormula = Formula(emptyList()) // Clear input field
                selectedLines = emptySet() // Clear selection after adding a line
                showAddLineDialog = false // Close dialog
            }
        )
    }

    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            onDismissRequest = { showDeleteDialog = false },
            onConfirm = {
                lineToDelete?.let { line ->
                    proof = Proof(proof.lines.take(line.lineNumber - 1))
                    selectedLines = emptySet()
                    feedbackMessage = "Proof updated. Continue building."
                    // TODO: Create string resource for this
                    // This should also reset currentDepth if the deleted line was in a sub-proof.
                }
                showDeleteDialog = false
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
                        // TODO: Create string resource for this
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // Expanding menu of actions
                AnimatedVisibility(
                    visible = isFabMenuExpanded,
                    enter = fadeIn(animationSpec = tween(150)),
                    exit = fadeOut(animationSpec = tween(150))
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        FabAction(text = addPremiseLabel,
                                  icon = Icons.Default.AddCircle,
                                  enabled = currentFormula.tiles.isNotEmpty() && currentDepth == 0,
                                  onClick = {
                                        val newLine = ProofLine(proof.lines.size + 1, currentFormula, Justification.Premise, 0)
                                        proof = Proof(proof.lines + newLine)
                                        currentFormula = Formula(emptyList())
                                        isFabMenuExpanded = false
                                  })

                        FabAction(text = "Reiterate",
                                  icon = Icons.Default.Repeat,
                                  enabled = currentDepth > 0 && selectedLines.size == 1,
                                  onClick = {
                                        val lineToReiterateNum = selectedLines.first()
                                        val lineToReiterate = proof.lines.find { it.lineNumber == lineToReiterateNum }
                                        if (lineToReiterate != null && lineToReiterate.depth < currentDepth) {
                                            val newLine = ProofLine(
                                                lineNumber = proof.lines.size + 1,
                                                formula = lineToReiterate.formula,
                                                justification = Justification.Reiteration(lineToReiterateNum),
                                                depth = currentDepth
                                            )
                                            proof = Proof(proof.lines + newLine)
                                            selectedLines = emptySet()
                                            isFabMenuExpanded = false
                                        } else {
                                            feedbackMessage = "Can only reiterate a single selected line from an outer scope."
                                        }
                                  })

                        FabAction(text = "Start Sub-proof",
                                  // TODO: Create string resource for this
                                  icon = Icons.Default.LibraryAdd,
                                  enabled = currentFormula.tiles.isNotEmpty(),
                                  onClick = {
                                        val startLineNumber = proof.lines.size + 1
                                        val newLine = ProofLine(startLineNumber, currentFormula, Justification.Assumption, currentDepth + 1)
                                        proof = Proof(proof.lines + newLine)
                                        currentDepth++
                                        subproofStartLines = subproofStartLines + startLineNumber
                                        currentFormula = Formula(emptyList())
                                        isFabMenuExpanded = false
                                  })
                        FabAction(text = "End Sub-proof",
                                  // TODO: Create string resource for this
                                  icon = Icons.Default.LibraryAddCheck,
                                  enabled = currentDepth > 0,
                                  onClick = {
                                        val startLine = subproofStartLines.last()
                                        val endLine = proof.lines.size
                                        val assumptionFormula = proof.lines.find { it.lineNumber == startLine }?.formula
                                        val conclusionFormula = proof.lines.last().formula
                                        if (assumptionFormula != null) {
                                            val implication =
                                                Formula(listOf(
                                                        leftParen) +
                                                        assumptionFormula.tiles +
                                                        listOf(implies) +
                                                        conclusionFormula.tiles +
                                                        listOf(rightParen))
                                            val newLine = ProofLine(endLine + 1,
                                                                    implication,
                                                                    Justification.ImplicationIntroduction(startLine, endLine),
                                                                    currentDepth - 1)
                                            proof = Proof(proof.lines + newLine)
                                            currentDepth--
                                            subproofStartLines = subproofStartLines.dropLast(1)
                                        }
                                        isFabMenuExpanded = false
                                  })
                        FabAction(text = addLineLabel,
                                  icon = Icons.Default.Create,
                                  enabled = currentFormula.tiles.isNotEmpty(),
                                  onClick = {
                                        dialogPreSelectedLines = selectedLines
                                        showAddLineDialog = true
                                        isFabMenuExpanded = false
                                  })
                        FabAction(text = validateProofLabel,
                                  icon = Icons.Default.PlaylistAddCheckCircle,
                                  enabled = true,
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
                                        isFabMenuExpanded = false
                                  })
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                // Main FAB
                FloatingActionButton(onClick = { isFabMenuExpanded = !isFabMenuExpanded }) {
                    Icon(if (isFabMenuExpanded) Icons.Default.Close else Icons.Default.Add, contentDescription = "Actions")
                }
            }
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
                            isSelected = line.lineNumber in selectedLines,
                            onLineClicked = {
                                selectedLines = if (line.lineNumber in selectedLines) {
                                    selectedLines - line.lineNumber
                                } else {
                                    selectedLines + line.lineNumber
                                }
                            },
                            currentScale = scale.value,
                            onDelete = {
                                lineToDelete = line
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }

            // --- FORMULA CONSTRUCTION AREA ---
            SymbolPalette {
                    tile -> currentFormula = Formula(currentFormula.tiles + tile)
            }
            ConstructionArea(
                formula = currentFormula,
                onDeleteLast = {
                    if (currentFormula.tiles.isNotEmpty()) {
                        currentFormula = Formula(currentFormula.tiles.dropLast(1))
                    }
                }
            )
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
