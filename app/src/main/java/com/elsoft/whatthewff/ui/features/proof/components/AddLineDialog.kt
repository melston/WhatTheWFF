package com.elsoft.whatthewff.ui.features.proof.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.elsoft.whatthewff.logic.AvailableTiles
import com.elsoft.whatthewff.logic.AvailableTiles.and
import com.elsoft.whatthewff.logic.Formula
import com.elsoft.whatthewff.logic.FormulaNode
import com.elsoft.whatthewff.logic.ForwardRuleGenerators
import com.elsoft.whatthewff.logic.ForwardRuleGenerators.fImplies
import com.elsoft.whatthewff.logic.ForwardRuleGenerators.fNeg
import com.elsoft.whatthewff.logic.ForwardRuleGenerators.fOr
import com.elsoft.whatthewff.logic.ForwardRuleGenerators.treeToFormula
import com.elsoft.whatthewff.logic.InferenceRule
import com.elsoft.whatthewff.logic.Justification
import com.elsoft.whatthewff.logic.LogicTile
import com.elsoft.whatthewff.logic.Proof
import com.elsoft.whatthewff.logic.ProofLine
import com.elsoft.whatthewff.logic.ReplacementRule
import com.elsoft.whatthewff.logic.WffParser
import kotlin.collections.plus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLineDialog(
    onDismiss: () -> Unit,
    onConfirm: (Justification, Formula) -> Unit,
    initialLines: Set<Int>,
    currentFormula: Formula,
    fullProof: Proof
) {
    val isSuggestionMode = currentFormula.tiles.isEmpty() && initialLines.isNotEmpty()

    var tabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Inference", "Replacement")
    var selectedInferenceRule by remember { mutableStateOf(InferenceRule.MODUS_PONENS) }
    var selectedReplacementRule by remember { mutableStateOf(ReplacementRule.DOUBLE_NEGATION) }
    var referenceLines by remember { mutableStateOf(initialLines.joinToString(",")) }

    var suggestions by remember { mutableStateOf<List<Suggestion>>(emptyList()) }
    var selectedSuggestion by remember { mutableStateOf<Suggestion?>(null) }

    // This effect calculates suggestions whenever the rule or selected lines change
    LaunchedEffect(tabIndex, selectedInferenceRule, selectedReplacementRule, initialLines) {
        if (isSuggestionMode) {
            val selectedProofLines = initialLines.map { fullProof.lines[it - 1] }
            suggestions = if (tabIndex == 0) {
                ProofSuggester.suggestInference(selectedInferenceRule, selectedProofLines, fullProof)
            } else {
                // Placeholder for replacement suggestions
                emptyList()
            }
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

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = referenceLines,
                    onValueChange = { referenceLines = it },
                    label = { Text("Reference Lines (e.g., 1,2)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !isSuggestionMode
                )

                if (isSuggestionMode) {
                    Spacer(Modifier.height(16.dp))
                    Text("Suggestions:", style = MaterialTheme.typography.titleSmall)
                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp).border(1.dp, MaterialTheme.colorScheme.outline)) {
                        items(suggestions) { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedSuggestion = suggestion }
                                    .background(if (suggestion == selectedSuggestion) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = suggestion == selectedSuggestion, onClick = { selectedSuggestion = suggestion })
                                Text(suggestion.formula.stringValue, fontFamily = FontFamily.Monospace)
                            }
                        }
                        if (suggestions.isEmpty()) {
                            item { Text("No suggestions for this rule and selection.", modifier = Modifier.padding(8.dp)) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val justification: Justification
                    val derivedFormula: Formula
                    if (isSuggestionMode) {
                        justification = if (tabIndex == 0) {
                            Justification.Inference(selectedInferenceRule, selectedSuggestion!!.sourceLines)
                        } else {
                            Justification.Replacement(selectedReplacementRule, selectedSuggestion!!.sourceLines.first())
                        }
                        derivedFormula = selectedSuggestion!!.formula
                    } else {
                        val lineRefs = referenceLines.split(',').mapNotNull { it.trim().toIntOrNull() }
                        justification = if (tabIndex == 0) {
                            Justification.Inference(selectedInferenceRule, lineRefs)
                        } else {
                            Justification.Replacement(selectedReplacementRule, lineRefs.firstOrNull() ?: 0)
                        }
                        derivedFormula = currentFormula
                    }
                    onConfirm(justification, derivedFormula)
                },
                enabled = if (isSuggestionMode) selectedSuggestion != null else true
            ) { Text("Confirm") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private data class Suggestion(val formula: Formula, val sourceLines: List<Int>)

private object ProofSuggester {
    private fun groupFormulaTiles(formula: Formula): List<LogicTile> {
        val tiles = if (WffParser.parse(formula) is FormulaNode.BinaryOpNode) {
            listOf(AvailableTiles.leftParen) + formula.tiles + listOf(AvailableTiles.rightParen)
        } else {
            formula.tiles
        }
        return tiles
    }

    // Helper to intelligently create a conjunction, adding parens if necessary
    private fun smartAnd(f1: Formula, f2: Formula): Formula {
        val f1Tiles = groupFormulaTiles(f1)
        val f2Tiles = groupFormulaTiles(f2)
        return Formula(f1Tiles + listOf(AvailableTiles.and) + f2Tiles)
    }

    // Helper to intelligently create a disjunction, adding parens if necessary
    private fun smartOr(f1: Formula, f2: Formula): Formula {
        val f1Tiles = groupFormulaTiles(f1)
        val f2Tiles = groupFormulaTiles(f2)
        return Formula(f1Tiles + listOf(AvailableTiles.or) + f2Tiles)
    }

    fun suggestInference(rule: InferenceRule, selectedLines: List<ProofLine>, fullProof: Proof): List<Suggestion> {
        val selectedFormulas = selectedLines.map { it.formula }
        return when (rule) {
            InferenceRule.MODUS_PONENS -> {
                ForwardRuleGenerators.findAllModusPonensPairs(selectedFormulas).map {
                            (imp, ante) ->
                    val impNode = WffParser.parse(imp) as FormulaNode.BinaryOpNode
                    val consequent = treeToFormula(impNode.right)
                    val impLine = selectedLines.first { it.formula == imp }.lineNumber
                    val anteLine = selectedLines.first { it.formula == ante }.lineNumber
                    Suggestion(consequent, listOf(impLine, anteLine))
                }
            }
            InferenceRule.MODUS_TOLLENS -> {
                ForwardRuleGenerators.findAllModusTollensPairs(selectedFormulas).map {
                            (imp, negCons) ->
                    val impNode = WffParser.parse(imp) as FormulaNode.BinaryOpNode
                    val negAnte = fNeg(treeToFormula(impNode.left))
                    val impLine = selectedLines.first { it.formula == imp }.lineNumber
                    val negConsLine = selectedLines.first { it.formula == negCons }.lineNumber
                    Suggestion(negAnte, listOf(impLine, negConsLine))
                }
            }
            InferenceRule.HYPOTHETICAL_SYLLOGISM -> {
                ForwardRuleGenerators.findAllHypotheticalSyllogismPairs(selectedFormulas).map {
                            (imp1, imp2, _) ->
                    val pNode = (WffParser.parse(imp1) as FormulaNode.BinaryOpNode).left
                    val rNode = (WffParser.parse(imp2) as FormulaNode.BinaryOpNode).right
                    val conclusion = fImplies(treeToFormula(pNode), treeToFormula(rNode))
                    val imp1Line = selectedLines.first { it.formula == imp1 }.lineNumber
                    val imp2Line = selectedLines.first { it.formula == imp2 }.lineNumber
                    Suggestion(conclusion, listOf(imp1Line, imp2Line))
                }

            }
            InferenceRule.DISJUNCTIVE_SYLLOGISM -> {
                ForwardRuleGenerators.findAllDisjunctiveSyllogismPairs(selectedFormulas).map {
                            (disjunction, negation) ->
                    val disjNode = WffParser.parse(disjunction) as FormulaNode.BinaryOpNode
                    val negNode = (WffParser.parse(negation) as FormulaNode.UnaryOpNode).child
                    val pFormula = treeToFormula(disjNode.left)
                    val conclusion = if (WffParser.parse(pFormula) == negNode) treeToFormula(disjNode.right) else pFormula
                    val disjLine = selectedLines.first { it.formula == disjunction }.lineNumber
                    val negLine = selectedLines.first { it.formula == negation }.lineNumber
                    Suggestion(conclusion, listOf(disjLine, negLine))
                }
            }
            InferenceRule.CONSTRUCTIVE_DILEMMA -> {
                ForwardRuleGenerators.findAllConstructiveDilemmaPairs(selectedFormulas).map {
                    (imp1, imp2, disj) ->
                    // Correctly deconstruct the two separate implications
                    // Given implications of (p → q) and (r → s) and a disjunction (p ∨ r),
                    // construct the conclusion (q ∨ s)
                    val qNode = (WffParser.parse(imp1) as FormulaNode.BinaryOpNode).right
                    val sNode = (WffParser.parse(imp2) as FormulaNode.BinaryOpNode).right
                    val conclusion = smartOr(treeToFormula(qNode), treeToFormula(sNode))

                    // Find the line numbers for all three premises
                    val imp1Line = selectedLines.firstOrNull { it.formula == imp1 }?.lineNumber
                    val imp2Line = selectedLines.firstOrNull { it.formula == imp2 }?.lineNumber
                    val disjLine = selectedLines.firstOrNull { it.formula == disj }?.lineNumber

                    // This handles the case where one of the implications came from a conjunction
                    val conjPremise = selectedLines.firstOrNull { line ->
                        val node = WffParser.parse(line.formula)

                        node is FormulaNode.BinaryOpNode &&
                        node.operator == and &&
                        treeToFormula(node.left) == imp1 &&
                        treeToFormula(node.right) == imp2
                    }

                    val sourceLines = if (conjPremise != null && disjLine != null) {
                        listOf(conjPremise.lineNumber, disjLine)
                    } else {
                        listOfNotNull(imp1Line, imp2Line, disjLine)
                    }
                    Suggestion(conclusion, sourceLines)
                }
            }
            InferenceRule.ABSORPTION -> {
                ForwardRuleGenerators.findAllAbsorptionPairs(selectedFormulas).map { implication ->
                    val impNode = WffParser.parse(implication) as FormulaNode.BinaryOpNode
                    val pNode = impNode.left
                    val qNode = impNode.right
                    val conclusion = fImplies(treeToFormula(pNode),
                                              smartAnd(treeToFormula(pNode),
                                              treeToFormula(qNode)))
                    val line = selectedLines.first { it.formula == implication }.lineNumber
                    Suggestion(conclusion, listOf(line))
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
                        Suggestion(treeToFormula(node.left), listOf(line)),
                        Suggestion(treeToFormula(node.right), listOf(line))
                    )
                }
            }
            InferenceRule.ADDITION -> {
                if (selectedLines.isEmpty()) return emptyList()
                val allKnownFormulas = fullProof.lines
                selectedLines.flatMap { p1 ->
                    allKnownFormulas.filter { p2 -> p1.lineNumber != p2.lineNumber }.flatMap { p2 ->
                        listOf(
                            Suggestion(smartOr(p1.formula, p2.formula), listOf(p1.lineNumber)),
                            Suggestion(smartOr(p2.formula, p1.formula), listOf(p1.lineNumber))
                        )
                    }
                }
            }
            InferenceRule.CONJUNCTION -> {
                if (selectedLines.size < 2) return emptyList()
                selectedLines.flatMap { p1 ->
                    selectedLines.filter { it.lineNumber != p1.lineNumber }.map { p2 ->
                        Suggestion(smartAnd(p1.formula, p2.formula), listOf(p1.lineNumber, p2.lineNumber))
                    }
                }
            }
        }.distinct()
    }
}