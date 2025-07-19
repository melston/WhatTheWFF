// File: logic/ForwardRuleGenerators.kt
// This file contains the logic for building complex formulas from simpler ones,
// which is Phase 1 of the problem generation process.

package com.elsoft.whatthewff.logic

/**
 * Defines the "shape" of a function that can take one or more existing formulas
 * and combine them to produce a new, more complex formula.
 */
typealias ForwardGenerationStrategy = (formulas: List<Formula>) -> Formula?

/**
 * This object contains the specific strategies for building complex goals.
 */
object ForwardRuleGenerators {

    /**
     * A strategy that takes two formulas (P, Q) and combines them into a conjunction (P ∧ Q).
     */
    val conjunction: ForwardGenerationStrategy = { formulas ->
        if (formulas.size < 2) null else {
            val p = formulas[0]
            val q = formulas[1]
            Formula(
                listOf(AvailableTiles.leftParen) + p.tiles + listOf(AvailableTiles.and) + q.tiles + listOf(AvailableTiles.rightParen)
            )
        }
    }

    /**
     * A strategy that takes two formulas (P, Q) and combines them into an implication (P → Q).
     */
    val implication: ForwardGenerationStrategy = { formulas ->
        if (formulas.size < 2) null else {
            val p = formulas[0]
            val q = formulas[1]
            Formula(
                listOf(AvailableTiles.leftParen) + p.tiles + listOf(AvailableTiles.implies) + q.tiles + listOf(AvailableTiles.rightParen)
            )
        }
    }

    /**
     * A strategy that takes one formula (P) and creates its negation (¬P).
     */
    val negation: ForwardGenerationStrategy = { formulas ->
        if (formulas.isEmpty()) null else {
            val p = formulas[0]
            Formula(listOf(AvailableTiles.not) + p.tiles)
        }
    }

    // A list of all available forward strategies for the generator to choose from.
    val allStrategies = listOf(conjunction, implication, negation)
}
