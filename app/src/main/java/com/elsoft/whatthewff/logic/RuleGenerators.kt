package com.elsoft.whatthewff.logic


// Represents the output of one backward step of generation.
data class GenerationStep(val newPremises: List<Formula>, val nextGoals: List<Formula>)

// A data class to bundle a generation strategy with its applicability check.
data class GenerationStrategy(
    val canApply: (goal: Formula) -> Boolean,
    val generate: (goal: Formula, availableVars: List<LogicTile>) -> GenerationStep?
)


object RuleGenerators {

    val modusPonens = GenerationStrategy(
        canApply = { goal ->
            true  // MP can apply to any goal.
        },
        generate = { goal, availableVars ->
            if (availableVars.isEmpty()) return@GenerationStrategy null
            val source = Formula(listOf(availableVars.first()))
            val premise = Formula(
                listOf(AvailableTiles.leftParen) + source.tiles + listOf(AvailableTiles.implies) + goal.tiles + listOf(AvailableTiles.rightParen)
            )
            GenerationStep(newPremises = listOf(premise), nextGoals = listOf(source))
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
            val qFormula = Formula(listOf(availableVars.first()))

            // The premise is always (P → Q).
            val premise = Formula(
                listOf(AvailableTiles.leftParen) + pFormula.tiles + listOf(AvailableTiles.implies) + qFormula.tiles + listOf(AvailableTiles.rightParen)
            )
            // The new goal is always ¬Q.
            val nextGoal = Formula(listOf(AvailableTiles.not) + qFormula.tiles)

            GenerationStep(newPremises = listOf(premise), nextGoals = listOf(nextGoal))
        }
    )

//    private fun generateHypotheticalSyllogismPremises(goal: Formula, source: Formula, availableVars: List<LogicTile>): List<Formula> {
//        // To get GOAL (p→r) from SOURCE (p), we need an intermediate variable (q)
//        // and the premises (p→q) and (q→r).
//        // For generation, we assume the goal is (source → some other var)
//        val p = source
//        val r = goal
//        val q = Formula(listOf(availableVars.first())) // Use an available var as the intermediate
//
//        val premise1 = Formula(
//            listOf(AvailableTiles.leftParen) + p.tiles + listOf(AvailableTiles.implies) + q.tiles + listOf(AvailableTiles.rightParen)
//        )
//        val premise2 = Formula(
//            listOf(AvailableTiles.leftParen) + q.tiles + listOf(AvailableTiles.implies) + r.tiles + listOf(AvailableTiles.rightParen)
//        )
//        return listOf(premise1, premise2)
//    }

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