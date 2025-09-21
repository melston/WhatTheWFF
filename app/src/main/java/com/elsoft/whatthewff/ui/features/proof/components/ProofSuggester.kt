package com.elsoft.whatthewff.ui.features.proof.components

import com.elsoft.whatthewff.logic.AvailableTiles
import com.elsoft.whatthewff.logic.AvailableTiles.and
import com.elsoft.whatthewff.logic.Formula
import com.elsoft.whatthewff.logic.FormulaNode
import com.elsoft.whatthewff.logic.ForwardRuleGenerators
import com.elsoft.whatthewff.logic.InferenceRule
import com.elsoft.whatthewff.logic.LogicTile
import com.elsoft.whatthewff.logic.ProofLine
import com.elsoft.whatthewff.logic.ReplacementRule
import com.elsoft.whatthewff.logic.RuleGenerators
import com.elsoft.whatthewff.logic.RuleGenerators.treeToFormula
import com.elsoft.whatthewff.logic.RuleReplacer
import com.elsoft.whatthewff.logic.WffParser
import kotlin.collections.plus

data class Suggestion(val formula: Formula)

object ProofSuggester {
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

    fun getInferenceSuggestions(
        rule: InferenceRule,
        selectedLines: List<ProofLine>
    ): List<Suggestion> {
        val selectedFormulas = selectedLines.map { it.formula }
        return when (rule) {
            InferenceRule.MODUS_PONENS -> {
                ForwardRuleGenerators.findAllModusPonensPairs(selectedFormulas).map {
                        (imp, _) ->
                    val impNode = WffParser.parse(imp) as FormulaNode.BinaryOpNode
                    val consequent = treeToFormula(impNode.right)
                    Suggestion(consequent)
                }
            }
            InferenceRule.MODUS_TOLLENS -> {
                ForwardRuleGenerators.findAllModusTollensPairs(selectedFormulas).map {
                        (imp, _) ->
                    val impNode = WffParser.parse(imp) as FormulaNode.BinaryOpNode
                    val negAnte =
                        RuleGenerators.fNeg(treeToFormula(impNode.left))
                    Suggestion(negAnte)
                }
            }
            InferenceRule.HYPOTHETICAL_SYLLOGISM -> {
                ForwardRuleGenerators.findAllHypotheticalSyllogismPairs(selectedFormulas).map {
                        (imp1, imp2, _) ->
                    val pNode = (WffParser.parse(imp1) as FormulaNode.BinaryOpNode).left
                    val rNode = (WffParser.parse(imp2) as FormulaNode.BinaryOpNode).right
                    val conclusion = RuleGenerators.fImplies(
                        treeToFormula(pNode),
                        treeToFormula(rNode)
                    )
                    Suggestion(conclusion)
                }

            }
            InferenceRule.DISJUNCTIVE_SYLLOGISM -> {
                ForwardRuleGenerators.findAllDisjunctiveSyllogismPairs(selectedFormulas).map { (disjunction, negation) ->
                    val disjNode = WffParser.parse(disjunction) as FormulaNode.BinaryOpNode
                    val pFormula = treeToFormula(disjNode.left)
                    val childFormula =
                        treeToFormula((WffParser.parse(negation) as FormulaNode.UnaryOpNode).child)
                    val conclusion = if (childFormula == pFormula) treeToFormula(disjNode.right) else pFormula
                    Suggestion(conclusion)
                }
            }
            InferenceRule.CONSTRUCTIVE_DILEMMA -> {
                ForwardRuleGenerators.findAllConstructiveDilemmaPairs(selectedFormulas).map { (imp1, imp2, disj) ->
                    val qNode = (WffParser.parse(imp1) as FormulaNode.BinaryOpNode).right
                    val sNode = (WffParser.parse(imp2) as FormulaNode.BinaryOpNode).right
                    val conclusion = smartOr(treeToFormula(qNode), treeToFormula(sNode))
                    Suggestion(conclusion)
                }
            }
            InferenceRule.ABSORPTION -> {
                ForwardRuleGenerators.findAllAbsorptionPairs(selectedFormulas)
                        .map { implication ->
                    val pNode = (WffParser.parse(implication) as FormulaNode.BinaryOpNode).left
                    val qNode = (WffParser.parse(implication) as FormulaNode.BinaryOpNode).right
                    val conclusion = RuleGenerators.fImplies(treeToFormula(pNode), treeToFormula(qNode))
                    Suggestion(conclusion)                }
            }
            InferenceRule.SIMPLIFICATION -> {
                selectedLines
                    .filter { WffParser.parse(it.formula) is FormulaNode.BinaryOpNode &&
                              (WffParser.parse(it.formula) as FormulaNode.BinaryOpNode).operator == and }
                    .flatMap { line ->
                        val node = WffParser.parse(line.formula) as FormulaNode.BinaryOpNode
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
                    selectedLines
                        .filter { it.lineNumber != p1.lineNumber }
                        .flatMap { p2 ->
                            listOf(
                                Suggestion(smartAnd(p1.formula, p2.formula)),
                                Suggestion(smartAnd(p2.formula, p1.formula))
                            )
                    }
                }
            }
        }.distinct()
    }

    fun getReplacementSuggestions(
        rule: ReplacementRule,
        selectedLine: ProofLine?
    ): List<Suggestion> {
        if (selectedLine == null) return emptyList()

        val rootNode = WffParser.parse(selectedLine.formula) ?: return emptyList()

        // Use the RuleReplacer to generate all possible outcomes
        val possibleTrees = RuleReplacer.apply(rule, rootNode)

        return possibleTrees.map { tree ->
            Suggestion(treeToFormula(tree))
        }
    }
}
