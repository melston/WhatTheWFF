package com.elsoft.whatthewff.logic


// Represents the output of one backward step of generation.
data class GenerationStep(val newPremises: List<Formula>, val nextGoals: List<Formula>)

// A data class to bundle a generation strategy with its applicability check.
data class GenerationStrategy(
    val canApply: (goal: Formula) -> Boolean,
    val generate: (goal: Formula, availableVars: MutableList<LogicTile>) -> GenerationStep?
)


object RuleGenerators {

    val modusPonens = GenerationStrategy(
        canApply = { true }, // Can always apply as a fallback
        generate = { goal, availableVars ->
            if (availableVars.isEmpty()) return@GenerationStrategy null
            val source = Formula(listOf(availableVars.removeAt(0)))
            val premise = Formula(
                listOf(AvailableTiles.leftParen) + source.tiles + listOf(AvailableTiles.implies) + goal.tiles + listOf(AvailableTiles.rightParen)
            )
            GenerationStep(newPremises = listOf(premise), nextGoals = listOf(source))
        }
    )

    val modusTollens = GenerationStrategy(
        canApply = { goal ->
            // MT can only apply if the goal is a negation.
            WffParser.parse(goal) is FormulaNode.UnaryOpNode
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
                listOf(AvailableTiles.leftParen) + pFormula.tiles + listOf(AvailableTiles.implies) + qFormula.tiles + listOf(AvailableTiles.rightParen)
            )
            // The new goal is always ¬Q.
            val nextGoal = Formula(listOf(AvailableTiles.not) + qFormula.tiles)

            GenerationStep(newPremises = listOf(premise), nextGoals = listOf(nextGoal))
        }
    )

    val conjunction = GenerationStrategy(
        canApply = { goal ->
            // Can only apply if the goal is a conjunction.
            WffParser.parse(goal)?.let { it is FormulaNode.BinaryOpNode && it.operator.symbol == "∧" } ?: false
        },
        generate = { goal, _ ->
            WffParser.parse(goal)?.let { node ->
                if (node is FormulaNode.BinaryOpNode) {
                    // To get (P ∧ Q), the new sub-goals are P and Q.
                    val pFormula = treeToFormula(node.left)
                    val qFormula = treeToFormula(node.right)
                    GenerationStep(newPremises = emptyList(), nextGoals = listOf(pFormula, qFormula))
                } else null
            }
        }
    )

    val hypotheticalSyllogism = GenerationStrategy(
        canApply = { goal ->
            // Can only apply if the goal is a conjunction.
            WffParser.parse(goal)?.let { it is FormulaNode.BinaryOpNode && it.operator.symbol == "⇒" } ?: false
        },
        generate = { goal, availableVars ->
            if (availableVars.isEmpty()) return@GenerationStrategy null
            WffParser.parse(goal)?.let { node ->
                // This is already checked in canApply, but just in case...
                if (node is FormulaNode.BinaryOpNode && node.operator.symbol == "⇒") {
                    val p = treeToFormula(node.left)
                    val r = treeToFormula(node.right)
                    val q = Formula(listOf(availableVars.removeAt(0))) // Intermediate variable
                    val goal1 = Formula(listOf(AvailableTiles.leftParen) +
                                                p.tiles + listOf(AvailableTiles.implies) + q.tiles +
                                                listOf(AvailableTiles.rightParen))
                    val goal2 = Formula(listOf(AvailableTiles.leftParen) +
                                                q.tiles + listOf(AvailableTiles.implies) + r.tiles +
                                                listOf(AvailableTiles.rightParen))
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

            val premise1 = Formula(listOf(AvailableTiles.leftParen) + p.tiles + listOf(AvailableTiles.or) + q.tiles + listOf(AvailableTiles.rightParen))
            val premise2 = Formula(listOf(AvailableTiles.not) + p.tiles)

            GenerationStep(newPremises = listOf(premise1), nextGoals = listOf(premise2))
        }
    )
    private fun treeToFormula(node: FormulaNode, isToplevel: Boolean = true): Formula {
        val tiles = mutableListOf<LogicTile>()
        fun build(n: FormulaNode, isInner: Boolean) {
            val needsParens = isInner && (n is FormulaNode.BinaryOpNode || n is FormulaNode.UnaryOpNode)
            if (needsParens) tiles.add(AvailableTiles.leftParen)
            when (n) {
                is FormulaNode.VariableNode -> tiles.add(n.tile)
                is FormulaNode.UnaryOpNode -> {
                    tiles.add(n.operator)
                    build(n.child, true)
                }
                is FormulaNode.BinaryOpNode -> {
                    build(n.left, true)
                    tiles.add(n.operator)
                    build(n.right, true)
                }
            }
            if (needsParens) tiles.add(AvailableTiles.rightParen)
        }
        build(node, !isToplevel)
        return Formula(tiles)
    }

}