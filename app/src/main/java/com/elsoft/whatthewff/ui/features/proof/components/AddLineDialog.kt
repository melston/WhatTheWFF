package com.elsoft.whatthewff.ui.features.proof.components

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.elsoft.whatthewff.logic.Formula
import com.elsoft.whatthewff.logic.ForwardRuleGenerators
import com.elsoft.whatthewff.logic.InferenceRule
import com.elsoft.whatthewff.logic.Justification
import com.elsoft.whatthewff.logic.ProofLine
import com.elsoft.whatthewff.logic.ReplacementRule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLineDialog(
    onDismiss: () -> Unit,
    onConfirm: (Justification, Formula) -> Unit,
    initialLines: String,
    isSuggestionMode: Boolean,
    allProofLines: List<ProofLine>,
    constructedFormula: Formula
) {
    var tabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Inference", "Replacement")
    var selectedInferenceRule by remember { mutableStateOf(InferenceRule.MODUS_PONENS) }
    var selectedReplacementRule by remember { mutableStateOf(ReplacementRule.DOUBLE_NEGATION) }
    var referenceLines by remember { mutableStateOf(initialLines) }

    var suggestions by remember { mutableStateOf<List<Formula>>(emptyList()) }
    var selectedSuggestion by remember { mutableStateOf<Formula?>(null) }

    LaunchedEffect(selectedInferenceRule, referenceLines, tabIndex) {
        if (isSuggestionMode && tabIndex == 0) {
            val lineRefs = referenceLines.split(',').mapNotNull { it.trim().toIntOrNull() }
            val selectedPremises =
                lineRefs.mapNotNull { lineNum -> allProofLines.find { it.lineNumber == lineNum }?.formula }
            suggestions = ProofSuggester.suggestInference(selectedInferenceRule, selectedPremises)
            selectedSuggestion = null // Reset selection when rule changes
        } else {
            suggestions = emptyList()
        }
    }


    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Derived Line") },
        text = {
            Column {
                TabRow(selectedTabIndex = tabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            text = { Text(title) },
                            selected = tabIndex == index,
                            onClick = { tabIndex = index }
                        )
                    }
                }
                Spacer(Modifier.Companion.height(16.dp))

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
                        modifier = Modifier.Companion.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isRuleDropdownExpanded,
                        onDismissRequest = { isRuleDropdownExpanded = false }
                    ) {
                        if (tabIndex == 0) {
                            InferenceRule.entries.forEach { rule ->
                                DropdownMenuItem(
                                    text = { Text(rule.ruleName) },
                                    onClick = {
                                        selectedInferenceRule = rule
                                        isRuleDropdownExpanded = false
                                    }
                                )
                            }
                        } else {
                            ReplacementRule.entries.forEach { rule ->
                                DropdownMenuItem(
                                    text = { Text(rule.ruleName) },
                                    onClick = {
                                        selectedReplacementRule = rule
                                        isRuleDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.Companion.height(8.dp))
                OutlinedTextField(
                    value = referenceLines,
                    onValueChange = { referenceLines = it },
                    label = { Text("Reference Lines (e.g., 1,2)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Companion.Number)
                )

                if (isSuggestionMode && suggestions.isNotEmpty()) {
                    Spacer(Modifier.Companion.height(16.dp))
                    Text("Suggestions:", style = MaterialTheme.typography.titleSmall)
                    LazyColumn(
                        modifier = Modifier.Companion.heightIn(max = 150.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        items(suggestions) { suggestion ->
                            Row(
                                modifier = Modifier.Companion
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = (suggestion == selectedSuggestion),
                                        onClick = { selectedSuggestion = suggestion }
                                    )
                                    .padding(8.dp),
                                verticalAlignment = Alignment.Companion.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (suggestion == selectedSuggestion),
                                    onClick = { selectedSuggestion = suggestion }
                                )
                                Text(
                                    text = suggestion.stringValue,
                                    modifier = Modifier.Companion.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val formulaToAdd =
                        if (isSuggestionMode) selectedSuggestion else constructedFormula
                    if (formulaToAdd != null) {
                        val lineRefs =
                            referenceLines.split(',').mapNotNull { it.trim().toIntOrNull() }
                        val justification = if (tabIndex == 0) {
                            Justification.Inference(selectedInferenceRule, lineRefs)
                        } else {
                            Justification.Replacement(
                                selectedReplacementRule,
                                lineRefs.firstOrNull() ?: 0
                            )
                        }
                        onConfirm(justification, formulaToAdd)
                    }
                },
                enabled = if (isSuggestionMode) selectedSuggestion != null else constructedFormula.tiles.isNotEmpty()
            ) { Text("Confirm") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private object ProofSuggester {
    fun suggestInference(rule: InferenceRule, premises: List<Formula>): List<Formula> {
        val forwardRule = when (rule) {
            InferenceRule.MODUS_PONENS -> ForwardRuleGenerators.modusPonens
            InferenceRule.MODUS_TOLLENS -> ForwardRuleGenerators.modusTollens
            InferenceRule.HYPOTHETICAL_SYLLOGISM -> ForwardRuleGenerators.hypotheticalSyllogism
            InferenceRule.DISJUNCTIVE_SYLLOGISM -> ForwardRuleGenerators.disjunctiveSyllogism
            InferenceRule.CONSTRUCTIVE_DILEMMA -> ForwardRuleGenerators.constructiveDilemma
            InferenceRule.ABSORPTION -> ForwardRuleGenerators.absorption
            InferenceRule.SIMPLIFICATION -> ForwardRuleGenerators.simplification
            InferenceRule.ADDITION -> ForwardRuleGenerators.addition
            InferenceRule.CONJUNCTION -> ForwardRuleGenerators.conjunction
        }
        return forwardRule.generate(premises)?.map { it.formula } ?: emptyList()
    }
}