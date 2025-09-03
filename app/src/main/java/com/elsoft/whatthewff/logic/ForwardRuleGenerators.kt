// File: logic/ForwardRuleGenerators.kt
// This file contains the logic for building complex formulas from simpler ones,
// which is Phase 1 of the problem generation process.

package com.elsoft.whatthewff.logic

import com.elsoft.whatthewff.logic.AvailableTiles.and
import com.elsoft.whatthewff.logic.AvailableTiles.iff
import com.elsoft.whatthewff.logic.AvailableTiles.implies
import com.elsoft.whatthewff.logic.AvailableTiles.or

/**
 * This object contains the specific strategies for building complex goals.
 */
object ForwardRuleGenerators {

    /**
     * A function that takes two formulas (P, Q) and combines them into a conjunction (P ∧ Q).
     */
    fun fAnd (f1: Formula, f2: Formula) = Formula(
        listOf(AvailableTiles.leftParen) +
                f1.tiles +
                listOf(AvailableTiles.and) +
                f2.tiles +
                listOf(AvailableTiles.rightParen)
    )

    /**
     * A function that takes two formulas (P, Q) and combines them into a disjunction (P ∨ Q).
     */
    fun fOr (f1: Formula, f2: Formula) = Formula(
        listOf(AvailableTiles.leftParen) +
                f1.tiles +
                listOf(AvailableTiles.or) +
                f2.tiles +
                listOf(AvailableTiles.rightParen)
    )

    /**
     * A function that takes two formulas (P, Q) and combines them into an implication (P → Q).
     */
    fun fImplies (f1: Formula, f2: Formula) = Formula (
        listOf(AvailableTiles.leftParen) +
                f1.tiles +
                listOf(AvailableTiles.implies) +
                f2.tiles +
                listOf(AvailableTiles.rightParen)
    )

    /**
     * A function that takes one formula (P) and creates its negation (¬P).
     */
    fun fNeg (f1: Formula) = Formula (listOf(AvailableTiles.not) + f1.tiles )

    val implicationIntroduction = ForwardRule(
        name = "Implication Introduction",
        weight = 10.0,
        canApply = { it.size >= 2 },
        generate = { knownFormulas ->
            val (p, q) = knownFormulas.shuffled().take(2)
            // Simplified justification
            listOf(ProofStep(fImplies(p, q), "II", listOf(p, q)))
        }
    )

    /**
     * A strategy that takes two formulas (P, Q) and combines them into a conjunction (P ∧ Q).
     */
    val conjunction = ForwardRule(
        name = "Conjunction",
        weight = 10.1,
        canApply = { it.size >= 2 },
        generate = { knownFormulas ->
            // Create all possible pairs of known formulas
            knownFormulas.flatMap { p ->
                knownFormulas.filter { q -> p != q }.flatMap { q ->
                    listOf(
                        ProofStep(fAnd(p, q), "Conj.", listOf(p, q)),
                        ProofStep(fAnd(q, p), "Conj.", listOf(q, p))
                    )
                }
            }.distinct() // Ensure no duplicate proof steps are generated
        }
    )

    val addition = ForwardRule(
        name = "Addition",
        weight = 5.2,
        canApply = { it.size >= 2 }, // Needs at least two formulas to pick from for P and Q
        generate = { knownFormulas ->
            // Create all possible pairs to form disjunctions
            knownFormulas.flatMap { p ->
                knownFormulas.filter { q -> p != q }.flatMap { q ->
                    listOf(
                        ProofStep(fOr(p, q), "Add.", listOf(p)),
                        ProofStep(fOr(q, p), "Add.", listOf(q))
                    )
                }
            }.distinct()
        }
    )

    val modusPonens = ForwardRule(
        name = "Modus Ponens",
        weight = 7.5,
        canApply = { knownFormulas -> findAllModusPonensPairs(knownFormulas).isNotEmpty() },
        generate = { knownFormulas ->
            findAllModusPonensPairs(knownFormulas).map { (implication, antecedent) ->
                val consequent = treeToFormula((WffParser.parse(implication) as FormulaNode.BinaryOpNode).right)
                ProofStep(consequent, "MP", listOf(implication, antecedent))
            }
        }
    )

    val modusTollens = ForwardRule(
        name = "Modus Tollens",
        weight = 7.75,
        canApply = { knownFormulas -> findAllModusTollensPairs(knownFormulas).isNotEmpty() },
        generate = { knownFormulas ->
            findAllModusTollensPairs(knownFormulas).map { (implication, negConsequent) ->
                val antecedent =
                    treeToFormula((WffParser.parse(implication) as FormulaNode.BinaryOpNode).left)
                val negAntecedent = fNeg(antecedent)
                ProofStep(negAntecedent, "MT", listOf(implication, negConsequent))
            }
        }
    )

    val hypotheticalSyllogism = ForwardRule(
        name = "Hypothetical Syllogism",
        weight = 10.2,
        canApply = { knownFormulas -> findAllHypotheticalSyllogismPairs(knownFormulas).isNotEmpty() },
        generate = { knownFormulas ->
            findAllHypotheticalSyllogismPairs(knownFormulas).map { (imp1, imp2, _) ->
                val pNode = (WffParser.parse(imp1) as FormulaNode.BinaryOpNode).left
                val rNode = (WffParser.parse(imp2) as FormulaNode.BinaryOpNode).right
                val pFormula = treeToFormula(pNode)
                val rFormula = treeToFormula(rNode)
                val newImplication = fImplies(pFormula, rFormula)
                ProofStep(newImplication, "HS", listOf(imp1, imp2))
            }
        }
    )

    val disjunctiveSyllogism = ForwardRule(
        name = "Disjunctive Syllogism",
        weight = 10.3,
        canApply = { knownFormulas -> findAllDisjunctiveSyllogismPairs(knownFormulas).isNotEmpty() },
        generate = { knownFormulas ->
            findAllDisjunctiveSyllogismPairs(knownFormulas).map { (disjunction, negation) ->
                val disNode = WffParser.parse(disjunction) as FormulaNode.BinaryOpNode
                // negation contains the operator and the negated formula.  We will need to see if
                // the negated formula is the same as either of the disjuncts.  So we have to
                // parse the formula to get to the child node, then turn that into a formula.
                val negNode = WffParser.parse(negation) as FormulaNode.UnaryOpNode
                val childFormula = treeToFormula(negNode.child)

                val pFormula = treeToFormula(disNode.left)
                val qFormula = treeToFormula(disNode.right)

                val conclusion = if (childFormula == pFormula) {
                    qFormula // (P ∨ Q), ¬P |- Q
                } else {
                    pFormula // (P ∨ Q), ¬Q |- P
                }
                ProofStep(conclusion, "DS", listOf(disjunction, negation))
            }
        }
    )

    val constructiveDilemma = ForwardRule(
        name = "Constructive Dilemma",
        weight = 10.4,
        canApply = { knownFormulas -> findAllConstructiveDilemmaPairs(knownFormulas).isNotEmpty() },
        generate = { knownFormulas ->
            findAllConstructiveDilemmaPairs(knownFormulas).map { (imp1, imp2, dis) ->
                val qNode = (WffParser.parse(imp1) as FormulaNode.BinaryOpNode).right
                val sNode = (WffParser.parse(imp2) as FormulaNode.BinaryOpNode).right
                val qFormula = treeToFormula(qNode)
                val sFormula = treeToFormula(sNode)
                val conclusion = fOr(qFormula, sFormula)
                ProofStep(conclusion, "CD", listOf(imp1, imp2, dis))
            }
        }
    )

    val absorption = ForwardRule(
        name = "Absorption",
        weight = 5.0,
        canApply = { knownFormulas -> findAllAbsorptionPairs(knownFormulas).isNotEmpty() },
        generate = { knownFormulas ->
            findAllAbsorptionPairs(knownFormulas).map { premise ->
                val pNode = (WffParser.parse(premise) as FormulaNode.BinaryOpNode).left
                val qNode = (WffParser.parse(premise) as FormulaNode.BinaryOpNode).right
                val pFormula = treeToFormula(pNode)
                val qFormula = treeToFormula(qNode)

                val pAndQ = fAnd(pFormula, qFormula)
                val conclusion = fImplies(pFormula, pAndQ)

                ProofStep(conclusion, "Abs.", listOf(premise))
            }
        }
    )

    val simplification = ForwardRule(
        name = "Simplification",
        weight = 5.1,
        canApply = { knownFormulas -> findAllConjunctions(knownFormulas).isNotEmpty() },
        generate = { knownFormulas ->
            findAllConjunctions(knownFormulas).flatMap { premise ->
                val node = WffParser.parse(premise) as FormulaNode.BinaryOpNode
                // Create two possible steps, one for each side of the conjunction
                val step1 = ProofStep(treeToFormula(node.left), "Simp.", listOf(premise))
                val step2 = ProofStep(treeToFormula(node.right), "Simp.", listOf(premise))
                listOf(step1, step2)
            }
        }
    )

    val allStrategies = listOf(modusPonens, modusTollens, conjunction, implicationIntroduction,
                               hypotheticalSyllogism, disjunctiveSyllogism, constructiveDilemma,
                               absorption, simplification, addition)

    // For building base premises
//    val simpleCompositionStrategies = listOf(conjunction, implicationIntroduction)

    ////////////// HELPER FUNCTIONS //////////////

    /**
     * Function used in MP to find all formulas in the known formulas list that are implications and
     * have the antecedent in the known formulas list.
     */
    private fun findAllModusPonensPairs(knownFormulas: List<Formula>): List<Pair<Formula, Formula>> {
        val pairs = mutableListOf<Pair<Formula, Formula>>()
        val implications = knownFormulas.filter { f ->
            WffParser.parse(f)?.let { it is FormulaNode.BinaryOpNode && it.operator == implies } ?: false
        }
        for (imp in implications) {
            val antecedent = treeToFormula((WffParser.parse(imp) as FormulaNode.BinaryOpNode).left)
            if (knownFormulas.contains(antecedent)) {
                pairs.add(Pair(imp, antecedent))
            }
        }
        return pairs
    }

    /**
     * Function used in MT to find all formulas in the known formulas list that are implications
     * and have the negation of the consequent in the known formulas list.
     */
    private fun findAllModusTollensPairs(knownFormulas: List<Formula>): List<Pair<Formula, Formula>> {
        val pairs = mutableListOf<Pair<Formula, Formula>>()
        val implications = knownFormulas.filter { f ->
            WffParser.parse(f)?.let { it is FormulaNode.BinaryOpNode && it.operator == implies } ?: false
        }
        for (imp in implications) {
            val consequent = treeToFormula((WffParser.parse(imp) as FormulaNode.BinaryOpNode).right)
            val negConsequent = fNeg(consequent)
            if (knownFormulas.contains(negConsequent)) {
                pairs.add(Pair(imp, negConsequent))
            }
        }
        return pairs
    }

    /**
     * Function used in HS to find all formulas in the known formulas list that are implications
     * whose consequent is the same as the antecedent of another implication also in the known
     * formulas list.
     */
    private fun findAllHypotheticalSyllogismPairs(knownFormulas: List<Formula>): List<Triple<Formula, Formula, Formula>> {
        val pairs = mutableListOf<Triple<Formula, Formula, Formula>>()
        val implications = knownFormulas.filter { f ->
            WffParser.parse(f)?.let { it is FormulaNode.BinaryOpNode && it.operator == implies } ?: false
        }
        for (imp1 in implications) {
            val qNode1 = (WffParser.parse(imp1) as FormulaNode.BinaryOpNode).right
            val qFormula1 = treeToFormula(qNode1)
            for (imp2 in implications) {
                if (imp1 == imp2) continue
                val pNode2 = (WffParser.parse(imp2) as FormulaNode.BinaryOpNode).left
                val pFormula2 = treeToFormula(pNode2)
                if (qFormula1 == pFormula2) {
                    pairs.add(Triple(imp1, imp2, qFormula1)) // Returns (P→Q), (Q→R), Q
                }
            }
        }
        return pairs
    }

    /**
     * Function used in DS to find all formulas in the known formulas list that are disjunctions
     * and have the negation of one of the disjuncts in the known formulas list.
     */
    private fun findAllDisjunctiveSyllogismPairs(knownFormulas: List<Formula>): List<Pair<Formula, Formula>> {
        val pairs = mutableListOf<Pair<Formula, Formula>>()
        val disjunctions = knownFormulas.filter { f ->
            WffParser.parse(f)?.let { it is FormulaNode.BinaryOpNode && it.operator.symbol == "∨" } ?: false
        }
        for (dis in disjunctions) {
            val pNode = (WffParser.parse(dis) as FormulaNode.BinaryOpNode).left
            val qNode = (WffParser.parse(dis) as FormulaNode.BinaryOpNode).right
            val pFormula = treeToFormula(pNode)
            val qFormula = treeToFormula(qNode)

            val negP = fNeg(pFormula)
            val negQ = fNeg(qFormula)

            if (knownFormulas.contains(negP)) {
                pairs.add(Pair(dis, negP))
            }
            if (knownFormulas.contains(negQ)) {
                pairs.add(Pair(dis, negQ))
            }
        }
        return pairs
    }

    /**
     * Function used in CD to find all possible combinations of formulas from the known formulas
     * list that are pairs of implications: (p → q), (r → s) where the disjunction of the
     * antecedents (p ∨ r) are also in the list.
     */
    private fun findAllConstructiveDilemmaPairs(knownFormulas: List<Formula>): List<Triple<Formula, Formula, Formula>> {
        val triples = mutableListOf<Triple<Formula, Formula, Formula>>()
        val implications = knownFormulas.filter { f ->
            WffParser.parse(f)?.let { it is FormulaNode.BinaryOpNode && it.operator == implies } ?: false
        }
        val disjunctions = knownFormulas.filter { f ->
            WffParser.parse(f)?.let { it is FormulaNode.BinaryOpNode && it.operator == or } ?: false
        }

        for (imp1 in implications) {
            for (imp2 in implications) {
                if (imp1 == imp2) continue

                val pNode = (WffParser.parse(imp1) as FormulaNode.BinaryOpNode).left
                val rNode = (WffParser.parse(imp2) as FormulaNode.BinaryOpNode).left
                val antecedent1 = treeToFormula(pNode)
                val antecedent2 = treeToFormula(rNode)

                for (dis in disjunctions) {
                    val disNode = WffParser.parse(dis) as FormulaNode.BinaryOpNode
                    val disLeft = treeToFormula(disNode.left)
                    val disRight = treeToFormula(disNode.right)

                    if ((disLeft == antecedent1 && disRight == antecedent2) || (disLeft == antecedent2 && disRight == antecedent1)) {
                        triples.add(Triple(imp1, imp2, dis))
                    }
                }
            }
        }
        return triples
    }

    /**
     * Function used in Abs to find all formulas in the known formulas list that are implications.
     * This is the only requirement for Absorption.
     */
    private fun findAllAbsorptionPairs(knownFormulas: List<Formula>): List<Formula> {
        return knownFormulas.filter { f ->
            WffParser.parse(f)?.let { it is FormulaNode.BinaryOpNode && it.operator == implies } ?: false
        }
    }

    /**
     * Function used in Simp to find all formulas in the known formulas list that are conjunctions.
     * This is the only requirement for Simplification..
     */
    private fun findAllConjunctions(knownFormulas: List<Formula>): List<Formula> {
        return knownFormulas.filter { f ->
            WffParser.parse(f)?.let { it is FormulaNode.BinaryOpNode && it.operator == and } ?: false
        }
    }

    ///////////////// Formula building helpers ///////////////////////////////

    /**
     * Converts a FormulaNode to a Formula.
     */
    fun treeToFormula(node: FormulaNode): Formula {
        val tiles = mutableListOf<LogicTile>()

        fun getPrecedence(op: LogicTile?): Int {
            return when (op) {
                iff -> 1
                implies -> 2
                or -> 3
                and -> 4
                else -> 0 // Handles null or any other tile (or null)
            }
        }

        fun build(node: FormulaNode, parentPrecedence: Int, isRightChild: Boolean) {
            when (node) {
                is FormulaNode.VariableNode -> tiles.add(node.tile)
                is FormulaNode.UnaryOpNode -> {
                    tiles.add(node.operator)
                    build(node.child, 10, false) // High precedence for unary content
                }
                is FormulaNode.BinaryOpNode -> {
                    val currentPrecedence = getPrecedence(node.operator)
                    // Parenthesize if the current operator has lower precedence than its parent,
                    // or if it's the same precedence and on the right side of a left-associative op.
                    val needsParens = currentPrecedence < parentPrecedence ||
                            (currentPrecedence == parentPrecedence && isRightChild && node.operator != implies)

                    if (needsParens) tiles.add(AvailableTiles.leftParen)

                    build(node.left, currentPrecedence, false)
                    tiles.add(node.operator)
                    build(node.right, currentPrecedence, true)

                    if (needsParens) tiles.add(AvailableTiles.rightParen)
                }
            }
        }

        build(node, 0, false)
        return Formula(tiles)
    }

}
