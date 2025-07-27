package com.elsoft.whatthewff.logic

import com.elsoft.whatthewff.logic.AvailableTiles.implies
import com.elsoft.whatthewff.logic.AvailableTiles.or
import com.elsoft.whatthewff.logic.AvailableTiles.and
import com.elsoft.whatthewff.logic.AvailableTiles.not
import com.elsoft.whatthewff.logic.AvailableTiles.leftParen
import com.elsoft.whatthewff.logic.AvailableTiles.rightParen

object RuleGenerators {

    /**
     * Creative Strategy: Take a goal of the form Q and return:
     *  - Premises: [ (P → Q) ]
     *  - Goals: [ P ]
     *
     *  Alternatively, it could return:
     *  - Premises: [ P ]
     *  - Goals: [ (P → Q) ]
     *
     *  It always consumes one variable [ P ]
     */
    val modusPonens = GenerationStrategy(
        name = "Modus Ponens",
        canApply = { true },
        generate = { goal, availableVars ->
            if (availableVars.isEmpty()) return@GenerationStrategy null
            val antecedent = Formula(listOf(availableVars.removeAt(0)))
            val implication = Formula(
                listOf(
                    leftParen) +
                    antecedent.tiles +
                    listOf(implies) +
                    goal.tiles +
                    listOf(rightParen)
            )

            val (premise, nextGoal) = if (Math.random() < 0.75) {
                Pair(implication, antecedent)
            } else {
                Pair(antecedent, implication)
            }

            GenerationStep(newPremises = listOf(premise), nextGoals = listOf(nextGoal))
        }
    )

    /**
     * Creative Strategy: Take a goal of the form ¬P and return:
     *  - Premises: [ (P -> Q) ]
     *  - Goals: [ ¬Q ]
     *
     *  Alternatively, it could return:
     *  - Premises: [ ¬Q ]
     *  - Goals: [ (P -> Q) ]
     *
     *  It always consumes: one variable [ Q ]
     */
    val modusTollens = GenerationStrategy(
        name = "Modus Tollens",
        canApply = { goal ->
            // MT can only apply if the goal is a negation.
            WffParser.parse(goal)?.let { it is FormulaNode.UnaryOpNode } ?: false
        },
        generate = { goal, availableVars ->
            if (availableVars.isEmpty()) return@GenerationStrategy null
            // GOAL is ¬P. We need to extract P.
            val pNode = WffParser.parse(goal)?.let { if (it is FormulaNode.UnaryOpNode) it.child else null }
                ?: return@GenerationStrategy null

            val pFormula = treeToFormula(pNode)
            val qFormula = Formula(listOf(availableVars.removeAt(0)))

            // The premise is always (P → Q).
            val implication = Formula(
                listOf(leftParen) +
                        pFormula.tiles +
                        listOf(implies) +
                        qFormula.tiles +
                        listOf(rightParen)
            )

            val negatedConsequent = Formula(listOf(not) + qFormula.tiles)

            val (premise, nextGoal) = if (Math.random() < 0.75) {
                Pair(implication, negatedConsequent)
            } else {
                Pair(negatedConsequent, implication)
            }

            GenerationStep(newPremises = listOf(premise), nextGoals = listOf(nextGoal))
        }
    )

    /**
     * Structural Strategy: Take a goal of the form (P ∧ Q) and return:
     *  - Premises: []
     *  - Goals: [ P, Q ]
     *  - Consumes: no variables
     */
    val conjunction = GenerationStrategy(
        name = "Conjunction",
        canApply = { goal ->
            // Can only apply if the goal is a conjunction.
            WffParser.parse(goal)?.let { it is FormulaNode.BinaryOpNode && it.operator == and } ?: false
        },
        generate = { goal, _ ->
            WffParser.parse(goal)?.let { node ->
                if (node is FormulaNode.BinaryOpNode) {
                    GenerationStep(newPremises = emptyList(),
                                   nextGoals = listOf(treeToFormula(node.left),
                                                      treeToFormula(node.right)))
                } else null
            }
        }
    )

    /**
     * Structural Strategy: Take a goal of the form (P → R) and return:
     *  - Premises: []
     *  - Goals: [ (P → Q), ( Q → R) ]
     *  - Consumes: one variable [ Q ]
     */
    val hypotheticalSyllogism = GenerationStrategy(
        name = "Hypothetical Syllogism",
        canApply = { goal ->
            // Can only apply if the goal is an implication.
            WffParser.parse(goal)?.let { it is FormulaNode.BinaryOpNode && it.operator == implies } ?: false
        },
        generate = { goal, availableVars ->
            if (availableVars.isEmpty()) return@GenerationStrategy null
            WffParser.parse(goal)?.let { node ->
                // This is already checked in canApply, but just in case...
                if (node is FormulaNode.BinaryOpNode && node.operator == implies) {
                    val p = treeToFormula(node.left)
                    val r = treeToFormula(node.right)
                    val q = Formula(listOf(availableVars.removeAt(0))) // Intermediate variable
                    val goal1 = Formula(listOf(leftParen) +
                                                p.tiles + listOf(implies) + q.tiles +
                                                listOf(rightParen))
                    val goal2 = Formula(listOf(leftParen) +
                                                q.tiles + listOf(implies) + r.tiles +
                                                listOf(rightParen))
                    GenerationStep(newPremises = emptyList(), nextGoals = listOf(goal1, goal2))
                } else null
            }
        }
    )

    /**
     * Creative Strategy: Take any goal (say Q) and return:
     *  - Premises: [ (P ∨ Q) ]
     *  - Goals: [ ¬P ]
     *  - Consumes: one variable [ P ]
     */
    val disjunctiveSyllogism = GenerationStrategy(
        name = "Disjunctive Syllogism",
        canApply = { true }, // Can apply to any goal
        generate = { goal, availableVars ->
            if (availableVars.isEmpty()) return@GenerationStrategy null
            val otherVar = availableVars.removeAt(0)
            val otherFormula = Formula(listOf(otherVar))

            // To get GOAL (Q), we need premises (P ∨ Q) and ¬P
            val p = otherFormula
            val q = goal

            val premise1 =
                Formula(
                    listOf(leftParen) +
                            p.tiles +
                            listOf(or) +
                            q.tiles +
                            listOf(rightParen))
            val premise2 = Formula(listOf(not) + p.tiles)

            GenerationStep(newPremises = listOf(premise1), nextGoals = listOf(premise2))
        }
    )

    /**
     * Structural Strategy: Take a goal of the form (Q ∧ S) and returns:
     *  - Premises: []
     *  - Goals: two new goals: [ ((P → Q) ∧ (R → S)), (P ∨ R) ]
     *  - Consumes: two variables [P, R]
     */
    val constructiveDilemma = GenerationStrategy(
        name = "Constructive Dilemma",
        canApply = { goal ->
            WffParser.parse(goal)?.let {
                it is FormulaNode.BinaryOpNode && it.operator == or
            } ?: false
        },
        generate = { goal, availableVars ->
            if (availableVars.size < 2) return@GenerationStrategy null
            val qNode = (WffParser.parse(goal) as? FormulaNode.BinaryOpNode)?.left ?: return@GenerationStrategy null
            val sNode = (WffParser.parse(goal) as? FormulaNode.BinaryOpNode)?.right ?: return@GenerationStrategy null

            val p = Formula(listOf(availableVars.removeAt(0)))
            val r = Formula(listOf(availableVars.removeAt(0)))
            val q = treeToFormula(qNode)
            val s = treeToFormula(sNode)

            val imp1 = Formula(listOf(leftParen) + p.tiles + listOf(implies) + q.tiles + listOf(rightParen))
            val imp2 = Formula(listOf(leftParen) + r.tiles + listOf(implies) + s.tiles + listOf(rightParen))

            val goal1 = Formula(listOf(leftParen) + imp1.tiles + listOf(and) + imp2.tiles + listOf(rightParen))
            val goal2 = Formula(listOf(leftParen) + p.tiles + listOf(or) + r.tiles + listOf(rightParen))

            GenerationStep(newPremises = emptyList(), nextGoals = listOf(goal1, goal2))
        }
    )

    /**
     * Structural Strategy: Take a goal of the form (A → (A ∧ B)) and returns:
     * - Premises: []
     * - Goals: [ (A → B) ]
     * - Consumes: no variables
     */
    val absorption = GenerationStrategy(
        name = "Absorption",
        canApply = { goal ->
            WffParser.parse(goal)?.let { rootNode ->
                // Check: Is the goal of the form A → B?
                if (rootNode is FormulaNode.BinaryOpNode && rootNode.operator == implies) {
                    val antecedent = rootNode.left
                    val consequent = rootNode.right

                    // Check: Is the consequent (B) of the form (C ∧ D)?
                    if (consequent is FormulaNode.BinaryOpNode && consequent.operator == and) {
                        // Check: Does the antecedent (A) match either C or D?
                        return@let antecedent == consequent.left || antecedent == consequent.right
                    }
                }
                false // If any check fails, it's not applicable.
            } ?: false // If parsing fails, it's not applicable.
        },
        generate = { goal, availableVars ->
            WffParser.parse(goal)?.let { rootNode ->
                if (rootNode is FormulaNode.BinaryOpNode && rootNode.operator == implies) {
                    val pNode = rootNode.left
                    val consequent = rootNode.right

                    if (consequent is FormulaNode.BinaryOpNode && consequent.operator == and) {
                        // Find which side of the conjunction is the "other" part (q).
                        val qNode = if (pNode == consequent.left) {
                            consequent.right
                        } else if (pNode == consequent.right) {
                            consequent.left
                        } else {
                            null // Should not happen if canApply passed
                        }

                        if (qNode != null) {
                            // Reconstruct the new goal formula: (p → q)
                            val pFormula = treeToFormula(pNode)
                            val qFormula = treeToFormula(qNode)
                            val newGoal = Formula(
                                listOf(AvailableTiles.leftParen) +
                                        pFormula.tiles +
                                        listOf(AvailableTiles.implies) +
                                        qFormula.tiles +
                                        listOf(AvailableTiles.rightParen)
                            )
                            // This rule creates one new goal and zero new premises.
                            return@let GenerationStep(newPremises = emptyList(), nextGoals = listOf(newGoal))
                        }
                    }
                }
                null
            }
        }
    )

    /**
     * Structural Strategy: Take any goal (say P ∧ Q) and return:
     * - Premises: []
     * - Goals: [ P ] (or [ Q ], decided by random choice)
     * - Consumes: no variables
     */
    val addition = GenerationStrategy(
        name = "Addition",
        canApply = { goal ->
            WffParser.parse(goal)?.let {
                it is FormulaNode.BinaryOpNode && it.operator == or
            } ?: false
        },
        generate = { goal, availableVars ->
            val pNode = (WffParser.parse(goal) as? FormulaNode.BinaryOpNode)?.left ?: return@GenerationStrategy null
            val qNode = (WffParser.parse(goal) as? FormulaNode.BinaryOpNode)?.right ?: return@GenerationStrategy null

            val nextGoal = if (Math.random() < 0.5) {
                treeToFormula(pNode)
            } else {
                treeToFormula(qNode)
            }
            GenerationStep(emptyList(), listOf(nextGoal))
        }
    )

    /**
     * Creative Strategy: Take any goal (P) and return:
     * - Premises: []
     * - Goals: [ P ∧ Q]
     * - Consumes: Q
     */
    val simplification = GenerationStrategy(
        name = "Simplification",
        canApply = { goal ->
            true
        },
        generate = { goal, availableVars ->
            if (availableVars.isEmpty()) return@GenerationStrategy null
            val qFormula = Formula(listOf(availableVars.removeAt(0)))
            val newGoal = Formula(
                listOf(leftParen) +
                        goal.tiles +
                        listOf(and) +
                        qFormula.tiles +
                        listOf(rightParen))

            GenerationStep(emptyList(), listOf(newGoal))
        }
    )

    /**
     * Structural Strategies: These are rules that deconstruct a complex goal based on its main
     * operator (e.g., Conjunction, Hypothetical Syllogism). They are the most logical choice
     * when a goal is complex.
     */
    val structuralStrategies = listOf(conjunction, hypotheticalSyllogism, constructiveDilemma, absorption, addition)

    /**
     * Creative Strategies: These are rules that add a new layer of complexity to a simpler goal
     * (e.g., Modus Ponens, Disjunctive Syllogism). They are best used when the goal is a simple
     * variable that can't be deconstructed further.
     */
    val creativeStrategies = listOf(modusPonens, modusTollens, disjunctiveSyllogism, simplification)
}