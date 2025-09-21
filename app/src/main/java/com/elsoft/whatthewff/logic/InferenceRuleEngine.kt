// File: logic/InferenceRuleEngine.kt
// This new file is the central "brain" for all rules of inference.
// It provides a single source of truth for both the validator and the suggester.

package com.elsoft.whatthewff.logic

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
        val implications = premises.filter {
            val node = WffParser.parse(it)
            node is FormulaNode.BinaryOpNode &&
            (node as FormulaNode.BinaryOpNode).operator == AvailableTiles.implies
        }
        val conclusions = mutableListOf<Formula>()
        for (imp in implications) {
            val node = WffParser.parse(imp)
            val antecedent = treeToFormula((node as FormulaNode.BinaryOpNode).left)
            val consequent = treeToFormula((node as FormulaNode.BinaryOpNode).right)
            conclusions.add(fImplies(antecedent,fAnd(antecedent, consequent)))
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
        val disjunctions = premises.filter {
            val node = WffParser.parse(it)
            node is FormulaNode.BinaryOpNode &&
            (node as FormulaNode.BinaryOpNode).operator == AvailableTiles.and
            (node as FormulaNode.BinaryOpNode).left is FormulaNode.BinaryOpNode &&
            (node as FormulaNode.BinaryOpNode).right is FormulaNode.BinaryOpNode &&
            (((node as FormulaNode.BinaryOpNode).left) as FormulaNode.BinaryOpNode).operator == AvailableTiles.implies &&
            (((node as FormulaNode.BinaryOpNode).right) as FormulaNode.BinaryOpNode).operator == AvailableTiles.implies
        }

        val conclusions = mutableListOf<Formula>()

        for (disjunction in disjunctions) {
            val node = WffParser.parse(disjunction) as FormulaNode.BinaryOpNode
            val leftImpl = (node as FormulaNode.BinaryOpNode).left as FormulaNode.BinaryOpNode
            val rightImpl = (node as FormulaNode.BinaryOpNode).right as FormulaNode.BinaryOpNode
            val leftAntecedent = treeToFormula((leftImpl as FormulaNode.BinaryOpNode).left)
            val rightAntecedent = treeToFormula((rightImpl as FormulaNode.BinaryOpNode).left)
            val leftConsequent = treeToFormula((leftImpl as FormulaNode.BinaryOpNode).right)
            val rightConsequent = treeToFormula((rightImpl as FormulaNode.BinaryOpNode).right)
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
        val disjunctions = premises.filter {
            val node = WffParser.parse(it)
            node is FormulaNode.BinaryOpNode &&
            (node as FormulaNode.BinaryOpNode).operator == AvailableTiles.or
        }
        val conclusions = mutableListOf<Formula>()
        for (disjunction in disjunctions) {
            val disNode = WffParser.parse(disjunction) as FormulaNode.BinaryOpNode
            val disjunct1 = disNode.left
            val disjunct2 = disNode.right
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
        val implications = premises.filter {
            val node = WffParser.parse(it)
            node is FormulaNode.BinaryOpNode &&
            (node as FormulaNode.BinaryOpNode).operator == AvailableTiles.implies }
        val conclusions = mutableListOf<Formula>()
        if (implications.size < 2) return conclusions

        for (imp1 in implications) {
            for (imp2 in implications) {
                if (imp1 == imp2) continue
                val node1 = WffParser.parse(imp1) as FormulaNode.BinaryOpNode
                val node2 = WffParser.parse(imp2) as FormulaNode.BinaryOpNode
                if (node1.right == node2.left) {
                    conclusions.add(fImplies(treeToFormula(node1.left), treeToFormula(node2.right)))
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
        val implications = premises.filter {
            val node = WffParser.parse(it)
            node is FormulaNode.BinaryOpNode &&
            (node as FormulaNode.BinaryOpNode).operator == AvailableTiles.implies }
        val conclusions = mutableListOf<Formula>()
        for (imp in implications) {
            val node = WffParser.parse(imp) as FormulaNode.BinaryOpNode
            val antecedent = treeToFormula((node as FormulaNode.BinaryOpNode).left)
            if (premises.contains(antecedent)) {
                conclusions.add(treeToFormula((node as FormulaNode.BinaryOpNode).right))
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
        val implications = premises.filter {
            val node = WffParser.parse(it)
            node is FormulaNode.BinaryOpNode &&
            (node as FormulaNode.BinaryOpNode).operator == AvailableTiles.implies }
        val conclusions = mutableListOf<Formula>()
        for (imp in implications) {
            val node = WffParser.parse(imp) as FormulaNode.BinaryOpNode
            val consequent = treeToFormula((node as FormulaNode.BinaryOpNode).right)
            val negConsequent = fNeg(consequent)
            if (premises.contains(negConsequent)) {
                conclusions.add(fNeg(treeToFormula((node as FormulaNode.BinaryOpNode).left)))
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
        val allConjunctions = premises.filter {
            val node = WffParser.parse(it)
            node is FormulaNode.BinaryOpNode &&
            (node as FormulaNode.BinaryOpNode).operator == AvailableTiles.and
        }

        val conclusions = mutableListOf<Formula>()

        for (conjunction in allConjunctions) {
            val node = WffParser.parse(conjunction) as FormulaNode.BinaryOpNode
            val left = treeToFormula((node as FormulaNode.BinaryOpNode).left)
            val right = treeToFormula((node as FormulaNode.BinaryOpNode).right)
            conclusions.add(left)
            conclusions.add(right)
        }
        return conclusions.distinct()
    }
}
