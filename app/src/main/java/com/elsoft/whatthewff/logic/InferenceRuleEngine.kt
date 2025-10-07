// File: logic/InferenceRuleEngine.kt
// This new file is the central "brain" for all rules of inference.
// It provides a single source of truth for both the validator and the suggester.

package com.elsoft.whatthewff.logic

import com.elsoft.whatthewff.logic.RuleGenerators.fAnd
import com.elsoft.whatthewff.logic.RuleGenerators.fImplies
import com.elsoft.whatthewff.logic.RuleGenerators.fNeg
import com.elsoft.whatthewff.logic.RuleGenerators.fOr
import com.elsoft.whatthewff.logic.RuleGenerators.treeToFormula

/**
 * This class represents the application of a rule to a set of premises, generating
 * a conclusion.  It also has a list of childApplications, which is used to trace
 * the full proof tree.
 *
 * Applications instances returned by the InferenceRuleEngine will have an empty
 * chileApplications list.
 */
data class Application(val conclusion: Formula,
                       val rule: InferenceRule,
                       val premises: List<Formula>,
                       val childApplications: List<Application> // Added to trace the full proof tree
) {}

object InferenceRuleEngine {

    // --- Public-facing function for the problem generator ---
    fun getPossibleApplications(rule: InferenceRule, premises: List<Formula>): List<Application> {
        return when (rule) {
            InferenceRule.ASSUMPTION -> emptyList()
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

    // --- Public-facing function for the Suggester ---
    fun getPossibleConclusions(rule: InferenceRule, premises: List<Formula>): List<Formula> {
        return getPossibleApplications(rule, premises).map { it.conclusion }
    }

    // --- Public-facing function for the Validator ---
    fun isValidInference(rule: InferenceRule, premises: List<Formula>, conclusion: Formula): Boolean {
        // A simple way to validate is to see if the conclusion's logical structure
        // is present in the list of possible derived structures.
        val poss = getPossibleConclusions(rule, premises)
        return poss
            .any { it.normalize() == conclusion.normalize() }
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
    private fun deriveAbsorption(premises: List<Formula>): List<Application> {
        val conclusions = mutableListOf<Application>()

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
                conclusions.add(
                    Application(
                        fImplies(antecedent, fAnd(antecedent, consequent)),
                        InferenceRule.ABSORPTION,
                        listOf(treeToFormula(node)),
                        emptyList()
                    )
                )
            }
//        println("ENGINE_DEBUG:           returns: ${conclusions.distinct()}")
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
    private fun deriveAddition(premises: List<Formula>): List<Application> {
        val conclusions = mutableListOf<Application>()
        for (p in premises) {
            for (q in premises) {
                if (p != q) {
                    conclusions.add(
                        Application(
                            fOr(p, q), InferenceRule.ADDITION, listOf(p, q), emptyList()
                        )
                    )
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
    private fun deriveConjunction(premises: List<Formula>): List<Application> {
//        println("ENGINE_DEBUG: deriveConjunction received premises: $premises")
        if (premises.size < 2) return emptyList()
        val conclusions = mutableListOf<Application>()
        val premiseList = premises.toList()
        for (i in premiseList.indices) {
            for (j in i + 1 until premiseList.size) {
                conclusions.add(
                    Application(
                        fAnd(premiseList[i], premiseList[j]),
                        InferenceRule.CONJUNCTION,
                        listOf(premiseList[i], premiseList[j]),
                        emptyList()
                    )
                )
                conclusions.add(
                    Application(
                        fAnd(premiseList[j], premiseList[i]),
                        InferenceRule.CONJUNCTION,
                        listOf(premiseList[j], premiseList[i]),
                        emptyList()
                    )
               )
            }
        }
//        println("ENGINE_DEBUG:           returns: ${conclusions.distinct()}")
        return conclusions.distinct()
    }

    /**
     * Constructive Dilemma: ((P → Q) ∧ (R → S)), P ∨ R |- (Q ∨ S)
     *
     * Find all conjunctions of implications in the set of premises.  If the disjunction
     * of the antecedents of both implications are also in the set of premises, then
     * construct a new disjunction with the consequents of both implications.
     */
    private fun deriveConstructiveDilemma(premises: List<Formula>): List<Application> {
//        println("ENGINE_DEBUG: deriveConstructiveDilemma received premises: $premises")
        val conclusions = mutableListOf<Application>()

        // Gather all conjunctions of implications
        val allConjunctions = premises.filter { f ->
            WffParser.parse(f)?.let { node ->
                node is FormulaNode.BinaryOpNode && node.operator == AvailableTiles.and &&
                node.left is FormulaNode.BinaryOpNode &&
                node.left.operator == AvailableTiles.implies &&
                node.right is FormulaNode.BinaryOpNode &&
                node.right.operator == AvailableTiles.implies
            } ?: false
        }.map { conj ->
            val node = WffParser.parse(conj)!! as FormulaNode.BinaryOpNode
            node to ((node.left as FormulaNode.BinaryOpNode) to
                     (node.right as FormulaNode.BinaryOpNode))
        }

        // 1. Gather ALL available implications, both standalone and from conjunctions.
        val allImplications = premises.flatMap { f ->
            WffParser.parse(f)?.let { node ->
                when {
                    // Case A: Standalone implication (P -> Q)
                    node is FormulaNode.BinaryOpNode && node.operator == AvailableTiles.implies ->
                        listOf(node)
                    // Case B: Conjunction of implications ((P->Q) & (R->S))
                    node is FormulaNode.BinaryOpNode && node.operator == AvailableTiles.and &&
                    node.left is FormulaNode.BinaryOpNode &&
                    node.left.operator == AvailableTiles.implies &&
                    node.right is FormulaNode.BinaryOpNode &&
                    node.right.operator == AvailableTiles.implies ->
                        listOf(node.left, node.right)
                    else -> emptyList()
                }
            } ?: emptyList()
        }.distinct()

        // 2. Gather all available disjunctions.
        val disjunctions = premises.mapNotNull { f ->
            WffParser.parse(f)?.let {
                if (it is FormulaNode.BinaryOpNode && it.operator == AvailableTiles.or) it
                else null
            }
        }

        if (allImplications.size < 2 || disjunctions.isEmpty()) return emptyList()

        // 3. Iterate through all pairs of implications and check against the disjunctions.
        allConjunctions.forEach { p ->
            val conj = p.first
            val firstImpl = p.second.first
            val secondImpl = p.second.second
            val pNode = firstImpl.left
            val qNode = firstImpl.right
            val rNode = secondImpl.left
            val sNode = secondImpl.right

            // Construct the required disjunctive premise (P v R)
            val requiredDisjNode =
                WffParser.parse(fOr(treeToFormula(pNode),
                                    treeToFormula(rNode))
                )

            // Check if any of the provided disjunctions match
            disjunctions.filter { it == requiredDisjNode }
                .map { dis ->
                    conclusions.add(
                        Application(
                            fOr(treeToFormula(qNode), treeToFormula(sNode)),
                            InferenceRule.CONSTRUCTIVE_DILEMMA,
                            listOf(
                                treeToFormula(conj),
                                treeToFormula(dis)
                            ),
                            emptyList()
                        )
                    )
                }
        }
//        println("ENGINE_DEBUG:           returns: ${conclusions.distinct()}")
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
    private fun deriveDisjunctiveSyllogism(premises: List<Formula>): List<Application> {
//        println("ENGINE_DEBUG: deriveDisjunctiveSyllogism received premises: $premises")
        val conclusions = mutableListOf<Application>()
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
                premises
                    .filter {it == fNeg(treeToFormula(disjunct1)) }
                    .map {
                        conclusions.add(
                            Application(
                                treeToFormula(disjunct2),
                                InferenceRule.DISJUNCTIVE_SYLLOGISM,
                                listOf(it, treeToFormula(node)),
                                emptyList()
                            )
                        )
                    }
                premises
                    .filter {it == fNeg(treeToFormula(disjunct2)) }
                    .map {
                        conclusions.add(
                            Application(
                                treeToFormula(disjunct1),
                                InferenceRule.DISJUNCTIVE_SYLLOGISM,
                                listOf(treeToFormula(node), it),
                                emptyList()
                            )
                        )
                    }
            }
//        println("ENGINE_DEBUG:           returns: ${conclusions.distinct()}")
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
    private fun deriveHypotheticalSyllogism(premises: List<Formula>): List<Application> {
//        println("ENGINE_DEBUG: deriveHypotheticalSyllogism received premises: $premises")
        val conclusions = mutableListOf<Application>()
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
        if (implications.size < 2) {
//            println("ENGINE_DEBUG:           returns: ${conclusions.distinct()}")
            return conclusions
        }

        // implications now has a list of pairs of formulas and their parsed nodes
        for (i in implications.indices) {
            for (j in implications.indices) { // Ensures each pair is visited once
                if (i == j) continue // Don't compare the same pair

                val (formula1, node1) = implications[i] // Destructuring for node1
                val (formula2, node2) = implications[j] // Destructuring for node2

                // Check A->B, B->C
                if (node1.right == node2.left) {
                    conclusions.add(
                        Application(
                            fImplies(treeToFormula(node1.left),
                                     treeToFormula(node2.right)),
                            InferenceRule.HYPOTHETICAL_SYLLOGISM,
                            listOf(formula1, formula2),
                            emptyList()
                        )
                    )
                }
            }
        }
//        println("ENGINE_DEBUG:           returns: ${conclusions.distinct()}")
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
    private fun deriveModusPonens(premises: List<Formula>): List<Application> {
//        println("ENGINE_DEBUG: deriveModusPonens received premises: $premises")
        val conclusions = mutableListOf<Application>()
        premises
            .mapNotNull { formula ->
                val node = WffParser.parse(formula)
                if (node is FormulaNode.BinaryOpNode && node.operator == AvailableTiles.implies) {
                    node
                } else {
                    null
                }
            }
            .forEach { impl ->
                val antecedent = treeToFormula(impl.left)
                val consequent = treeToFormula(impl.right)
                if (premises.contains(antecedent)) {
                    conclusions.add(
                        Application(
                            consequent,
                            InferenceRule.MODUS_PONENS,
                            listOf(treeToFormula(impl),
                                   antecedent),
                            emptyList()
                        )
                    )
                }
            }
//        println("ENGINE_DEBUG:           returns: ${conclusions.distinct()}")
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
    private fun deriveModusTollens(premises: List<Formula>): List<Application> {
//        println("ENGINE_DEBUG: deriveModusTollens received premises: $premises")
        val conclusions = mutableListOf<Application>()
        premises
            .mapNotNull { formula ->
                val node = WffParser.parse(formula)
                if (node is FormulaNode.BinaryOpNode && node.operator == AvailableTiles.implies) {
                    node
                } else {
                    null
                }
            }
            .forEach { impl ->
                val consequent = treeToFormula(impl.right)
                val negConsequent = fNeg(consequent)
                if (premises.contains(negConsequent)) {
                    conclusions.add(
                        Application(
                            fNeg(treeToFormula(impl.left)),
                            InferenceRule.MODUS_TOLLENS,
                            listOf(treeToFormula(impl),
                                   negConsequent),
                            emptyList()
                        )
                    )
                }
            }
//        println("ENGINE_DEBUG:           returns: ${conclusions.distinct()}")
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
    private fun deriveSimplification(premises: List<Formula>): List<Application> {
//        println("ENGINE_DEBUG: deriveSimplification received premises: $premises")
        val conclusions = mutableListOf<Application>()
        premises
            .mapNotNull { formula ->
                val node = WffParser.parse(formula)
                if (node is FormulaNode.BinaryOpNode && node.operator == AvailableTiles.and) {
                    node
                } else {
                    null
                }
            }
            .forEach { conj ->
                val left = treeToFormula(conj.left)
                val right = treeToFormula(conj.right)
                conclusions.add(
                    Application(
                        left,
                        InferenceRule.SIMPLIFICATION,
                        listOf(treeToFormula(conj)),
                        emptyList()
                    )
                )
                conclusions.add(
                    Application(
                        right,
                        InferenceRule.SIMPLIFICATION,
                        listOf(treeToFormula(conj)),
                        emptyList()
                    )
                )
            }
//        println("ENGINE_DEBUG:           returns: ${conclusions.distinct()}")
        return conclusions.distinct()
    }
}
