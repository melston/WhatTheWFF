package com.elsoft.whatthewff.ui.features.proof.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.elsoft.whatthewff.logic.Formula
import com.elsoft.whatthewff.logic.InferenceRule
import com.elsoft.whatthewff.logic.Justification
import com.elsoft.whatthewff.logic.Proof
import com.elsoft.whatthewff.logic.ProofLine
import com.elsoft.whatthewff.logic.ProofValidator
import com.elsoft.whatthewff.logic.ReplacementRule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLineDialog(
    currentProof: Proof,
    selectedProofLines: List<ProofLine>,
    currentFormula: Formula,
    currentDepth: Int,
    onDismiss: () -> Unit,
    onConfirm: (formula: Formula, justification: Justification) -> Unit
) {
    var tabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Inference", "Replacement")
    var selectedInferenceRule by remember { mutableStateOf(InferenceRule.MODUS_PONENS) }
    var selectedReplacementRule by remember { mutableStateOf(ReplacementRule.DOUBLE_NEGATION) }

    val isSuggestionMode = currentFormula.tiles.isEmpty() && selectedProofLines.isNotEmpty()
    var suggestions by remember { mutableStateOf<List<Suggestion>>(emptyList()) }
    var selectedSuggestion by remember { mutableStateOf<Suggestion?>(null) }
    val referenceLinesFromSelection = selectedProofLines.joinToString(",") { it.lineNumber.toString() }
    var referenceLinesInput by remember { mutableStateOf(referenceLinesFromSelection) }
    var validationError by remember { mutableStateOf<String?>(null) }

    // Calculate suggestions whenever the rule or selection changes in suggestion mode
    LaunchedEffect(isSuggestionMode, selectedInferenceRule, selectedProofLines) {
        if (isSuggestionMode) {
            suggestions = ProofSuggester.getSuggestions(
                rule = selectedInferenceRule,
                selectedLines = selectedProofLines
            )
            selectedSuggestion = null // Reset selection when suggestions change
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isSuggestionMode) "Select Derived Line" else "Add Derived Line") },
        text = {
            Column {
                TabRow(selectedTabIndex = tabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(text = { Text(title) },
                            selected = tabIndex == index,
                            onClick = { tabIndex = index }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                var isRuleDropdownExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = isRuleDropdownExpanded,
                    onExpandedChange = { isRuleDropdownExpanded = !isRuleDropdownExpanded }
                ) {
                    TextField(
                        value = if (tabIndex == 0) selectedInferenceRule.ruleName else selectedReplacementRule.ruleName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isRuleDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isRuleDropdownExpanded,
                        onDismissRequest = { isRuleDropdownExpanded = false }
                    ) {
                        if (tabIndex == 0) {
                            InferenceRule.entries.forEach { rule ->
                                DropdownMenuItem(text = { Text(rule.ruleName) },
                                                 onClick = {
                                                     selectedInferenceRule = rule
                                                     isRuleDropdownExpanded = false
                                                 }
                                )
                            }
                        } else {
                            ReplacementRule.entries.forEach { rule ->
                                DropdownMenuItem(text = { Text(rule.ruleName) },
                                                 onClick = {
                                                     selectedReplacementRule = rule
                                                     isRuleDropdownExpanded = false
                                                 }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = referenceLinesInput,
                    onValueChange = { referenceLinesInput = it },
                    label = { Text("Reference Lines (e.g., 1,2)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !isSuggestionMode
                )

                if (isSuggestionMode) {
                    Spacer(Modifier.height(16.dp))
                    Text("Suggestions:", style = MaterialTheme.typography.labelMedium)
                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline))) {
                        items(suggestions) { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = (suggestion == selectedSuggestion),
                                        onClick = { selectedSuggestion = suggestion },
                                        role = Role.RadioButton
                                    )
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = (suggestion == selectedSuggestion), onClick = { selectedSuggestion = suggestion })
                                Text(text = suggestion.formula.stringValue, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        if (suggestions.isEmpty()) {
                            item { Text("No suggestions for this rule and selection.", modifier = Modifier.padding(8.dp)) }
                        }                    }
                }

                if (validationError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(text = validationError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    validationError = null
                    val formulaToUse = if (isSuggestionMode) selectedSuggestion?.formula else currentFormula

                    if (formulaToUse == null) {
                        validationError = "Error: No formula provided or selected."
                        return@Button
                    }

                    val lineRefs = if (isSuggestionMode) {
                        selectedProofLines.map { it.lineNumber }
                    } else {
                        referenceLinesInput.split(',').mapNotNull { it.trim().toIntOrNull() }
                    }

                    val justification = if (tabIndex == 0) {
                        Justification.Inference(selectedInferenceRule, lineRefs)
                    } else {
                        Justification.Replacement(selectedReplacementRule, lineRefs.firstOrNull() ?: 0)
                    }

                    val tempLine = ProofLine(currentProof.lines.size + 1, formulaToUse, justification, currentDepth)
                    val tempProof = currentProof.copy(lines = currentProof.lines + tempLine)
                    val result = ProofValidator.validate(tempProof)

                    if (result.isValid) {
                        onConfirm(formulaToUse, justification)
                    } else {
                        validationError = "Error: ${result.errorMessage}"
                    }
                },
                enabled = !isSuggestionMode || selectedSuggestion != null // Enable confirm button
            ) { Text("Confirm") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

