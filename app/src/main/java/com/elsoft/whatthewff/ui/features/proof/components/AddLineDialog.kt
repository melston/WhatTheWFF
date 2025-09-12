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
import com.elsoft.whatthewff.logic.AvailableTiles
import com.elsoft.whatthewff.logic.AvailableTiles.and
import com.elsoft.whatthewff.logic.AvailableTiles.or
import com.elsoft.whatthewff.logic.Formula
import com.elsoft.whatthewff.logic.FormulaNode
import com.elsoft.whatthewff.logic.ForwardRuleGenerators.findAllAbsorptionPairs
import com.elsoft.whatthewff.logic.ForwardRuleGenerators.findAllConstructiveDilemmaPairs
import com.elsoft.whatthewff.logic.ForwardRuleGenerators.findAllDisjunctiveSyllogismPairs
import com.elsoft.whatthewff.logic.ForwardRuleGenerators.findAllHypotheticalSyllogismPairs
import com.elsoft.whatthewff.logic.ForwardRuleGenerators.findAllModusPonensPairs
import com.elsoft.whatthewff.logic.ForwardRuleGenerators.findAllModusTollensPairs
import com.elsoft.whatthewff.logic.RuleGenerators.fImplies
import com.elsoft.whatthewff.logic.RuleGenerators.fNeg
import com.elsoft.whatthewff.logic.RuleGenerators.treeToFormula
import com.elsoft.whatthewff.logic.InferenceRule
import com.elsoft.whatthewff.logic.Justification
import com.elsoft.whatthewff.logic.LogicTile
import com.elsoft.whatthewff.logic.Proof
import com.elsoft.whatthewff.logic.ProofLine
import com.elsoft.whatthewff.logic.ProofValidator
import com.elsoft.whatthewff.logic.ReplacementRule
import com.elsoft.whatthewff.logic.RuleGenerators
import com.elsoft.whatthewff.logic.WffParser

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

private data class Suggestion(val formula: Formula)

private object ProofSuggester {
    // ... Suggester logic from previous versions ...
    private fun smartAnd(f1: Formula, f2: Formula): Formula {
        val f1Tiles = groupFormulaTiles(f1)
        val f2Tiles = groupFormulaTiles(f2)
        return Formula(f1Tiles + listOf(and) + f2Tiles)
    }

    private fun smartOr(f1: Formula, f2: Formula): Formula {
        val f1Tiles = groupFormulaTiles(f1)
        val f2Tiles = groupFormulaTiles(f2)
        return Formula(f1Tiles + listOf(or) + f2Tiles)
    }

    private fun groupFormulaTiles(formula: Formula): List<LogicTile> {
        return if (WffParser.parse(formula) is FormulaNode.BinaryOpNode) {
            listOf(AvailableTiles.leftParen) + formula.tiles + listOf(AvailableTiles.rightParen)
        } else {
            formula.tiles
        }
    }

    fun getSuggestions(
        rule: InferenceRule,
        selectedLines: List<ProofLine>
    ): List<Suggestion> {
        val selectedFormulas = selectedLines.map { it.formula }
        return when (rule) {
            InferenceRule.MODUS_PONENS -> {
                findAllModusPonensPairs(selectedFormulas).map {
                        (imp, ante) ->
                    val impNode = WffParser.parse(imp) as FormulaNode.BinaryOpNode
                    val consequent = treeToFormula(impNode.right)
                    val impLine = selectedLines.first { it.formula == imp }.lineNumber
                    val anteLine = selectedLines.first { it.formula == ante }.lineNumber
                    Suggestion(consequent)
                }
            }
            InferenceRule.MODUS_TOLLENS -> {
                findAllModusTollensPairs(selectedFormulas).map {
                        (imp, negCons) ->
                    val impNode = WffParser.parse(imp) as FormulaNode.BinaryOpNode
                    val negAnte = fNeg(treeToFormula(impNode.left))
                    val impLine = selectedLines.first { it.formula == imp }.lineNumber
                    val negConsLine = selectedLines.first { it.formula == negCons }.lineNumber
                    Suggestion(negAnte)
                }
            }
            InferenceRule.HYPOTHETICAL_SYLLOGISM -> {
                findAllHypotheticalSyllogismPairs(selectedFormulas).map {
                        (imp1, imp2, _) ->
                    val pNode = (WffParser.parse(imp1) as FormulaNode.BinaryOpNode).left
                    val rNode = (WffParser.parse(imp2) as FormulaNode.BinaryOpNode).right
                    val conclusion = fImplies(treeToFormula(pNode), treeToFormula(rNode))
                    val imp1Line = selectedLines.first { it.formula == imp1 }.lineNumber
                    val imp2Line = selectedLines.first { it.formula == imp2 }.lineNumber
                    Suggestion(conclusion)
                }

            }
            InferenceRule.DISJUNCTIVE_SYLLOGISM -> {
                findAllDisjunctiveSyllogismPairs(selectedFormulas).map {
                        (disjunction, negation) ->
                    val disjNode = WffParser.parse(disjunction) as FormulaNode.BinaryOpNode
                    val negNode = (WffParser.parse(negation) as FormulaNode.UnaryOpNode).child
                    val pFormula = treeToFormula(disjNode.left)
                    val conclusion = if (WffParser.parse(pFormula) == negNode) treeToFormula(disjNode.right) else pFormula
                    val disjLine = selectedLines.first { it.formula == disjunction }.lineNumber
                    val negLine = selectedLines.first { it.formula == negation }.lineNumber
                    Suggestion(conclusion)
                }
            }
            InferenceRule.CONSTRUCTIVE_DILEMMA -> {
                findAllConstructiveDilemmaPairs(selectedFormulas).map { (imp1, imp2, disj) ->
                    val qNode = (WffParser.parse(imp1) as FormulaNode.BinaryOpNode).right
                    val sNode = (WffParser.parse(imp2) as FormulaNode.BinaryOpNode).right
                    val conclusion = smartOr(treeToFormula(qNode), treeToFormula(sNode))
                    val imp1Line = selectedLines.firstOrNull { it.formula == imp1 }?.lineNumber
                    val imp2Line = selectedLines.firstOrNull { it.formula == imp2 }?.lineNumber
                    val disjLine = selectedLines.firstOrNull { it.formula == disj }?.lineNumber
                    val conjPremise = selectedLines.firstOrNull { line ->
                        val node = WffParser.parse(line.formula)
                        if (node is FormulaNode.BinaryOpNode && node.operator == and) {
                            treeToFormula(node.left) == imp1 &&
                                    treeToFormula(node.right) == imp2
                        } else {
                            false
                        }
                    }
                    val sourceLines = if (conjPremise != null && disjLine != null) {
                        listOf(conjPremise.lineNumber, disjLine)
                    } else {
                        listOfNotNull(imp1Line, imp2Line, disjLine)
                    }
                    Suggestion(conclusion)
                }
            }
            InferenceRule.ABSORPTION -> {
                findAllAbsorptionPairs(selectedFormulas).map { implication ->
                    val impNode = WffParser.parse(implication) as FormulaNode.BinaryOpNode
                    val pNode = impNode.left
                    val qNode = impNode.right
                    val conclusion = fImplies(treeToFormula(pNode),
                                              smartAnd(treeToFormula(pNode),
                                                       treeToFormula(qNode)))
                    val line = selectedLines.first { it.formula == implication }.lineNumber
                    Suggestion(conclusion)
                }

            }
            InferenceRule.SIMPLIFICATION -> {
                val conjunctions = selectedFormulas.filter {
                    WffParser.parse(it) is FormulaNode.BinaryOpNode &&
                            (WffParser.parse(it) as FormulaNode.BinaryOpNode).operator == AvailableTiles.and }
                conjunctions.flatMap { conj ->
                    val node = WffParser.parse(conj) as FormulaNode.BinaryOpNode
                    val line = selectedLines.first { it.formula == conj }.lineNumber
                    listOf(
                        Suggestion(treeToFormula(node.left)),
                        Suggestion(treeToFormula(node.right))
                    )
                }
            }
            InferenceRule.ADDITION -> {
                if (selectedLines.isEmpty()) return emptyList()
                selectedLines.flatMap { p1 ->
                    selectedLines.filter { p2 -> p1 != p2 }.flatMap { p2 ->
                        listOf(
                            Suggestion(smartOr(p1.formula, p2.formula)),
                            Suggestion(smartOr(p2.formula, p1.formula))
                        )
                    }
                }
            }
            InferenceRule.CONJUNCTION -> {
                if (selectedLines.size < 2) return emptyList()
                selectedLines.flatMap { p1 ->
                    selectedLines.filter { it.lineNumber != p1.lineNumber }.map { p2 ->
                        Suggestion(smartAnd(p1.formula, p2.formula))
                    }
                }
            }
            else -> emptyList() // Placeholder for other rules
        }.distinct()
    }
}
