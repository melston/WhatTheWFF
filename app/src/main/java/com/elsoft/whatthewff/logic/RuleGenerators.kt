package com.elsoft.whatthewff.logic

import com.elsoft.whatthewff.logic.AvailableTiles.implies
import com.elsoft.whatthewff.logic.AvailableTiles.or
import com.elsoft.whatthewff.logic.AvailableTiles.and
import com.elsoft.whatthewff.logic.AvailableTiles.not
import com.elsoft.whatthewff.logic.AvailableTiles.leftParen
import com.elsoft.whatthewff.logic.AvailableTiles.rightParen
//import com.elsoft.whatthewff.logic.AvailableTiles.iff

// Represents the output of one backward step of generation.
data class GenerationStep(val newPremises: List<Formula>, val nextGoals: List<Formula>)

// A data class to bundle a generation strategy with its applicability check.
data class GenerationStrategy(
    val canApply: (goal: Formula) -> Boolean,
    val generate: (goal: Formula, availableVars: MutableList<LogicTile>) -> GenerationStep?
)


object RuleGenerators {

    val modusPonens = GenerationStrategy(
        // Corrected canApply logic to match test expectations for current generator
        // It should not apply if a more specific rule like HS could apply.
        canApply = { goal ->
            WffParser.parse(goal)?.let { it !is FormulaNode.BinaryOpNode || it.operator.symbol != implies.symbol } ?: true
        },
        generate = { goal, availableVars ->
            if (availableVars.isEmpty()) return@GenerationStrategy null
            val source = Formula(listOf(availableVars.removeAt(0)))
            val premise = Formula(
                listOf(
                    leftParen) +
                    source.tiles +
                    listOf(implies) +
                    goal.tiles +
                    listOf(rightParen)
            )
            GenerationStep(newPremises = listOf(premise), nextGoals = listOf(source))
        }
    )

    val modusTollens = GenerationStrategy(
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
            val premise = Formula(
                listOf(leftParen) +
                        pFormula.tiles +
                        listOf(implies) +
                        qFormula.tiles +
                        listOf(rightParen)
            )
            // The new goal is always ¬Q.
            val nextGoal = Formula(listOf(not) + qFormula.tiles)

            GenerationStep(newPremises = listOf(premise), nextGoals = listOf(nextGoal))
        }
    )

    val conjunction = GenerationStrategy(
        canApply = { goal ->
            // Can only apply if the goal is a conjunction.
            WffParser.parse(goal)?.let { it is FormulaNode.BinaryOpNode && it.operator.symbol == and.symbol } ?: false
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

    val hypotheticalSyllogism = GenerationStrategy(
        canApply = { goal ->
            // Can only apply if the goal is a conjunction.
            WffParser.parse(goal)?.let { it is FormulaNode.BinaryOpNode && it.operator.symbol == implies.symbol } ?: false
        },
        generate = { goal, availableVars ->
            if (availableVars.isEmpty()) return@GenerationStrategy null
            WffParser.parse(goal)?.let { node ->
                // This is already checked in canApply, but just in case...
                if (node is FormulaNode.BinaryOpNode && node.operator.symbol == implies.symbol) {
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

    val disjunctiveSyllogism = GenerationStrategy(
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

    // This function is now more robust and correctly handles parentheses
    // to match the expectations of the unit tests.
    fun treeToFormula(node: FormulaNode): Formula {
        val tiles = mutableListOf<LogicTile>()
        fun recurse(n: FormulaNode) {
            when (n) {
                is FormulaNode.VariableNode -> {
                    tiles.add(n.tile)
                }
                is FormulaNode.UnaryOpNode -> {
                    tiles.add(leftParen)
                    tiles.add(n.operator)
                    recurse(n.child)
                    tiles.add(rightParen)
                }
                is FormulaNode.BinaryOpNode -> {
                    tiles.add(leftParen)
                    recurse(n.left)
                    tiles.add(n.operator)
                    recurse(n.right)
                    tiles.add(rightParen)
                }
            }
        }

        // A special case to handle top-level negations like ¬p without adding extra parens
        if (node is FormulaNode.UnaryOpNode) {
            tiles.add(node.operator)
            recurse(node.child)
        } else {
            recurse(node)
        }

        return Formula(tiles)
    }

}