// File: logic/InferenceRuleEngine.kt
// This new file is the central "brain" for all rules of inference.
// It provides a single source of truth for both the validator and the suggester.

package com.elsoft.whatthewff.logic

import com.elsoft.whatthewff.logic.AvailableTiles.and
import com.elsoft.whatthewff.logic.AvailableTiles.implies
import com.elsoft.whatthewff.logic.AvailableTiles.or
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
)

object InferenceRuleEngine {

    // --- Public-facing function for the problem generator ---
    /**
     * Function for the top-down solver.
     * Given a rule and a target conclusion, what are the possible sets of premises?
     */
    fun getPremiseSetsForConclusion(rule: InferenceRule, conclusion: Formula,
                                    vars: VarLists): List<List<Formula>> {
        val conclusionNode = WffParser.parse(conclusion) ?: return emptyList()
        val rv: MutableList<List<Formula>> = mutableListOf()

        when (rule) {
            InferenceRule.ASSUMPTION -> {}
            InferenceRule.ABSORPTION -> {
                if (conclusionNode is FormulaNode.BinaryOpNode &&
                    conclusionNode.operator == implies &&
                    conclusionNode.right is FormulaNode.BinaryOpNode&&
                    conclusionNode.right.operator == and) {
                    // If conclusion is p->(p&q), we need a premise p->q
                    // So we get 'q' from 'vars'

                    // The antecedent of the main implication. This is 'p'.
                    val p = treeToFormula(conclusionNode.left)

                    // The two parts of the inner conjunction.
                    val conjunct1 = treeToFormula(conclusionNode.right.left)
                    val conjunct2 = treeToFormula(conclusionNode.right.right)

                    // Check if both of the conjuncts are 'p'.
                    if ((compareFormulas(p, conjunct1) &&
                        compareFormulas(p, conjunct2)) ||
                        compareFormulas(p, fNeg(conjunct1)) || // contradiction
                        compareFormulas(p, fNeg(conjunct2))) { // contradiction
                        // This is the p->(p&p) case. Do nothing.
                    } else {
                        // Otherwise, find the one that is NOT p, and that's our q.
                        // If neither one is 'p' then we do nothing.
                        when {
                            // Case 1: The conjunction is (p & q)
                            compareFormulas(p, conjunct1) -> {
                                val q = conjunct2 // This is 'q'
                                rv.add(listOf(fImplies(p, q)))
                            }
                            // Case 2: The conjunction is (q & p)
                            compareFormulas(p, conjunct2) -> {
                                val q = conjunct1 // This is 'q'
                                rv.add(listOf(fImplies(p, q)))
                            }
                        }
                    }
                }
            }
            InferenceRule.ADDITION -> {
                if (conclusionNode is FormulaNode.BinaryOpNode &&
                    conclusionNode.operator == and) {
                    val p = treeToFormula(conclusionNode.left)
                    val q = treeToFormula(conclusionNode.right)
                    rv.add(listOf(p, q))
                    rv.add(listOf(q, p))
                }
            }
            InferenceRule.CONJUNCTION -> {
                // Make sure that we don't have a situation where we wind up with
                // 'p & p & p, ...'
                if (conclusionNode is FormulaNode.BinaryOpNode &&
                    conclusionNode.operator == and) {
                    // If the conclusion is p & q, the premises are [p, q] or
                    // [q, p].val p = treeToFormula(conclusionNode.left)
                    val p = treeToFormula(conclusionNode.left)
                    val q = treeToFormula(conclusionNode.right)

                    if (!compareFormulas(p, q) && !compareFormulas(p, fNeg(q))) {
                        rv.add(listOf(p, q))
                        rv.add(listOf(q, p))
                    }
                    // If they are the same then do nothing!!
                }
            }
            InferenceRule.CONSTRUCTIVE_DILEMMA -> {
                // If conclusion is (q v s), premises would be ((p -> q) & (r -> s))
                // and (p v r)
                if (conclusionNode is FormulaNode.BinaryOpNode &&
                    conclusionNode.operator == or) {
                    val q = treeToFormula(conclusionNode.left)
                    val s = treeToFormula(conclusionNode.right)

                    // Get a list of all variables we can possibly use.
                    val availableAtoms = (vars.availableVars + vars.usedVars).distinct()

                    // Use nested loops to get all unique pairs of atoms for 'p' and 'r'.
                    // This is equivalent to a cartesian product on the available variables.
                    for (p in availableAtoms) {
                        for (r in availableAtoms) {
                            // Ensure we don't pick the same atom for both p and r.
                            if (p == r) continue

                            // Prevent tautologies (p->p)
                            if (compareFormulas(p, q) || compareFormulas(r, s)) {
                                continue
                            }
                            // Prevent nonsensical implications like (¬q → q).
                            if (compareFormulas(p, fNeg(q)) ||
                                compareFormulas(r, fNeg(s))) {
                                continue
                            }
                            // Construct the two premises from our invented p and r,
                            // and the q and s from the conclusion.
                            val premise1 = fAnd(fImplies(p, q), fImplies(r, s))
                            val premise2 = fOr(p, r)

                            // Add both orderings for the solver.
                            rv.add(listOf(premise1, premise2))
                            rv.add(listOf(premise2, premise1))
                        }
                    }                }
            }
            InferenceRule.DISJUNCTIVE_SYLLOGISM -> {
                // If conclusion is q, premises would be p | q and ~p
                (vars.availableVars + vars.usedVars)
                    .filter { it != conclusion}
                    .forEach { p ->
                        rv.add(listOf(fOr(p, conclusion), fNeg(p)))
                        rv.add(listOf(fNeg(p), fOr(p, conclusion)))
                    }
            }
            InferenceRule.HYPOTHETICAL_SYLLOGISM -> {
                if (conclusionNode is FormulaNode.BinaryOpNode &&
                    conclusionNode.operator == implies) {
                    // If conclusion is p->r, we need premises p->q and q->r
                    // So we get 'q' from 'vars'
                    val p = treeToFormula(conclusionNode.left)
                    val r = treeToFormula(conclusionNode.right)
                    (vars.availableVars + vars.usedVars)
                        .filter { it != p && it != r }
                        .forEach { q ->
                            // Prevent tautological (p->p) or contradictory premises.
                            if (!compareFormulas(p, q) &&       // p != q (tautology)
                                !compareFormulas(q, r) &&       // q != r (tautology)
                                !compareFormulas(p, fNeg(q)) && // p != ~q (contradiction)
                                !compareFormulas(q, fNeg(r))    // q != ~r (contradiction)
                                ) {
                                rv.add(listOf(fImplies(p, q), fImplies(q, r)))
                            }
                        }
                }
            }
            InferenceRule.MODUS_PONENS -> {
                // If conclusion is q, premises would be p->q and p
                (vars.availableVars + vars.usedVars)
                    .filter { it != conclusion}
                    .forEach { p ->
                        // Prevent inventing a premise 'p' that is already a part of
                        // the conclusion 'q'.
                        // This avoids generating redundant premises like t -> (t -> q).
                        val conclusionAtoms = conclusion.getAtomicAssertions()
                        if (conclusionAtoms.contains(p)) return@forEach

                        // Prevent tautological (p->p) or contradictory premises.
                        if (!compareFormulas(p, conclusion) &&    // p != q (tautology)
                            !compareFormulas(p, fNeg(conclusion)) // p != ~q (contradiction)
                        ) {
                            rv.add(listOf(fImplies(p, conclusion), p))
                            rv.add(listOf(p, fImplies(p, conclusion)))
                        }
                    }
            }
            InferenceRule.MODUS_TOLLENS -> {
                // If conclusion is p, premises would be ~q and ~p->q
                (vars.availableVars + vars.usedVars)
                    .filter { it != conclusion}
                    .forEach { q ->
                        // Prevent tautological (p->p) or contradictory premises.
                        if (!compareFormulas(q, conclusion) &&    // p != q (tautology)
                            !compareFormulas(q, fNeg(conclusion)) // p != ~q (contradiction)
                        ) {
                            rv.add(listOf(fNeg(q), fImplies(fNeg(conclusion), q)))
                            rv.add(listOf(fImplies(fNeg(conclusion), q), fNeg(q)))
                        }
                    }
            }
            InferenceRule.SIMPLIFICATION -> {
                // If conclusion is p, premise could be p&q, p&r, etc.
                (vars.availableVars + vars.usedVars)
                    .filter { it != conclusion}
                    .forEach { q ->
                        rv.add(listOf(fAnd(conclusion, q)))
                    }
            }
        }
        return rv
    }

    /**
     * New function for the top-down solver.
     * Generates potential sets of concrete premises for a given rule.
     */
    fun getPossiblePremises(rule: InferenceRule, vars: VarLists): List<Application> {
        val atoms = (vars.availableVars + vars.usedVars).distinct().shuffled()
        if (atoms.size < 4) return emptyList()

        val p = atoms[0]
        val q = atoms[1]
        val r = atoms[2]
        val s = atoms[3]

        return when (rule) {
            InferenceRule.MODUS_PONENS ->
                listOf(Application(q, rule,
                                   listOf(fImplies(p, q), p), emptyList()))
            InferenceRule.MODUS_TOLLENS ->
                listOf(Application(fNeg(p), rule,
                                   listOf(fImplies(p, q), fNeg(q)), emptyList()))
            InferenceRule.HYPOTHETICAL_SYLLOGISM ->
                listOf(Application(fImplies(p,r), rule,
                                   listOf(fImplies(p, q), fImplies(q, r)), emptyList()))
            InferenceRule.DISJUNCTIVE_SYLLOGISM ->
                listOf(Application(q, rule,
                                   listOf(fOr(p, q), fNeg(p)), emptyList()))
            InferenceRule.CONSTRUCTIVE_DILEMMA ->
                listOf(Application(fOr(q,s), rule,
                                   listOf(fAnd(fImplies(p, q), fImplies(r, s)),
                                          fOr(p, r)), emptyList()))
            InferenceRule.SIMPLIFICATION ->
                listOf(Application(p, rule,
                                   listOf(fAnd(p, q)), emptyList()))
            InferenceRule.CONJUNCTION ->
                listOf(Application(fAnd(p,q), rule,
                                   listOf(p, q), emptyList()))
            InferenceRule.ADDITION ->
                listOf(Application(fOr(p,q), rule,
                                   listOf(p), emptyList()))
            InferenceRule.ABSORPTION ->
                listOf(Application(fImplies(p, fAnd(p,q)), rule,
                                   listOf(fImplies(p,q)), emptyList()))
            else -> emptyList()
        }
    }

    // --- Public-facing function for the Suggester ---
    fun getPossibleConclusions(rule: InferenceRule, premises: List<Formula>): List<Formula> {
        return getPossibleApplications(rule, premises).map { it.conclusion }
    }

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
                if (node is FormulaNode.BinaryOpNode && node.operator == implies) {
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
                // Ensure that the premises being combined are not logically identical.
                if (!compareFormulas(premiseList[i], premiseList[j])) {
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
                node is FormulaNode.BinaryOpNode && node.operator == and &&
                node.left is FormulaNode.BinaryOpNode &&
                node.left.operator == implies &&
                node.right is FormulaNode.BinaryOpNode &&
                node.right.operator == implies
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
                    node is FormulaNode.BinaryOpNode && node.operator == implies ->
                        listOf(node)
                    // Case B: Conjunction of implications ((P->Q) & (R->S))
                    node is FormulaNode.BinaryOpNode && node.operator == and &&
                    node.left is FormulaNode.BinaryOpNode &&
                    node.left.operator == implies &&
                    node.right is FormulaNode.BinaryOpNode &&
                    node.right.operator == implies ->
                        listOf(node.left, node.right)
                    else -> emptyList()
                }
            } ?: emptyList()
        }.distinct()

        // 2. Gather all available disjunctions.
        val disjunctions = premises.mapNotNull { f ->
            WffParser.parse(f)?.let {
                if (it is FormulaNode.BinaryOpNode && it.operator == or) it
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
                if (node is FormulaNode.BinaryOpNode && node.operator == or) {
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
                if (node is FormulaNode.BinaryOpNode && node.operator == implies) {
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
                if (node is FormulaNode.BinaryOpNode && node.operator == implies) {
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
                if (node is FormulaNode.BinaryOpNode && node.operator == implies) {
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
                if (node is FormulaNode.BinaryOpNode && node.operator == and) {
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
