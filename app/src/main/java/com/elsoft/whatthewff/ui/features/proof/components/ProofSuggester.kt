package com.elsoft.whatthewff.ui.features.proof.components

import com.elsoft.whatthewff.logic.AvailableTiles
import com.elsoft.whatthewff.logic.AvailableTiles.and
import com.elsoft.whatthewff.logic.Formula
import com.elsoft.whatthewff.logic.FormulaNode
import com.elsoft.whatthewff.logic.ForwardRuleGenerators
import com.elsoft.whatthewff.logic.InferenceRule
import com.elsoft.whatthewff.logic.InferenceRuleEngine
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
        val selectedFormulas = selectedLines.map { it.formula }.toSet()

        // --- KEY CHANGE: Defer all logic to the central engine ---
        val conclusions = InferenceRuleEngine.getPossibleConclusions(rule, selectedFormulas)

        return conclusions.map { Suggestion(it) }
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
