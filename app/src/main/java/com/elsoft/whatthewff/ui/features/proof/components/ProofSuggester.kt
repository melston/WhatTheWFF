package com.elsoft.whatthewff.ui.features.proof.components

import com.elsoft.whatthewff.logic.AvailableTiles
import com.elsoft.whatthewff.logic.Formula
import com.elsoft.whatthewff.logic.FormulaNode
import com.elsoft.whatthewff.logic.ForwardRuleGenerators
import com.elsoft.whatthewff.logic.InferenceRule
import com.elsoft.whatthewff.logic.LogicTile
import com.elsoft.whatthewff.logic.ProofLine
import com.elsoft.whatthewff.logic.RuleGenerators
import com.elsoft.whatthewff.logic.WffParser
import kotlin.collections.plus

public data class Suggestion(val formula: Formula)

public object ProofSuggester {
    // ... Suggester logic from previous versions ...
    private fun smartAnd(f1: Formula, f2: Formula): Formula {
        val f1Tiles = groupFormulaTiles(f1)
        val f2Tiles = groupFormulaTiles(f2)
        return Formula(f1Tiles + listOf(AvailableTiles.and) + f2Tiles)
    }

    private fun smartOr(f1: Formula, f2: Formula): Formula {
        val f1Tiles = groupFormulaTiles(f1)
        val f2Tiles = groupFormulaTiles(f2)
        return Formula(f1Tiles + listOf(AvailableTiles.or) + f2Tiles)
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
                ForwardRuleGenerators.findAllModusPonensPairs(selectedFormulas).map {
                        (imp, ante) ->
                    val impNode = WffParser.parse(imp) as FormulaNode.BinaryOpNode
                    val consequent = RuleGenerators.treeToFormula(impNode.right)
                    val impLine = selectedLines.first { it.formula == imp }.lineNumber
                    val anteLine = selectedLines.first { it.formula == ante }.lineNumber
                    Suggestion(consequent)
                }
            }
            InferenceRule.MODUS_TOLLENS -> {
                ForwardRuleGenerators.findAllModusTollensPairs(selectedFormulas).map {
                        (imp, negCons) ->
                    val impNode = WffParser.parse(imp) as FormulaNode.BinaryOpNode
                    val negAnte =
                        RuleGenerators.fNeg(RuleGenerators.treeToFormula(impNode.left))
                    val impLine = selectedLines.first { it.formula == imp }.lineNumber
                    val negConsLine = selectedLines.first { it.formula == negCons }.lineNumber
                    Suggestion(negAnte)
                }
            }
            InferenceRule.HYPOTHETICAL_SYLLOGISM -> {
                ForwardRuleGenerators.findAllHypotheticalSyllogismPairs(selectedFormulas).map {
                        (imp1, imp2, _) ->
                    val pNode = (WffParser.parse(imp1) as FormulaNode.BinaryOpNode).left
                    val rNode = (WffParser.parse(imp2) as FormulaNode.BinaryOpNode).right
                    val conclusion = RuleGenerators.fImplies(
                        RuleGenerators.treeToFormula(pNode),
                        RuleGenerators.treeToFormula(rNode)
                    )
                    val imp1Line = selectedLines.first { it.formula == imp1 }.lineNumber
                    val imp2Line = selectedLines.first { it.formula == imp2 }.lineNumber
                    Suggestion(conclusion)
                }

            }
            InferenceRule.DISJUNCTIVE_SYLLOGISM -> {
                ForwardRuleGenerators.findAllDisjunctiveSyllogismPairs(selectedFormulas).map {
                        (disjunction, negation) ->
                    val disjNode = WffParser.parse(disjunction) as FormulaNode.BinaryOpNode
                    val negNode = (WffParser.parse(negation) as FormulaNode.UnaryOpNode).child
                    val pFormula = RuleGenerators.treeToFormula(disjNode.left)
                    val conclusion = if (WffParser.parse(pFormula) == negNode) RuleGenerators.treeToFormula(
                        disjNode.right
                    ) else pFormula
                    val disjLine = selectedLines.first { it.formula == disjunction }.lineNumber
                    val negLine = selectedLines.first { it.formula == negation }.lineNumber
                    Suggestion(conclusion)
                }
            }
            InferenceRule.CONSTRUCTIVE_DILEMMA -> {
                ForwardRuleGenerators.findAllConstructiveDilemmaPairs(selectedFormulas)
                    .map { (imp1, imp2, disj) ->
                    val qNode = (WffParser.parse(imp1) as FormulaNode.BinaryOpNode).right
                    val sNode = (WffParser.parse(imp2) as FormulaNode.BinaryOpNode).right
                    val conclusion = smartOr(
                        RuleGenerators.treeToFormula(qNode),
                        RuleGenerators.treeToFormula(sNode)
                    )
                    val imp1Line = selectedLines.firstOrNull { it.formula == imp1 }?.lineNumber
                    val imp2Line = selectedLines.firstOrNull { it.formula == imp2 }?.lineNumber
                    val disjLine = selectedLines.firstOrNull { it.formula == disj }?.lineNumber
                    val conjPremise = selectedLines.firstOrNull { line ->
                        val node = WffParser.parse(line.formula)
                        if (node is FormulaNode.BinaryOpNode && node.operator == AvailableTiles.and) {
                            RuleGenerators.treeToFormula(node.left) == imp1 &&
                            RuleGenerators.treeToFormula(node.right) == imp2
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
                ForwardRuleGenerators.findAllAbsorptionPairs(selectedFormulas)
                    .map { implication ->
                    val impNode = WffParser.parse(implication) as FormulaNode.BinaryOpNode
                    val pNode = impNode.left
                    val qNode = impNode.right
                    val conclusion = RuleGenerators.fImplies(
                        RuleGenerators.treeToFormula(pNode),
                        smartAnd(
                            RuleGenerators.treeToFormula(pNode),
                            RuleGenerators.treeToFormula(qNode)
                        )
                    )
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
                        Suggestion(RuleGenerators.treeToFormula(node.left)),
                        Suggestion(RuleGenerators.treeToFormula(node.right))
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
