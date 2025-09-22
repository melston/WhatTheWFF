// File: logic/InferenceRuleEngine.kt
// This new file is the central "brain" for all rules of inference.
// It provides a single source of truth for both the validator and the suggester.

package com.elsoft.whatthewff.logic

import androidx.compose.foundation.layout.add
import com.elsoft.whatthewff.logic.RuleGenerators.fImplies
import com.elsoft.whatthewff.logic.RuleGenerators.fNeg
import com.elsoft.whatthewff.logic.RuleGenerators.fOr
import com.elsoft.whatthewff.logic.RuleGenerators.fAnd
import com.elsoft.whatthewff.logic.RuleGenerators.treeToFormula

object InferenceRuleEngine {

    // --- Public-facing function for the Suggester ---
    fun getPossibleConclusions(rule: InferenceRule, premises: Set<Formula>): List<Formula> {
        return when (rule) {
            InferenceRule.ABSORPTION -> deriveAbsorption(premises)
            InferenceRule.ADDITION -> deriveAddition(premises)
            InferenceRule.CONJUNCTION -> deriveConjunction(premises)
            InferenceRule.CONSTRUCTIVE_DILEMMA -> deriveConstructiveDilemma(premises)
            InferenceRule.DISJUNCTIVE_SYLLOGISM -> deriveDisjunctiveSyllogism(premises)
            InferenceRule.HYPOTHETICAL_SYLLOGISM -> deriveHypotheticalSyllogism(premises)
            InferenceRule.MODUS_PONENS -> deriveModusPonens(premises)
            InferenceRule.MODUS_TOLLENS -> deriveModusTollens(premises)
            InferenceRule.SIMPLIFICATION -> deriveSimplification(premises)
            // ... add other rules here as they are implemented ...
        }
    }

    // --- Public-facing function for the Validator ---
    fun isValidInference(rule: InferenceRule, premises: Set<Formula>, conclusion: Formula): Boolean {
        // A simple way to validate is to see if the conclusion is in the list of possible derivations.
        return conclusion in getPossibleConclusions(rule, premises)
    }

    // --- Core Logic Implementations for each rule ---

    /**
     * Absorption: Absorption: (P → Q) |- P → (P ∧ Q)
     *
     * For all implications in the set of premises, construct a new implication with
     * the antecedent of the implication being the antecedent of the original implication
     * and the consequent of the new implication being the conjunction of the antecedent
     * and the consequent of the original implication.
     * @param premises The set of premises to use in the derivation.
     * @return A list of all possible conclusions derived from the premises.
     */
    private fun deriveAbsorption(premises: Set<Formula>): List<Formula> {
        val conclusions = mutableListOf<Formula>()

        premises
            .mapNotNull { formula ->
                val node = WffParser.parse(formula)
                if (node is FormulaNode.BinaryOpNode && node.operator == AvailableTiles.implies) {
                    node
                } else {
                    null
                }
            }
            .forEach { node ->
                val antecedent = treeToFormula(node.left)
                val consequent = treeToFormula(node.right)
                conclusions.add(fImplies(antecedent, fAnd(antecedent, consequent)))
            }
        return conclusions.distinct()
    }

    /**
     * Addition: P |- P ∨ Q
     *
     * Construct new disjunctions consisting of all unique pairs of the provided
     * premises
     * @param premises The set of premises to use in the derivation.
     * @return A list of all possible conclusions derived from the premises.
     */
    private fun deriveAddition(premises: Set<Formula>): List<Formula> {
        val conclusions = mutableListOf<Formula>()
        for (p in premises) {
            for (q in premises) {
                if (p != q) {
                    conclusions.add(fOr(p, q))
                    conclusions.add(fOr(q, p))
                }
            }
        }
        return conclusions.distinct()
    }

    /**
     * Conjunction: P, Q |- P ∧ Q
     *
     * Construct new conjunctions consisting of all unique pairs of the provided
     * premises
     * @param premises The set of premises to use in the derivation.
     * @return A list of all possible conclusions derived from the premises.
     */
    private fun deriveConjunction(premises: Set<Formula>): List<Formula> {
        val conclusions = mutableListOf<Formula>()
        for (p in premises) {
            for (q in premises) {
                if (p != q) {
                    conclusions.add(fAnd(p, q))
                    conclusions.add(fAnd(q, p))
                }
            }
        }
        return conclusions.distinct()
    }

    /**
     * Constructive Dilemma: (P → Q) ∧ (R → S), P ∨ R |- (Q ∨ S)
     *
     * Find all conjunctions of implications in the set of premises.  If the disjunction
     * of the antecedents of both implications are also in the set of premises, then
     * construct a new disjunction with the consequents of both implications.
     */
    private fun deriveConstructiveDilemma(premises: Set<Formula>): List<Formula> {
        val conclusions = mutableListOf<Formula>()

        premises
            .mapNotNull { formula ->
                val node = WffParser.parse(formula)
                if (node is FormulaNode.BinaryOpNode && node.operator == AvailableTiles.and) {
                    var leftNode = node.left
                    var rightNode = node.right
                    if (leftNode is FormulaNode.BinaryOpNode && leftNode.operator == AvailableTiles.implies &&
                        rightNode is FormulaNode.BinaryOpNode && rightNode.operator == AvailableTiles.implies) {
                        leftNode to rightNode
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            .forEach { (leftNode, rightNode) ->
                val leftAntecedent = treeToFormula(leftNode.left)
                val rightAntecedent = treeToFormula(rightNode.left)
                val leftConsequent = treeToFormula(leftNode.right)
                val rightConsequent = treeToFormula(rightNode.right)
                if (premises.contains(fOr(leftAntecedent, rightAntecedent)) ||
                    premises.contains(fOr(rightAntecedent, leftAntecedent))) {
                    conclusions.add(fOr(leftConsequent, rightConsequent))
                    conclusions.add(fOr(rightConsequent, leftConsequent))
                }

            }
        return conclusions.distinct()
    }

    /**
     * Disjunctive Syllogism: (P ∨ Q), ¬P |- Q
     *
     * For all the disjunctions in the set of premises, if the negation of one of
     * the disjuncts is in the set of remaining premises, then add the consequent to
     * the return value.
     * @param premises The set of premises to use in the derivation.
     * @return A list of all possible conclusions derived from the premises.
     */
    private fun deriveDisjunctiveSyllogism(premises: Set<Formula>): List<Formula> {
        val conclusions = mutableListOf<Formula>()
        premises
            .mapNotNull { formula ->
                val node = WffParser.parse(formula)
                if (node is FormulaNode.BinaryOpNode && node.operator == AvailableTiles.or) {
                    node
                } else {
                    null
                }
            }
            .forEach { node ->
                val disjunct1 = node.left
                val disjunct2 = node.right
                if (premises.contains(fNeg(treeToFormula(disjunct1)))) {
                    conclusions.add(treeToFormula(disjunct2))
                }
                if (premises.contains(fNeg(treeToFormula(disjunct2)))) {
                    conclusions.add(treeToFormula(disjunct1))
                }
            }
        return conclusions.distinct()
    }

    /**
     * Hypothetical Syllogism: (P → Q), (Q → R) |- P → R
     *
     * For all pairs of implications in the set of premises where the two consequent
     * of the first implication is the same as the antecedent of the second, construct a
     * new implication with the antecedent of the first implication and the consequent
     * of the second implication.
     * @param premises The set of premises to use in the derivation.
     * @return A list of all possible conclusions derived from the premises.    */
    private fun deriveHypotheticalSyllogism(premises: Set<Formula>): List<Formula> {
        val conclusions = mutableListOf<Formula>()
        val implications = premises
            .mapNotNull { formula ->
                // Try to parse, return null if not a BinaryOpNode or not an implication
                val node = WffParser.parse(formula)
                if (node is FormulaNode.BinaryOpNode && node.operator == AvailableTiles.implies) {
                    // Keep the original formula along with its parsed node for easier access
                    // Kotlin knows that node is a BinaryOpNode so the type of the pair is
                    // Pair<Formula, FormulaNode.BinaryOpNode>
                    formula to node
                } else {
                    // In this case, mapNotNull will filter out the result automatically
                    null
                }
            }
        if (implications.size < 2) return conclusions

        // implications now has a list of pairs of formulas and their parsed nodes
        for (i in implications.indices) {
            for (j in (i + 1) until implications.size) { // Ensures each pair is visited once
                val (_, node1) = implications[i] // Destructuring for node1
                val (_, node2) = implications[j] // Destructuring for node2

                // Check A->B, B->C
                if (node1.right == node2.left) {
                    conclusions.add(
                        fImplies(treeToFormula(node1.left),
                                 treeToFormula(node2.right)))
                }
                // Check B->C, A->B (if order matters for the input implications, or if they are different implications)
                if (node2.right == node1.left) {
                    conclusions.add(
                        fImplies(treeToFormula(node2.left),
                                 treeToFormula(node1.right)))
                }
            }
        }
        return conclusions.distinct()
    }

    /**
     * Modus Ponens: (P → Q), P |- Q
     *
     * For all implications in the set of premises, if any of the remaining premises
     * have the antecedent, then add the consequent to the return value.
     * @param premises The set of premises to use in the derivation.
     * @return A list of all possible conclusions derived from the premises.
     */
    private fun deriveModusPonens(premises: Set<Formula>): List<Formula> {
        val conclusions = mutableListOf<Formula>()
        premises
            .mapNotNull { formula ->
                val node = WffParser.parse(formula)
                if (node is FormulaNode.BinaryOpNode && node.operator == AvailableTiles.implies) {
                    node
                } else {
                    null
                }
            }
            .forEach { node ->
                val antecedent = treeToFormula(node.left)
                val consequent = treeToFormula(node.right)
                if (premises.contains(antecedent)) {
                    conclusions.add(consequent)
                }
            }
        return conclusions.distinct()
    }

    /**
     * Modus Tollens: (P → Q), ¬Q |- ¬P
     *
     * For all implications in the set of premises, if any of the remaining premises
     * have the negation of the consequent, then add the negation of the antecedent to the
     * return value.
     * @param premises The set of premises to use in the derivation.
     * @return A list of all possible conclusions derived from the premises.
     */
    private fun deriveModusTollens(premises: Set<Formula>): List<Formula> {
        val conclusions = mutableListOf<Formula>()
        premises
            .mapNotNull { formula ->
                val node = WffParser.parse(formula)
                if (node is FormulaNode.BinaryOpNode && node.operator == AvailableTiles.implies) {
                    node
                } else {
                    null
                }
            }
            .forEach { node ->
                val consequent = treeToFormula(node.right)
                val negConsequent = fNeg(consequent)
                if (premises.contains(negConsequent)) {
                    conclusions.add(fNeg(treeToFormula(node.left)))
                }
            }
        return conclusions.distinct()
    }

    /**
     * Simplification: (P ∧ Q) |- P
     *
     * For all conjunctions in the set of premises, add the two conjunts to the
     * set of returned values.
     * @param premises The set of premises to use in the derivation.
     * @return A list of all possible conclusions derived from the premises.
     */
    private fun deriveSimplification(premises: Set<Formula>): List<Formula> {
        val conclusions = mutableListOf<Formula>()
        premises
            .mapNotNull { formula ->
                val node = WffParser.parse(formula)
                if (node is FormulaNode.BinaryOpNode && node.operator == AvailableTiles.and) {
                    node
                } else {
                    null
                }
            }
            .forEach { node ->
                val left = treeToFormula(node.left)
                val right = treeToFormula(node.right)
                conclusions.add(left)
                conclusions.add(right)
            }
        return conclusions.distinct()
    }
}
