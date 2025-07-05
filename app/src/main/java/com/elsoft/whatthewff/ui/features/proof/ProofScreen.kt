// File: ui/features/proof/ProofScreen.kt
// This file defines the UI for the proof construction screen.

package com.elsoft.whatthewff.ui.features.proof

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
fun ProofLineView(line: ProofLine, scale: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "${line.lineNumber}.", modifier = Modifier.width(30.dp),
             fontWeight = FontWeight.Bold, fontSize = 16.sp * scale)
        Text(text = line.formula.stringValue, modifier = Modifier.weight(1f), fontSize = 16.sp * scale)
        Text(text = line.justification.desc(), modifier = Modifier.width(100.dp),
             fontSize = 12.sp * scale, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A dialog for adding a new line to the proof by specifying its justification.
 *
 * @param onDismissRequest Called when the user wants to close the dialog.
 * @param onConfirm Called when the user confirms the new line, passing the justification.
 */
@Composable
fun AddLineDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (Justification) -> Unit
) {
    var selectedRule by remember { mutableStateOf(InferenceRule.MODUS_PONENS) }
    var lineRefs by remember { mutableStateOf("") } // e.g., "1,2"

    Dialog(onDismissRequest = onDismissRequest) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Add Justification", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                // TODO: In the future, this could be a dropdown or a list of all rules.
                Text("Rule: ${selectedRule.ruleName}", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = lineRefs,
                    onValueChange = { lineRefs = it.filter { char -> char.isDigit() || char == ',' } },
                    label = { Text("Reference Lines (e.g., 1,2)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val refList = lineRefs.split(',').mapNotNull { it.trim().toIntOrNull() }
                        if (refList.isNotEmpty()) {
                            onConfirm(Justification.Inference(selectedRule, refList))
                        }
                    }) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}


/**
 * The main screen for building and validating proofs.
 *
 * @param modifier The modifier to be applied to the layout.
 */
@Composable
fun ProofScreen(modifier: Modifier = Modifier) {
    // --- STATE MANAGEMENT ---
    val initialProof = Proof(
        lines = listOf(
            ProofLine(1, Formula(listOf(AvailableTiles.leftParen, AvailableTiles.p, AvailableTiles.implies, AvailableTiles.q, AvailableTiles.rightParen)), Justification.Premise),
            ProofLine(2, Formula(listOf(AvailableTiles.p)), Justification.Premise)
        )
    )
    var proof by remember { mutableStateOf(initialProof) }
    var currentFormula by remember { mutableStateOf(Formula(emptyList())) }
    var showAddLineDialog by remember { mutableStateOf(false) }
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // --- PROOF DISPLAY AREA ---
        Text("Proof:", style = MaterialTheme.typography.titleLarge)
        Card(modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .then(zoomModifier),
             elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
            LazyColumn(modifier = Modifier.padding(16.dp)) {
                items(proof.lines) { line ->
                    ProofLineView(line = line, scale = scale.value)
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
                onClick = { /* TODO: Add logic for validating the entire proof */ },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Validate Proof")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProofScreenPreview() {
    WhatTheWFFTheme {
        ProofScreen()
    }
}
